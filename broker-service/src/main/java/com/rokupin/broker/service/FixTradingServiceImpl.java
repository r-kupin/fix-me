package com.rokupin.broker.service;

import com.rokupin.broker.model.TradeRequest;
import org.springframework.stereotype.Service;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;

import java.nio.charset.StandardCharsets;

@Service
public class FixTradingServiceImpl implements FixTradingService {
//    private final Connection connection;

    // Initialize the TcpClient and establish connection with Service B
    public FixTradingServiceImpl() {
//        connection = TcpClient.create()
//                .host("localhost")
//                .port(5000)
//                .connectNow();
    }

    @Override
    public Mono<Void> sendFixMessage(TradeRequest tradeRequest) {
        System.out.println(tradeRequest.asFix());
        return Mono.empty().then();
//        return connection.outbound()
//                .sendString(Mono.just(tradeRequest.asFix()),
//                        StandardCharsets.UTF_8)
//                .then();
    }
}