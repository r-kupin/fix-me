package com.rokupin.broker.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.broker.events.InputEvent;
import com.rokupin.broker.model.StocksStateMessage;
import com.rokupin.broker.model.TradeRequest;
import com.rokupin.broker.model.TradeResponse;
import com.rokupin.broker.service.TradingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.util.EventObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Component
@Slf4j
public class TradingWebSocketHandler implements WebSocketHandler {

    private final TradingService tradingService;
    private final Map<String, WebSocketSession> activeClients;
    private final ObjectMapper objectMapper;
    private final Flux<InputEvent<StocksStateMessage>> stockStateUpdateEventFlux;
    private final Flux<InputEvent<TradeResponse>> tradeResponseEventFlux;

    public TradingWebSocketHandler(TradingService tradingService,
                                   ObjectMapper objectMapper,
                                   Consumer<FluxSink<InputEvent<StocksStateMessage>>> stockStateUpdateEventPublisher,
                                   Consumer<FluxSink<InputEvent<TradeResponse>>> tradeResponseEventPublisher) {
        this.tradingService = tradingService;
        this.objectMapper = objectMapper;
        this.stockStateUpdateEventFlux = Flux.create(stockStateUpdateEventPublisher).share();
        this.tradeResponseEventFlux = Flux.create(tradeResponseEventPublisher).share();
        this.activeClients = new ConcurrentHashMap<>();
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = session.getId();
        activeClients.putIfAbsent(sessionId, session);

        Mono<WebSocketMessage> onConnection = handleNewClient(session);

        Flux<WebSocketMessage> onStateUpdate = handleStateUpdateEvent(session);
        Flux<WebSocketMessage> onTradeResponse = handleTradeResponseEvent(session);
        Flux<WebSocketMessage> onClientMessage = handleClientInput(session);

        return session.send(Flux.concat(onConnection,
                                onClientMessage
                                        .mergeWith(onStateUpdate)
                                        .mergeWith(onTradeResponse))
                        .doOnError(e -> log.error("Error in combined stream for session {}: {}", sessionId, e.getMessage()))
                        .doOnTerminate(() -> log.info("Session {} terminated", sessionId)))
                .doOnCancel(() -> log.info("Session {} canceled by client", sessionId))
                .doFinally(signalType -> {
                    log.info("Session {} cleanup triggered with signal: {}", sessionId, signalType);
                    activeClients.remove(sessionId);
                    closeSession(session);
                });

//        return session.send(onClientMessage
//                        .mergeWith(onStateUpdate)
//                        .mergeWith(onTradeResponse)
//                        .doOnError(e -> log.error("Error in combined stream for session {}: {}", sessionId, e.getMessage()))
//                        .doOnCancel(() -> log.info("Session {} canceled by client", sessionId))
//                        .doOnTerminate(() -> log.info("Combined stream completed for session: {}", sessionId)))
//                .doOnError(e -> log.error("Session {} encountered error: {}", sessionId, e.getMessage()))
//                .doFinally(signal -> {
//                    log.info("Session {} finalized with signal: {}", sessionId, signal);
//                    activeClients.remove(sessionId);
//                    try {
//                        session.close().subscribe();
//                    } catch (Exception closeException) {
//                        log.warn("Exception while closing session {}: {}", sessionId, closeException.getMessage());
//                    }
//                });

//        return session.send(Flux.concat(onConnection,
//                                onClientMessage
//                                        .mergeWith(onStateUpdate)
//                                        .mergeWith(onTradeResponse)
//                        )
//                        .doOnError(e -> log.error("Error in combined stream: {}", e.getMessage()))
//                        .doOnTerminate(() -> log.info("Combined stream completed for session: {}", sessionId)))
//                .doFinally(signal -> activeClients.remove(sessionId));
    }

    private void closeSession(WebSocketSession session) {
        session.close()
                .doOnSuccess(aVoid -> log.info("Session {} closed successfully", session.getId()))
                .doOnError(e -> log.error("Failed to close session {}: {}", session.getId(), e.getMessage()))
                .subscribe();
    }

    private Mono<WebSocketMessage> handleNewClient(WebSocketSession session) {
        return tradingService.getState()
                .doOnNext(state -> log.info("WSHandler [{}]: received state '{}' via getState", session.getId(), state))
                .map(session::textMessage);
    }

    private Flux<WebSocketMessage> handleTradeResponseEvent(WebSocketSession session) {
        return tradeResponseEventFlux
                .map(EventObject::getSource)
                .flatMap(msg -> {
                    if (msg instanceof TradeResponse response) {
                        if (response.getTargetSubId().equals(session.getId())) {
                            log.info("WSHandler [{}]: processing trading " +
                                    "response event '{}'", session.getId(), msg);
                            try {
                                String responseJson = objectMapper.writeValueAsString(response);
                                log.info("WSHandler [{}]: sending a trade " +
                                        "response: '{}'", session.getId(), responseJson);
                                return Mono.just(responseJson).map(session::textMessage);
                            } catch (JsonProcessingException e) {
                                log.warn("WSHandler [{}]: event: '{}' can't be " +
                                        "serialized to JSON", session.getId(), response);
                                return Flux.empty();
                            }
                        } else {
                            return Flux.empty();
                        }
                    } else {
                        log.warn("WSHandler [{}]: event: '{}' doesn't " +
                                "represent a TradeResponse instance", session.getId(), msg);
                        return Flux.empty();
                    }
                });
    }

    private Flux<WebSocketMessage> handleStateUpdateEvent(WebSocketSession session) {
        return stockStateUpdateEventFlux
                .map(EventObject::getSource)
                .flatMap(msg -> {
                    log.info("WSHandler [{}]: processing stock update event '{}'",
                            session.getId(), msg);

                    if (msg instanceof StocksStateMessage stocksStateMessage) {
                        try {
                            String stocksStateJson = objectMapper.writeValueAsString(stocksStateMessage);
                            log.info("WSHandler [{}]: broadcasting a stock " +
                                    "state update: '{}'", session.getId(), stocksStateJson);
                            return Mono.just(stocksStateJson).map(session::textMessage);
                        } catch (JsonProcessingException e) {
                            log.warn("WSHandler [{}]: event: '{}' can't be " +
                                    "serialized to JSON", session.getId(), stocksStateMessage);
                            return Flux.empty();
                        }
                    } else {
                        log.warn("WSHandler [{}]: event: '{}' doesn't " +
                                "represent a StocksState instance", session.getId(), msg);
                        return Flux.empty();
                    }
                });
    }

    private Flux<WebSocketMessage> handleClientInput(WebSocketSession session) {
        return session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(msg -> {
                    Mono<String> response_msg;
                    log.info("WSHandler [{}]: processing request '{}'",
                            session.getId(), msg);
                    try {
                        TradeRequest request = objectMapper.readValue(msg, TradeRequest.class);
                        request.setSenderSubId(session.getId());
                        response_msg = tradingService.handleTradingRequest(request);
                    } catch (JsonMappingException e) {
                        log.warn("WSHandler [{}]: Mapping failed: {}",
                                session.getId(), e.toString());
                        response_msg = Mono.just("Trading request not sent:" +
                                " mapping to FIX message failed");
                    } catch (JsonProcessingException e) {
                        log.warn("WSHandler [{}]: JSON parsing failed: {}",
                                session.getId(), e.toString());
                        response_msg = Mono.just("Trading request not sent:" +
                                " JSON syntax is incorrect");
                    }
                    return response_msg.map(session::textMessage);
                });
    }
}
