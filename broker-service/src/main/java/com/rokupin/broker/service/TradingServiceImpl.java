package com.rokupin.broker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.broker.events.InputEvent;
import com.rokupin.broker.model.InitialStockStateMessage;
import com.rokupin.broker.model.StocksStateMessage;
import com.rokupin.model.fix.MissingRequiredTagException;
import com.rokupin.model.fix.TradeRequest;
import com.rokupin.model.fix.TradeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;

import java.nio.charset.StandardCharsets;
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
                              ObjectMapper objectMapper) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.currentStockState = new ConcurrentHashMap<>();
        this.updateRequested = new AtomicBoolean(false);
        this.initialStateSink = Sinks.many().replay().all();

        connection = TcpClient.create()
                .host("localhost")
                .port(5000)
                .handle((inbound, outbound) ->
                        // handle the incoming data (responses / updates from router)
                        inbound.receive()
                                .asString(StandardCharsets.UTF_8)
                                .doOnNext(this::handleIncomingMessage)
                                .then() // Handle incoming messages
                )
                .connect()
                .cast(Connection.class); // lazy, initialized on first call
    }

    @Override
    public Mono<String> handleTradingRequest(TradeRequest tradeRequest) {
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

    private void handleIncomingMessage(String message) {
        log.info("TCPService: received message: '{}'", message);
        try { // received state update
            updateStateInitial(message);
        } catch (JsonProcessingException e) {
            try { // received trading response
                updateState(TradeResponse.fromFix(message, new TradeResponse()));
            } catch (MissingRequiredTagException ex) {
                try {
                    updateStateFollowing(message);
                } catch (JsonProcessingException exception) {
                    log.warn("TCP client received invalid message");
                }
            }
        }
    }

    private void updateState(String stockId, Map<String, Integer> stockState) {
        if (currentStockState.containsKey(stockId)) {
            currentStockState.replace(stockId, stockState);
        } else {
            currentStockState.put(stockId, new HashMap<>(stockState));
        }
    }

    private void updateStateInitial(String message) throws JsonProcessingException {
        InitialStockStateMessage initialMessage =
                objectMapper.readValue(message, InitialStockStateMessage.class);
        if (Objects.isNull(assignedId)) {
            initialMessage.getStocks().forEach(this::updateState);
            assignedId = initialMessage.getAssignedId();
            initialStateSink.tryEmitNext(serializeCurrentState());
            log.info("TCPService: stock state updated from initial update. " +
                    "Emitting update to the flux.");
        } else {
            log.warn("TCPService: repeatable init messages received.");
        }
    }

    private void updateStateFollowing(String message) throws JsonProcessingException {
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
    }

    private void updateState(TradeResponse response) {
        String id = response.getSender();

        if (!currentStockState.containsKey(id)) {
            log.warn("Response from unknown stock id: {}", id);
            return;
        }
        if (response.getOrdStatus() == TradeResponse.MSG_ORD_FILLED) {
            Map<String, Integer> stock = currentStockState.get(id);
            String instrument = response.getInstrument();
            if (!stock.containsKey(instrument)) {
                log.warn("Stock id: {} sent response on unknown " +
                        "instrument: {}", id, instrument);
                return;
            }

            int before = stock.get(instrument);
            int after = response.getAction().equals("buy") ?
                    before - response.getAmount() : before + response.getAmount();
            if (after < 0) {
                log.warn("Remaining instrument amount can't be negative." +
                                "stock response: '{}', current amount: {}",
                        response, before);
                return;
            }
            stock.replace(instrument, after);
            publisher.publishEvent(
                    new InputEvent<>(
                            new StocksStateMessage(
                                    Map.of(id, currentStockState.get(id)))));

            log.info("TCPService: published 2 events");
        }
        publisher.publishEvent(new InputEvent<>(response));
    }
}
