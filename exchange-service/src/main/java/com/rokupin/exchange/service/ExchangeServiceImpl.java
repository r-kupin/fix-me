package com.rokupin.exchange.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.exchange.model.InstrumentEntry;
import com.rokupin.exchange.repo.StockRepo;
import com.rokupin.model.fix.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
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
    public final int MAX_AMOUNT = 1_000_000_000;
    private final StockRepo stockRepo;
    private final Connection connection;
    private final ObjectMapper objectMapper;
    private final FixMessageProcessor routerInputProcessor;
    private String assignedId;

    public ExchangeServiceImpl(StockRepo stockRepo, ObjectMapper objectMapper,
                               @Value("${tcp.host}") String host,
                               @Value("${tcp.port}") int port) {
        this.objectMapper = objectMapper;
        this.stockRepo = stockRepo;
        this.routerInputProcessor = new FixMessageProcessor();
        this.connection = connectTcpClient(host, port);
    }

    @PostConstruct
    private void checkDB() {
        Long instruments_amount = stockRepo.count().block();
        if (Objects.nonNull(instruments_amount) && instruments_amount < 1) {
            log.error("Database is empty, nothing to trade");
            System.exit(1);
        }
        InstrumentEntry record = stockRepo.findAll()
                .filter(entry ->
                        entry.amount() > MAX_AMOUNT || entry.amount() < 0
                ).next()
                .block();
        if (Objects.nonNull(record)) {
            log.error("Instrument {} amount of {} is unacceptable. " +
                            "Amount should be from 0 to {}."
                    , record.name(), record.amount(), MAX_AMOUNT);
            System.exit(1);
        }
    }

    private Connection connectTcpClient(String host, int port) {

        TcpClient client = TcpClient.create()
                .host(host)
                .port(port)
                .doOnConnect(con -> routerInputProcessor.getFlux()
                        .flatMap(this::handleIncomingMessage)
                        .subscribe())
                .handle((inbound, outbound) -> inbound.receive()
                        .asString(StandardCharsets.UTF_8)
                        .doOnNext(routerInputProcessor::processInput)
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

    private Mono<Void> handleIncomingMessage(String msg) {
        log.debug("Received message: '{}'", msg);

        try { // is it a trading request?
            FixRequest request = FixMessage.fromFix(msg, new FixRequest());
            log.debug("Processing trading request");
            if (!Objects.isNull(assignedId)) {
                return prepareSendResponse(request);
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

    Mono<String> publishCurrentStockState() {
        return stockRepo.findAll()
                .collectMap(InstrumentEntry::name, InstrumentEntry::amount)
                .flatMap(map -> {
                    try {
                        return Mono.just(
                                new FixStockStateReport(assignedId,
                                        objectMapper.writeValueAsString(map)
                                ).asFix()
                        );
                    } catch (JsonProcessingException e) {
                        return Mono.error(new RuntimeException("Failed to serialize state", e));
                    } catch (FixMessageMisconfiguredException e) {
                        return Mono.error(new RuntimeException("Failed make a fix message", e));
                    }
                });
    }

    private Mono<Void> sendStateMessage() {
        return publishCurrentStockState()
                .flatMap(stateReport -> {
                    log.debug("Sending state report: {}", stateReport);
                    return connection.outbound()
                            .sendString(Mono.just(stateReport), StandardCharsets.UTF_8)
                            .then();
                });
    }

    private Mono<Void> prepareSendResponse(FixRequest request) {
        return processTradeRequest(request)
                .flatMap(response -> {
                    try {
                        log.debug("Sending response: {}", response.asFix());
                        Publisher<String> to_send;
                        if (response.getOrdStatus() == FixResponse.MSG_ORD_FILLED) {
                            to_send = Flux.concat(Mono.just(response.asFix()),
                                    publishCurrentStockState());
                        } else {
                            to_send = Mono.just(response.asFix());
                        }
                        return connection.outbound()
                                .sendString(to_send, StandardCharsets.UTF_8)
                                .then();
                    } catch (FixMessageMisconfiguredException e) {
                        log.error("Response assembly failed. This can't happen.");
                        return Mono.empty();
                    }
                }).doOnError(e -> log.error(
                        "Failed to process or respond to trade request: {}",
                        e.getMessage())
                ).then();
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
                    .flatMap(entry -> prepareResponse(entry, request, response))
                    .switchIfEmpty(Mono.defer(() -> {
                        response.setOrdStatus(FixResponse.MSG_ORD_REJECTED);
                        response.setRejectionReason(FixResponse.INSTRUMENT_NOT_SUPPORTED);
                        return Mono.just(response);
                    }));
        } catch (FixMessageMisconfiguredException e) {
            log.error("Response creation failed: '{}'", e.getMessage());
            return Mono.empty();
        }
    }

    private Mono<FixResponse> prepareResponse(InstrumentEntry entry,
                                              FixRequest request,
                                              FixResponse response) {
        if (request.getAction() == FixRequest.SIDE_BUY) {
            if (entry.amount() >= request.getAmount()) {
                return updateStockQuantity(entry,
                        entry.amount() - request.getAmount()
                ).thenReturn(response);
            } else {
                response.setOrdStatus(FixResponse.MSG_ORD_REJECTED);
                response.setRejectionReason(FixResponse.EXCHANGE_LACKS_REQUESTED_AMOUNT);
                return Mono.just(response);
            }
        } else if (request.getAction() == FixRequest.SIDE_SELL) {
            if (entry.amount() + request.getAmount() <= MAX_AMOUNT) {
                return updateStockQuantity(entry,
                        entry.amount() + request.getAmount()
                ).thenReturn(response);
            } else {
                response.setOrdStatus(FixResponse.MSG_ORD_REJECTED);
                response.setRejectionReason(FixResponse.TOO_MUCH);
                return Mono.just(response);
            }
        } else {
            response.setOrdStatus(FixResponse.MSG_ORD_REJECTED);
            response.setRejectionReason(FixResponse.ACTION_UNSUPPORTED);
            return Mono.just(response);
        }
    }

    private Mono<InstrumentEntry> updateStockQuantity(InstrumentEntry entry, int updatedAmount) {
        InstrumentEntry updatedEntry = new InstrumentEntry(
                entry.id(), entry.name(), updatedAmount
        );
        return stockRepo.save(updatedEntry);
    }
}
