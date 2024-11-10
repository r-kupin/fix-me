package com.rokupin.broker.model.trading_msg;

public class MissingRequiredTagException extends Exception {
    public MissingRequiredTagException(String s) {
        super(s);
    }
}
