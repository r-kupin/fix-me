package com.rokupin.exchange.service;

import com.rokupin.model.fix.FixRequest;
import com.rokupin.model.fix.FixResponse;
import reactor.core.publisher.Mono;

public interface ExchangeService {
    Mono<String> publishCurrentStockState(String assignedId);
    Mono<FixResponse> processTradeRequest(FixRequest request, String assignedId);
}
