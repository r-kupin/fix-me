package com.rokupin.router.controller;

import com.rokupin.router.service.RouterService;
import jakarta.annotation.PostConstruct;
import reactor.netty.tcp.TcpServer;

public class TcpController {
    private final TcpServer server;
    private final RouterService service;

    public TcpController(String host, int port,
                         RouterService service) {
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
