package com.rokupin.broker.events;

import com.rokupin.broker.model.StocksStateMessage;
import com.rokupin.model.fix.FixMessage;
import com.rokupin.model.fix.FixResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import reactor.core.publisher.FluxSink;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Configuration
public class EventConfig {
    @Bean
    @Scope(scopeName = "prototype")
    Executor eventPublisherExecutor() {
        return Executors.newSingleThreadExecutor();
    }

    @Bean
    public Consumer<FluxSink<BrokerEvent<StocksStateMessage>>> stockStateUpdateEventPublisher(
            @Qualifier("eventPublisherExecutor") Executor executor) {
        return new InputEventPublisher<>(executor);
    }

    @Bean
    public Consumer<FluxSink<BrokerEvent<FixResponse>>> tradeResponseEventPublisher(
            @Qualifier("eventPublisherExecutor") Executor executor) {
        return new InputEventPublisher<>(executor);
    }

    @Bean
    public Consumer<FluxSink<BrokerEvent<FixMessage>>> tradeRequestEventPublisher(
            @Qualifier("eventPublisherExecutor") Executor executor) {
        return new InputEventPublisher<>(executor);
    }
}
