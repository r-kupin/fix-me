package com.rokupin.router.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.router.service.fix.BrokerCommunicationKit;
import com.rokupin.router.service.fix.CommunicationKit;
import com.rokupin.router.service.fix.ExchangeCommunicationKit;
import com.rokupin.router.controller.TcpController;
import com.rokupin.router.service.BrokerServiceImpl;
import com.rokupin.router.service.ExchangeServiceImpl;
import com.rokupin.router.service.RouterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.EnableWebFlux;

@Configuration
@EnableWebFlux
public class RouterServiceConfig {

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
                                       CommunicationKit exchangeCommunicationKit) {
        return new BrokerServiceImpl(objectMapper,
                brokerCommunicationKit,
                exchangeCommunicationKit);
    }

    @Bean
    RouterService exchangeRoutingService(ObjectMapper objectMapper,
                                         CommunicationKit brokerCommunicationKit,
                                         CommunicationKit exchangeCommunicationKit) {
        return new ExchangeServiceImpl(objectMapper,
                brokerCommunicationKit,
                exchangeCommunicationKit);
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
