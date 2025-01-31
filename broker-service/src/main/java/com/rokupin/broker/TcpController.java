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
        this.toRouterSink = Sinks.many().multicast().onBackpressureBuffer();
    }

    @PostConstruct
    private void init() {
        tradeRequestEventFlux.map(EventObject::getSource)
                .flatMap(this::requestEventToStringPublisher)
                .doOnNext(toRouterSink::tryEmitNext)
                .subscribe();

//        toRouterSink.asFlux()
//                .flatMap(this::sendMessage)
//                .doOnNext(msg -> log.debug("TCPHandler: message '{}' sent", msg))
//                .subscribe();

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
        if (routerInputProcessor != null) {
            log.info("TCPHandler: Cleaning up existing processor before re-initialization.");
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

        Flux<String> outputFlux = toRouterSink.asFlux()
                .doOnNext(msg -> log.debug("TCPHandler: sending message {}", msg));

        connection.outbound()
                .sendString(outputFlux, StandardCharsets.UTF_8)
                .then()
                .subscribe();

        connection.onDispose()
                .doFinally(signalType -> {
                    log.info("Peer disconnected, retrying...");
                    this.connection = null;
                }).subscribe();

        log.info("TCPHandler: Processor initialized and subscription established.");
    }

//    private Publisher<String> sendMessage(String s) {
//        if (Objects.nonNull(connection)) {
//            return connection.outbound()
//                    .sendString(Mono.just(s), StandardCharsets.UTF_8)
//                    .then()
//                    .onErrorResume()
//        } else if (!connectionInProgress.get()) {
//            log.info("TCPHandler: trading request not sent: Router service is unavailable.");
//            try {
//                FixResponse autogen = FixResponse.autoGenerateResponseOnFail(
//                        request, FixResponse.SEND_FAILED
//                );
//                tradingService.handleMessageFromRouter(autogen.asFix());
//            } catch (FixMessageMisconfiguredException e) {
//                log.error("TCPHandler: Response autogeneration failed");
//            }
//            initiateRouterConnection();
//        }
//    }

    private Publisher<String> requestEventToStringPublisher(Object event) {
        if (event instanceof FixRequest request) {
            if (Objects.nonNull(connection)) {
                return publishRequestString(request);
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
                return publishStateRequestString(stateRequest);
            } else if (!connectionInProgress.get()) {
                log.info("TCPHandler: State update request not sent: Router service is unavailable.");
                initiateRouterConnection();
            }
        } else {
            log.info("TCPHandler: reported event is not FixRequest");
        }
        return Mono.empty();
    }

    private Publisher<String> publishStateRequestString(FixStateUpdateRequest request) {
        try {
            String fix = request.asFix();
            log.info("TCPHandler: publishing state request message '{}'", fix);
            return Mono.just(fix);
        } catch (FixMessageMisconfiguredException e) {
            log.info("Trading request not sent: '{}'", e.getMessage());
        }
        return Mono.empty();
    }

    private Publisher<String> publishRequestString(FixRequest request) {
        try {
            request.setSender(tradingService.getAssignedId());
            String fix = request.asFix();
            log.info("TCPHandler: publishing trade request message '{}'", fix);
            return Mono.just(fix);
        } catch (FixMessageMisconfiguredException e) {
            log.info("TCPHandler: Trading request not sent: '{}'", e.getMessage());
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
