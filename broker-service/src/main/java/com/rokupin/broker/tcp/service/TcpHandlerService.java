package com.rokupin.broker.tcp.service;

import reactor.core.publisher.Mono;

public interface TcpHandlerService {
    Mono<Void> handle(ConnectionWrapper connection);
}
