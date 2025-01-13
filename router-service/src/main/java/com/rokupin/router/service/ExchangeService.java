package com.rokupin.router.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.model.fix.FixMessage;
import com.rokupin.model.fix.FixMessageMisconfiguredException;
import com.rokupin.model.fix.FixStockStateReport;
import com.rokupin.router.controller.CommunicationKit;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ExchangeService extends RouterService {

    public ExchangeService(String host, int port,
                           ObjectMapper objectMapper,
                           CommunicationKit brokerCommunicationKit,
                           CommunicationKit exchangeCommunicationKit) {
        super(host, port,
                objectMapper,
                brokerCommunicationKit,
                exchangeCommunicationKit);
    }


    @PostConstruct
    private void init() {
        initServer(exchangeCommunicationKit);
    }

    @Override
    protected void doOnConnection(Connection connection) {
        exchangeCommunicationKit.newConnection(connection,
                null,
                this::handleExchangeInput,
                null);
    }

    private Publisher<Void> handleExchangeInput(String input) {
        log.debug("Received '{}' from exchange", input);
        try {
            FixStockStateReport fix = FixMessage.fromFix(input, new FixStockStateReport());
            return handleStockStateMsg(fix);
        } catch (FixMessageMisconfiguredException e) {
            return handleTradingResponseMsg(input, brokerCommunicationKit.getRouterId());
        } catch (JsonProcessingException e) {
            log.error("JSON map is misconfigured");
            return Mono.empty();
        }
    }

    private Publisher<Void> handleStockStateMsg(FixStockStateReport stockState) throws JsonProcessingException {
        ConcurrentHashMap<String, Integer> state = objectMapper.readValue(
                stockState.getStockJson(), new TypeReference<>() {}
        );

        if (!state.isEmpty()) {
            updateStateFromUpdateMessage(stockState.getSender(), state);
            String broadcastMessage = makeStateUpdateMsgString(
                    exchangeCommunicationKit.getRouterId()
            );
            if (Objects.nonNull(broadcastMessage))
                return broadcastToBrokers(broadcastMessage);
        }
        return Mono.empty();
    }

    private void updateStateFromUpdateMessage(String sender,
                                              Map<String, Integer> state) {
        if (stateCache.containsKey(sender)) {
            stateCache.replace(sender, state);
        } else {
            stateCache.putIfAbsent(sender, state);
        }
    }
}
