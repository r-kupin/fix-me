package com.rokupin.router.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.model.fix.FixMessage;
import com.rokupin.model.fix.FixMessageMisconfiguredException;
import com.rokupin.model.fix.FixStockStateReport;
import com.rokupin.router.service.fix.CommunicationKit;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ExchangeServiceImpl extends RouterService {

    public ExchangeServiceImpl(ObjectMapper objectMapper,
                               CommunicationKit brokerCommunicationKit,
                               CommunicationKit exchangeCommunicationKit) {
        super(objectMapper,
                brokerCommunicationKit,
                exchangeCommunicationKit);
    }

    @Override
    public void doOnConnection(Connection connection) {
        exchangeCommunicationKit.newConnection(connection,
                null,
                this::handleExchangeInput,
                null);
    }

    @Override
    public OnConnectionHandler getConnectionHandler() {
        return new OnConnectionHandler(
                exchangeCommunicationKit.getIdToMsgProcessorMap()
        );
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
