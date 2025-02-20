package com.rokupin.router.controller;

import com.rokupin.router.service.RouterService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import reactor.netty.tcp.TcpServer;

@Slf4j
public class TcpController {
    private final TcpServer server;
    private final RouterService service;

    public TcpController(String host, int port, RouterService service) {
        log.info("Starting TcpServer at {}:{}", host, port);
        this.service = service;
        this.server = TcpServer.create().host(host).port(port);
    }

    @PostConstruct
    private void init() {
        server.doOnConnection(service::doOnConnection)
                .doOnConnection(service.getConnectionHandler())
                .bindNow()
                .onDispose()
                .subscribe();
    }
}
