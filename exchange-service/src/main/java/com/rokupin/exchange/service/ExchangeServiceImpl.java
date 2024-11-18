package com.rokupin.exchange.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.exchange.model.InstrumentEntry;
import com.rokupin.exchange.repo.StockRepo;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Transactional
public class ExchangeServiceImpl {
    private final StockRepo stockRepo;
    private final ObjectMapper objectMapper;
    private final Connection connection;

    private String assignedId;

    public ExchangeServiceImpl(StockRepo stockRepo, ObjectMapper objectMapper,
                               @Value("${tcp.host}") String host,
                               @Value("${tcp.port}") int port) {
        this.stockRepo = stockRepo;
        this.objectMapper = objectMapper;
        this.connection = connectTcpClient(host, port);
        this.assignedId = "";
    }

    private Connection connectTcpClient(String host, int port) {
        TcpClient client = TcpClient.create()
                .host(host)
                .port(port)
                .handle((inbound, outbound) ->
                        inbound.receive()
                                .asString(StandardCharsets.UTF_8)
                                .flatMap(this::handleIncomingData)
                                .then());
        client.warmup().block();
        return persistConnectionAttempts(client);
    }

    private Connection persistConnectionAttempts(TcpClient client) {
        try {
            return client.connect().block(Duration.ofSeconds(10));
        } catch (Exception e) {
            log.info("Can't connect: {}", e.getMessage());
        }
        return persistConnectionAttempts(client);
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

        try {
            FixRequest request = FixResponse.fromFix(msg, new FixRequest());
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
        } catch (MissingRequiredTagException e) {
            try {
                FixIdAssignation idMsg = FixIdAssignation.fromFix(msg, new FixIdAssignation());

                if (assignedId.isEmpty())
                    assignedId = idMsg.getTarget();
                else
                    log.warn("Exchange service: ID re-assignation operation!");
            } catch (MissingRequiredTagException ex) {
                log.warn("Exchange service: received message is not supported. Ignoring.");
            }
        }
        return Mono.empty();
    }

    public Mono<FixResponse> processTradeRequest(FixRequest request) {
        return stockRepo.findByName(request.getInstrument())
                .flatMap(entry -> {
                    FixResponse response = new FixResponse(
                            request.getTarget(),
                            request.getSender(),
                            request.getSenderSubId(),
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
        // Update the quantity in the database and return the updated entry
        InstrumentEntry updatedEntry = new InstrumentEntry(entry.id(), entry.name(), updatedAmount);
        return stockRepo.save(updatedEntry);
    }
}