package com.rokupin.router.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.model.fix.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpServer;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RouterServiceImpl {
    private final ObjectMapper objectMapper;

    private final TcpServer brokerServer;
    private final TcpServer exchangeServer;
    private final String id;
    private final Map<String, Map<String, Integer>> stateCache;
    private final Map<String, Connection> brokerConnections;
    private final Map<String, Connection> exchangeConnections;

    int brokers, exchanges;

    public RouterServiceImpl(ObjectMapper objectMapper,
                             @Value("${router.tcp.broker.host}") String brokerHost,
                             @Value("${router.tcp.broker.port}") int brokerPort,
                             @Value("${router.tcp.exchange.host}") String exchangeHost,
                             @Value("${router.tcp.exchange.port}") int exchangePort,
                             @Value("${router.id}") String id) {

        this.id = id;
        this.objectMapper = objectMapper;

        brokers = 0;
        exchanges = 0;
        stateCache = new ConcurrentHashMap<>();
        brokerConnections = new ConcurrentHashMap<>();
        exchangeConnections = new ConcurrentHashMap<>();
        brokerServer = TcpServer.create().host(brokerHost).port(brokerPort);
        exchangeServer = TcpServer.create().host(exchangeHost).port(exchangePort);
    }

    @PostConstruct
    private void init() {
        brokerServer.doOnConnection(connection -> connection.outbound()
                        .sendString(welcomeNewBroker(connection), StandardCharsets.UTF_8)
                        .then()
                        .subscribe()
                ).handle((inbound, outbound) -> inbound.receive()
                        .asString(StandardCharsets.UTF_8)
                        .flatMap(data -> Flux.fromIterable(FixMessage.splitFixMessages(data)))
                        .flatMap(this::handleBrokerInput)
                        .doOnError(e -> log.error(
                                "Broker service interaction went wrong: {}",
                                e.getMessage())
                        ).then()
                ).bindNow()
                .onDispose()
                .subscribe();

        exchangeServer.doOnConnection(connection -> connection.outbound()
                        .sendString(welcomeNewExchange(connection), StandardCharsets.UTF_8)
                        .then()
                        .subscribe()
                ).handle((inbound, outbound) -> inbound.receive()
                        .asString(StandardCharsets.UTF_8)
                        .flatMap(data -> Flux.fromIterable(FixMessage.splitFixMessages(data)))
                        .flatMap(this::handleExchangeInput)
                        .doOnError(e -> log.error(
                                "Exchange service interaction went wrong: {}",
                                e.getMessage())
                        ).then()
                ).bindNow()
                .onDispose()
                .subscribe();
    }

    private Mono<String> welcomeNewExchange(Connection connection) {
        try {
            String newId = "E" + String.format("%05d", exchanges++);
            FixIdAssignation msg = new FixIdAssignation(id, newId);
            exchangeConnections.put(newId, connection);
            return Mono.just(msg.asFix());
        } catch (MissingRequiredTagException e) {
            log.error("Router service: Cant make an exchange welcome string: {}",
                    e.getMessage());
        }
        return Mono.empty();
    }

    private Mono<Void> handleExchangeInput(String input) {
        try {
            FixStockStateReport stockState = FixMessage.fromFix(input, new FixStockStateReport());
            Map<String, Integer> state = objectMapper.readValue(
                    stockState.getStockJson(), new TypeReference<>() {
                    });
            if (!state.isEmpty()) { // stateCache.put(stockState.getSender(), state);
                if (stateCache.containsKey(stockState.getSender()))
                    stateCache.replace(stockState.getSender(), state);
                else
                    stateCache.putIfAbsent(stockState.getSender(), state);
                String broadcastMessage = new FixStockStateReport(id, objectMapper.writeValueAsString(stateCache)).asFix();
                return Flux.fromIterable(brokerConnections.values())
                        .flatMap(connection -> connection.outbound()
                                .sendString(Mono.just(broadcastMessage), StandardCharsets.UTF_8)
                                .then())
                        .then();
            }
        } catch (MissingRequiredTagException e) {
            try {
                FixResponse response = FixMessage.fromFix(input, new FixResponse());
                updateState(response);
                Connection brokerConnection = brokerConnections.get(response.getTarget());
                if (Objects.nonNull(brokerConnection)) {
                    return brokerConnection.outbound()
                            .sendString(Mono.just(input), StandardCharsets.UTF_8)
                            .then();
                } else {
                    log.warn("Target broker {} not connected for trading response",
                            response.getTarget());
                }
            } catch (MissingRequiredTagException ex) {
                log.error("Router service: unsupported inbound traffic format: {}",
                        ex.getMessage());
            }
        } catch (JsonProcessingException e) {
            log.error("Router service: json map is misconfigured");
        }
        return Mono.empty();
    }

    private void updateState(FixResponse response) {
        String id = response.getSender();

        if (!stateCache.containsKey(id)) {
            log.warn("Response from unknown stock id: {}", id);
        } else if (response.getOrdStatus() == FixResponse.MSG_ORD_FILLED) {
            Map<String, Integer> stock = stateCache.get(id);
            String instrument = response.getInstrument();
            if (!stock.containsKey(instrument)) {
                log.warn("Stock id: {} sent response on unknown " +
                        "instrument: {}", id, instrument);
            } else {
                int before = stock.get(instrument);
                int after = response.getAction() == 1 ?
                        before - response.getAmount() : before + response.getAmount();
                if (after < 0) {
                    log.warn("Remaining instrument amount can't be negative." +
                                    "stock response: '{}', current amount: {}",
                            response, before);
                } else {
                    stock.replace(instrument, after);
                }
            }
        }
    }

    private Mono<String> welcomeNewBroker(Connection connection) {
        try {
            String newId = "B" + String.format("%05d", brokers++);
            FixIdAssignationStockState msg = new FixIdAssignationStockState(
                    id, newId, objectMapper.writeValueAsString(stateCache)
            );
            brokerConnections.put(newId, connection);
            return Mono.just(msg.asFix());
        } catch (MissingRequiredTagException e) {
            log.error("Router service: Cant make an broker welcome string: {}",
                    e.getMessage());
        } catch (JsonProcessingException e) {
            log.error("Router service: Cant serialize cache map");
        }
        return Mono.empty();
    }

    private Mono<Void> handleBrokerInput(String input) {
        try {
            FixRequest request = FixMessage.fromFix(input, new FixRequest());

            Connection exchangeConnection = exchangeConnections.get(request.getTarget());
            if (exchangeConnection != null) {
                return exchangeConnection.outbound()
                        .sendString(Mono.just(input), StandardCharsets.UTF_8)
                        .then();
            } else {
                log.warn("Target exchange {} not connected for trading request", request.getTarget());
            }
        } catch (MissingRequiredTagException e) {
            log.error("Router service: unsupported broker input format: {}", e.getMessage());
        }
        return Mono.empty();
    }
}
