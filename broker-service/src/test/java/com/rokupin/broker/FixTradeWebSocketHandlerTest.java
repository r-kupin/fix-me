package com.rokupin.broker;

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
import reactor.netty.tcp.TcpServer;
import reactor.test.StepVerifier;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FixTradeWebSocketHandlerTest {

    private final int port = 8081;

    @Autowired
    private ObjectMapper objectMapper;

    private WebSocketClient mockClient;
    private TcpServer mockRouterServer;

    @BeforeEach
    public void setup() {
        objectMapper = new ObjectMapper();
        mockClient = new ReactorNettyWebSocketClient();
        mockRouterServer = TcpServer.create().host("localhost").port(5000);
    }

    @Test
    public void clientJsonToRouterFIXTest() throws Exception {
        // JSON request that client sends to BrockerService
        TradeRequest request = new TradeRequest("BROKER1", "AAPL", "buy", 100, 567);
        String jsonRequest = objectMapper.writeValueAsString(request);
        // BrockerService URL
        URI uri = URI.create("ws://localhost:" + port + "/ws/requests");

        // Setup RouterService listening mock to verify transferred fix messages
        mockRouterServer.handle((nettyInbound, nettyOutbound) ->
                        nettyInbound.receive()
                                .asString(StandardCharsets.UTF_8)
                                .doOnNext(payload -> {
                                    System.out.println(
                                            "Router received: '" + payload + "'");
                                    assert (request.equals(TradeRequest.fromFix(payload)));
                                })
                                .then())
                .bind().subscribe();

        // Setup client mock to send trading requests to BrokerService
        Mono<Void> clientExecution = mockClient.execute(
                uri, session -> {
                    Mono<WebSocketMessage> message = Mono.just(session.textMessage(jsonRequest));

                    Mono<Void> receiveAndClose = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .next()
                            .doOnNext(payload -> {
                                System.out.println(
                                        "Client received: '" + payload + "'");
                                assert payload.equals("Success");
                            })
                            .then();
                    return session.send(message).then(receiveAndClose);
                });

        // perform execution
        StepVerifier.create(clientExecution).expectComplete().verify();
    }
}
