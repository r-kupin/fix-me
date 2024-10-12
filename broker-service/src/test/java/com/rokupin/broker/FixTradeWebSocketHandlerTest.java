package com.rokupin.broker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.broker.model.TradeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FixTradeWebSocketHandlerTest {

    private int port = 8081;

    @Autowired
    private ObjectMapper objectMapper;

    private WebSocketClient webSocketClient;

    @BeforeEach
    public void setup() {
        objectMapper = new ObjectMapper();
        webSocketClient = new ReactorNettyWebSocketClient();
    }

    @Test
    public void testSuccessfulTradeRequest() throws Exception {
        // Create JSON representing the trade request
        TradeRequest request = new TradeRequest("BROKER1","AAPL","buy",100,567);
        String jsonRequest = objectMapper.writeValueAsString(request);
        System.out.println("Parsed output to json:" + jsonRequest);
        URI uri = URI.create("ws://localhost:" + port + "/ws/requests");

        Mono<Void> clientExecution = webSocketClient.execute(
                uri, session -> {
                    Mono<WebSocketMessage> message = Mono.just(session.textMessage(jsonRequest));

                    Mono<Void> receiveAndClose = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .next()
                            .doOnNext(payload -> {
                                System.out.println("Received message: " + payload);
                                TradeRequest received = TradeRequest.fromFix(payload);
                                try {
                                    System.out.println("Parsed input to json:"
                                            + objectMapper.writeValueAsString(received));
                                } catch (JsonProcessingException e) {
                                    throw new RuntimeException(e);
                                }
                                assert(request.equals(received));
                            })
                            .then();
                    return session.send(message).then(receiveAndClose);
                });

        StepVerifier.create(clientExecution).expectComplete().verify();
    }
}
