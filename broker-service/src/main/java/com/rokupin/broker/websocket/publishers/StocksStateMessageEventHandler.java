package com.rokupin.broker.websocket.publishers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.broker.events.BrokerEvent;
import com.rokupin.broker.model.StocksStateMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.util.EventObject;
import java.util.function.Consumer;

@Slf4j
public class StocksStateMessageEventHandler implements WebSocketSessionEventHandler {

    private final ObjectMapper objectMapper;
    private final Flux<Object> inputFlux;

    public StocksStateMessageEventHandler(ObjectMapper objectMapper,
                                          Consumer<FluxSink<BrokerEvent<StocksStateMessage>>> stockStateUpdateEventPublisher) {
        this.objectMapper = objectMapper;
        this.inputFlux = Flux.create(stockStateUpdateEventPublisher)
                .share()
                .map(EventObject::getSource);
    }

    @Override
    public Flux<String> handle(WebSocketSession session) {
        log.debug("WSHandler [{}]: stock state handler is ready", session.getId());
        return inputFlux.flatMap(event -> handleEmission(event, session));
    }

    private Mono<String> handleEmission(Object event, WebSocketSession session) {
        if (event instanceof StocksStateMessage stocksStateMessage) {
            try {
                String stocksStateJson = objectMapper.writeValueAsString(stocksStateMessage);
                log.info("WSHandler [{}]: broadcasting a stock " +
                        "state update: '{}'", session.getId(), stocksStateJson);
                return Mono.just(stocksStateJson);
            } catch (JsonProcessingException e) {
                log.warn("WSHandler [{}]: state update event: '{}' can't be " +
                        "serialized to JSON", session.getId(), stocksStateMessage);
                return Mono.empty();
            }
        } else {
            return Mono.empty();
        }
    }
}