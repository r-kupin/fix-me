package com.rokupin.router.service;

import com.rokupin.model.fix.FixMessageProcessor;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class ExchangeOnConnectionHandler implements Consumer<Connection> {

    private final CommunicationKit communicationKit;

    public ExchangeOnConnectionHandler(CommunicationKit communicationKit) {
        this.communicationKit = communicationKit;
    }

    @Override
    public void accept(Connection connection) {
        String id = connection.channel()
                .attr(CommunicationKit.ASSIGNED_ID_KEY)
                .get();

        FixMessageProcessor inputProcessor = communicationKit
                .getExchangeFixInputProcessor(id);

        Mono.fromDirect(connection.inbound()
                .receive()
                .asString(StandardCharsets.UTF_8)
                .doOnNext(inputProcessor::processInput)
                .then()
        ).subscribe(connection.disposeSubscriber());
    }
}

