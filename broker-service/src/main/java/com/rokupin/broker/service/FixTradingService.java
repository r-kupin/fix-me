package com.rokupin.broker.service;

import com.rokupin.broker.model.TradeRequest;
import reactor.core.publisher.Mono;

public interface FixTradingService {
    Mono<String> sendFixMessage(TradeRequest message);
}