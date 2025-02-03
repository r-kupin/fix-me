package com.rokupin.broker.tcp;

import com.rokupin.broker.publishers.TcpConnectionEventHandler;
import com.rokupin.broker.tcp.service.ConnectionWrapper;
import com.rokupin.broker.tcp.service.TcpHandlerService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public class TcpHandlerServiceImpl implements TcpHandlerService {
    private final List<TcpConnectionEventHandler> handlers;

    public TcpHandlerServiceImpl(List<TcpConnectionEventHandler> handlers) {
        this.handlers = handlers;
    }

    @Override
    public Mono<Void> handle(ConnectionWrapper connection) {
        return Flux.fromIterable(handlers)
                .flatMap(handler -> handler.handle(connection))
                .then();
    }
}
