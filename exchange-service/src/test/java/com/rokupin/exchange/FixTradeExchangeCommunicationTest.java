package com.rokupin.exchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.exchange.model.IdAssignationMessage;
import com.rokupin.exchange.repo.StockRepo;
import com.rokupin.model.fix.TradeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.MountableFile;
import reactor.core.publisher.Mono;
import reactor.netty.tcp.TcpServer;

import java.nio.charset.StandardCharsets;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FixTradeExchangeCommunicationTest {
    @Container
    static MariaDBContainer<?> mariaDB = new MariaDBContainer<>("mariadb:latest")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("testcontainers/create-schema.sql"),
                    "/docker-entrypoint-initdb.d/init.sql");
    @Autowired
    StockRepo repo;

    @Autowired
    private ObjectMapper objectMapper;
    private TcpServer mockRouterServer;

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url",
                () -> "r2dbc:mariadb://" + mariaDB.getHost() + ":" +
                        mariaDB.getFirstMappedPort() + "/" +
                        mariaDB.getDatabaseName());
        registry.add("spring.r2dbc.username", () -> mariaDB.getUsername());
        registry.add("spring.r2dbc.password", () -> mariaDB.getPassword());
    }

    @BeforeEach
    public void setup() {
        objectMapper = new ObjectMapper();
        mockRouterServer = TcpServer.create().host("localhost").port(5001);
    }

    @Test
    public void connectionTest() {
        mockRouterServer.handle((nettyInbound, nettyOutbound) -> {
            nettyOutbound.sendString(Mono.just(makeRequest()))
                    .then(nettyOutbound.sendString(Mono.just(makeTradingRequest())))
                    .then()
                    .subscribe();

            return nettyInbound.receive()
                    .asString(StandardCharsets.UTF_8)
                    .doOnNext(payload -> System.out.println(
                            "External API mock: received '" + payload + "'")
                    ).then();
        }).bind().subscribe();
    }

    private String makeRequest() {
        try {
            return objectMapper.writeValueAsString(new TradeRequest(
                    "000000",
                    "1",
                    "000000",
                    "TEST1",
                    "buy",
                    1));
        } catch (JsonProcessingException e) {
            assert false;
            return null;
        }
    }

    private String makeTradingRequest() {
        try {
            return objectMapper.writeValueAsString(
                    new IdAssignationMessage("000001"));
        } catch (JsonProcessingException e) {
            assert false;
            return null;
        }
    }
}