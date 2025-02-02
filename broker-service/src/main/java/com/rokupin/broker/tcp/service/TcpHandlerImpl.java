package com.rokupin.broker.tcp.service;

import com.rokupin.broker.events.BrokerEvent;
import com.rokupin.broker.service.TradingService;
import com.rokupin.broker.tcp.ConnectivityProvider;
import com.rokupin.model.fix.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.Connection;

import java.nio.charset.StandardCharsets;
import java.util.EventObject;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
public class TcpHandlerImpl implements TcpHandler, TcpConfigurer {
    private final TradingService tradingService;
    private final ConnectivityProvider connectivityProvider;
    private final String host;
    private final int port;

    private final AtomicBoolean connectionInProgress;
    private final Sinks.Many<String> toRouterSink;
    private volatile Connection connection;
    private FixMessageProcessor routerInputProcessor;

    public TcpHandlerImpl(String host, int port,
                          Consumer<FluxSink<BrokerEvent<FixMessage>>> consumer,
                          ConnectivityProvider connectivityProvider,
                          TradingService tradingService
    ) {
        this.toRouterSink = Sinks.many().multicast().directAllOrNothing();
        this.connectivityProvider = connectivityProvider;
        this.tradingService = tradingService;
        this.connectionInProgress = new AtomicBoolean(false);
        this.host = host;
        this.port = port;

        Flux.create(consumer)
                .share()
                .map(EventObject::getSource)
                .flatMap(this::requestEventToStringPublisher)
                .doOnNext(toRouterSink::tryEmitNext)
                .subscribe();
    }

    @PostConstruct
    public void startConnection() {
        if (Objects.isNull(connection) &&
                connectionInProgress.compareAndSet(false, true)) {
            connectivityProvider.connect(this, host, port);
        }
    }

    @Override
    public void configureConnection(Connection connection) {
        configureInputProcessing(connection);
        configureOutputProcessing(connection);

        // discard connection if router disconnects
        connection.onDispose().doFinally(signalType -> {
            log.info("TCPHandler: Router disconnected");
            this.connection = null;
        }).subscribe();
    }

    private void configureOutputProcessing(Connection connection) {
        Flux<String> outputFlux = toRouterSink.asFlux()
                .doOnNext(msg -> log.debug("TCPHandler: sending message {}", msg));

        connection.outbound()
                .sendString(outputFlux, StandardCharsets.UTF_8)
                .then()
                .subscribe();
    }

    private void configureInputProcessing(Connection connection) {
        if (routerInputProcessor != null) {
            log.debug("TCPHandler: Cleaning up existing processor before re-initialization.");
            routerInputProcessor.complete();
        }

        routerInputProcessor = new FixMessageProcessor();

        // redirect all router input to processor (message un-chunking)
        connection.inbound()
                .receive()
                .asString()
                .doOnNext(routerInputProcessor::processInput)
                .subscribe();

        // complete messages - to be processed one-by one
        routerInputProcessor.getFlux()
                .doOnNext(tradingService::handleMessageFromRouter)
                .subscribe();
    }

    @Override
    public void handleConnected(Connection connection) {
        this.connection = connection;
        connectionInProgress.set(false);
        log.info("TCPHandler: Connected successfully to {}:{}", host, port);
    }

    @Override
    public void handleNotConnected(Throwable e) {
        connection = null;
        log.warn("TCPHandler: Connection failed: {}", e.getMessage());
    }

    @Override
    public void handleConnectionFailed(Throwable e) {
        connectionInProgress.set(false);
        log.error("TCPHandler: Connection can't be established right now");
    }

    private Publisher<String> requestEventToStringPublisher(Object event) {
        if (event instanceof FixRequest request) {
            if (Objects.nonNull(connection)) {
                request.setSender(tradingService.getAssignedId());
                return publishFixMessage(request);
            } else if (!connectionInProgress.get()) {
                log.info("TCPHandler: trading request not sent: Router service is unavailable.");
                try {
                    FixResponse autogen = FixResponse.autoGenerateResponseOnFail(
                            request, FixResponse.SEND_FAILED
                    );
                    tradingService.handleMessageFromRouter(autogen.asFix());
                } catch (FixMessageMisconfiguredException e) {
                    log.error("TCPHandler: Response autogeneration failed");
                }
                startConnection();
            }
        } else if (event instanceof FixStateUpdateRequest stateRequest) {
            if (Objects.nonNull(connection) &&
                    !stateRequest.getTarget().equals("not assigned")) {
                return publishFixMessage(stateRequest);
            } else if (!connectionInProgress.get()) {
                log.info("TCPHandler: State update request not sent: Router service is unavailable.");
                startConnection();
            }
        }
        return Mono.empty();
    }

    private Publisher<String> publishFixMessage(FixMessage msg) {
        try {
            String fix = msg.asFix();
            log.debug("TCPHandler: Publishing fix message '{}'", fix);
            return Mono.just(fix);
        } catch (FixMessageMisconfiguredException e) {
            log.info("TCPHandler: '{}'", e.getMessage());
        }
        return Mono.empty();
    }
}
