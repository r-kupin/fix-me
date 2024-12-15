package com.rokupin.broker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.broker.model.StocksStateMessage;
import com.rokupin.model.fix.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.NettyOutbound;
import reactor.netty.tcp.TcpServer;
import reactor.test.StepVerifier;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FixTradeWebSocketHandlerTest {

    private final int port = 8081;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private ObjectMapper objectMapper;
    private WebSocketClient mockClient;
    private TcpServer mockRouterServer;
    private URI brockerServiceWsUri;
    private final boolean[] clientSentRequest = {false, false, false, false};
    private FixMessageProcessor fixMessageProcessor;

    @BeforeEach
    public void setup() {
        objectMapper = new ObjectMapper();
        mockClient = new ReactorNettyWebSocketClient();
        mockRouterServer = TcpServer.create().host("localhost").port(5000);
        brockerServiceWsUri = URI.create("ws://localhost:" + port + "/ws/requests");
        fixMessageProcessor = new FixMessageProcessor();
    }

    public Map<String, Map<String, Integer>> initStocksMock(String exchange1ID, String exchange2ID) {
        Map<String, Integer> stock1 = new HashMap<>();
        stock1.put("TEST1", 1);
        stock1.put("TEST2", 2);

        Map<String, Integer> stock2 = new HashMap<>();
        stock2.put("TEST3", 3);
        stock2.put("TEST4", 4);

        return Map.of(exchange1ID, stock1, exchange2ID, stock2);
    }

    @Test
    public void routerUpdateBroadcastsToClients() {
        WebSocketClient mockClient_2 = new ReactorNettyWebSocketClient();
        WebSocketClient mockClient_3 = new ReactorNettyWebSocketClient();
        String brokerID = "B00000";
        String routerID = "R00000";
        String exchange1ID = "E00000";
        String exchange2ID = "E00001";
        Map<String, Map<String, Integer>> stocks = initStocksMock(exchange1ID, exchange2ID);

        Mono<Void> clientExecution1 = mockClient.execute(brockerServiceWsUri,
                session -> sendRequestWaitForStockStateUpdate(session, 1));

        Mono<Void> clientExecution2 = mockClient_2.execute(brockerServiceWsUri,
                session -> sendRequestWaitForStockStateUpdate(session, 2));

        Mono<Void> clientExecution3 = mockClient_3.execute(brockerServiceWsUri,
                session -> sendRequestWaitForStockStateUpdate(session, 3));

        DisposableServer server = mockRouterServer
                .doOnConnection(connection -> {
                            fixMessageProcessor.getFlux()
                                    .flatMap(payload ->
                                            processIncomingMessage(payload,
                                                    stocks,
                                                    brokerID,
                                                    connection.outbound())
                                    ).subscribe();

                            connection.outbound()
                                    .sendString(
                                            Mono.just(makeJsonInitialMsg(routerID, brokerID, stocks)),
                                            StandardCharsets.UTF_8
                                    ).then()
                                    .subscribe();
                        }
                ).handle((inbound, outbound) -> inbound.receive()
                        .asString(StandardCharsets.UTF_8)
                        .doOnNext(fixMessageProcessor::processInput)
                        .then()
                ).bindNow();

        Mono<Void> clientsExecution = Mono.when(
                clientExecution1,
                clientExecution2,
                clientExecution3
        ).then(Mono.fromRunnable(server::disposeNow));

        StepVerifier.create(clientsExecution)
                .expectComplete()
                .verify();
    }

    private Mono<Void> processIncomingMessage(String payload,
                                              Map<String, Map<String, Integer>> stocks,
                                              String brokerID,
                                              NettyOutbound outbound) {
        System.out.println("External API mock: received '" + payload + "'");
        try {
            FixRequest request = FixMessage.fromFix(payload, new FixRequest());
            if (request.getSender().equals(brokerID)) {
                String fix = updateMockStock(stocks, request).asFix();
                System.out.println("External API mock: sending: '" + fix + "'");
                return outbound.sendString(Mono.just(fix), StandardCharsets.UTF_8).then();
            }
            return Mono.error(new AssertionError("Received message is not supported"));
        } catch (FixMessageMisconfiguredException e) {
            return Mono.error(new AssertionError("Received message is not supported"));
        }
    }

    private Mono<Void> sendRequestWaitForStockStateUpdate(WebSocketSession session, int clientMockId) {
        return session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(msg -> onMessage(msg, clientMockId, session))
                .then(Mono.fromRunnable(() -> {
                    System.out.println("WS ClientMock" + clientMockId + ": disconnecting gracefully");
                    session.close().subscribe();  // Close session to simulate graceful disconnect
                }));
    }

    private Mono<Void> onMessage(String msg, int clientMockId, WebSocketSession session) {
        System.out.println("WS ClientMock" + clientMockId + ": received : '" + msg + "'");
        try { // if message is a stocks state
            StocksStateMessage stocksStateMessage = objectMapper.readValue(msg, StocksStateMessage.class);
            if (!clientSentRequest[clientMockId]) {
                clientSentRequest[clientMockId] = true;
                String requestJson = assembleTradingRequestJson(stocksStateMessage, clientMockId);
                return session.send(Mono.just(session.textMessage(requestJson)));
            } else {
                return Mono.empty();
            }
        } catch (JsonProcessingException e) {
            try { // if message is a trading response
                processTradingResponse(clientMockId, msg);
                return Mono.empty();
            } catch (JsonProcessingException ex) {
                if (msg.equals("Trading request sent")) {
                    // if message is a sending confirmation
                    return Mono.empty();
                } else {
                    return Mono.error(new AssertionError(
                            "Received message '" + msg + "' was not expected"));
                }
            }
        }
    }

    private void processTradingResponse(int clientMockId, String response) throws JsonProcessingException {
        ClientTradingResponse responseJson = objectMapper.readValue(response, ClientTradingResponse.class);
        System.out.println("WS ClientMock" + clientMockId +
                ": received trading response: '" + responseJson + "'");
    }

    private String assembleTradingRequestJson(StocksStateMessage stocksStateMessage, int clientMockId) throws JsonProcessingException {
        if (!stocksStateMessage.getStocks().containsKey("E00000")) {
            System.out.println("Received StockStates lacking of expected stock data");
            assert false;
            return null;
        }
        String requestJson = objectMapper.writeValueAsString(
                new ClientTradingRequest("E00000",
                        clientMockId == 2 ? "TEST2" : "TEST1",
                        clientMockId == 2 ? "sell" : "buy",
                        1
                ));
        System.out.println("WS ClientMock" + clientMockId +
                ": sends trading request: '" + requestJson + "'");
        return requestJson;
    }

    private FixResponse updateMockStock(Map<String, Map<String, Integer>> stocks,
                                        FixRequest request) {
        lock.writeLock().lock();
        try {
            String instrument = request.getInstrument();

            int before = stocks.get(request.getTarget()).get(instrument);
            int after = request.getAction() == 1 ?
                    before - request.getAmount() : before + request.getAmount();
            FixResponse response = new FixResponse(
                    request.getTarget(),
                    request.getSender(),
                    request.getSenderSubId(),
                    request.getInstrument(),
                    request.getAction(),
                    request.getAmount(),
                    FixResponse.MSG_ORD_REJECTED,
                    FixResponse.EXCHANGE_LACKS_REQUESTED_AMOUNT
            );
            System.out.println("Instrument:'" + instrument + "', " +
                    "amount after: " + after + " state before '" + stocks + "'");
            if (after < 0) {
                System.out.println("transaction failed");
                return response;
            }
            System.out.println("transaction succeed");
            response.setOrdStatus(FixResponse.MSG_ORD_FILLED);
            response.setRejectionReason(0);
            stocks.get(request.getTarget()).replace(request.getInstrument(), after);
            return response;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private String makeJsonInitialMsg(String routerID,
                                      String brokerID,
                                      Map<String, Map<String, Integer>> stocks) {
        try {
            return new FixIdAssignationStockState(
                    routerID, brokerID, objectMapper.writeValueAsString(stocks)
            ).asFix();
        } catch (JsonProcessingException | FixMessageMisconfiguredException e) {
            throw new RuntimeException(e);
        }
    }
}
