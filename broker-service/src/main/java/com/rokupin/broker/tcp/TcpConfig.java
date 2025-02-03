package com.rokupin.broker.tcp;

import com.rokupin.broker.publishers.PublisherConfig;
import com.rokupin.broker.publishers.TcpConnectionEventHandler;
import com.rokupin.broker.service.TradingService;
import com.rokupin.broker.tcp.service.TcpHandler;
import com.rokupin.broker.tcp.service.TcpHandlerImpl;
import com.rokupin.broker.tcp.service.TcpHandlerService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;

import java.util.List;

@Configuration
@Import(PublisherConfig.class)
public class TcpConfig {

    @Bean
    TcpHandlerService tcpHandlerService(
            @Qualifier("fixRequestEventHandler")
            TcpConnectionEventHandler fixRequestEventHandler,
            @Qualifier("fixStateUpdateRequestEventHandler")
            TcpConnectionEventHandler fixStateUpdateRequestEventHandler
    ) {
        return new TcpHandlerServiceImpl(List.of(
                fixRequestEventHandler,
                fixStateUpdateRequestEventHandler
        ));
    }

    @Bean
    @DependsOn("tcpConnectivityProviderImpl")
    TcpHandler tcpHandler(
            TcpHandlerService tcpHandlerService,
            @Qualifier("tcpConnectivityProviderImpl")
            ConnectivityProvider connectivityProvider,
            @Value("${tcp.host}") String host,
            @Value("${tcp.port}") int port,
            TradingService tradingService
    ) {
        return new TcpHandlerImpl(
                host,
                port,
                tcpHandlerService,
                connectivityProvider,
                tradingService
        );
    }
}
