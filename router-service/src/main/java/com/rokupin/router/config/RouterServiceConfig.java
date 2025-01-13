package com.rokupin.router.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.router.controller.TcpController;
import com.rokupin.router.service.BrokerServiceImpl;
import com.rokupin.router.service.ExchangeServiceImpl;
import com.rokupin.router.service.RouterService;
import com.rokupin.router.service.fix.BrokerCommunicationKit;
import com.rokupin.router.service.fix.CommunicationKit;
import com.rokupin.router.service.fix.ExchangeCommunicationKit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.EnableWebFlux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableWebFlux
public class RouterServiceConfig {
    @Bean
    Map<String, Map<String, Integer>> stateCache() {
        return new ConcurrentHashMap<>();
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
    RouterService brokerRoutingService(ObjectMapper objectMapper,
                                       CommunicationKit brokerCommunicationKit,
                                       CommunicationKit exchangeCommunicationKit,
                                       Map<String, Map<String, Integer>> stateCache) {
        return new BrokerServiceImpl(objectMapper,
                brokerCommunicationKit,
                exchangeCommunicationKit,
                stateCache);
    }

    @Bean
    RouterService exchangeRoutingService(ObjectMapper objectMapper,
                                         CommunicationKit brokerCommunicationKit,
                                         CommunicationKit exchangeCommunicationKit,
                                         Map<String, Map<String, Integer>> stateCache) {
        return new ExchangeServiceImpl(objectMapper,
                brokerCommunicationKit,
                exchangeCommunicationKit,
                stateCache);
    }

    @Bean
    TcpController brokerController(@Value("${router.tcp.broker.host}") String host,
                                   @Value("${router.tcp.broker.port}") int port,
                                   RouterService brokerRoutingService) {
        return new TcpController(host, port, brokerRoutingService);
    }

    @Bean
    TcpController exchangeController(@Value("${router.tcp.exchange.host}") String host,
                                     @Value("${router.tcp.exchange.port}") int port,
                                     RouterService exchangeRoutingService) {
        return new TcpController(host, port, exchangeRoutingService);
    }
}
