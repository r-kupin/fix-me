package com.rokupin.broker.publishers;

import com.rokupin.broker.tcp.service.ConnectionWrapper;
import reactor.core.publisher.Mono;

public interface TcpConnectionEventHandler {
    Mono<Void> handle(ConnectionWrapper connection);
}
