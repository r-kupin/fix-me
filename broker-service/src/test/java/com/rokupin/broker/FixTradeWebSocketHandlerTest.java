package com.rokupin.broker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.broker.model.MissingRequiredTagException;
import com.rokupin.broker.model.StocksStateMessage;
import com.rokupin.broker.model.TradeRequest;
import com.rokupin.broker.model.TradeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Mono;
import reactor.netty.tcp.TcpServer;
import reactor.test.StepVerifier;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FixTradeWebSocketHandlerTest {

    private final int port = 8081;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    @Autowired
    private ObjectMapper objectMapper;
    private WebSocketClient mockClient;
    private TcpServer mockRouterServer;
    private URI brockerServiceWsUri;

    @BeforeEach
    public void setup() {
        objectMapper = new ObjectMapper();
        mockClient = new ReactorNettyWebSocketClient();
        mockRouterServer = TcpServer.create().host("localhost").port(5000);
        brockerServiceWsUri = URI.create("ws://localhost:" + port + "/ws/requests");
    }

    @Test
    public void clientJsonToRouterFIXTest() throws Exception {
        // JSON request that client sends to BrokerService
        TradeRequest request = new TradeRequest("BROKER1", "0", 1, "ABCD",
                "buy", 1);
        String jsonRequest = objectMapper.writeValueAsString(request);

        // Setup RouterService listening mock to verify transferred fix messages
        mockRouterServer.handle((nettyInbound, nettyOutbound) -> nettyInbound.receive()
                        .asString(StandardCharsets.UTF_8)
                        .doOnNext(payload -> {
                            System.out.println("Router received: '" + payload + "'");
                            try {
                                TradeRequest parsed = TradeRequest.fromFix(payload, new TradeRequest());
                                assert request.equals(parsed);
                            } catch (MissingRequiredTagException e) {
                                assert false;
                            }
                        }).then())
                .bind()
                .subscribe();

        // Setup client mock to send trading requests to BrokerService
        Mono<Void> clientExecution = mockClient.execute(brockerServiceWsUri, session -> {
            Mono<WebSocketMessage> message = Mono.just(session.textMessage(jsonRequest));

            Mono<Void> receiveAndClose = session.receive()
                    .map(WebSocketMessage::getPayloadAsText)
                    .doOnNext(payload -> System.out.println("Client received: '" + payload + "'"))
                    .take(2)
                    .then();
            return session.send(message).then(receiveAndClose);
        });
        // perform execution
        StepVerifier.create(clientExecution).expectComplete().verify();
    }

    @Test
    public void routerUpdateBroadcastsToClients() {
        WebSocketClient mockClient_2 = new ReactorNettyWebSocketClient();
        WebSocketClient mockClient_3 = new ReactorNettyWebSocketClient();
        Map<String, Integer> map = new HashMap<>();

        // initial state to send on connection (1 stock with 2 instruments)
        map.put("TEST1", 1);
        map.put("TEST2", 2);

        // on Message - update state, then post current to broker service
        mockRouterServer.handle((nettyInbound, nettyOutbound) -> nettyInbound
                        .receive()
                        .asString(StandardCharsets.UTF_8)
                        .doOnNext(payload -> System.out.println("External" +
                                " API mock: received '" + payload + "'"))
                        .map(payload -> {
                            try {
                                TradeRequest fixRequest = TradeRequest.fromFix(payload, new TradeRequest());
                                TradeResponse upd = updateMockStock(map, fixRequest);
                                return upd.asFix();
                            } catch (MissingRequiredTagException e) {
                                if (payload.equals("STATE_UPD")) {
                                    try {
                                        return objectMapper.writeValueAsString(new StocksStateMessage(1, map));
                                    } catch (JsonProcessingException ex) {
                                        assert false;
                                        return null;
                                    }
                                } else {
                                    assert false;
                                    return null;
                                }
                            }
                        }).flatMap(updStr ->
                                nettyOutbound.sendString(Mono.just(updStr))
                                        .then(Mono.fromRunnable(() ->
                                                System.out.println("External API mock: sent: '" +
                                                        updStr + "'")))
                        ).then())
                .bind().subscribe();


        Mono<Void> clientExecution1 = mockClient.execute(brockerServiceWsUri,
                session -> sendRequestWaitForStockStateUpdate(session, 1));

        Mono<Void> clientExecution2 = mockClient_2.execute(brockerServiceWsUri,
                session -> sendRequestWaitForStockStateUpdate(session, 2));

        Mono<Void> clientExecution3 = mockClient_3.execute(brockerServiceWsUri,
                session -> sendRequestWaitForStockStateUpdate(session, 3));

        StepVerifier.create(Mono.when(clientExecution1,
                        clientExecution2,
                        clientExecution3))
                .expectComplete().verify();
    }

    private Mono<Void> sendRequestWaitForStockStateUpdate(WebSocketSession session, int clientMockId) {
        final AtomicBoolean tradingRequestSent = new AtomicBoolean(false);

        return session.receive()
                .take(7) // wait only for current stocks
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(msg -> System.out.println("WS ClientMock" + clientMockId +
                        ": received : '" + msg + "'"))
                .flatMap(msg -> {
                    try {
                        String requestJson = assembleTradingRequest(clientMockId, msg);
                        return session.send(Mono.just(session.textMessage(requestJson)));
                    } catch (JsonProcessingException e) {
                        try {
                            processTradingResponse(clientMockId, msg);
                            return Mono.empty();
                        } catch (JsonProcessingException ex) {
                            if (msg.equals("Trading request sent"))
                                return Mono.empty();
                            assert false;
                            return null;
                        }
                    }
                }).then(Mono.fromRunnable(() -> {
                    System.out.println("WS ClientMock" + clientMockId + ": disconnecting gracefully");
                    session.close().subscribe();  // Close session to simulate graceful disconnect
                }));
    }

    private void processTradingResponse(int clientMockId, String response) throws JsonProcessingException {
        TradeResponse responseJson = objectMapper.readValue(response, TradeResponse.class);
        System.out.println("WS ClientMock" + clientMockId +
                ": received trading response: '" + responseJson + "'");
    }

    private String assembleTradingRequest(int clientMockId, String stateJson) throws JsonProcessingException {
        StocksStateMessage stocksStateMessage = objectMapper.readValue(stateJson, StocksStateMessage.class);
        String requestJson = objectMapper.writeValueAsString(new TradeRequest(
                "BROKER" + clientMockId,
                "BROKER" + clientMockId + "_subId",
                stocksStateMessage.getStockId(),
                clientMockId == 2 ? "TEST2" : "TEST1",
                clientMockId == 2 ? "sell" : "buy",
                1
        ));
        System.out.println("WS ClientMock" + clientMockId +
                ": sends trading request: '" + requestJson + "'");
        return requestJson;
    }

    private TradeResponse updateMockStock(Map<String, Integer> map, TradeRequest request) {
        lock.writeLock().lock();
        try {
            String instrument = request.getInstrument();
            int before = map.get(instrument);
            int after = request.getAction().equals("buy") ?
                    before - request.getAmount() : before + request.getAmount();
            TradeResponse response = new TradeResponse(
                    request.getTarget(),
                    request.getSender(),
                    request.getSenderSubId(),
                    request.getInstrument(),
                    request.getAction(),
                    request.getAmount(),
                    TradeResponse.MSG_ORD_REJECTED
            );
            System.out.println("Instrument:'" + instrument + "', " +
                    "amount after: " + after + " state before '" + map + "'");
            if (after < 0) {
                System.out.println("transaction failed");
                return response;
            }
            System.out.println("transaction succeed");
            response.setOrdStatus(TradeResponse.MSG_ORD_FILLED);
            map.replace(request.getInstrument(), after);
            return response;
        } finally {
            lock.writeLock().unlock();
        }
    }
}
