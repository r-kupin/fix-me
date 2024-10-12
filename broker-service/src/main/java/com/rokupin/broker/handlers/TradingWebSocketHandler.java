//package com.rokupin.broker.handlers;
//
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.rokupin.broker.model.TradeRequest;
//import com.rokupin.broker.service.FixTradingService;
//import org.jetbrains.annotations.NotNull;
//import org.springframework.stereotype.Component;
//import org.springframework.web.reactive.socket.WebSocketHandler;
//import org.springframework.web.reactive.socket.WebSocketMessage;
//import org.springframework.web.reactive.socket.WebSocketSession;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Mono;
//import reactor.core.scheduler.Schedulers;
//
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//
//@Component
//public class TradingWebSocketHandler implements WebSocketHandler {
//
////    private final FixTradingService fixTradingService;
////    private final Map<String, WebSocketSession> activeClients = new ConcurrentHashMap<>();
////    private final ObjectMapper objectMapper; // For JSON parsing
////
////    public TradingWebSocketHandler(FixTradingService fixTradingService, ObjectMapper objectMapper) {
////        this.fixTradingService = fixTradingService;
////        this.objectMapper = objectMapper;
////    }
//
//    @Override
//    public Mono<Void> handle(WebSocketSession session) {
//        return session.receive()
//                .flatMap(webSocketMessage -> {
//                    // Wrap the echoed message into a Mono and send it
//                    WebSocketMessage responseMessage = session.textMessage(webSocketMessage.getPayloadAsText());
//                    return session.send(Mono.just(responseMessage));
//                }).then();
//    }
//
////    private Mono<Void> handleIncomingMessage(String message, WebSocketSession session) {
////        return parseFixTradeMessage(message)
////                .flatMap(tradeRequest -> {
////                    // If parsing succeeds, forward the message via the service
////                    return fixTradingService.sendFixMessage(tradeRequest)
////                            .then(sendWebSocketResponseSuccess(session))
////                            .onErrorResume(error -> {
////                                // If service call fails, send error status
////                                return sendWebSocketResponseError(session,
////                                        "Forwarding failed");
////                            });
////                })
////                .onErrorResume(JsonProcessingException.class, e -> {
////                    // If JSON is invalid, send error response
////                    return sendWebSocketResponseError(session, "Invalid formatting");
////                });
////    }
////
////    private Mono<TradeRequest> parseFixTradeMessage(String json) {
////        // Non-blocking way to parse JSON to FixTradeMessage
////        return Mono.fromCallable(() -> objectMapper.readValue(json, TradeRequest.class))
////                .subscribeOn(Schedulers.boundedElastic()); // Offload blocking work to a bounded elastic thread pool
////    }
////
////    private Mono<Void> sendWebSocketResponseError(WebSocketSession session,
////                                                  String description) {
////        String message = "{\"Status\":\"Error\", " +
////                "\"Description\":\"" + description + "\"}";
////        return session.send(Mono.just(session.textMessage(message))).then();
////    }
////
////    private Mono<Void> sendWebSocketResponseSuccess(WebSocketSession session) {
////        String message = "{\"Status\":\"Success\"}";
////        return session.send(Mono.just(session.textMessage(message))).then();
////    }
//}
