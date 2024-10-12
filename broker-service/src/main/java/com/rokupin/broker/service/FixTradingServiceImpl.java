package com.rokupin.broker.service;

import com.rokupin.broker.model.TradeRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;

import java.nio.charset.StandardCharsets;

@Service
public class FixTradingServiceImpl implements FixTradingService {
    private final Mono<Connection> connection;

    //     Initialize the TcpClient and establish connection with Service B
    public FixTradingServiceImpl() {
        connection = TcpClient.create()
                .host("localhost")
                .port(5000)
                .connect()
                .cast(Connection.class); // lazy, initialized on first call
    }

    @Override
    public Mono<String> sendFixMessage(TradeRequest tradeRequest) {
        return connection
                .flatMap(conn -> conn.outbound()
                        .sendString(Mono.just(tradeRequest.asFix()), StandardCharsets.UTF_8)
                        .then()
                        .thenReturn("Success"))
                .onErrorResume(e -> Mono.just("Fail"));
    }
}