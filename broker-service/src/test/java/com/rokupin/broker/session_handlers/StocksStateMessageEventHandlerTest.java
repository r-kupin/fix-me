package com.rokupin.broker.session_handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.broker.events.BrokerEvent;
import com.rokupin.broker.events.InputEventPublisher;
import com.rokupin.broker.model.StocksStateMessage;
import com.rokupin.broker.publishers.StocksStateMessageEventHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.concurrent.Executors;

@ExtendWith(MockitoExtension.class)
class StocksStateMessageEventHandlerTest {

    @Mock
    private WebSocketSession session;

    private ObjectMapper objectMapper;
    private StocksStateMessageEventHandler handler;
    private InputEventPublisher<BrokerEvent<StocksStateMessage>> consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new InputEventPublisher<>(Executors.newSingleThreadExecutor());
        handler = new StocksStateMessageEventHandler(objectMapper, consumer);
    }

    @Test
    void testHandle_onValidStockStateMessage() throws JsonProcessingException {
        // --- ARRANGEMENT
        StocksStateMessage stocksStateMessage = new StocksStateMessage(
                Map.of("E00001", Map.of("TEST1", 100, "TEST2", 200),
                        "E00002", Map.of("TEST3", 300))
        );

        // --- ACTION
        Publisher<String> result = handler.handle(session);
        consumer.onApplicationEvent(new BrokerEvent<>(stocksStateMessage));

        // --- ASSERTION
        StepVerifier.create(result)
                .expectNext(objectMapper.writeValueAsString(stocksStateMessage))
                .thenCancel()
                .verify();
    }
}
