package com.rokupin.broker;

import com.rokupin.broker.websocket.WebSocketConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({TcpController.class, WebSocketConfig.class})
public class BrokerServiceApp {
    public static void main(String[] args) {
        SpringApplication.run(BrokerServiceApp.class, args);
    }
}
