package com.rokupin.broker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.broker.model.InitialStockStateMessage;
import com.rokupin.broker.model.StocksStateMessage;
import com.rokupin.model.fix.ClientTradingRequest;
import com.rokupin.model.fix.FixRequest;
import com.rokupin.model.fix.FixResponse;
import com.rokupin.model.fix.MissingRequiredTagException;
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

    public Map<String, Map<String, Integer>> initStocksMock() {
        Map<String, Integer> stock1 = new HashMap<>();
        stock1.put("TEST1", 1);
        stock1.put("TEST2", 2);

        Map<String, Integer> stock2 = new HashMap<>();
        stock2.put("TEST3", 3);
        stock2.put("TEST4", 4);

        return Map.of("000000", stock1, "000001", stock2);
    }

    @Test
    public void routerUpdateBroadcastsToClients() {
        WebSocketClient mockClient_2 = new ReactorNettyWebSocketClient();
        WebSocketClient mockClient_3 = new ReactorNettyWebSocketClient();
        Map<String, Map<String, Integer>> stocks = initStocksMock();


        // on Message - update state, then post current to broker service
        mockRouterServer.handle(
                        (nettyInbound, nettyOutbound) -> nettyInbound.receive()
                        .asString(StandardCharsets.UTF_8)
                        .doOnNext(payload -> System.out.println("External" +
                                " API mock: received '" + payload + "'"))
                        .map(payload -> {
                            try {
                                FixRequest fixRequest = FixRequest.fromFix(payload, new FixRequest());
                                FixResponse upd = updateMockStock(stocks, fixRequest);
                                return upd.asFix();
                            } catch (MissingRequiredTagException e) {
                                if (payload.equals("STATE_UPD")) {
                                    try {
                                        return objectMapper.writeValueAsString(
                                                new InitialStockStateMessage("000001", stocks));
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
        return session.receive()
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
                            try {
                                StocksStateMessage stocksStateMessage = objectMapper.readValue(msg, StocksStateMessage.class);
                                System.out.println("WS ClientMock" + clientMockId + ": received : '" + stocksStateMessage + "'");
                                return Mono.empty();
                            } catch (JsonProcessingException exception) {
                                assert false;
                                return null;
                            }
                        }
                    }
                }).then(Mono.fromRunnable(() -> {
                    System.out.println("WS ClientMock" + clientMockId + ": disconnecting gracefully");
                    session.close().subscribe();  // Close session to simulate graceful disconnect
                }));
    }

    private void processTradingResponse(int clientMockId, String response) throws JsonProcessingException {
        FixResponse responseJson = objectMapper.readValue(response, FixResponse.class);
        System.out.println("WS ClientMock" + clientMockId +
                ": received trading response: '" + responseJson + "'");
    }

    private String assembleTradingRequest(int clientMockId, String stateJson) throws JsonProcessingException {
        Map<String, Map<String, Integer>> stocksStateMessages = objectMapper.readValue(
                stateJson, new TypeReference<>() {
                }
        );
        if (!stocksStateMessages.containsKey("000000")) {
            System.out.println("Received StockStates lacking of expected stock data");
            assert false;
            return null;
        }
        String requestJson = objectMapper.writeValueAsString(
                new ClientTradingRequest("000000",
                clientMockId == 2 ? "TEST2" : "TEST1",
                clientMockId == 2 ? "sell" : "buy",
                1
        ));
        System.out.println("WS ClientMock" + clientMockId +
                ": sends trading request: '" + requestJson + "'");
        return requestJson;
    }

    private FixResponse updateMockStock(Map<String, Map<String, Integer>> stocks, FixRequest request) {
        lock.writeLock().lock();
        try {
            String instrument = request.getInstrument();

            int before = stocks.get(request.getTarget()).get(instrument);
            int after = request.getAction().equals("buy") ?
                    before - request.getAmount() : before + request.getAmount();
            FixResponse response = new FixResponse(
                    request.getTarget(),
                    request.getSender(),
                    request.getSenderSubId(),
                    request.getInstrument(),
                    request.getAction(),
                    request.getAmount(),
                    FixResponse.MSG_ORD_REJECTED
            );
            System.out.println("Instrument:'" + instrument + "', " +
                    "amount after: " + after + " state before '" + stocks + "'");
            if (after < 0) {
                System.out.println("transaction failed");
                return response;
            }
            System.out.println("transaction succeed");
            response.setOrdStatus(FixResponse.MSG_ORD_FILLED);
            stocks.get(request.getTarget()).replace(request.getInstrument(), after);
            return response;
        } catch (Exception e) {
            System.out.println(e);
            return null;
        } finally {
            lock.writeLock().unlock();
        }
    }
}
