package com.rokupin.exchange;

import com.rokupin.exchange.controller.TcpController;
import com.rokupin.exchange.service.ExchangeService;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.reactive.config.EnableWebFlux;

@SpringBootApplication
@EnableTransactionManagement
@EnableWebFlux
public class ExchangeServiceApp {
    public static void main(String[] args) {
        SpringApplication.run(ExchangeServiceApp.class, args);
    }

    @Bean
    ReactiveTransactionManager transactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }

    @Bean
    TcpController tcpController(@Value("${tcp.host}") String host,
                                @Value("${tcp.port}") int port,
                                ExchangeService service) {
        return new TcpController(host, port, service);
    }
}
