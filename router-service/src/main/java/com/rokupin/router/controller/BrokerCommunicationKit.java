package com.rokupin.router.controller;

import com.rokupin.model.fix.FixIdAssignationStockState;
import com.rokupin.model.fix.FixMessageMisconfiguredException;
import com.rokupin.model.fix.FixMessageProcessor;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.NettyOutbound;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

@Slf4j
public class BrokerCommunicationKit extends CommunicationKit {

    public BrokerCommunicationKit(String routerId) {
        super(routerId);
    }

    @Override
    public void newConnection(Connection connection,
                              String serializedState,
                              Function<String, Publisher<Void>> handlerCallback,
                              BiFunction<Throwable, NettyOutbound, Publisher<Void>> errorCallback) {
        String newBrokerId = "B" + String.format("%05d", connectedServices++);

        FixMessageProcessor brokerInputProcessor = new FixMessageProcessor();
        brokerInputProcessor.getFlux()
                .flatMap(handlerCallback)
                .onErrorResume(e -> errorCallback.apply(e, connection.outbound()))
                .subscribe();
        idToMsgProcessor.put(newBrokerId, brokerInputProcessor);

        connection.outbound()
                .sendString(
                        publishWelcomeMsg(serializedState, newBrokerId),
                        StandardCharsets.UTF_8
                ).then()
                .subscribe();

        connection.channel().attr(ASSIGNED_ID_KEY).set(newBrokerId);
        idToConnection.put(newBrokerId, connection);
    }

    private Mono<String> publishWelcomeMsg(String serializedState, String newId) {
        try {
            FixIdAssignationStockState msg = new FixIdAssignationStockState(
                    routerId, newId, serializedState
            );
            log.debug("New broker '{}' connected", newId);
            return Mono.just(msg.asFix());
        } catch (FixMessageMisconfiguredException e) {
            log.error("Cant make an broker welcome string: {}", e.getMessage());
        }
        return Mono.empty();
    }
}
