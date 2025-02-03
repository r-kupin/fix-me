package com.rokupin.broker.websocket;

import com.rokupin.broker.publishers.PublisherConfig;
import com.rokupin.broker.publishers.WebSocketSessionEventHandler;
import com.rokupin.broker.websocket.service.WebSocketHandlerService;
import com.rokupin.broker.websocket.service.WebSocketHandlerServiceImpl;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.List;
import java.util.Map;

@Configuration
@Import(PublisherConfig.class)
public class WebSocketConfig {
    @Bean
    WebSocketHandlerService webSocketHandlerService(
            @Qualifier("clientInputHandler")
            WebSocketSessionEventHandler clientInputHandler,
            @Qualifier("fixResponseEventHandler")
            WebSocketSessionEventHandler fixResponseEventHandler,
            @Qualifier("stocksStateMessageEventHandler")
            WebSocketSessionEventHandler stocksStateMessageEventHandler
    ) {
        return new WebSocketHandlerServiceImpl(List.of(
                clientInputHandler,
                fixResponseEventHandler,
                stocksStateMessageEventHandler
        ));
    }

    @Bean
    WebSocketHandler webSocketHandler(WebSocketHandlerService webSocketHandlerService) {
        return new WebSocketHandlerImpl(webSocketHandlerService);
    }

    @Bean
    WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }

    @Bean
    HandlerMapping handlerMapping(WebSocketHandlerService service) {
        return new SimpleUrlHandlerMapping() {
            {
                setUrlMap(Map.of("/ws/requests", webSocketHandler(service)));
                setOrder(10);
            }
        };
    }
}
