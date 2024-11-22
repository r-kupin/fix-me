package com.rokupin.broker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.broker.events.InputEvent;
import com.rokupin.broker.model.StocksStateMessage;
import com.rokupin.model.fix.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class TradingServiceImpl implements TradingService {
    private final ObjectMapper objectMapper;
    private final String host;
    private final int port;
    private final ApplicationEventPublisher publisher;
    private final AtomicBoolean connectionInProgress;
    private final AtomicBoolean updateRequested;
    // StockId : {Instrument : AmountAvailable}
    private final Map<String, Map<String, Integer>> currentStockState;
    private Sinks.Many<String> initialStateSink;
    private Connection connection;
    private String assignedId;

    public TradingServiceImpl(ApplicationEventPublisher publisher,
                              ObjectMapper objectMapper,
                              @Value("${tcp.host}") String host,
                              @Value("${tcp.port}") int port) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.host = host;
        this.port = port;
        this.currentStockState = new ConcurrentHashMap<>();
        this.updateRequested = new AtomicBoolean(false);
        this.connectionInProgress = new AtomicBoolean(false);
        this.initialStateSink = Sinks.many().replay().all();
    }

// -------------------------- Connection to Router

    private void connect() {
        TcpClient.create()
                .host(host)
                .port(port)
                .handle((inbound, outbound) -> inbound.receive()
                        .asString(StandardCharsets.UTF_8)
                        .flatMap(this::handleIncomingData)
                        .then()
                ).connect()
                .retryWhen(retrySpec())
                .doOnError(e -> {
                    log.info("TCPService: Connection failed: {}", e.getMessage());
                    connection = null;
                }).doOnSuccess(conn -> {
                    this.connection = conn;
                    connectionInProgress.set(false);
                    log.info("TCPService: Connected successfully to {}:{}", host, port);
                }).onErrorResume(e -> {
                    initialStateSink.tryEmitNext(
                            "Router service is unavailable. Try to reconnect later.");
                    log.info("TCPService: Connection attempts exhausted. Reporting failure.");
                    connectionInProgress.set(false);
                    return Mono.empty();
                }).subscribe();
    }

    private Retry retrySpec() {
        return Retry.backoff(5, Duration.ofSeconds(2))
                .maxBackoff(Duration.ofSeconds(10))
                .doBeforeRetry(signal -> log.info("TCPService: retrying connection, attempt {}", signal.totalRetriesInARow() + 1))
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                        new RuntimeException("TCPService: Max retry attempts reached."));
    }

// -------------------------- Events from Router processing

    private Mono<Void> handleIncomingData(String data) {
        log.info("TCPservice: received data: '{}'", data);

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

    private Mono<Void> handleIncomingMessage(String message) {
        log.info("TCPService: processing message: '{}'", message);
        try { // received initial state update
            FixIdAssignationStockState initialMessage =
                    FixMessage.fromFix(message, new FixIdAssignationStockState());
            return updateStateInitial(initialMessage);
        } catch (MissingRequiredTagException e) {
            try { // received trading response
                FixResponse response =
                        FixMessage.fromFix(message, new FixResponse());
                return updateState(response);
            } catch (MissingRequiredTagException ex) {
                try {
                    FixStockStateReport followUp =
                            FixMessage.fromFix(message, new FixStockStateReport());
                    return updateStateFollowing(followUp);
                } catch (MissingRequiredTagException exc) {
                    log.warn("TCP client received invalid message");
                    return Mono.empty();
                }
            }
        }
    }

    private Mono<Void> updateStateInitial(FixIdAssignationStockState initialMessage) {
        if (Objects.isNull(assignedId)) {
            try {
                Map<String, Map<String, Integer>> stocksStateMessages = objectMapper.readValue(initialMessage.getStockJson(), new TypeReference<>() {
                });
                stocksStateMessages.forEach(this::updateState);
                assignedId = initialMessage.getTarget();
                initialStateSink.tryEmitNext(serializeCurrentState());
                log.info("TCPService: stock state updated from initial update. " +
                        "Emitting update to the flux.");
                updateRequested.set(false);
            } catch (JsonProcessingException e) {
                log.warn("TCPService: received initial stock state JSON parsing failed");
            }
        } else {
            log.warn("TCPService: repeatable init messages received.");
        }
        return Mono.empty();
    }

    private void updateState(String stockId, Map<String, Integer> stockState) {
        if (currentStockState.containsKey(stockId)) {
            currentStockState.replace(stockId, stockState);
        } else {
            currentStockState.put(stockId, new HashMap<>(stockState));
        }
    }

    private Mono<Void> updateStateFollowing(FixStockStateReport stateReport) {
        try {
            Map<String, Map<String, Integer>> state = objectMapper.readValue(
                    stateReport.getStockJson(), new TypeReference<>() {
                    });
            state.forEach((stockId, stockState) -> {
                updateState(stockId, stockState);
                publisher.publishEvent(
                        new InputEvent<>(
                                new StocksStateMessage(Map.of(stockId, stockState))));
            });
            log.info("TCPService: stock state updated from router-sent state update. " +
                    "Publishing update event.");
            updateRequested.set(false);
        } catch (JsonProcessingException e) {
            log.warn("TCPService: received follow-up stock state JSON parsing failed");
        }
        return Mono.empty();
    }

    // todo: Might not publish response if went wrong
    private Mono<Void> updateState(FixResponse response) {
        String id = response.getSender();

        if (!currentStockState.containsKey(id)) {
            log.warn("Response from unknown stock id: {}", id);
            return Mono.empty();
        }
        if (response.getOrdStatus() == FixResponse.MSG_ORD_FILLED) {
            Map<String, Integer> stock = currentStockState.get(id);
            String instrument = response.getInstrument();
            if (!stock.containsKey(instrument)) {
                log.warn("Stock id: {} sent response on unknown " +
                        "instrument: {}", id, instrument);
                return Mono.empty();
            }

            int before = stock.get(instrument);
            int after = response.getAction().equals("buy") ?
                    before - response.getAmount() : before + response.getAmount();
            if (after < 0) {
                log.warn("Remaining instrument amount can't be negative." +
                                "stock response: '{}', current amount: {}",
                        response, before);
                return Mono.empty();
            }
            stock.replace(instrument, after);
            publisher.publishEvent(
                    new InputEvent<>(
                            new StocksStateMessage(
                                    Map.of(id, currentStockState.get(id)))));

            log.info("TCPService: published 2 events");
        }
        publisher.publishEvent(new InputEvent<>(response));
        return Mono.empty();
    }

// -------------------------- To be triggered by WS Handler

    @Override
    public void initiateRouterConnection() {
        if (Objects.isNull(connection) &&
                connectionInProgress.compareAndSet(false, true)) {
            connect();
        }
    }

    @Override
    public Mono<String> handleTradingRequest(FixRequest tradeRequest) {
        if (Objects.nonNull(connection)) {
            try {
                if (assignedId.isEmpty())
                    return Mono.just("Trading request not sent:" +
                            " this broker service has no assigned ID");
                tradeRequest.setSender(assignedId);
                String fix = tradeRequest.asFix();
                log.info("TCPService: sending message '{}'", fix);
                return connection.outbound()
                        .sendString(Mono.just(fix), StandardCharsets.UTF_8)
                        .then()
                        .thenReturn("Trading request sent")
                        .onErrorResume(e -> Mono.just("Trading request not sent:" +
                                " router service isn't available"));
            } catch (MissingRequiredTagException e) {
                return Mono.just("Trading request not sent: " + e);
            }
        } else {
            initiateRouterConnection();
            if (Objects.isNull(connection))
                return Mono.just("Trading request not sent:" +
                        "Router service is unavailable.");
            else
                return handleTradingRequest(tradeRequest);
        }
    }

    @Override
    public Mono<String> getState() {
        // If cache is not empty, return the cached state immediately
        if (!currentStockState.isEmpty()) {
            return Mono.just(serializeCurrentState());
        } else {
            // Stock state cache is empty
            if (Objects.nonNull(connection)) {
                // connection established
                if (updateRequested.compareAndSet(false, true)) {
                    // Ask for initialisation
                    connection.outbound()
                            .sendString(Mono.just("STATE_UPD"),
                                    StandardCharsets.UTF_8);
                }
            } else {
                if (!connectionInProgress.get()) {
                    initialStateSink = Sinks.many().replay().all();
                    initiateRouterConnection();
                }
            }
        }
        // Listen to the flux, states will be published upon router's answer
        return initialStateSink.asFlux().next();
    }

// -------------------------- Util

    private String serializeCurrentState() {
        try {
            return objectMapper.writeValueAsString(currentStockState);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize currentStockState");
        }
    }
}
