package com.rokupin.broker.tcp.service;

public class TcpConnectionError extends Exception {
    public TcpConnectionError(String msg) {
        super(msg);
    }
}
