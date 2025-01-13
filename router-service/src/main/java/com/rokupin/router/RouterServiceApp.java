package com.rokupin.router;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.router.controller.BrokerCommunicationKit;
import com.rokupin.router.controller.CommunicationKit;
import com.rokupin.router.controller.ExchangeCommunicationKit;
import com.rokupin.router.service.BrokerService;
import com.rokupin.router.service.ExchangeService;
import com.rokupin.router.service.RouterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class RouterServiceApp {
    public static void main(String[] args) {
        SpringApplication.run(RouterServiceApp.class, args);
    }

    @Bean
    CommunicationKit brokerCommunicationKit(@Value("${router.id}") String routerId) {
        return new BrokerCommunicationKit(routerId);
    }

    @Bean
    CommunicationKit exchangeCommunicationKit(@Value("${router.id}") String routerId) {
        return new ExchangeCommunicationKit(routerId);
    }

    @Bean
    RouterService brokerRoutingService(@Value("${router.tcp.broker.host}") String host,
                                       @Value("${router.tcp.broker.port}") int port,
                                       ObjectMapper objectMapper,
                                       CommunicationKit brokerCommunicationKit,
                                       CommunicationKit exchangeCommunicationKit) {
        return new BrokerService(host, port, objectMapper,
                brokerCommunicationKit,
                exchangeCommunicationKit);
    }

    @Bean
    RouterService exchangeRoutingService(@Value("${router.tcp.broker.host}") String host,
                                         @Value("${router.tcp.exchange.port}") int port,
                                         ObjectMapper objectMapper,
                                         CommunicationKit brokerCommunicationKit,
                                         CommunicationKit exchangeCommunicationKit) {
        return new ExchangeService(host, port, objectMapper,
                brokerCommunicationKit,
                exchangeCommunicationKit);
    }
}
