package com.rokupin.exchange.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.exchange.model.InstrumentEntry;
import com.rokupin.exchange.repo.StockRepo;
import com.rokupin.model.StocksStateMessage;
import com.rokupin.model.fix.FixIdAssignation;
import com.rokupin.model.fix.FixRequest;
import com.rokupin.model.fix.FixResponse;
import com.rokupin.model.fix.MissingRequiredTagException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@Transactional
public class ExchangeServiceImpl {
    private final StockRepo stockRepo;
    private final Connection connection;
    private final ObjectMapper objectMapper;

    private String assignedId;

    public ExchangeServiceImpl(StockRepo stockRepo, ObjectMapper objectMapper,
                               @Value("${tcp.host}") String host,
                               @Value("${tcp.port}") int port) {
        this.objectMapper = objectMapper;
        this.stockRepo = stockRepo;
        this.connection = connectTcpClient(host, port);
    }

    private Connection connectTcpClient(String host, int port) {
        TcpClient client = TcpClient.create()
                .host(host)
                .port(port)
                .handle((inbound, outbound) -> inbound.receive()
                        .asString(StandardCharsets.UTF_8)
                        .flatMap(this::handleIncomingData)
                        .then());

        return client.connect()
                .doOnError(e -> log.info("Connection failed: {}", e.getMessage()))
                .retryWhen(retrySpec())
                .doOnSuccess(conn -> log.info("Connected successfully to {}:{}", host, port))
                .block();
    }

    private Retry retrySpec() {
        return Retry.backoff(5, Duration.ofSeconds(2)) // Retry up to 5 times with exponential backoff
                .maxBackoff(Duration.ofSeconds(10))   // Cap delay at 10 seconds
                .doBeforeRetry(signal -> log.info("Retrying connection, attempt {}", signal.totalRetriesInARow() + 1))
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                        new RuntimeException("Max retry attempts reached."));
    }

    private Mono<Void> handleIncomingData(String data) {
        log.info("Exchange service: received data: '{}'", data);

        return Flux.fromIterable(splitFixMessages(data)) // Split into individual messages
                .flatMap(this::handleIncomingMessage) // Process each message individually
                .then();
    }

    private List<String> splitFixMessages(String messages) {
        List<String> fixMessages = new ArrayList<>();
        StringBuilder currentMessage = new StringBuilder();
        String[] parts = messages.split("\u0001"); // Split by the SOH character

        for (String part : parts) {
            currentMessage.append(part).append("\u0001"); // Re-add the delimiter
            if (part.startsWith("10=")) { // Detect the end of a FIX message
                fixMessages.add(currentMessage.toString());
                currentMessage.setLength(0); // Reset for the next message
            }
        }

        return fixMessages;
    }

    private Mono<Void> handleIncomingMessage(String msg) {
        log.info("Exchange service: received message: '{}'", msg);

        try { // is it a trading request?
            FixRequest request = FixResponse.fromFix(msg, new FixRequest());
            if (!Objects.isNull(assignedId)) {
                return sendResponse(request);
            } else {
                log.warn("Exchange service: received trading request before ID was assigned");
            }
        } catch (MissingRequiredTagException e) {
            try { // is it an ID assignation message?
                FixIdAssignation idMsg = FixIdAssignation.fromFix(msg, new FixIdAssignation());

                if (assignedId.isEmpty()) {
                    assignedId = idMsg.getTarget();
                    return sendStateMessage();
                } else {
                    log.warn("Exchange service: ID re-assignation operation!");
                }
            } catch (MissingRequiredTagException ex) {
                log.warn("Exchange service: received message is not supported. Ignoring.");
            }
        } catch (IllegalStateException e) {
            log.warn("Exchange service: received message is not valid FIX request");
        }
        return Mono.empty();
    }

    private Mono<Void> sendStateMessage() {
        return stockRepo.findAll()
                .collectMap(InstrumentEntry::name, InstrumentEntry::amount)
                .flatMap(map -> {
                    try {
                        // Serialize the map to a JSON string
                        String jsonState = objectMapper.writeValueAsString(
                                new StocksStateMessage(Map.of(assignedId, map))
                        );

                        // Send the JSON string as a response
                        return connection.outbound()
                                .sendString(Mono.just(jsonState), StandardCharsets.UTF_8)
                                .then(); // Complete the send operation
                    } catch (JsonProcessingException e) {
                        // Handle JSON serialization error
                        return Mono.error(new RuntimeException("Failed to serialize state", e));
                    }
                });
    }

    private Mono<Void> sendResponse(FixRequest request) {
        return processTradeRequest(request)
                .flatMap(response -> {
                    try {
                        return connection.outbound()
                                .sendString(Mono.just(response.asFix()),
                                        StandardCharsets.UTF_8)
                                .then();
                    } catch (MissingRequiredTagException e) {
                        log.error("Exchange service: response assembly failed. This can't happen.");
                        return Mono.empty();
                    }
                })
                .doOnError(e -> log.error("Failed to process or respond to trade request: {}", e.getMessage()))
                .then();
    }

    public Mono<FixResponse> processTradeRequest(FixRequest request) {
        return stockRepo.findByName(request.getInstrument())
                .flatMap(entry -> {
                    FixResponse response = new FixResponse(
                            assignedId,                 // sender
                            request.getSender(),        // receiving service id
                            request.getSenderSubId(),   // receiving client id
                            request.getInstrument(),
                            request.getAction(),
                            request.getAmount(),
                            FixResponse.MSG_ORD_FILLED
                    );
                    if (entry.amount() >= request.getAmount() && request.getAction().equals("1")) {
                        // Update stock amount if buy action and sufficient quantity exists
                        return updateStockQuantity(entry, entry.amount() - request.getAmount())
                                .thenReturn(response);
                    } else if (request.getAction().equals("2")) {
                        // Update stock amount for a sell action (increasing stock quantity)
                        return updateStockQuantity(entry, entry.amount() + request.getAmount())
                                .thenReturn(response);
                    } else {
                        // Insufficient quantity for buy, reject order
                        response.setOrdStatus(FixResponse.MSG_ORD_REJECTED);
                        return Mono.just(response);
                    }
                });
    }

    private Mono<InstrumentEntry> updateStockQuantity(InstrumentEntry entry, int updatedAmount) {
        InstrumentEntry updatedEntry = new InstrumentEntry(
                entry.id(),
                entry.name(),
                updatedAmount
        );
        return stockRepo.save(updatedEntry);
    }
}
