package com.rokupin.broker.session_handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.broker.events.BrokerEvent;
import com.rokupin.broker.events.InputEventPublisher;
import com.rokupin.broker.publishers.FixResponseEventHandler;
import com.rokupin.model.fix.ClientTradingResponse;
import com.rokupin.model.fix.FixRequest;
import com.rokupin.model.fix.FixResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.test.StepVerifier;

import java.util.concurrent.Executors;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FixResponseEventHandlerTest {
    private final String sessionId = "0";
    private final String brokerId = "B0000";
    private final String exchngId = "E0000";
    private final String instrument = "TEST0";

    @Mock
    private WebSocketSession session;
    private ObjectMapper objectMapper;
    private FixResponseEventHandler handler;
    private InputEventPublisher<BrokerEvent<FixResponse>> consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new InputEventPublisher<>(Executors.newSingleThreadExecutor());
        handler = new FixResponseEventHandler(objectMapper, consumer);
        when(session.getId()).thenReturn(sessionId);
    }

    @Test
    void testHandle_onValidFixResponse() throws Exception {
        // --- ARRANGEMENT
        FixResponse fixResponse = new FixResponse(
                exchngId,
                brokerId,
                sessionId,
                instrument,
                FixRequest.SIDE_BUY,
                50,
                FixResponse.MSG_ORD_FILLED,
                0
        );

        ClientTradingResponse expectedClientResponse = new ClientTradingResponse(fixResponse);
        String expectedJsonResponse = objectMapper.writeValueAsString(expectedClientResponse);

        // --- ACTION
        Publisher<String> result = handler.handle(session);

        // Simulate publishing the FIX response event
        consumer.onApplicationEvent(new BrokerEvent<>(fixResponse));

        // --- ASSERTION
        StepVerifier.create(result)
                .expectNext(expectedJsonResponse) // Expect properly formatted JSON response
                .thenCancel()
                .verify();
    }

    @Test
    void testHandle_onFixResponseForDifferentSession() throws Exception {
        // --- ARRANGEMENT
        FixResponse fixResponse = new FixResponse(
                exchngId,
                brokerId,
                "does not match",
                instrument,
                FixRequest.SIDE_BUY,
                50,
                FixResponse.MSG_ORD_FILLED,
                0
        );

        // --- ACTION
        Publisher<String> result = handler.handle(session);

        // Simulate publishing the FIX response event (wrong session)
        consumer.onApplicationEvent(new BrokerEvent<>(fixResponse));

        // --- ASSERTION
        StepVerifier.create(result)
                .expectComplete()
                .verify();
    }
}
