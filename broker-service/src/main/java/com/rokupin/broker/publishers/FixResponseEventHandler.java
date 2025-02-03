package com.rokupin.broker.publishers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.broker.events.BrokerEvent;
import com.rokupin.model.fix.ClientTradingResponse;
import com.rokupin.model.fix.FixMessageMisconfiguredException;
import com.rokupin.model.fix.FixResponse;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.util.EventObject;
import java.util.function.Consumer;

@Slf4j
public class FixResponseEventHandler implements WebSocketSessionEventHandler {

    private final ObjectMapper objectMapper;
    private final Flux<Object> inputFlux;

    public FixResponseEventHandler(ObjectMapper objectMapper,
                                   Consumer<FluxSink<BrokerEvent<FixResponse>>> responseConsumer) {
        this.objectMapper = objectMapper;
        this.inputFlux = Flux.create(responseConsumer)
                .share()
                .map(EventObject::getSource);
    }

    @Override
    public Publisher<String> handle(WebSocketSession session) {
        log.debug("WSHandler [{}]: fix response handler is ready", session.getId());
        return inputFlux.flatMap(event -> handleEmission(event, session));
    }

    private Publisher<String> handleEmission(Object event, WebSocketSession session) {
        if (event instanceof FixResponse fixResponse) {
            if (fixResponse.getTargetSubId().equals(session.getId())) {
                log.debug("WSHandler [{}]: processing trading " +
                        "response event '{}'", session.getId(), event);
                return fixToClientResponse(session, fixResponse);
            }
        }
        return Mono.empty();
    }

    private Publisher<String> fixToClientResponse(WebSocketSession session,
                                                  FixResponse fixResponse) {
        try {
            ClientTradingResponse response = new ClientTradingResponse(fixResponse);
            try {
                String jsonResponse = objectMapper.writeValueAsString(response);
                log.debug("WSHandler [{}]: sending a trade " +
                        "response: '{}'", session.getId(), jsonResponse);
                return Mono.just(jsonResponse);
            } catch (JsonProcessingException e) {
                log.warn("WSHandler [{}]: response: '{}' can't be " +
                                "serialized to JSON:'{}'",
                        session.getId(), response, e.getMessage());
                return Mono.empty();
            }
        } catch (FixMessageMisconfiguredException e) {
            log.warn("WSHandler [{}]: Fix trading response: '{}' can't be " +
                            "converted to the ClientResponse:'{}'",
                    session.getId(), fixResponse, e.getMessage());
            return Mono.empty();
        }
    }
}
