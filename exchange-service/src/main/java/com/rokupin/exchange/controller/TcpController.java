package com.rokupin.exchange.controller;

import com.rokupin.exchange.service.ExchangeService;
import com.rokupin.model.fix.*;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

@Slf4j
public class TcpController {
    private String assignedId;
    private FixMessageProcessor routerInputProcessor;
    private final Connection connection;
    private final ExchangeService service;

    public TcpController(String host, int port, ExchangeService service) {
        this.service = service;
        this.connection = connectTcpClient(host, port);
        this.routerInputProcessor = new FixMessageProcessor();
    }

    private Connection connectTcpClient(String host, int port) {

        TcpClient client = TcpClient.create()
                .host(host)
                .port(port)
                .handle((inbound, outbound) -> {
                    initializeProcessor();
                    return inbound.receive()
                            .asString(StandardCharsets.UTF_8)
                            .doOnNext(routerInputProcessor::processInput)
                            .then();
                });

        return client.connect()
                .doOnError(e -> log.info("Connection failed: {}", e.getMessage()))
                .retryWhen(retrySpec())
                .doOnSuccess(conn -> log.info("Connected successfully to {}:{}", host, port))
                .block();
    }

    private void initializeProcessor() {
        if (routerInputProcessor != null) {
            log.info("Cleaning up existing processor before re-initialization.");
            routerInputProcessor.complete();
        }

        routerInputProcessor = new FixMessageProcessor();
        routerInputProcessor.getFlux()
                .flatMap(this::handleIncomingMessage)
                .subscribe();

        log.info("Processor initialized and subscription established.");
    }

    private Retry retrySpec() {
        return Retry.backoff(5, Duration.ofSeconds(2))
                .maxBackoff(Duration.ofSeconds(10))
                .doBeforeRetry(signal -> log.info("Retrying connection, attempt {}",
                        signal.totalRetriesInARow() + 1))
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                    log.error("Max retry attempts reached.");
                    System.exit(1);
                    return null;
                });
    }

    private Mono<Void> handleIncomingMessage(String msg) {
        log.debug("Received message: '{}'", msg);

        try { // is it a trading request?
            FixRequest request = FixMessage.fromFix(msg, new FixRequest());
            log.debug("Processing trading request");
            if (!Objects.isNull(assignedId)) {
                log.debug("Calling prepareSendResponse for {}", request);
                return prepareSendResponse(request);
            } else {
                log.warn("Received trading request before ID was assigned");
            }
        } catch (FixMessageMisconfiguredException e) {
            try { // is it an ID assignation message?
                FixIdAssignation idMsg = FixMessage.fromFix(msg, new FixIdAssignation());

                if (Objects.isNull(assignedId)) {
                    assignedId = idMsg.getTarget();
                    return sendStateMessage();
                } else {
                    log.warn("Re-assignation of the ID");
                }
            } catch (FixMessageMisconfiguredException ex) {
                log.warn("Received message is not supported. Ignoring.");
            }
        } catch (IllegalStateException e) {
            log.warn("Received message is not valid FIX request");
        }
        log.warn("Returning empty");
        return Mono.empty();
    }

    private Mono<Void> sendStateMessage() {
        return service.publishCurrentStockState(assignedId)
                .flatMap(stateReport -> {
                    log.debug("Sending state report: {}", stateReport);
                    return connection.outbound()
                            .sendString(Mono.just(stateReport), StandardCharsets.UTF_8)
                            .then();
                });
    }

    private Mono<Void> prepareSendResponse(FixRequest request) {
        return service.processTradeRequest(request, assignedId)
                .flatMap(response -> {
                    try {
                        log.debug("Sending response: {}", response.asFix());
                        Publisher<String> to_send;
                        if (response.getOrdStatus() == FixResponse.MSG_ORD_FILLED) {
                            to_send = Flux.concat(
                                    Mono.just(response.asFix()),
                                    service.publishCurrentStockState(assignedId)
                            );
                        } else {
                            to_send = Mono.just(response.asFix());
                        }
//                        return connection.outbound()
//                                .sendString(to_send, StandardCharsets.UTF_8)
//                                .then();
                        return connection.outbound()
                                .sendString(to_send, StandardCharsets.UTF_8)
                                .then()
                                .doOnSuccess(v -> log.info("Successfully sent response"))
                                .doOnError(e -> log.error("Failed to send response over TCP: {}", e.getMessage()))
                                .onErrorResume(e -> Mono.empty());
                    } catch (FixMessageMisconfiguredException e) {
                        log.error("Response assembly failed. This can't happen.");
                        return Mono.empty();
                    }
                }).doOnError(e -> log.error(
                        "Failed to process or respond to trade request: {}",
                        e.getMessage())
                ).then();
    }
}
