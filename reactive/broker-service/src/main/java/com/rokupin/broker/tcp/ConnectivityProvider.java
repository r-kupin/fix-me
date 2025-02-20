package com.rokupin.broker.tcp;

import com.rokupin.broker.tcp.service.TcpConfigurer;

public interface ConnectivityProvider {
    void connect(TcpConfigurer service, String host, int port);
}
