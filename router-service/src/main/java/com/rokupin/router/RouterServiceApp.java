package com.rokupin.router;

import com.rokupin.router.controller.BrokerCommunicationKit;
import com.rokupin.router.controller.CommunicationKit;
import com.rokupin.router.controller.ExchangeCommunicationKit;
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
}
