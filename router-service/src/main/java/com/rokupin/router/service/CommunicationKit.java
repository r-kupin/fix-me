package com.rokupin.router.service;

import com.rokupin.model.fix.FixIdAssignation;
import com.rokupin.model.fix.FixIdAssignationStockState;
import com.rokupin.model.fix.FixMessageMisconfiguredException;
import com.rokupin.model.fix.FixMessageProcessor;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.NettyOutbound;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

@Slf4j
@Component
public class CommunicationKit {
    public static final AttributeKey<String> ASSIGNED_ID_KEY = AttributeKey.valueOf("id");
    private final String routerId;
    private final Map<String, Connection> brokerToConnection;
    private final Map<String, Connection> exchangeToConnection;
    private final Map<String, FixMessageProcessor> brokerInputProcessors;
    private final Map<String, FixMessageProcessor> exchangeInputProcessors;
    int brokers, exchanges;

    public CommunicationKit(@Value("${router.id}") String routerId) {
        brokers = 0;
        exchanges = 0;
        this.routerId = routerId;
        this.brokerToConnection = new ConcurrentHashMap<>();
        this.exchangeToConnection = new ConcurrentHashMap<>();
        this.brokerInputProcessors = new ConcurrentHashMap<>();
        this.exchangeInputProcessors = new ConcurrentHashMap<>();
    }

    public void newBrokerConnection(Connection connection,
                                    String serializedState,
                                    Function<String, Publisher<Void>> handlerCallback,
                                    BiFunction<Throwable, NettyOutbound, Publisher<Void>> errorCallback) {
        String newBrokerId = "B" + String.format("%05d", brokers++);

        FixMessageProcessor brokerInputProcessor = new FixMessageProcessor();
        brokerInputProcessor.getFlux()
                .flatMap(handlerCallback)
                .onErrorResume(e -> errorCallback.apply(e, connection.outbound()))
                .subscribe();
        brokerInputProcessors.put(newBrokerId, brokerInputProcessor);

        connection.outbound()
                .sendString(
                        publishBrokerWelcomeMsg(serializedState, newBrokerId),
                        StandardCharsets.UTF_8
                ).then()
                .subscribe();

        connection.channel().attr(ASSIGNED_ID_KEY).set(newBrokerId);
        brokerToConnection.put(newBrokerId, connection);
    }

    private Mono<String> publishBrokerWelcomeMsg(String serializedState, String newId) {
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

    public void newExchangeConnection(Connection connection,
                                      Function<String, Publisher<Void>> handlerCallback) {
        String newExchangeId = "E" + String.format("%05d", exchanges++);

        FixMessageProcessor exchangeInputProcessor = new FixMessageProcessor();
        exchangeInputProcessor.getFlux()
                .flatMap(handlerCallback)
                .doOnError(e -> log.error(
                        "Exchange service interaction went wrong: {}",
                        e.getMessage())
                ).subscribe();
        exchangeInputProcessors.put(newExchangeId, exchangeInputProcessor);

        connection.outbound()
                .sendString(
                        publishExchangeWelcomeMsg(newExchangeId),
                        StandardCharsets.UTF_8
                ).then()
                .subscribe();
        connection.channel().attr(ASSIGNED_ID_KEY).set(newExchangeId);
        exchangeToConnection.put(newExchangeId, connection);
    }

    private Mono<String> publishExchangeWelcomeMsg(String newId) {
        try {
            FixIdAssignation msg = new FixIdAssignation(routerId, newId);
            log.debug("New exchange '{}' connected", newId);
            return Mono.just(msg.asFix());
        } catch (FixMessageMisconfiguredException e) {
            log.error("Cant make an exchange welcome string: {}",
                    e.getMessage());
        }
        return Mono.empty();
    }

    public Connection getBrokerConnection(String brokerId) {
        return brokerToConnection.get(brokerId);
    }

    public Iterable<Map.Entry<String, Connection>> allBrokerConnections() {
        return brokerToConnection.entrySet();
    }

    public void removeBroker(String brokerId) {
        brokerToConnection.remove(brokerId);
    }

    public void removeExchange(String exchangeId) {
        exchangeToConnection.remove(exchangeId);
    }

    public Connection getExchangeConnection(String exchangeId) {
        return exchangeToConnection.get(exchangeId);
    }

    public FixMessageProcessor getBrokerFixInputProcessor(String id) {
        return brokerInputProcessors.get(id);
    }

    public FixMessageProcessor getExchangeFixInputProcessor(String id) {
        return exchangeInputProcessors.get(id);
    }
}
