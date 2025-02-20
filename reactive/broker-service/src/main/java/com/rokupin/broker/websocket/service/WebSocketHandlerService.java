package com.rokupin.broker.websocket.service;

import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

public interface WebSocketHandlerService {
    Mono<Void> handleSession(WebSocketSession session);
}
