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
import reactor.netty.NettyOutbound;
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
                        .onErrorResume(e -> handleBrokerCommunicationError(e, outbound))
                        .then()
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

// -------------------------- Exchange connectivity

    private Mono<String> welcomeNewExchange(Connection connection) {
        try {
            String newId = "E" + String.format("%05d", exchanges++);
            FixIdAssignation msg = new FixIdAssignation(id, newId);
            exchangeConnections.put(newId, connection);
            log.debug("New exchange '{}' connected", newId);
            return Mono.just(msg.asFix());
        } catch (FixMessageMisconfiguredException e) {
            log.error("Cant make an exchange welcome string: {}",
                    e.getMessage());
        }
        return Mono.empty();
    }

    private Mono<Void> handleExchangeInput(String input) {
        log.debug("Received '{}' from exchange", input);
        try {
            FixStockStateReport fix = FixMessage.fromFix(input, new FixStockStateReport());
            return handleStockStateMsg(fix);
        } catch (FixMessageMisconfiguredException e) {
            return handleTradingResponseMsg(input);
        } catch (JsonProcessingException e) {
            log.error("JSON map is misconfigured");
            return Mono.empty();
        }
    }

// ---------------- Stock update message handling

    private Mono<Void> handleStockStateMsg(FixStockStateReport stockState) throws JsonProcessingException {
        Map<String, Integer> state = objectMapper.readValue(
                stockState.getStockJson(), new TypeReference<>() {
                });

        if (!state.isEmpty()) {
            updateStateFromUpdateMessage(stockState.getSender(), state);
            String broadcastMessage = makeStateUpdateMsgString();
            if (Objects.nonNull(broadcastMessage))
                return broadcastToBrokers(broadcastMessage);
        }
        return Mono.empty();
    }

    private String makeStateUpdateMsgString() {
        try {
            return new FixStockStateReport(
                    id, objectMapper.writeValueAsString(stateCache)
            ).asFix();
        } catch (FixMessageMisconfiguredException e) {
            log.error("Can't make fix state update message: {}", e.getMessage());
        } catch (JsonProcessingException e) {
            log.error("JSON cache map is misconfigured: {}", e.getMessage());
        }
        return null;
    }

// ---------------- Trading response handling

    private Mono<Void> handleTradingResponseMsg(String input) {
        try {
            FixResponse response = FixMessage.fromFix(input, new FixResponse());
            boolean stateModified = updateStateFromTradingResponse(response);

            Connection connection = brokerConnections.get(response.getTarget());
            if (Objects.isNull(connection)) {
                log.warn("Target broker {} not connected for trading response", response.getTarget());
                return Mono.empty();
            }

            log.debug("Sending trading response to '{}'", response.getTarget());
            if (!stateModified)
                return forwardResponseToTargetBroker(connection.outbound(), input);
            return sendStateUpdateWithResponse(response, connection, input);
        } catch (FixMessageMisconfiguredException e) {
            log.error("Unsupported inbound traffic format: {}", e.getMessage());
        }
        return Mono.empty();
    }

    private Mono<Void> sendStateUpdateWithResponse(FixResponse response,
                                                   Connection brokerConnection,
                                                   String input) {
        String broadcastMessage = makeStateUpdateMsgString();

        return Flux.concat(
                broadcastToBrokers(response.getTarget(), broadcastMessage),
                forwardResponseToTargetBroker(brokerConnection.outbound(), input)
        ).then();
    }

// ---------------- Sending data to brokers

    private Mono<Void> broadcastToBrokers(String excludeTarget, String message) {
        return Flux.fromIterable(brokerConnections.entrySet())
                .filter(entry -> !entry.getKey().equals(excludeTarget))
                .flatMap(entry -> {
                    log.debug("Sending state to {}", entry.getKey());
                    Connection connection = entry.getValue();
                    return connection.outbound()
                            .sendString(Mono.just(message), StandardCharsets.UTF_8)
                            .then()
                            .doOnError(e -> log.warn("Failed to send state"));
                }).then();
    }

    private Mono<Void> broadcastToBrokers(String message) {
        return Flux.fromIterable(brokerConnections.entrySet())
                .flatMap(entry -> {
                    log.debug("Sending state to {}", entry.getKey());
                    Connection connection = entry.getValue();
                    return connection.outbound()
                            .sendString(Mono.just(message), StandardCharsets.UTF_8)
                            .then()
                            .doOnError(e -> log.warn("Failed to send state"));
                }).then();
    }

    private Mono<Void> forwardResponseToTargetBroker(NettyOutbound outbound, String message) {
        return outbound.sendString(Mono.just(message), StandardCharsets.UTF_8)
                .then()
                .doOnError(e -> log.warn("Failed to forward trading response"));
    }

// ---------------- DB cache update

    private void updateStateFromUpdateMessage(String sender,
                                              Map<String, Integer> state) {
        if (stateCache.containsKey(sender)) {
            stateCache.replace(sender, state);
        } else {
            stateCache.putIfAbsent(sender, state);
        }
    }

    private boolean updateStateFromTradingResponse(FixResponse response) {
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
                int after = response.getAction() == FixRequest.SIDE_BUY ?
                        before - response.getAmount() : before + response.getAmount();
                if (after < 0) {
                    log.warn("Remaining instrument amount can't be negative." +
                                    "stock response: '{}', current amount: {}",
                            response, before);
                } else {
                    stock.replace(instrument, after);
                    return true;
                }
            }
        } else if (response.getRejectionReason() == FixResponse.EXCHANGE_IS_NOT_AVAILABLE) {
            exchangeConnections.remove(response.getSender());
            stateCache.remove(response.getSender());
            return true;
        }
        return false;
    }

// -------------------------- Broker connectivity

    private Mono<String> welcomeNewBroker(Connection connection) {
        try {
            String newId = "B" + String.format("%05d", brokers++);
            FixIdAssignationStockState msg = new FixIdAssignationStockState(
                    id, newId, objectMapper.writeValueAsString(stateCache)
            );
            brokerConnections.put(newId, connection);
            log.debug("New broker '{}' connected", newId);
            return Mono.just(msg.asFix());
        } catch (FixMessageMisconfiguredException e) {
            log.error("Cant make an broker welcome string: {}", e.getMessage());
        } catch (JsonProcessingException e) {
            log.error("Cant serialize cache map");
        }
        return Mono.empty();
    }

    private Mono<Void> handleBrokerInput(String input) {
        log.debug("Received '{}' from broker", input);
        try {
            FixRequest request = FixMessage.fromFix(input, new FixRequest());

            Connection exchangeConnection = exchangeConnections.get(request.getTarget());
            if (Objects.nonNull(exchangeConnection)) {
                return exchangeConnection.outbound()
                        .sendString(Mono.just(input), StandardCharsets.UTF_8)
                        .then()
                        .onErrorResume(e -> handleTradingResponseMsg(
                                makeFixResponseStr(request, FixResponse.EXCHANGE_IS_NOT_AVAILABLE)));
            } else {
                log.warn("Target exchange {} is unavailable", request.getTarget());
                String fixMsg = makeFixResponseStr(request, FixResponse.EXCHANGE_IS_NOT_AVAILABLE);
                return Mono.error(new ConnectivityException(fixMsg));
            }
        } catch (FixMessageMisconfiguredException e) {
            log.warn("Unsupported broker input format: {}", e.getMessage());
            return Mono.empty();
        }
    }

    private String makeFixResponseStr(FixRequest request, int reason) {
        try {
            return new FixResponse(
                    request.getTarget(),        // non-accessible exchange (In fact, Router)
                    request.getSender(),        // receiving service id
                    request.getSenderSubId(),   // receiving client id
                    request.getInstrument(),
                    request.getAction(),
                    request.getAmount(),
                    FixResponse.MSG_ORD_REJECTED,
                    reason
            ).asFix();
        } catch (FixMessageMisconfiguredException e) {
            log.error("FixResponse for request {}, reason {} failed: {}", request, reason, e.getMessage());
            return null;
        }
    }

    private Mono<Void> handleBrokerCommunicationError(Throwable throwable, NettyOutbound outbound) {
        if (throwable instanceof ConnectivityException e ) {
            log.debug("Sending fix error message: '{}'", e.getMessage());
            return forwardResponseToTargetBroker(outbound, e.getMessage());
        }
        log.warn("Broker service communication went wrong '{}'", throwable.getMessage());
        return Mono.empty();
    }
}