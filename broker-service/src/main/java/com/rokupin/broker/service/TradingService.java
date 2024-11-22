package com.rokupin.broker.service;

import com.rokupin.model.fix.FixRequest;
import reactor.core.publisher.Mono;

public interface TradingService {
    void silentConnectToRouter();

    Mono<String> handleTradingRequest(FixRequest message);

    Mono<String> getState();
}