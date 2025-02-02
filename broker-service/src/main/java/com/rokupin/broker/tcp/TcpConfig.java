package com.rokupin.broker.tcp;

import com.rokupin.broker.events.BrokerEvent;
import com.rokupin.broker.service.TradingService;
import com.rokupin.broker.tcp.service.TcpHandler;
import com.rokupin.broker.tcp.service.TcpHandlerImpl;
import com.rokupin.model.fix.FixMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import reactor.core.publisher.FluxSink;

import java.util.function.Consumer;

@Configuration
public class TcpConfig {
    @Bean
    @DependsOn("tcpConnectivityProviderImpl")
    TcpHandler tcpHandler(
            @Qualifier("tradeRequestEventPublisher")
            Consumer<FluxSink<BrokerEvent<FixMessage>>> consumer,
            @Qualifier("tcpConnectivityProviderImpl")
            ConnectivityProvider connectivityProvider,
            @Value("${tcp.host}") String host,
            @Value("${tcp.port}") int port,
            TradingService tradingService
    ) {
        return new TcpHandlerImpl(host, port, consumer, connectivityProvider, tradingService);
    }
}
