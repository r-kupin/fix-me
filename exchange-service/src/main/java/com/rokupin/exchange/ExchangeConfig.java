package com.rokupin.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.exchange.controller.TcpController;
import com.rokupin.exchange.repo.StockRepo;
import com.rokupin.exchange.service.ExchangeService;
import com.rokupin.exchange.service.ExchangeServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExchangeConfig {
    @Bean
    TcpController tcpController(@Value("${tcp.host}") String host,
                                @Value("${tcp.port}") int port,
                                ExchangeService service) {
        return new TcpController(host, port, service);
    }

    @Bean
    ExchangeService exchangeService(@Value("${exchange.max-amount}") int maxAmount,
                                    StockRepo stockRepo,
                                    ObjectMapper objectMapper) {
        return new ExchangeServiceImpl(stockRepo, objectMapper, maxAmount);
    }
}
