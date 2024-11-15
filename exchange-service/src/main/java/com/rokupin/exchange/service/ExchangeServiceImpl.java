package com.rokupin.exchange.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.exchange.model.IdAssignationMessage;
import com.rokupin.exchange.model.InstrumentEntry;
import com.rokupin.exchange.repo.StockRepo;
import com.rokupin.model.fix.MissingRequiredTagException;
import com.rokupin.model.fix.TradeRequest;
import com.rokupin.model.fix.TradeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Service
@Transactional
public class ExchangeServiceImpl {
    private final StockRepo stockRepo;
    private final ObjectMapper objectMapper;
    private final Mono<Connection> connectionPublisher;

    private String assignedId;

    public ExchangeServiceImpl(StockRepo stockRepo, ObjectMapper objectMapper,
                               @Value("${tcp.host}") String host,
                               @Value("${tcp.port}") int port) {
        this.stockRepo = stockRepo;
        this.objectMapper = objectMapper;
        this.connectionPublisher = Mono.defer(() -> connectTcpClient(host, port)) // Eagerly start the connection
                .retryWhen(Retry.fixedDelay(Long.MAX_VALUE, Duration.ofSeconds(1)) // Retry indefinitely every 1 second
                        .doBeforeRetry(retrySignal -> System.out.println(
                                "Reconnecting due to connection failure: " +
                                        retrySignal.failure())))
                .cache(); // Cache the connection to share among subscribers
        this.connectionPublisher.subscribe(
                conn -> System.out.println("Initial connection established"),
                error -> System.err.println("Initial connection attempt failed: " + error.getMessage())
        );
    }

    private Mono<Connection> connectTcpClient(String host, int port) {
        return TcpClient.create()
                .host(host)
                .port(port)
                .handle((inbound, outbound) ->
                        inbound.receive()
                                .asString(StandardCharsets.UTF_8)
                                .flatMap(this::handleIncomingMessage)
                                .then())
                .connect()
                .cast(Connection.class)
                .doOnSuccess(conn -> System.out.println("Connected to TCP server"))
                .doOnError(e -> System.err.println("Failed to connect: " + e.getMessage()));
    }

    private Mono<Void> handleIncomingMessage(String msg) {
        log.info("Exchange service: received message: '{}'", msg);

        try {
            TradeRequest request = TradeResponse.fromFix(msg, new TradeRequest());
            return processTradeRequest(request)
                    .flatMap(response -> connectionPublisher.flatMap(connection -> {
                        try {
                            return connection.outbound()
                                    .sendString(Mono.just(response.asFix()),
                                            StandardCharsets.UTF_8)
                                    .then();
                        } catch (MissingRequiredTagException e) {
                            log.error("Exchange service: response assembly failed. This can't happen.");
                            return Mono.empty();
                        }
                    }))
                    .doOnError(e -> log.error("Failed to process or respond to trade request: {}", e.getMessage()))
                    .then();
        } catch (MissingRequiredTagException e) {
            try {
                IdAssignationMessage idMsg = objectMapper.readValue(msg, IdAssignationMessage.class);
                if (assignedId.isEmpty())
                    assignedId = idMsg.id();
                else
                    log.warn("Exchange service: ID re-assignation operation!");
            } catch (JsonProcessingException ex) {
                log.warn("Exchange service: received message is not supported. Ignoring.");
            }
        }
        return Mono.empty();
    }

    public Mono<TradeResponse> processTradeRequest(TradeRequest request) {
        return stockRepo.findByName(request.getInstrument())
                .flatMap(entry -> {
                    TradeResponse response = new TradeResponse(
                            request.getTarget(),
                            request.getSender(),
                            request.getSenderSubId(),
                            request.getInstrument(),
                            request.getAction(),
                            request.getAmount(),
                            TradeResponse.MSG_ORD_FILLED
                    );
                    if (entry.amount() >= request.getAmount() && request.getAction().equals("1")) {
                        // Update stock amount if buy action and sufficient quantity exists
                        return updateStockQuantity(entry, entry.amount() - request.getAmount())
                                .thenReturn(response);
                    } else if (request.getAction().equals("2")) {
                        // Update stock amount for a sell action (increasing stock quantity)
                        return updateStockQuantity(entry, entry.amount() + request.getAmount())
                                .thenReturn(response);
                    } else {
                        // Insufficient quantity for buy, reject order
                        response.setOrdStatus(TradeResponse.MSG_ORD_REJECTED);
                        return Mono.just(response);
                    }
                });
    }

    private Mono<InstrumentEntry> updateStockQuantity(InstrumentEntry entry, int updatedAmount) {
        // Update the quantity in the database and return the updated entry
        InstrumentEntry updatedEntry = new InstrumentEntry(entry.id(), entry.name(), updatedAmount);
        return stockRepo.save(updatedEntry);
    }
}
