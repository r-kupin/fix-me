package com.rokupin.broker.websocket.publishers;

import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;

public interface WebSocketSessionEventHandler {
    Flux<String> handle(WebSocketSession session);
}
