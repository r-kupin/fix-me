package com.rokupin.router.service.fix;

import com.rokupin.model.fix.FixIdAssignation;
import com.rokupin.model.fix.FixMessageMisconfiguredException;
import com.rokupin.model.fix.FixMessageProcessor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.NettyOutbound;

import java.nio.charset.StandardCharsets;
import java.util.function.BiFunction;
import java.util.function.Function;

@Slf4j
public class ExchangeCommunicationKit extends CommunicationKit{

    public ExchangeCommunicationKit(String routerId) {
        super(routerId);
    }

    @Override
    public void newConnection(Connection connection,
                              String serializedState,
                              Function<String, Publisher<Void>> handlerCallback,
                              BiFunction<Throwable, NettyOutbound, Publisher<Void>> errorCallback) {
        String newExchangeId = "E" + String.format("%05d", connectedServices++);

        FixMessageProcessor exchangeInputProcessor = new FixMessageProcessor();
        exchangeInputProcessor.getFlux()
                .flatMap(handlerCallback)
                .doOnError(e -> log.error(
                        "Exchange service interaction went wrong: {}",
                        e.getMessage())
                ).subscribe();
        idToMsgProcessorMap.put(newExchangeId, exchangeInputProcessor);

        connection.outbound()
                .sendString(
                        publishWelcomeMsg(newExchangeId),
                        StandardCharsets.UTF_8
                ).then()
                .subscribe();
        connection.channel().attr(ASSIGNED_ID_KEY).set(newExchangeId);
        idToConnectionMap.put(newExchangeId, connection);
    }

    private Mono<String> publishWelcomeMsg(String newId) {
        try {
            FixIdAssignation msg = new FixIdAssignation(routerId, newId);
            log.debug("New exchange '{}' connected", newId);
            return Mono.just(msg.asFix());
        } catch (FixMessageMisconfiguredException e) {
            log.error("Cant make an exchange welcome string: {}",
                    e.getMessage());
        }
        return Mono.empty();
    }
}
