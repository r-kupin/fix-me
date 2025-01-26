package com.rokupin.broker.events;

import org.springframework.context.ApplicationEvent;

import java.util.Objects;

public class BrokerEvent<E> extends ApplicationEvent {

    public BrokerEvent(E source) {
        super(source);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BrokerEvent<E> that = (BrokerEvent<E>) o;
        return Objects.equals(source, that.source);
    }
}
