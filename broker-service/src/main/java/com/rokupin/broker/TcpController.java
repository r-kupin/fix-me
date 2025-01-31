package com.rokupin.broker;

import com.rokupin.broker.events.BrokerEvent;
import com.rokupin.broker.service.TradingService;
import com.rokupin.model.fix.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EventObject;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
@Component
public class TcpController {
    private final String host;
    private final int port;
    private final AtomicBoolean connectionInProgress;
    private final TradingService tradingService;
    private final Flux<BrokerEvent<FixMessage>> tradeRequestEventFlux;
    private final Sinks.Many<String> toRouterSink;
    private Connection connection;
    private FixMessageProcessor routerInputProcessor;


    public TcpController(TradingService tradingService,
                         @Qualifier("tradeRequestEventPublisher") Consumer<FluxSink<BrokerEvent<FixMessage>>> tradeRequestEventPublisher,
                         @Value("${tcp.host}") String host,
                         @Value("${tcp.port}") int port) {
        this.host = host;
        this.port = port;
        this.tradingService = tradingService;
        this.tradeRequestEventFlux = Flux.create(tradeRequestEventPublisher).share();
        this.connectionInProgress = new AtomicBoolean(false);
        this.toRouterSink = Sinks.many().multicast().directAllOrNothing();
    }

    @PostConstruct
    private void init() {
        tradeRequestEventFlux.map(EventObject::getSource)
                .flatMap(this::requestEventToStringPublisher)
                .doOnNext(toRouterSink::tryEmitNext)
                .subscribe();

        initiateRouterConnection();
    }

    private void initiateRouterConnection() {
        if (Objects.isNull(connection) &&
                connectionInProgress.compareAndSet(false, true)) {
            connect();
        }
    }

    private void connect() {
        TcpClient.create()
                .host(host)
                .port(port)
                .doOnConnected(this::onConnected)
                .connect()
                .retryWhen(retrySpec())
                .doOnError(e -> {
                    log.warn("TCPHandler: Connection failed: {}", e.getMessage());
                    connection = null;
                }).doOnSuccess(connection -> {
                    connectionInProgress.set(false);
                    this.connection = connection;
                    log.info("TCPHandler: Connected successfully to {}:{}", host, port);
                }).onErrorResume(e -> {
                    log.error("TCPHandler: Connection attempts exhausted. Reporting failure.");
                    connectionInProgress.set(false);
                    return Mono.empty();
                }).subscribe();
    }

    private void onConnected(Connection connection) {
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
                initiateRouterConnection();
            }
        } else if (event instanceof FixStateUpdateRequest stateRequest) {
            if (Objects.nonNull(connection) &&
                    !stateRequest.getTarget().equals("not assigned")) {
                return publishFixMessage(stateRequest);
            } else if (!connectionInProgress.get()) {
                log.info("TCPHandler: State update request not sent: Router service is unavailable.");
                initiateRouterConnection();
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

    private Retry retrySpec() {
        return Retry.backoff(5, Duration.ofSeconds(2))
                .maxBackoff(Duration.ofSeconds(10))
                .doBeforeRetry(signal -> log.info(
                        "TCPHandler: retrying connection, attempt {}",
                        signal.totalRetriesInARow() + 1)
                ).onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                        new RuntimeException("TCPHandler: Max retry attempts reached."));
    }
}
