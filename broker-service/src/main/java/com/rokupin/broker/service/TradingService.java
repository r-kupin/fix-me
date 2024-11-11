package com.rokupin.broker.service;

import com.rokupin.model.fix.TradeRequest;
import reactor.core.publisher.Mono;

public interface TradingService {
    Mono<String> handleTradingRequest(TradeRequest message);

    Mono<String> getState();
}