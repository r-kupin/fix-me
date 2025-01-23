package com.rokupin.broker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.broker.events.BrokerEvent;
import com.rokupin.broker.model.StocksStateMessage;
import com.rokupin.model.fix.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class TradingServiceImpl implements TradingService {
    @Getter
    @Setter
    private String assignedId;

    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher publisher;
    private final AtomicBoolean connectionInProgress;
    private final AtomicBoolean updateRequested;
    // StockId : {Instrument : AmountAvailable}
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> currentStockState;
    private Sinks.Many<String> initialStateSink;
    private String routerId;

    public TradingServiceImpl(ApplicationEventPublisher publisher,
                              ObjectMapper objectMapper) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.currentStockState = new ConcurrentHashMap<>();
        this.updateRequested = new AtomicBoolean(false);
        this.connectionInProgress = new AtomicBoolean(false);
    }

// -------------------------- Events from Router processing

    @Override
    public void handleMessageFromRouter(String message) {
        log.debug("TCPService: processing message: '{}'", message);

        try { // received initial state update
            FixIdAssignationStockState initialMessage =
                    FixMessage.fromFix(message, new FixIdAssignationStockState());
            updateStateInitial(initialMessage);
        } catch (FixMessageMisconfiguredException e) {
            try { // received trading response
                FixResponse response =
                        FixMessage.fromFix(message, new FixResponse());
                updateStateOnResponse(response);
            } catch (FixMessageMisconfiguredException ex) {
                try { // received follow-up update
                    FixStockStateReport followUp =
                            FixMessage.fromFix(message, new FixStockStateReport());
                    updateStateFollowing(followUp);
                } catch (FixMessageMisconfiguredException exc) {
                    log.warn("TCP client received invalid message");
                }
            }
        }
    }

    private void updateStateInitial(FixIdAssignationStockState initialMessage) {
        try {
            ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> stocksStateMessages =
                    objectMapper.readValue(initialMessage.getStockJson(), new TypeReference<>() {});
            currentStockState.clear();
            stocksStateMessages.forEach(this::updateState);
            assignedId = initialMessage.getTarget();
            routerId = initialMessage.getSender();

            initialStateSink = Sinks.many().replay().all();
            initialStateSink.tryEmitNext(serializeCurrentState());

            log.debug("TCPService: stock state updated from initial update. " +
                    "Emitting update to the flux.");
            updateRequested.set(false);
        } catch (JsonProcessingException e) {
            log.warn("TCPService: received initial stock state JSON parsing failed");
        }
    }

    private void updateState(String stockId, ConcurrentHashMap<String, Integer> stockState) {
        if (currentStockState.containsKey(stockId)) {
            currentStockState.replace(stockId, stockState);
        } else {
            currentStockState.put(stockId, new ConcurrentHashMap<>(stockState));
        }
    }

    private void updateStateFollowing(FixStockStateReport stateReport) {
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
    }

    private void updateStateOnResponse(FixResponse response) {
        if (response.getRejectionReason() == FixResponse.EXCHANGE_IS_NOT_AVAILABLE &&
                currentStockState.containsKey(response.getSender())) {
            currentStockState.remove(response.getSender());
            publishCurrentStockState();
        }
        publisher.publishEvent(new BrokerEvent<>(response));
    }

// -------------------------- To be triggered by WS Handler

    @Override
    public String handleMessageFromClient(ClientTradingRequest clientMsg,
                                          String clientId) {
        try {
            FixRequest request = new FixRequest(clientMsg);
            request.setSenderSubId(clientId);
            publisher.publishEvent(new BrokerEvent<>(request));
            return "";
        } catch (FixMessageMisconfiguredException e) {
            log.warn("WSHandler [{}]: Fix Request creation failed: {}",
                    clientId, e.toString());
            return "Provided input can't be converted to the " +
                    "Fix Request because " + e;
        }
    }

    @Override
    public Mono<String> getState() {
        // If cache is not empty, return the cached state immediately
        if (!currentStockState.isEmpty()) {
            return Mono.just(serializeCurrentState());
        } else {
            // Stock state cache is empty
            if (updateRequested.compareAndSet(false, true)) {
                publisher.publishEvent(new BrokerEvent<>(
                        new FixStateUpdateRequest(assignedId, routerId))
                );
            } else if (!connectionInProgress.get()) {
                initialStateSink = Sinks.many().replay().all();
            }
        }
        // Listen to the flux, states will be published upon router's answer
        return initialStateSink.asFlux().next();
    }

    // -------------------------- Util
    private void publishCurrentStockState() {
        publisher.publishEvent(new BrokerEvent<>(
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
