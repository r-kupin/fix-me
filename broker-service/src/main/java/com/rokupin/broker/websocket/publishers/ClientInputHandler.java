package com.rokupin.broker.websocket.publishers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.broker.model.CommunicationReport;
import com.rokupin.broker.service.TradingService;
import com.rokupin.model.fix.ClientTradingRequest;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class ClientInputHandler implements WebSocketSessionEventHandler {
    private final ObjectMapper objectMapper;
    private final TradingService service;

    public ClientInputHandler(ObjectMapper objectMapper,
                              TradingService service) {
        this.objectMapper = objectMapper;
        this.service = service;
    }

    @Override
    public Publisher<String> handle(WebSocketSession session) {
        log.debug("WSHandler [{}]: client input handler is ready", session.getId());

        return Flux.just(service.getState())
                .mergeWith(session.receive()
                        .map(WebSocketMessage::getPayloadAsText)
                        .flatMap(msg -> clientInputHandler(msg, session)));
    }

    private Publisher<String> clientInputHandler(String msg,
                                            WebSocketSession session) {
        String report;

        log.debug("WSHandler [{}]: processing request '{}'", session.getId(), msg);

        try {
            ClientTradingRequest clientMsg = objectMapper.readValue(msg,
                    ClientTradingRequest.class
            );
            report = service.handleMessageFromClient(clientMsg, session.getId());
        } catch (JsonMappingException e) {
            log.warn("WSHandler [{}]: Mapping failed: {}",
                    session.getId(), e.toString());
            report = "Mapping to FIX failed: " + e;
        } catch (JsonProcessingException e) {
            log.warn("WSHandler [{}]: JSON parsing failed: {}",
                    session.getId(), e.toString());
            report = "JSON syntax is incorrect: " + e;
        }

        if (!report.isEmpty()) {
            try {
                return Mono.just(objectMapper.writeValueAsString(
                        new CommunicationReport(report))
                );
            } catch (JsonProcessingException e) {
                log.warn("WSHandler [{}]: parsing to JSON failed: {}",
                        session.getId(), e.toString());
            }
        }
        return Mono.empty();
    }
}
