package com.rokupin.broker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.HashMap;
import java.util.Map;

//@Configuration
@Slf4j
public class ControllerConfig {

    private final WebSocketController webSocketController;
    private final TcpController tcpController;

    public ControllerConfig(WebSocketController webSocketController,
                            TcpController tcpController) {
        this.webSocketController = webSocketController;
        this.tcpController = tcpController;
    }

    //    @Bean
    WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }

    //    @Bean
    HandlerMapping handlerMapping() {
        Map<String, WebSocketHandler> handlers = new HashMap<>();
        handlers.put("/ws/requests", webSocketController);
        return new SimpleUrlHandlerMapping() {
            {
                setUrlMap(handlers);
                setOrder(10);
            }
        };
    }
}
