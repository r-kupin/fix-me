package com.rokupin.broker.tcp.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.reactivestreams.Publisher;
import reactor.netty.Connection;
import reactor.netty.NettyOutbound;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Setter
@Getter
@AllArgsConstructor
public class ConnectionWrapper {
    private final AtomicBoolean inProgress;
    private boolean isActive;
    private Connection connection;

    public ConnectionWrapper() {
        this.isActive = false;
        this.connection = null;
        this.inProgress = new AtomicBoolean(false);
    }

    public boolean makeInProgressIfIdle() {
        return inProgress.compareAndSet(false, true);
    }

    public boolean makeInProgressIfNeeded() {
        return Objects.isNull(connection) &&
                inProgress.compareAndSet(false, true);
    }

    public void establish(Connection connection) {
        this.connection = connection;
        isActive = true;
        inProgress.set(false);
    }

    public void deactivate() {
        connection = null;
        isActive = false;
        inProgress.set(false);
    }

    public boolean isInProgress() {
        return inProgress.get();
    }

    public NettyOutbound sendString(Publisher<String> toSend) {
        return connection.outbound().sendString(toSend, StandardCharsets.UTF_8);
    }
}
