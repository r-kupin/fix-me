package com.rokupin.broker.tcp.service;

import com.rokupin.broker.service.TradingService;
import com.rokupin.broker.tcp.ConnectivityProvider;
import com.rokupin.model.fix.FixMessageProcessor;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;

@Slf4j
public class TcpHandlerImpl implements TcpHandler, TcpConfigurer {
    private final int port;
    private final String host;
    private final TradingService tradingService;
    private final TcpHandlerService tcpHandlerService;
    private final ConnectionWrapper connection;
    private final ConnectivityProvider connectivityProvider;

    private FixMessageProcessor routerInputProcessor;

    public TcpHandlerImpl(String host, int port,
                          TcpHandlerService tcpHandlerService,
                          ConnectivityProvider connectivityProvider,
                          TradingService tradingService
    ) {
        this.host = host;
        this.port = port;
        this.tradingService = tradingService;
        this.tcpHandlerService = tcpHandlerService;
        this.connectivityProvider = connectivityProvider;
        this.connection = new ConnectionWrapper();
    }

    @PostConstruct
    public void startConnection() {
        if (connection.makeInProgressIfNeeded())
            connectivityProvider.connect(this, host, port);
    }

    @Override
    public void configureConnection(Connection newConnection) {
        connection.establish(newConnection);
        configureOutputProcessing();
        configureInputProcessing();
        configureShutdownProcessing();
    }

    private void configureShutdownProcessing() {
        // discard connection if router disconnects
        connection.getConnection()
                .onDispose()
                .doFinally(signalType -> {
                    log.info("TCPHandler: Router disconnected");
                    connection.deactivate();
                }).subscribe();
    }

    private void configureInputProcessing() {
        if (routerInputProcessor != null) {
            log.debug("TCPHandler: Cleaning up existing processor before re-initialization.");
            routerInputProcessor.complete();
        }

        routerInputProcessor = new FixMessageProcessor();

        // redirect all router input to processor (message un-chunking)
        connection.getConnection()
                .inbound()
                .receive()
                .asString()
                .doOnNext(routerInputProcessor::processInput)
                .subscribe();

        // complete messages - to be processed one-by one
        routerInputProcessor.getFlux()
                .doOnNext(tradingService::handleMessageFromRouter)
                .subscribe();
    }

    private void configureOutputProcessing() {
        tcpHandlerService.handle(connection)
                .onErrorResume(e -> {
                    startConnection();
                    return Mono.empty();
                }).subscribe();
    }


    @Override
    public void handleConnected(Connection connection) {
        log.info("TCPHandler: Connected successfully to {}:{}", host, port);
    }

    @Override
    public void handleNotConnected(Throwable e) {
        log.warn("TCPHandler: Connection failed: {}", e.getMessage());
    }

    @Override
    public void handleConnectionFailed(Throwable e) {
        connection.deactivate();
        log.error("TCPHandler: Connection can't be established right now");
    }
}
