package com.rokupin.router.service;

import reactor.netty.Connection;

import java.util.function.Consumer;

public interface RouterService {
    Consumer<Connection> getOnConnectionEventHandler();
}
