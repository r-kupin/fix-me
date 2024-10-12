package com.rokupin.broker.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.broker.model.TradeRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableWebFlux
@Slf4j
public class WebSocketConfig {

    // to bridge events to the reactive websocket stream <1>
    @Bean
    Executor executor() {
        return Executors.newSingleThreadExecutor();
    }

    // <2>
    @Bean
    HandlerMapping handlerMapping(WebSocketHandler wsh) {
        return new SimpleUrlHandlerMapping() {
            {
                // <3>
                setUrlMap(Collections.singletonMap("/ws/requests", wsh));
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

    @Bean
    WebSocketHandler webSocketHandler(
            ObjectMapper objectMapper, // <5>
            // consumes our application events and forwards them to the reactive websocket stream
            TradeRequestCreatedEventPublisher eventPublisher // <6>
    ) {

//        Flux<TradeRequestCreatedEvent> publish = Flux
//                .create(eventPublisher)
//                .share(); // without this operator clients end up exclusively consuming events <7>

        return session -> {
            Flux<WebSocketMessage> output = session.receive()
                    .map(WebSocketMessage::getPayloadAsText)
                    .map(message -> {
                        String response_msg;
                        try {
                            response_msg = objectMapper
                                    .readValue(message, TradeRequest.class)
                                    .asFix();
                        } catch (JsonMappingException e) {
                            response_msg = "can't map";
                        } catch (JsonProcessingException e) {
                            response_msg = "invalid json";
                        }
                        return session.textMessage(response_msg);
                    });


//            Flux<WebSocketMessage> messageFlux = publish
//                    .map(evt -> {
//                        try {
//                            // TradeRequestCreatedEven to string <8>
//                            return objectMapper.writeValueAsString(evt.getSource());
//                        } catch (JsonProcessingException e) {
//                            throw new RuntimeException(e);
//                        }
//                    })
//                    .map(str -> {
//                        log.info("sending {}", str);
//                        // create message from string
//                        return session.textMessage(str);
//                    });

            return session.send(output); // <9>
        };
    }

}
