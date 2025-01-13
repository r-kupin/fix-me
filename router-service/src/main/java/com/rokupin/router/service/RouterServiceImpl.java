package com.rokupin.router.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.model.fix.*;
import com.rokupin.router.controller.CommunicationKit;
import com.rokupin.router.controller.ExchangeConnectivityFailure;
import com.rokupin.router.controller.OnConnectionHandler;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
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
import java.util.function.Consumer;

@Slf4j
@Service
public class RouterServiceImpl {
    private final ObjectMapper objectMapper;

    private final TcpServer brokerServer;
    private final TcpServer exchangeServer;
    private final Map<String, Map<String, Integer>> stateCache;
    private final CommunicationKit brokerCommunicationKit;
    private final CommunicationKit exchangeCommunicationKit;

    public RouterServiceImpl(CommunicationKit brokerCommunicationKit,
                             CommunicationKit exchangeCommunicationKit,
                             ObjectMapper objectMapper,
                             @Value("${router.tcp.broker.host}") String brokerHost,
                             @Value("${router.tcp.broker.port}") int brokerPort,
                             @Value("${router.tcp.exchange.host}") String exchangeHost,
                             @Value("${router.tcp.exchange.port}") int exchangePort,
                             @Value("${router.id}") String id) {
        this.objectMapper = objectMapper;
        this.brokerCommunicationKit = brokerCommunicationKit;
        this.exchangeCommunicationKit = exchangeCommunicationKit;

        stateCache = new ConcurrentHashMap<>();
        brokerServer = TcpServer.create().host(brokerHost).port(brokerPort);
        exchangeServer = TcpServer.create().host(exchangeHost).port(exchangePort);
    }

    @PostConstruct
    private void init() {
        initServer(brokerServer, this::doOnBrokerConnection,
                brokerCommunicationKit.getIdToMsgProcessorMap());
        initServer(exchangeServer, this::doOnExchangeConnection,
                exchangeCommunicationKit.getIdToMsgProcessorMap());
    }

    private void initServer(TcpServer server,
                            Consumer<Connection> onConnection,
                            Map<String, FixMessageProcessor> inputProcessorsMap) {
        server.doOnConnection(onConnection)
                .doOnConnection(new OnConnectionHandler(inputProcessorsMap))
                .bindNow()
                .onDispose()
                .subscribe();
    }

    private void doOnBrokerConnection(Connection connection) {
        try {
            String state = objectMapper.writeValueAsString(stateCache);
            brokerCommunicationKit.newConnection(connection,
                    state,
                    this::handleBrokerInput,
                    this::handleBrokerCommunicationError
            );
        } catch (JsonProcessingException e) {
            log.error("Cant serialize cache map");
        }
    }

    private void doOnExchangeConnection(Connection connection) {
        exchangeCommunicationKit.newConnection(connection,
                null,
                this::handleExchangeInput,
                null);
    }

// -------------------------- Exchange connectivity

    private Publisher<Void> handleExchangeInput(String input) {
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

    private Publisher<Void> handleStockStateMsg(FixStockStateReport stockState) throws JsonProcessingException {
        ConcurrentHashMap<String, Integer> state = objectMapper.readValue(
                stockState.getStockJson(), new TypeReference<>() {}
        );

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

    private Publisher<Void> handleTradingResponseMsg(String input) {
        try {
            FixResponse response = FixMessage.fromFix(input, new FixResponse());
            boolean stateModified = updateStateFromTradingResponse(response);

            Connection connection = brokerCommunicationKit.getConnectionById(response.getTarget());

            if (Objects.isNull(connection)) {
                log.warn("Target broker {} not connected for trading response", response.getTarget());
                return Mono.empty();
            }

            Mono<Void> responseToBrokerPublisher = forwardResponseToTargetBroker(
                    connection.outbound(),
                    response.getTarget(),
                    input
            );

            if (stateModified) {
                return Flux.concat(
                        responseToBrokerPublisher,
                        broadcastToBrokers(makeStateUpdateMsgString())
                );
            } else {
                return responseToBrokerPublisher;
            }
        } catch (FixMessageMisconfiguredException e) {
            log.error("Unsupported inbound traffic format: {}", e.getMessage());
        }
        return Mono.empty();
    }

// ---------------- Sending data to brokers

    private Publisher<Void> broadcastToBrokers(String message) {
        Map<String, Connection> brokerConnections =
                    brokerCommunicationKit.getIdToConnectionMap();
        return Flux.fromIterable(brokerConnections.entrySet())
                .flatMap(entry -> forwardResponseToTargetBroker(
                        entry.getValue().outbound(), entry.getKey(), message)
                );
    }

    private Mono<Void> forwardResponseToTargetBroker(NettyOutbound outbound,
                                                     String brokerId,
                                                     String message) {
        log.debug("Sending '{}' to {}", message, brokerId);

        return outbound.sendString(Mono.just(message), StandardCharsets.UTF_8)
                .then()
                .onErrorResume(e -> {
                    log.warn("Failed to send to {}. Removing connection: {}",
                            brokerId, e.getMessage());
                    brokerCommunicationKit.remove(brokerId);
                    return Mono.empty();
                });
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
        if (response.getRejectionReason() == FixResponse.EXCHANGE_IS_NOT_AVAILABLE &&
                stateCache.containsKey(response.getSender())) {
            exchangeCommunicationKit.remove(response.getSender());
            stateCache.remove(response.getSender());
            return true;
        }
        return false;
    }

// -------------------------- Broker connectivity
    private Publisher<Void> handleBrokerInput(String input) {
        log.debug("Received '{}' from broker", input);
        try {
            return handleTradingRequest(input);
        } catch (FixMessageMisconfiguredException e) {
            try {
                return handleUpdateRequest(input);
            } catch (FixMessageMisconfiguredException ex) {
                log.warn("Unsupported broker input format: {}", e.getMessage());
                return Mono.empty();
            }
        }
    }

    private Publisher<Void> handleUpdateRequest(String input) throws FixMessageMisconfiguredException {
        FixStateUpdateRequest request = FixMessage.fromFix(input, new FixStateUpdateRequest());
        String sender = request.getSender();
        Connection brokerConnection = brokerCommunicationKit.getConnectionById(sender);

        if (Objects.nonNull(brokerConnection)) {
            return forwardResponseToTargetBroker(
                    brokerConnection.outbound(),
                    sender,
                    makeStateUpdateMsgString()
            );
        } else {
            brokerCommunicationKit.remove(sender);
            return Mono.empty();
        }
    }

    private Publisher<Void> handleTradingRequest(String input) throws FixMessageMisconfiguredException {
        FixRequest request = FixMessage.fromFix(input, new FixRequest());
        String target = request.getTarget();
        Connection exchangeConnection = exchangeCommunicationKit.getConnectionById(target);

        if (Objects.nonNull(exchangeConnection)) {
            return exchangeConnection.outbound()
                    .sendString(Mono.just(input), StandardCharsets.UTF_8)
                    .then()
                    .onErrorResume(e -> Mono.from(
                            publishUnavailableExchangeResponse(request))
                    );
        } else {
            return publishUnavailableExchangeResponse(request);
        }
    }

    private Publisher<Void> publishUnavailableExchangeResponse(FixRequest request) {
        log.warn("Target exchange {} is unavailable", request.getTarget());
        String fixMsg = makeFixResponseStr(request,
                FixResponse.EXCHANGE_IS_NOT_AVAILABLE
        );
        return handleTradingResponseMsg(fixMsg);
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
        if (throwable instanceof ExchangeConnectivityFailure e) {
            log.debug("Sending fix error message: '{}'", e.getMessage());
            return forwardResponseToTargetBroker(
                    outbound, "Can't get ID at this point", e.getMessage());
        }
        log.warn("Broker service communication went wrong '{}'", throwable.getMessage());
        return Mono.empty();
    }
}
