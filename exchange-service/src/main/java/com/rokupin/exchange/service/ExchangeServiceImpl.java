package com.rokupin.exchange.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.exchange.model.InstrumentEntry;
import com.rokupin.exchange.repo.StockRepo;
import com.rokupin.model.fix.*;
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
        return Retry.backoff(5, Duration.ofSeconds(2))
                .maxBackoff(Duration.ofSeconds(10))
                .doBeforeRetry(signal -> log.info("Retrying connection, attempt {}",
                        signal.totalRetriesInARow() + 1))
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                        new RuntimeException("Max retry attempts reached."));
    }

    private Mono<Void> handleIncomingData(String data) {
        return Flux.fromIterable(FixMessage.splitFixMessages(data))
                .flatMap(this::handleIncomingMessage)
                .then();
    }

    private Mono<Void> handleIncomingMessage(String msg) {
        log.debug("Received message: '{}'", msg);

        try { // is it a trading request?
            FixRequest request = FixMessage.fromFix(msg, new FixRequest());
            log.debug("Processing trading request");
            if (!Objects.isNull(assignedId)) {
                return sendResponse(request);
            } else {
                log.warn("Received trading request before ID was assigned");
            }
        } catch (FixMessageMisconfiguredException e) {
            try { // is it an ID assignation message?
                FixIdAssignation idMsg = FixMessage.fromFix(msg, new FixIdAssignation());

                if (Objects.isNull(assignedId)) {
                    assignedId = idMsg.getTarget();
                    return sendStateMessage();
                } else {
                    log.warn("Re-assignation of the ID");
                }
            } catch (FixMessageMisconfiguredException ex) {
                log.warn("Received message is not supported. Ignoring.");
            }
        } catch (IllegalStateException e) {
            log.warn("Received message is not valid FIX request");
        }
        return Mono.empty();
    }

    private Mono<Void> sendStateMessage() {
        return stockRepo.findAll()
                .collectMap(InstrumentEntry::name, InstrumentEntry::amount)
                .flatMap(map -> {
                    try {
                        String stateReport = new FixStockStateReport(
                                assignedId,
                                objectMapper.writeValueAsString(map)
                        ).asFix();
                        log.debug("Sendind state report: {}", stateReport);
                        return connection.outbound()
                                .sendString(Mono.just(stateReport), StandardCharsets.UTF_8)
                                .then();
                    } catch (JsonProcessingException e) {
                        return Mono.error(new RuntimeException("Failed to serialize state", e));
                    } catch (FixMessageMisconfiguredException e) {
                        return Mono.error(new RuntimeException("Failed make a fix message", e));
                    }
                });
    }

    private Mono<Void> sendResponse(FixRequest request) {
        return processTradeRequest(request)
                .flatMap(response -> {
                    try {
                        log.debug("Sending response: {}", response.asFix());
                        return connection.outbound()
                                .sendString(Mono.just(response.asFix()),
                                        StandardCharsets.UTF_8)
                                .then();
                    } catch (FixMessageMisconfiguredException e) {
                        log.error("Response assembly failed. This can't happen.");
                        return Mono.empty();
                    }
                })
                .doOnError(e -> log.error("Failed to process or respond to trade request: {}", e.getMessage()))
                .then();
    }

    public Mono<FixResponse> processTradeRequest(FixRequest request) {
        try {
            FixResponse response = new FixResponse(
                    assignedId,                 // sender
                    request.getSender(),        // receiving service id
                    request.getSenderSubId(),   // receiving client id
                    request.getInstrument(),
                    request.getAction(),
                    request.getAmount(),
                    FixResponse.MSG_ORD_FILLED,
                    FixResponse.UNSPECIFIED
            );
            return stockRepo.findByName(request.getInstrument())
                    .flatMap(entry -> {
                        if (request.getAction() == FixRequest.SIDE_BUY &&
                                entry.amount() >= request.getAmount()) {
                            return updateStockQuantity(entry, entry.amount() - request.getAmount())
                                    .thenReturn(response);
                        } else if (request.getAction() == FixRequest.SIDE_SELL) {
                            return updateStockQuantity(entry, entry.amount() + request.getAmount())
                                    .thenReturn(response);
                        } else {
                            response.setOrdStatus(FixResponse.MSG_ORD_REJECTED);
                            response.setRejectionReason(FixResponse.EXCHANGE_LACKS_REQUESTED_AMOUNT);
                            return Mono.just(response);
                        }
                    }).switchIfEmpty(Mono.defer(() -> {
                        response.setOrdStatus(FixResponse.MSG_ORD_REJECTED);
                        response.setRejectionReason(FixResponse.INSTRUMENT_NOT_SUPPORTED);
                        return Mono.just(response);
                    }));
        } catch (FixMessageMisconfiguredException e) {
            log.error("Response creation failed: '{}'", e.getMessage());
            return Mono.empty();
        }
    }

    private Mono<InstrumentEntry> updateStockQuantity(InstrumentEntry entry, int updatedAmount) {
        InstrumentEntry updatedEntry = new InstrumentEntry(
                entry.id(), entry.name(), updatedAmount
        );
        return stockRepo.save(updatedEntry);
    }
}
