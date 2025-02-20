package com.rokupin.broker.tcp.service;

import reactor.netty.Connection;

public interface TcpConfigurer {
    void configureConnection(Connection connection);

    void handleConnected(Connection connection);

    void handleNotConnected(Throwable e);

    void handleConnectionFailed(Throwable e);
}
