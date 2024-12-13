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
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
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
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> currentStockState;
    private Sinks.Many<String> initialStateSink;
    private Connection connection;
    private String assignedId;
    private final FixMessageProcessor routerInputProcessor;

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
        this.routerInputProcessor = new FixMessageProcessor();
    }

// -------------------------- Connection to Router

    private void connect() {
        TcpClient.create()
                .host(host)
                .port(port)
                .doOnConnect(connection -> routerInputProcessor.getFlux()
                        .flatMap(this::handleIncomingMessage)
                        .subscribe()
                ).handle((inbound, outbound) -> inbound.receive()
                        .asString(StandardCharsets.UTF_8)
                        .doOnNext(routerInputProcessor::processInput)
                        .then()
                ).connect()
                .retryWhen(retrySpec())
                .doOnError(e -> {
                    log.warn("TCPService: Connection failed: {}", e.getMessage());
                    connection = null;
                }).doOnSuccess(connection -> {
                    this.connection = connection;
                    connectionInProgress.set(false);
                    log.info("TCPService: Connected successfully to {}:{}", host, port);
                }).onErrorResume(e -> {
                    initialStateSink.tryEmitNext(
                            "Router service is unavailable. Try to reconnect later.");
                    log.error("TCPService: Connection attempts exhausted. Reporting failure.");
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

    private Mono<Void> handleIncomingMessage(String message) {
        log.debug("TCPService: processing message: '{}'", message);
        try { // received initial state update
            FixIdAssignationStockState initialMessage =
                    FixMessage.fromFix(message, new FixIdAssignationStockState());
            return updateStateInitial(initialMessage);
        } catch (FixMessageMisconfiguredException e) {
            try { // received trading response
                FixResponse response =
                        FixMessage.fromFix(message, new FixResponse());
                return updateStateOnResponse(response);
            } catch (FixMessageMisconfiguredException ex) {
                try { // received follow-up update
                    FixStockStateReport followUp =
                            FixMessage.fromFix(message, new FixStockStateReport());
                    return updateStateFollowing(followUp);
                } catch (FixMessageMisconfiguredException exc) {
                    log.warn("TCP client received invalid message");
                    return Mono.empty();
                }
            }
        }
    }

    private Mono<Void> updateStateInitial(FixIdAssignationStockState initialMessage) {
        if (Objects.isNull(assignedId)) {
            try {
                ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> stocksStateMessages =
                        objectMapper.readValue(initialMessage.getStockJson(),
                                new TypeReference<>() {});
                currentStockState.clear();
                stocksStateMessages.forEach(this::updateState);
                assignedId = initialMessage.getTarget();
                initialStateSink.tryEmitNext(serializeCurrentState());
                log.debug("TCPService: stock state updated from initial update. " +
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

    private void updateState(String stockId, ConcurrentHashMap<String, Integer> stockState) {
        if (currentStockState.containsKey(stockId)) {
            currentStockState.replace(stockId, stockState);
        } else {
            currentStockState.put(stockId, new ConcurrentHashMap<>(stockState));
        }
    }

    private Mono<Void> updateStateFollowing(FixStockStateReport stateReport) {
        try {
            ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> state =
                    objectMapper.readValue(stateReport.getStockJson(),
                            new TypeReference<>() {}
                    );
            currentStockState.clear();
            state.forEach(this::updateState);
            publishCurrentStockState();
        } catch (JsonProcessingException e) {
            log.warn("TCPService: received follow-up stock state JSON parsing failed");
        }
        return Mono.empty();
    }

    private Mono<Void> updateStateOnResponse(FixResponse response) {
        if (response.getRejectionReason() == FixResponse.EXCHANGE_IS_NOT_AVAILABLE &&
                currentStockState.containsKey(response.getSender())) {
            currentStockState.remove(response.getSender());
            publishCurrentStockState();
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
            } catch (FixMessageMisconfiguredException e) {
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
                            .sendString(publishFixStateUpdateRequest(),
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
    private Mono<String> publishFixStateUpdateRequest() {
        try {
            return Mono.just(new FixStateUpdateRequest(assignedId).asFix());
        } catch (FixMessageMisconfiguredException e) {
            log.error("TCPService: can't make FixStateUpdateRequest");
            return Mono.empty();
        }
    }

    private void publishCurrentStockState() {
        publisher.publishEvent(new InputEvent<>(
                new StocksStateMessage(
                        Map.copyOf(currentStockState))));
        log.debug("TCPService: published stock update event");
        updateRequested.set(false);
    }

    private String serializeCurrentState() {
        try {
            return objectMapper.writeValueAsString(
                    new StocksStateMessage(
                            Map.copyOf(currentStockState)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize currentStockState");
        }
    }
}
