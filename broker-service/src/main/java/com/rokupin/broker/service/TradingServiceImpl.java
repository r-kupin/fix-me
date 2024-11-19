package com.rokupin.broker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.broker.events.InputEvent;
import com.rokupin.broker.model.InitialStockStateMessage;
import com.rokupin.model.StocksStateMessage;
import com.rokupin.model.fix.FixRequest;
import com.rokupin.model.fix.FixResponse;
import com.rokupin.model.fix.MissingRequiredTagException;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class TradingServiceImpl implements TradingService {
    private final ObjectMapper objectMapper;

    private final Mono<Connection> connection;
    private final ApplicationEventPublisher publisher;
    private final Sinks.Many<String> initialStateSink;
    private final AtomicBoolean updateRequested;

    // StockId : {Instrument : AmountAvailable}
    private final Map<String, Map<String, Integer>> currentStockState;

    private String assignedId;

    public TradingServiceImpl(ApplicationEventPublisher publisher,
                              ObjectMapper objectMapper,
                              @Value("${tcp.host}") String host,
                              @Value("${tcp.port}") int port) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.currentStockState = new ConcurrentHashMap<>();
        this.updateRequested = new AtomicBoolean(false);
        this.initialStateSink = Sinks.many().replay().all();

        connection = TcpClient.create()
                .host(host)
                .port(port)
                .handle((inbound, outbound) -> inbound.receive()
                        .asString(StandardCharsets.UTF_8)
                        .flatMap(this::handleIncomingMessage)
                        .then()
                ).connect()
                .doOnError(e -> log.info("Connection failed: {}", e.getMessage()))
                .retryWhen(retrySpec())
                .doOnSuccess(conn -> log.info("Connected successfully to {}:{}", host, port))
                .cast(Connection.class);
    }

    private Retry retrySpec() {
        return Retry.backoff(5, Duration.ofSeconds(2)) // Retry up to 5 times with exponential backoff
                .maxBackoff(Duration.ofSeconds(10))   // Cap delay at 10 seconds
                .doBeforeRetry(signal -> log.info("Retrying connection, attempt {}", signal.totalRetriesInARow() + 1))
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                        new RuntimeException("Max retry attempts reached."));
    }

    private Mono<Void> handleIncomingMessage(String message) {
        log.info("TCPService: received message: '{}'", message);
        try { // received state update
            InitialStockStateMessage initialMessage = objectMapper.readValue(
                    message, InitialStockStateMessage.class);
            return updateStateInitial(initialMessage);
        } catch (JsonProcessingException e) {
            try { // received trading response
                FixResponse response = FixResponse.fromFix(message, new FixResponse());
                return updateState(response);
            } catch (MissingRequiredTagException ex) {
                try {
                    return updateStateFollowing(message);
                } catch (JsonProcessingException exception) {
                    log.warn("TCP client received invalid message");
                    return Mono.empty();
                }
            }
        }
    }

    private Mono<Void> updateStateInitial(InitialStockStateMessage initialMessage) {
        if (Objects.isNull(assignedId)) {
            initialMessage.getStocks().forEach(this::updateState);
            assignedId = initialMessage.getAssignedId();
            initialStateSink.tryEmitNext(serializeCurrentState());
            log.info("TCPService: stock state updated from initial update. " +
                    "Emitting update to the flux.");
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

    private Mono<Void> updateStateFollowing(String message) throws JsonProcessingException {
        Map<String, Map<String, Integer>> state =
                objectMapper.readValue(message, new TypeReference<>() {
                });
        state.forEach((stockId, stockState) -> {
            updateState(stockId, stockState);
            publisher.publishEvent(
                    new InputEvent<>(
                            new StocksStateMessage(Map.of(stockId, stockState))));
        });
        log.info("TCPService: stock state updated from router-sent state update. " +
                "Publishing update event.");
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

    @Override
    public Mono<String> handleTradingRequest(FixRequest tradeRequest) {
        try {
            if (assignedId.isEmpty())
                return Mono.just("Trading request not sent:" +
                        " this broker service has no assigned ID");
            tradeRequest.setSender(assignedId);
            String fix = tradeRequest.asFix();
            log.info("TCPService: sending message '{}'", fix);
            return connection.flatMap(con -> con.outbound()
                            .sendString(Mono.just(fix), StandardCharsets.UTF_8)
                            .then()
                            .thenReturn("Trading request sent"))
                    .onErrorResume(e -> Mono.just("Trading request not sent:" +
                            " router service isn't available"));
        } catch (MissingRequiredTagException e) {
            return Mono.just("Trading request not sent: " + e);
        }
    }

    public Mono<String> getState() {
        // If cache is not empty, return the cached state immediately
        if (!currentStockState.isEmpty()) {
            return Mono.just(serializeCurrentState());
        }

        // Stock state cache is empty
        if (updateRequested.compareAndSet(false, true)) {
            // Ask for initialisation
            connection.flatMap(conn -> conn.outbound()
                    .sendString(Mono.just("STATE_UPD"), StandardCharsets.UTF_8)
                    .then()
            ).subscribe();
        }
        // Listen to the flux, states will be published upon router's answer
        return initialStateSink.asFlux().next();
    }

    private String serializeCurrentState() {
        try {
            return objectMapper.writeValueAsString(currentStockState);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize currentStockState");
        }
    }
}
