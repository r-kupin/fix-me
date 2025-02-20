package com.rokupin.broker.tcp;

import com.rokupin.broker.tcp.service.TcpConfigurer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.netty.tcp.TcpClient;
import reactor.util.retry.Retry;

import java.time.Duration;

@Slf4j
@Component
public class TcpConnectivityProviderImpl implements ConnectivityProvider {
    @Override
    public void connect(TcpConfigurer service, String host, int port) {
        TcpClient.create()
                .host(host)
                .port(port)
                .doOnConnected(service::configureConnection)
                .connect()
                .retryWhen(retrySpec())
                .doOnError(service::handleNotConnected)
                .doOnSuccess(service::handleConnected)
                .onErrorResume(e -> {
                    service.handleConnectionFailed(e);
                    return Mono.empty();
                }).subscribe();
    }

    private Retry retrySpec() {
        return Retry.backoff(5, Duration.ofSeconds(2))
                .maxBackoff(Duration.ofSeconds(10))
                .doBeforeRetry(signal -> log.info(
                        "TCPHandler: retrying connection, attempt {}",
                        signal.totalRetriesInARow() + 1)
                ).onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                        new RuntimeException("TCPHandler: Max retry attempts reached."));
    }
}
