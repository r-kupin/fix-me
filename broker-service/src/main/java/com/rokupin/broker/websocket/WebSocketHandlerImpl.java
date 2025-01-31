package com.rokupin.broker.websocket;

import com.rokupin.broker.websocket.service.WebSocketHandlerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.util.function.Consumer;

@Slf4j
public class WebSocketHandlerImpl implements WebSocketHandler {

    private final WebSocketHandlerService service;

    public WebSocketHandlerImpl(WebSocketHandlerService service) {
        this.service = service;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        log.debug("WSHandler [{}]: handling session in handler", session.getId());
        return service.handleSession(session)
                .doOnError(e -> log.error(
                        "WSHandler [{}]: encountered error: {}",
                        session.getId(),
                        e.getMessage())
                ).doFinally(handleSessionShutdown(session));
    }

    private Consumer<SignalType> handleSessionShutdown(WebSocketSession session) {
        return signalType -> {
            log.info("WSHandler [{}]: cleanup triggered with signal: {}",
                    session.getId(),
                    signalType);
            session.close()
                    .doOnSuccess(aVoid -> log.info(
                            "WSHandler [{}]: session closed successfully",
                            session.getId())
                    ).doOnError(e -> log.error(
                            "WSHandler [{}]: failed to close session: {}",
                            session.getId(),
                            e.getMessage())
                    ).subscribe();
        };
    }
}
