package com.rokupin.broker.config;

import com.rokupin.broker.model.TradeRequest;
import org.springframework.context.ApplicationEvent;

public class TradeRequestCreatedEvent extends ApplicationEvent {

    public TradeRequestCreatedEvent(TradeRequest source) {
        super(source);
    }
}
