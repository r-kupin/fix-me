package com.rokupin.broker.events;

import org.springframework.context.ApplicationEvent;

public class BrokerEvent<E> extends ApplicationEvent {

    public BrokerEvent(E source) {
        super(source);
    }
}
