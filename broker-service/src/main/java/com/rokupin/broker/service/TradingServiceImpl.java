package com.rokupin.broker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.broker.events.InputEvent;
import com.rokupin.broker.model.MissingRequiredTagException;
import com.rokupin.broker.model.StocksStateMessage;
import com.rokupin.broker.model.TradeRequest;
import com.rokupin.broker.model.TradeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class TradingServiceImpl implements TradingService {
    private final Mono<Connection> connection;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final Sinks.Many<String> initialStateSink;
    private final AtomicBoolean updateRequested;

    private final List<Map<String, Integer>> currentStockState;

    public TradingServiceImpl(ApplicationEventPublisher publisher,
                              ObjectMapper objectMapper) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.currentStockState = new ArrayList<>();
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
        if (updateRequested.compareAndSet(false, true)) {
            connection.flatMap(conn ->
                    conn.outbound()
                            .sendString(Mono.just("STATE_UPD"), StandardCharsets.UTF_8)
                            .then()
            ).subscribe();
        }

        return initialStateSink.asFlux().next();
    }

    private String serializeCurrentState() {
        try {
            return objectMapper.writeValueAsString(new StocksStateMessage(1, currentStockState.get(1)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize currentStockState");
        }
    }

    private void handleIncomingMessage(String message) {
        log.info("TCPService: received message: '{}'", message);
        try { // received state update
            updateState(objectMapper.readValue(message, StocksStateMessage.class));
        } catch (JsonProcessingException e) {
            try { // received trading response
                TradeResponse response = TradeResponse.fromFix(message, new TradeResponse());
                updateState(response);
            } catch (MissingRequiredTagException ex) {
                log.warn("TCP client received message that isn't a valid ExchangeStateUpdateMessage");
            }
        }
    }

    private void updateState(StocksStateMessage state) {
        int id = state.getStockId();
        boolean isInitialUpdate = currentStockState.isEmpty();

        while (currentStockState.size() <= id)
            currentStockState.add(null);

        if (currentStockState.get(id) != null) {
            currentStockState.set(id, state.getInstrumentToAvailableQty());
        } else {
            currentStockState.set(id, new HashMap<>(state.getInstrumentToAvailableQty()));
        }

        if (isInitialUpdate) {
            log.info("Emitting updated state: initial update");
            initialStateSink.tryEmitNext(serializeCurrentState()); // Unblock waiting clients
        } else {
            log.info("Publishing updated state: real update");
            publisher.publishEvent(new InputEvent<>(state)); // Only publish real updates
        }
    }

    private void updateState(TradeResponse response) {
        if (response.getOrdStatus() == TradeResponse.MSG_ORD_FILLED) {
            int id = response.getSender();
            if (currentStockState.size() <= id) {
                log.warn("Response from unknown stock id: {}", id);
                return;
            }

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
                            new StocksStateMessage(id, currentStockState.get(id))));
            publisher.publishEvent(new InputEvent<>(response));
            log.info("TCPService: published 2 events");
        }
    }
}
