package com.rokupin.exchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.model.StocksStateMessage;
import com.rokupin.model.fix.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.tcp.TcpServer;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FixTradeExchangeCommunicationTest {
    private TcpServer mockRouterServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        mockRouterServer = TcpServer.create().host("localhost").port(5001);
        objectMapper = new ObjectMapper();
    }

    @Test
    public void connectionTest() {
        CountDownLatch latch = new CountDownLatch(1);

        DisposableServer server = mockRouterServer.doOnConnection(connection -> connection.outbound()
                .sendString(Mono.just(makeIdAssignationMsg()), StandardCharsets.UTF_8)
                .then()
                .thenMany(connection.outbound().sendString(Mono.just(makeRequest()),
                        StandardCharsets.UTF_8).then())
                .subscribe()
        ).handle((inbound, outbound) -> inbound.receive()
                .asString(StandardCharsets.UTF_8)
                .flatMap(payload -> {
                    System.out.println("External API mock: received '" + payload + "'");
                    try {
                        StocksStateMessage stockState = objectMapper.readValue(payload, StocksStateMessage.class);
                        System.out.println("External API mock: parsed '" + stockState + "'");
                    } catch (JsonProcessingException e) {
                        try {
                            FixResponse fix = FixMessage.fromFix(payload, new FixResponse());
                            System.out.println("External API mock: parsed '" + fix + "'");
                        } catch (MissingRequiredTagException ex) {
                            assert false;
                            return null;
                        }
                    }
                    return Flux.empty();
                })
                .then()
        ).bindNow();

        try {
            // Wait for a message to be processed, or timeout after 10 minutes.
            if (!latch.await(10, TimeUnit.MINUTES)) {
                System.err.println("Test timed out waiting for server to process requests");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // Ensure the server is stopped after the test.
            server.disposeNow();
        }
    }

    private String makeRequest() {
        try {
            return new FixRequest(
                    "B00000",
                    "1",
                    "E00000",
                    "TEST1",
                    "1",
                    1).asFix();
        } catch (MissingRequiredTagException e) {
            throw new RuntimeException(e);
        }
    }

    private String makeIdAssignationMsg() {
        try {
            return new FixIdAssignation("R00000", "E00000").asFix();
        } catch (MissingRequiredTagException e) {
            assert false;
            return null;
        }
    }
}