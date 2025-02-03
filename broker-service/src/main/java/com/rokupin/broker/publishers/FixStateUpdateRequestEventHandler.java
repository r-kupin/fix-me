package com.rokupin.broker.publishers;

import com.rokupin.broker.events.BrokerEvent;
import com.rokupin.broker.tcp.service.ConnectionWrapper;
import com.rokupin.broker.tcp.service.TcpConnectionError;
import com.rokupin.model.fix.FixMessageMisconfiguredException;
import com.rokupin.model.fix.FixStateUpdateRequest;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.EventObject;
import java.util.function.Consumer;

@Slf4j
public class FixStateUpdateRequestEventHandler implements TcpConnectionEventHandler {
    private final Flux<Object> flux;

    public FixStateUpdateRequestEventHandler(Consumer<FluxSink<BrokerEvent<FixStateUpdateRequest>>> stateUpdateRequestEventPublisher) {
        this.flux = Flux.create(stateUpdateRequestEventPublisher)
                .share()
                .map(EventObject::getSource);
    }

    @Override
    public Mono<Void> handle(ConnectionWrapper connection) {
        Sinks.Many<String> sink = Sinks.many()
                .multicast()
                .directAllOrNothing();

        connection.sendString(sink.asFlux())
                .then()
                .subscribe();

        return flux.flatMap(event -> handleEmission(event, connection))
                .doOnNext(sink::tryEmitNext)
                .then();
    }

    private Publisher<String> handleEmission(Object event, ConnectionWrapper connection) {
        if (event instanceof FixStateUpdateRequest stateRequest) {
            if (connection.isActive()) {
                try {
                    return Mono.just(stateRequest.asFix());
                } catch (FixMessageMisconfiguredException e) {
                    log.info("{}", e.getMessage());
                }
            } else if (!connection.isInProgress()) {
                return Mono.error(
                        new TcpConnectionError("Connection is not active")
                );
            }
        }
        return Mono.empty();
    }
}
