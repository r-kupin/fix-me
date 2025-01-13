package com.rokupin.router.controller;

import com.rokupin.model.fix.FixMessageProcessor;
import io.netty.util.AttributeKey;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.netty.Connection;
import reactor.netty.NettyOutbound;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public abstract class CommunicationKit {
    public static final AttributeKey<String> ASSIGNED_ID_KEY = AttributeKey.valueOf("id");

    protected final String routerId;
    @Getter
    protected final Map<String, Connection> idToConnection;
    @Getter
    protected final Map<String, FixMessageProcessor> idToMsgProcessor;

    protected int connectedServices;

    public CommunicationKit(String routerId) {
        this.routerId = routerId;
        this.idToConnection = new ConcurrentHashMap<>();
        this.idToMsgProcessor = new ConcurrentHashMap<>();
    }

    public abstract void newConnection(Connection connection,
                                       String serializedState,
                                       Function<String, Publisher<Void>> handlerCallback,
                                       BiFunction<Throwable, NettyOutbound, Publisher<Void>> errorCallback);

    public Connection getConnectionById(String id) {
        return idToConnection.get(id);
    }

    public void remove(String id) {
        idToConnection.remove(id);
    }

}
