package com.rokupin.router.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.model.fix.*;
import com.rokupin.router.service.fix.CommunicationKit;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.NettyOutbound;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Slf4j
public class BrokerServiceImpl extends RouterService {

    public BrokerServiceImpl(ObjectMapper objectMapper,
                             CommunicationKit brokerCommunicationKit,
                             CommunicationKit exchangeCommunicationKit) {
        super(objectMapper,
                brokerCommunicationKit,
                exchangeCommunicationKit);
    }

    @Override
    public void doOnConnection(Connection connection) {
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

    @Override
    public OnConnectionHandler getConnectionHandler() {
        return new OnConnectionHandler(
                brokerCommunicationKit.getIdToMsgProcessorMap()
        );
    }

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
                    makeStateUpdateMsgString(
                            brokerCommunicationKit.getRouterId()
                    )
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
        return handleTradingResponseMsg(fixMsg, exchangeCommunicationKit.getRouterId());
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
