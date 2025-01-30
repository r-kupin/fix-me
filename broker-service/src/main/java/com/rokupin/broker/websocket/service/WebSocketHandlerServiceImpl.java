package com.rokupin.broker.websocket.service;

import com.rokupin.broker.websocket.publishers.WebSocketSessionEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.socket.WebSocketSession;
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

//                Sinks.Many<String> sinks = Sinks.many()
//                .multicast()
//                .onBackpressureBuffer();
//
//        handlers.forEach(handler ->
//                Flux.from(handler.handle(session))
//                        .subscribe(sinks::tryEmitNext)
//        );

        return session.send(
                handlers.get(0).handle(session).mergeWith(
                                handlers.get(1).handle(session)).mergeWith(
                                handlers.get(2).handle(session))
                        .map(session::textMessage));
    }
}
