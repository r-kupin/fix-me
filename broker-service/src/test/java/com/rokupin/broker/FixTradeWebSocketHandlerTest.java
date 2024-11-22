package com.rokupin.broker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.broker.model.StocksStateMessage;
import com.rokupin.model.fix.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.NettyOutbound;
import reactor.netty.tcp.TcpServer;
import reactor.test.StepVerifier;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FixTradeWebSocketHandlerTest {

    private final int port = 8081;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
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
                .doOnConnection(connection -> connection.outbound()
                        .sendString(Mono.just(makeJsonInitialMsg(routerID, brokerID, stocks)), StandardCharsets.UTF_8)
                        .then()
                        .subscribe()
                ).handle((inbound, outbound) -> inbound.receive()
                        .asString(StandardCharsets.UTF_8)
                        .flatMap(payload -> handleIncomingData(payload, stocks, brokerID, outbound))
                        .doOnError(e -> {
                            throw new AssertionError(e.getMessage());
                        }).then()
                ).bindNow();

        Mono<Void> clientsExecution = Mono.when(clientExecution1,
                        clientExecution2,
                        clientExecution3)
                .then(Mono.defer(() -> Mono.fromRunnable(server::dispose)));

        StepVerifier.create(Mono.when(clientsExecution, server.onDispose()))
                .expectComplete()
                .verify();
    }

    private Mono<Void> handleIncomingData(String data,
                                          Map<String, Map<String, Integer>> stocks,
                                          String brokerID,
                                          NettyOutbound outbound) {
        return Flux.fromIterable(splitFixMessages(data)) // Split into individual messages
                .flatMap(msg -> processIncomingMessage(
                        msg, stocks, brokerID, outbound
                )).then();
    }

    private List<String> splitFixMessages(String messages) {
        List<String> fixMessages = new ArrayList<>();
        StringBuilder currentMessage = new StringBuilder();
        String[] parts = messages.split("\u0001"); // Split by the SOH character

        for (String part : parts) {
            currentMessage.append(part).append("\u0001"); // Re-add the delimiter
            if (part.startsWith("10=")) { // Detect the end of a FIX message
                fixMessages.add(currentMessage.toString());
                currentMessage.setLength(0); // Reset for the next message
            }
        }
        return fixMessages;
    }

    private Mono<Void> processIncomingMessage(String payload,
                                              Map<String, Map<String, Integer>> stocks,
                                              String brokerID,
                                              NettyOutbound outbound) {
        System.out.println("External API mock: received '" + payload + "'");
        try {
            FixRequest request = FixRequest.fromFix(payload, new FixRequest());
            if (request.getSender().equals(brokerID)) {
                String fix = updateMockStock(stocks, request).asFix();
                System.out.println("External API mock: sending: '" + fix + "'");
                return outbound.sendString(Mono.just(fix), StandardCharsets.UTF_8).then();
            }
            return Mono.error(new AssertionError("Received message is not supported"));
        } catch (MissingRequiredTagException e) {
            return Mono.error(new AssertionError("Received message is not supported"));
        }
    }

    private Mono<Void> sendRequestWaitForStockStateUpdate(WebSocketSession session, int clientMockId) {
        return session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(msg -> {
                    System.out.println("WS ClientMock" + clientMockId +
                            ": received : '" + msg + "'");
                    try { // if message is a stocks state
                        String requestJson = assembleTradingRequestJson(clientMockId, msg);
                        return session.send(Mono.just(session.textMessage(requestJson)));
                    } catch (JsonProcessingException e) {
                        try { // if message is a trading response
                            processTradingResponse(clientMockId, msg);
                            return Mono.empty();
                        } catch (JsonProcessingException ex) {
                            if (msg.equals("Trading request sent")) {
                                // if message is a sending confirmation
                                return Mono.empty();
                            }
                            try {
                                StocksStateMessage stocksStateMessage = objectMapper.readValue(msg, StocksStateMessage.class);
                                System.out.println("WS ClientMock" + clientMockId + ": received : '" + stocksStateMessage + "'");
                                return Mono.empty();
                            } catch (JsonProcessingException exception) {
                                return Mono.error(new AssertionError(
                                        "Received message '" + msg +
                                                "' was expected to be a valid StocksStateMessage instance"));
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

    private String assembleTradingRequestJson(int clientMockId, String stateJson) throws JsonProcessingException {
        Map<String, Map<String, Integer>> stocksStateMessages = objectMapper.readValue(
                stateJson, new TypeReference<>() {
                }
        );
        if (!stocksStateMessages.containsKey("E00000")) {
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
        } catch (JsonProcessingException | MissingRequiredTagException e) {
            throw new RuntimeException(e);
        }
    }

    private String serializeStockState(Map<String, Map<String, Integer>> stocks) {
        lock.readLock().lock();
        try {
            return objectMapper.writeValueAsString(stocks);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } finally {
            lock.readLock().unlock();
        }
    }
}
