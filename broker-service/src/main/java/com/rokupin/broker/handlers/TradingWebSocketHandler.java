package com.rokupin.broker.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.broker.model.TradeRequest;
import com.rokupin.broker.service.FixTradingService;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class TradingWebSocketHandler implements WebSocketHandler {

    //    private final FixTradingService fixTradingService;
//    private final Map<String, WebSocketSession> activeClients = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public TradingWebSocketHandler(FixTradingService fixTradingService, ObjectMapper objectMapper) {
//        this.fixTradingService = fixTradingService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        Flux<WebSocketMessage> output = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .map(message -> {
                    String response_msg;
                    try {
                        response_msg = objectMapper
                                .readValue(message, TradeRequest.class)
                                .asFix();
                    } catch (JsonMappingException e) {
                        response_msg = "can't map";
                    } catch (JsonProcessingException e) {
                        response_msg = "invalid json";
                    }
                    return session.textMessage(response_msg);
                });
        return session.send(output);
    }

//    private Mono<Void> handleIncomingMessage(String message, WebSocketSession session) {
//        return parseFixTradeMessage(message)
//                .flatMap(tradeRequest -> {
//                    // If parsing succeeds, forward the message via the service
//                    return fixTradingService.sendFixMessage(tradeRequest)
//                            .then(sendWebSocketResponseSuccess(session))
//                            .onErrorResume(error -> {
//                                // If service call fails, send error status
//                                return sendWebSocketResponseError(session,
//                                        "Forwarding failed");
//                            });
//                })
//                .onErrorResume(JsonProcessingException.class, e -> {
//                    // If JSON is invalid, send error response
//                    return sendWebSocketResponseError(session, "Invalid formatting");
//                });
//    }
//
//    private Mono<TradeRequest> parseFixTradeMessage(String json) {
//        // Non-blocking way to parse JSON to FixTradeMessage
//        return Mono.fromCallable(() -> objectMapper.readValue(json, TradeRequest.class))
//                .subscribeOn(Schedulers.boundedElastic()); // Offload blocking work to a bounded elastic thread pool
//    }
//
//    private Mono<Void> sendWebSocketResponseError(WebSocketSession session,
//                                                  String description) {
//        String message = "{\"Status\":\"Error\", " +
//                "\"Description\":\"" + description + "\"}";
//        return session.send(Mono.just(session.textMessage(message))).then();
//    }
//
//    private Mono<Void> sendWebSocketResponseSuccess(WebSocketSession session) {
//        String message = "{\"Status\":\"Success\"}";
//        return session.send(Mono.just(session.textMessage(message))).then();
//    }
}
