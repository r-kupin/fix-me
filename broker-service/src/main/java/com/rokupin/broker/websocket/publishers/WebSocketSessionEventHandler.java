package com.rokupin.broker.websocket.publishers;

import org.reactivestreams.Publisher;
import org.springframework.web.reactive.socket.WebSocketSession;

public interface WebSocketSessionEventHandler {
    Publisher<String> handle(WebSocketSession session);
}
