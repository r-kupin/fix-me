package com.rokupin.router.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.model.fix.FixMessage;
import com.rokupin.model.fix.FixMessageMisconfiguredException;
import com.rokupin.model.fix.FixResponse;
import com.rokupin.model.fix.FixStockStateReport;
import com.rokupin.router.service.fix.CommunicationKit;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.NettyOutbound;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public abstract class RouterService {
    protected final ObjectMapper objectMapper;
    protected final Map<String, Map<String, Integer>> stateCache;
    protected final CommunicationKit brokerCommunicationKit;
    protected final CommunicationKit exchangeCommunicationKit;


    public RouterService(ObjectMapper objectMapper,
                         CommunicationKit brokerCommunicationKit,
                         CommunicationKit exchangeCommunicationKit) {
        // todo: One stateCache for all!
        this.stateCache = new ConcurrentHashMap<>();
        this.objectMapper = objectMapper;
        this.brokerCommunicationKit = brokerCommunicationKit;
        this.exchangeCommunicationKit = exchangeCommunicationKit;
    }

    public abstract void doOnConnection(Connection connection);

    public abstract OnConnectionHandler getConnectionHandler();

    protected String makeStateUpdateMsgString(String id) {
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

    protected Publisher<Void> handleTradingResponseMsg(String input, String routerId) {
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
                        broadcastToBrokers(makeStateUpdateMsgString(routerId))
                );
            } else {
                return responseToBrokerPublisher;
            }
        } catch (FixMessageMisconfiguredException e) {
            log.error("Unsupported inbound traffic format: {}", e.getMessage());
        }
        return Mono.empty();
    }

    protected Publisher<Void> broadcastToBrokers(String message) {
        Map<String, Connection> brokerConnections =
                brokerCommunicationKit.getIdToConnectionMap();
        return Flux.fromIterable(brokerConnections.entrySet())
                .flatMap(entry -> forwardResponseToTargetBroker(
                        entry.getValue().outbound(), entry.getKey(), message)
                );
    }

    protected Mono<Void> forwardResponseToTargetBroker(NettyOutbound outbound,
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

    private boolean updateStateFromTradingResponse(FixResponse response) {
        if (response.getRejectionReason() == FixResponse.EXCHANGE_IS_NOT_AVAILABLE &&
                stateCache.containsKey(response.getSender())) {
            exchangeCommunicationKit.remove(response.getSender());
            stateCache.remove(response.getSender());
            return true;
        }
        return false;
    }
}
