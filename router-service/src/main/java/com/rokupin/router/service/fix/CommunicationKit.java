package com.rokupin.router.service.fix;

import com.rokupin.model.fix.FixMessageProcessor;
import io.netty.util.AttributeKey;
import lombok.Getter;
import org.reactivestreams.Publisher;
import reactor.netty.Connection;
import reactor.netty.NettyOutbound;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

@Getter
public abstract class CommunicationKit {
    public static final AttributeKey<String> ASSIGNED_ID_KEY = AttributeKey.valueOf("id");

    protected final String routerId;
    protected final Map<String, Connection> idToConnectionMap;
    protected final Map<String, FixMessageProcessor> idToMsgProcessorMap;

    protected int connectedServices;

    public CommunicationKit(String routerId) {
        this.routerId = routerId;
        this.idToConnectionMap = new ConcurrentHashMap<>();
        this.idToMsgProcessorMap = new ConcurrentHashMap<>();
    }

    public abstract void newConnection(Connection connection,
                                       String serializedState,
                                       Function<String, Publisher<Void>> handlerCallback,
                                       BiFunction<Throwable, NettyOutbound, Publisher<Void>> errorCallback);

    public Connection getConnectionById(String id) {
        return idToConnectionMap.get(id);
    }

    public void remove(String id) {
        idToConnectionMap.remove(id);
    }

}
