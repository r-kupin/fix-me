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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class TradingServiceImpl implements TradingService {
    @Getter
    @Setter
    private String assignedId;
    @Getter
    @Setter
    private String routerId;

    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher publisher;
    // StockId : {Instrument : AmountAvailable}
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> currentStockState;

    public TradingServiceImpl(ApplicationEventPublisher publisher,
                              ObjectMapper objectMapper) {
        this.assignedId = "not assigned";
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.currentStockState = new ConcurrentHashMap<>();
    }

    @Override
    public void handleMessageFromRouter(String message) {
        log.debug("Trading service: processing message: '{}'", message);

        try { // received initial state update
            FixIdAssignationStockState initialMessage =
                    FixMessage.fromFix(message, new FixIdAssignationStockState());
            assignedId = initialMessage.getTarget();
            routerId = initialMessage.getSender();
            updateState(initialMessage.getStockJson());
        } catch (FixMessageMisconfiguredException e) {
            try { // received trading response
                FixResponse response =
                        FixMessage.fromFix(message, new FixResponse());
                updateStateOnResponse(response);
            } catch (FixMessageMisconfiguredException ex) {
                try { // received follow-up update
                    FixStockStateReport followUp =
                            FixMessage.fromFix(message, new FixStockStateReport());
                    updateState(followUp.getStockJson());
                } catch (FixMessageMisconfiguredException exc) {
                    log.warn("Trading service: received invalid message");
                }
            }
        }
    }

    private void updateState(String stock) {
        try {
            ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> state =
                    objectMapper.readValue(stock, new TypeReference<>() {
                    });
            currentStockState.clear();

            state.forEach((stockId, stockState) -> {
                if (currentStockState.containsKey(stockId)) {
                    currentStockState.replace(stockId, stockState);
                } else {
                    currentStockState.put(stockId,
                            new ConcurrentHashMap<>(stockState));
                }
            });

            publishCurrentStockState();
        } catch (JsonProcessingException e) {
            log.warn("Trading service: received stock state JSON parsing failed");
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

    @Override
    public String handleMessageFromClient(ClientTradingRequest clientMsg,
                                          String clientId) {
        try {
            FixRequest request = new FixRequest(clientMsg);
            request.setSender(assignedId);
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
    public String getState() {
        BrokerEvent<FixStateUpdateRequest> event = null;
        if (currentStockState.isEmpty() && Objects.nonNull(routerId)) {
            event = new BrokerEvent<>(
                    new FixStateUpdateRequest(assignedId, routerId));
        } else if (Objects.isNull(routerId)) {
            event = new BrokerEvent<>(
                    new FixStateUpdateRequest(assignedId, "not assigned"));
        }
        if (Objects.nonNull(event))
            publisher.publishEvent(event);
        return serializeCurrentState();
    }

    private void publishCurrentStockState() {
        publisher.publishEvent(new BrokerEvent<>(
                new StocksStateMessage(
                        Map.copyOf(currentStockState))));
        log.debug("Trading service: published stock update event");
    }

    private String serializeCurrentState() {
        try {
            return objectMapper.writeValueAsString(new StocksStateMessage(
                    Map.copyOf(currentStockState)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Trading service: failed to serialize currentStockState");
        }
    }
}
