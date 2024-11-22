package com.rokupin.exchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.model.fix.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.NettyOutbound;
import reactor.netty.tcp.TcpServer;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class FixTradeExchangeCommunicationTest {
    private TcpServer mockRouterServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        mockRouterServer = TcpServer.create().host("localhost").port(5001);
        objectMapper = new ObjectMapper();
    }

    /**
     * Expect output:
     * External API mock: received '{"stocks":{"E00000":{"TEST2":2,"TEST1":1}}}'
     * External API mock: received '8=FIX.5.0|35=8|49=E00000|56=B00000|57=1|55=TEST1|54=1|38=1|39=2|10=057|'
     * VerifySubscriber timed out error is fine
     */
    @Test
    public void connectionTest() {
        Map<String, Integer> expectedState = Map.of("TEST1", 1, "TEST2", 2);
        String exchangeID = "E00000";

        DisposableServer server = mockRouterServer
                .doOnConnection(connection -> connection.outbound()
                        .sendString(Mono.just(makeIdAssignationMsg(exchangeID)), StandardCharsets.UTF_8)
                        .then()
                        .subscribe()) // Send ID assignation on client connection
                .handle((inbound, outbound) -> inbound.receive()
                        .asString(StandardCharsets.UTF_8)
                        .flatMap(payload -> {
                            System.out.println("External API mock: received '" + payload + "'");
                            return processIncomingMessages(outbound, payload, exchangeID, expectedState);
                        }).then()
                ).bindNow();

        StepVerifier.create(server.onDispose())
                .expectComplete()
                .verify();

        server.disposeNow();
    }

    private Mono<Void> processIncomingMessages(NettyOutbound outbound,
                                               String payload,
                                               String exchangeID,
                                               Map<String, Integer> expectedState) {

        try {
            FixStockStateReport fix = FixMessage.fromFix(payload, new FixStockStateReport());
            Map<String, Integer> state = objectMapper.readValue(
                    fix.getStockJson(), new TypeReference<>() {
                    }
            );
            if (fix.getSender().equals(exchangeID) && state.equals(expectedState))
                return outbound.sendString(Mono.just(makeRequest(exchangeID)),
                        StandardCharsets.UTF_8).then();
            else
                return Mono.error(new AssertionError("Stock state message is wrong: " + payload));
        } catch (MissingRequiredTagException e) {
            try {
                FixResponse fix = FixMessage.fromFix(payload, new FixResponse());
                if (fix.getSender().equals(exchangeID) &&
                        fix.getTarget().equals("B00000") &&
                        fix.getTargetSubId().equals("1") &&
                        fix.getInstrument().equals("TEST1") &&
                        fix.getAction().equals("1") &&
                        fix.getAmount() == 1 &&
                        fix.getOrdStatus() == 2)
                    return Mono.empty();
                else
                    return Mono.error(new AssertionError("Stock trading reply is wrong: " + payload));
            } catch (MissingRequiredTagException ex) {
                return Mono.error(new AssertionError("unsupported inbound traffic format: " + payload));
            }
        } catch (JsonProcessingException e) {
            return Mono.error(new AssertionError("json map is misconfigured: " + payload));
        }
    }

    private String makeRequest(String exchangeId) {
        try {
            return new FixRequest(
                    "B00000",
                    "1",
                    exchangeId,
                    "TEST1",
                    "1",
                    1).asFix();
        } catch (MissingRequiredTagException e) {
            throw new RuntimeException(e);
        }
    }

    private String makeIdAssignationMsg(String exchangeId) {
        try {
            return new FixIdAssignation("R00000", exchangeId).asFix();
        } catch (MissingRequiredTagException e) {
            assert false;
            return null;
        }
    }
}