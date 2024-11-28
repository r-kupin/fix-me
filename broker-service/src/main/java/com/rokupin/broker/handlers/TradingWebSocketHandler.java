package com.rokupin.broker.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.broker.events.InputEvent;
import com.rokupin.broker.model.StocksStateMessage;
import com.rokupin.broker.service.TradingService;
import com.rokupin.model.fix.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.util.EventObject;
import java.util.function.Consumer;

@Component
@Slf4j
public class TradingWebSocketHandler implements WebSocketHandler {


    private final TradingService tradingService;
    private final ObjectMapper objectMapper;
    private final Flux<InputEvent<StocksStateMessage>> stockStateUpdateEventFlux;
    private final Flux<InputEvent<FixResponse>> tradeResponseEventFlux;

    public TradingWebSocketHandler(TradingService tradingService,
                                   ObjectMapper objectMapper,
                                   Consumer<FluxSink<InputEvent<StocksStateMessage>>> stockStateUpdateEventPublisher,
                                   Consumer<FluxSink<InputEvent<FixResponse>>> tradeResponseEventPublisher) {
        this.tradingService = tradingService;
        this.objectMapper = objectMapper;
        this.stockStateUpdateEventFlux = Flux.create(stockStateUpdateEventPublisher).share();
        this.tradeResponseEventFlux = Flux.create(tradeResponseEventPublisher).share();
    }

    @PostConstruct
    private void init() {
        tradingService.initiateRouterConnection();
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = session.getId();

        Mono<WebSocketMessage> onConnection = handleNewClient(session);

        Flux<WebSocketMessage> onStateUpdate = handleStateUpdateEvent(session);
        Flux<WebSocketMessage> onTradeResponse = handleTradeResponseEvent(session);
        Flux<WebSocketMessage> onClientMessage = handleClientInput(session);

        return session.send(combineOutputs(onConnection, onClientMessage, onStateUpdate, onTradeResponse, sessionId))
                .doOnError(e -> log.error("WSHandler [{}]: encountered error: {}", sessionId, e.getMessage()))
                .doFinally(handleSessionShutdown(session));
    }

    private Flux<WebSocketMessage> combineOutputs(Mono<WebSocketMessage> onConnection,
                                                  Flux<WebSocketMessage> onClientMessage,
                                                  Flux<WebSocketMessage> onStateUpdate,
                                                  Flux<WebSocketMessage> onTradeResponse,
                                                  String sessionId) {
        return Flux.concat(onConnection, onClientMessage
                        .mergeWith(onStateUpdate)
                        .mergeWith(onTradeResponse))
                .doOnError(e -> log.error("WSHandler [{}]: error in combined stream: {}", sessionId, e.getMessage()))
                .doOnCancel(() -> log.info("WSHandler [{}]: canceled by client", sessionId))
                .doOnTerminate(() -> log.info("WSHandler [{}]: combined stream completed for ", sessionId));
    }

    private Consumer<SignalType> handleSessionShutdown(WebSocketSession session) {
        return signalType -> {
            log.info("WSHandler [{}]: cleanup triggered with signal: {}", session.getId(), signalType);
            session.close()
                    .doOnSuccess(aVoid -> log.info("WSHandler [{}]: session closed successfully", session.getId()))
                    .doOnError(e -> log.error("WSHandler [{}]: ailed to close session: {}", session.getId(), e.getMessage()))
                    .subscribe();
        };
    }

    private Mono<WebSocketMessage> handleNewClient(WebSocketSession session) {
        return tradingService.getState()
                .doOnNext(state -> log.debug(
                        "WSHandler [{}]: received state '{}' via getState",
                        session.getId(), state)
                ).map(session::textMessage);
    }

    private Flux<WebSocketMessage> handleTradeResponseEvent(WebSocketSession session) {
        return tradeResponseEventFlux
                .map(EventObject::getSource)
                .flatMap(msg -> {
                    if (msg instanceof FixResponse fixResponse) {
                        if (fixResponse.getTargetSubId().equals(session.getId())) {
                            log.debug("WSHandler [{}]: processing trading " +
                                    "response event '{}'", session.getId(), msg);
                            return fixToClientResponse(session, fixResponse);
                        } else {
                            return Flux.empty();
                        }
                    } else {
                        return Flux.empty();
                    }
                });
    }

    private Mono<WebSocketMessage> fixToClientResponse(WebSocketSession session,
                                                       FixResponse fixResponse) {
        try {
            ClientTradingResponse response = new ClientTradingResponse(fixResponse);
            try {
                String jsonResponse = objectMapper.writeValueAsString(response);
                log.debug("WSHandler [{}]: sending a trade " +
                        "response: '{}'", session.getId(), jsonResponse);
                return Mono.just(jsonResponse).map(session::textMessage);
            } catch (JsonProcessingException e) {
                log.warn("WSHandler [{}]: response: '{}' can't be " +
                                "serialized to JSON:'{}'",
                        session.getId(), response, e.getMessage());
                return Mono.empty();
            }
        } catch (FixMessageMisconfiguredException e) {
            log.warn("WSHandler [{}]: Fix trading response: '{}' can't be " +
                            "converted to the ClientResponse:'{}'",
                    session.getId(), fixResponse, e.getMessage());
            return Mono.empty();
        }
    }

    private Flux<WebSocketMessage> handleStateUpdateEvent(WebSocketSession session) {
        return stockStateUpdateEventFlux
                .map(EventObject::getSource)
                .flatMap(msg -> {
                    if (msg instanceof StocksStateMessage stocksStateMessage) {
                        try {
                            String stocksStateJson = objectMapper.writeValueAsString(stocksStateMessage);
                            log.info("WSHandler [{}]: broadcasting a stock " +
                                    "state update: '{}'", session.getId(), stocksStateJson);
                            return Mono.just(stocksStateJson).map(session::textMessage);
                        } catch (JsonProcessingException e) {
                            log.warn("WSHandler [{}]: state update event: '{}' can't be " +
                                    "serialized to JSON", session.getId(), stocksStateMessage);
                            return Flux.empty();
                        }
                    } else {
                        return Flux.empty();
                    }
                });
    }

    private Flux<WebSocketMessage> handleClientInput(WebSocketSession session) {
        return session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(msg -> clientInputHandler(msg, session));
    }

    Mono<WebSocketMessage> clientInputHandler(String msg, WebSocketSession session) {
        Mono<String> response_msg;
        log.info("WSHandler [{}]: processing request '{}'",
                session.getId(), msg);
        try {
            ClientTradingRequest clientMsg = objectMapper.readValue(msg, ClientTradingRequest.class);
            FixRequest request = new FixRequest(clientMsg);
            request.setSenderSubId(session.getId());
            response_msg = tradingService.handleTradingRequest(request);
        } catch (JsonMappingException e) {
            log.warn("WSHandler [{}]: Mapping failed: {}",
                    session.getId(), e.toString());
            response_msg = Mono.just("Trading request not sent:" +
                    " mapping to FIX message failed: " + e);
        } catch (JsonProcessingException e) {
            log.warn("WSHandler [{}]: JSON parsing failed: {}",
                    session.getId(), e.toString());
            response_msg = Mono.just("Trading request not sent:" +
                    " JSON syntax is incorrect: " + e);
        } catch (FixMessageMisconfiguredException e) {
            log.warn("WSHandler [{}]: Fix Request creation failed: {}",
                    session.getId(), e.toString());
            response_msg = Mono.just("Trading request not sent:" +
                    " provided input can't be converted to the Fix Request. Reason: '" +
                    e.getMessage() + "'");
        }
        return response_msg.map(session::textMessage);
    }
}
