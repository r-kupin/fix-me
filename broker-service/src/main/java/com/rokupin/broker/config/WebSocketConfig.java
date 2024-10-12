package com.rokupin.broker.config;

import com.rokupin.broker.handlers.TradingWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableWebFlux
@Slf4j
public class WebSocketConfig {

    private final TradingWebSocketHandler tradingWebSocketHandler;

    public WebSocketConfig(TradingWebSocketHandler tradingWebSocketHandler) {
        this.tradingWebSocketHandler = tradingWebSocketHandler;
    }

    @Bean
    HandlerMapping handlerMapping() {
        Map<String, WebSocketHandler> handlers = new HashMap<>();
        handlers.put("/ws/requests", tradingWebSocketHandler);
        return new SimpleUrlHandlerMapping() {
            {
                setUrlMap(handlers);
                setOrder(10);
            }
        };
    }

    // bridges the websocket support in Spring WebFlux with Spring WebFlux’s
    // general routing machinery <4>
    @Bean
    WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
