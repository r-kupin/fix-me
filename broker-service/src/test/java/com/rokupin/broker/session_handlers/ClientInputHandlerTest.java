package com.rokupin.broker.session_handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.broker.publishers.ClientInputHandler;
import com.rokupin.broker.service.TradingService;
import com.rokupin.model.fix.ClientTradingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientInputHandlerTest {

    private final String sessionId = "0";
    private final ClientTradingRequest validRequest = new ClientTradingRequest(
            "E0001",
            "TEST1",
            "buy",
            100
    );
    @Mock
    private WebSocketSession session;
    @Mock
    private TradingService tradingService;
    private ObjectMapper objectMapper;
    private ClientInputHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new ClientInputHandler(objectMapper, tradingService);

        when(session.getId()).thenReturn(sessionId);
    }

    @Test
    void testHandle_onClientConnected() throws Exception {
        // --- ARRANGEMENT
        String requestJson = objectMapper.writeValueAsString(validRequest);
        String expectedResponse = "";
        String expectedState = "{\"stocks\":{}}";

        when(tradingService.getState()).thenReturn(expectedState);
        when(session.receive()).thenReturn(Flux.just(mockMessage(requestJson)));
        when(tradingService.handleMessageFromClient(any(), eq(sessionId)))
                .thenReturn(expectedResponse);

        // --- ACTION
        Publisher<String> result = handler.handle(session);

        // --- ASSERTION
        StepVerifier.create(result)
                .expectNext(expectedState)
                .expectComplete()
                .verify();
    }

    @Test
    void testHandle_onMalformedJsonRequest() {
        // --- ARRANGEMENT
        String malformedJson = "{invalidJson}";
        String expectedState = "{\"stocks\":{}}";
        String expectedError = "JSON syntax is incorrect";

        when(tradingService.getState()).thenReturn(expectedState);
        when(session.receive()).thenReturn(Flux.just(mockMessage(malformedJson)));

        // --- ACTION
        Publisher<String> result = handler.handle(session);

        // --- ASSERTION
        StepVerifier.create(result)
                .expectNext(expectedState)
                .expectNextMatches(response -> response.contains(expectedError))
                .thenCancel()
                .verify();
    }

    private WebSocketMessage mockMessage(String payload) {
        DataBufferFactory bufferFactory = new DefaultDataBufferFactory();
        DataBuffer dataBuffer = bufferFactory.wrap(payload.getBytes(StandardCharsets.UTF_8));

        return new WebSocketMessage(WebSocketMessage.Type.TEXT, dataBuffer);
    }
}

