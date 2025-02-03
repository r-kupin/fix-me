package com.rokupin.broker.publishers;

import com.rokupin.broker.events.BrokerEvent;
import com.rokupin.broker.service.TradingService;
import com.rokupin.broker.tcp.service.ConnectionWrapper;
import com.rokupin.broker.tcp.service.TcpConnectionError;
import com.rokupin.model.fix.FixMessageMisconfiguredException;
import com.rokupin.model.fix.FixRequest;
import com.rokupin.model.fix.FixResponse;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.EventObject;
import java.util.function.Consumer;

@Slf4j
public class FixRequestEventHandler implements TcpConnectionEventHandler {
    private final TradingService tradingService;
    private final Flux<Object> flux;

    public FixRequestEventHandler(
            TradingService tradingService,
            Consumer<FluxSink<BrokerEvent<FixRequest>>> eventPublisher
    ) {
        this.tradingService = tradingService;
        this.flux = Flux.create(eventPublisher)
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
        if (event instanceof FixRequest request) {
            if (connection.isActive()) {
                try {
                    request.setSender(tradingService.getAssignedId());
                    return Mono.just(request.asFix());
                } catch (FixMessageMisconfiguredException e) {
                    log.info("{}", e.getMessage());
                }
            } else if (!connection.isInProgress()) {
                log.info("TCPHandler: trading request not sent: Router service is unavailable.");
                try {
                    FixResponse autogen = FixResponse.autoGenerateResponseOnFail(
                            request, FixResponse.SEND_FAILED
                    );
                    tradingService.handleMessageFromRouter(autogen.asFix());
                    return Mono.error(
                            new TcpConnectionError("Connection is not active")
                    );
                } catch (FixMessageMisconfiguredException e) {
                    log.error("TCPHandler: Response autogeneration failed");
                }
            }
        }
        return Mono.empty();
    }
}
