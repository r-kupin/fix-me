package com.rokupin.broker.publishers;

import org.reactivestreams.Publisher;
import org.springframework.web.reactive.socket.WebSocketSession;

public interface WebSocketSessionEventHandler {
    Publisher<String> handle(WebSocketSession session);
}
