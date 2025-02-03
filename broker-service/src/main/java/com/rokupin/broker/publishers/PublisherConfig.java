package com.rokupin.broker.publishers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.broker.events.BrokerEvent;
import com.rokupin.broker.events.EventConfig;
import com.rokupin.broker.model.StocksStateMessage;
import com.rokupin.broker.service.TradingService;
import com.rokupin.model.fix.FixRequest;
import com.rokupin.model.fix.FixResponse;
import com.rokupin.model.fix.FixStateUpdateRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import reactor.core.publisher.FluxSink;

import java.util.function.Consumer;

@Configuration
@Slf4j
@Import(EventConfig.class)
public class PublisherConfig {
    @Bean
    WebSocketSessionEventHandler clientInputHandler(ObjectMapper objectMapper,
                                                    TradingService service) {
        return new ClientInputHandler(objectMapper, service);
    }

    @Bean
    WebSocketSessionEventHandler fixResponseEventHandler(
            ObjectMapper objectMapper,
            Consumer<FluxSink<BrokerEvent<FixResponse>>> tradeResponseEventPublisher
    ) {
        return new FixResponseEventHandler(objectMapper, tradeResponseEventPublisher);
    }

    @Bean
    WebSocketSessionEventHandler stocksStateMessageEventHandler(
            ObjectMapper objectMapper,
            Consumer<FluxSink<BrokerEvent<StocksStateMessage>>> stocksStateMessagePublisher
    ) {
        return new StocksStateMessageEventHandler(objectMapper, stocksStateMessagePublisher);
    }

    @Bean
    TcpConnectionEventHandler fixRequestEventHandler(
            Consumer<FluxSink<BrokerEvent<FixRequest>>> tradeRequestEventPublisher,
            TradingService service
    ) {
        return new FixRequestEventHandler(service, tradeRequestEventPublisher);
    }

    @Bean
    TcpConnectionEventHandler fixStateUpdateRequestEventHandler(
            Consumer<FluxSink<BrokerEvent<FixStateUpdateRequest>>> stateUpdateRequestEventPublisher
    ) {
        return new FixStateUpdateRequestEventHandler(stateUpdateRequestEventPublisher);
    }
}
