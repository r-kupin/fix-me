package com.rokupin.broker.events;

import org.springframework.context.ApplicationEvent;

public class InputEvent<E> extends ApplicationEvent {

    public InputEvent(E source) {
        super(source);
    }
}
