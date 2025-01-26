package com.rokupin.broker.service;

import com.rokupin.model.fix.ClientTradingRequest;

public interface TradingService {
    void            handleMessageFromRouter(String message);
    String          handleMessageFromClient(ClientTradingRequest clientMsg,
                                            String clientId);
    String          getAssignedId();

    String getState();
}