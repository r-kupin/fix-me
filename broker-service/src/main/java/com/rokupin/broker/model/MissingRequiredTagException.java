package com.rokupin.broker.model;

public class MissingRequiredTagException extends Exception {
    public MissingRequiredTagException(String s) {
        super(s);
    }
}
