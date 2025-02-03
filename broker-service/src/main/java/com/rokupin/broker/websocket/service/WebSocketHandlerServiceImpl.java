package com.rokupin.broker.websocket.service;

import com.rokupin.broker.publishers.WebSocketSessionEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
public class WebSocketHandlerServiceImpl implements WebSocketHandlerService {
    private final List<WebSocketSessionEventHandler> handlers;

    public WebSocketHandlerServiceImpl(List<WebSocketSessionEventHandler> handlers) {
        this.handlers = handlers;
    }

    @Override
    public Mono<Void> handleSession(WebSocketSession session) {
        log.debug("WSHandler [{}]: service started session handling", session.getId());

        return session.send(
                Flux.fromIterable(handlers)
                        .flatMap(handler -> handler.handle(session))
                        .map(session::textMessage)
        );
    }
}
