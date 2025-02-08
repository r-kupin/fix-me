package com.rokupin.exchange.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.exchange.model.InstrumentEntry;
import com.rokupin.exchange.repo.StockRepo;
import com.rokupin.model.fix.FixMessageMisconfiguredException;
import com.rokupin.model.fix.FixRequest;
import com.rokupin.model.fix.FixResponse;
import com.rokupin.model.fix.FixStockStateReport;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Slf4j
@Transactional
public class ExchangeServiceImpl implements ExchangeService  {
    private final int maxAmount;
    private final StockRepo stockRepo;
    private final ObjectMapper objectMapper;


    public ExchangeServiceImpl(StockRepo stockRepo,
                               ObjectMapper objectMapper,
                               int maxAmount) {
        this.maxAmount = maxAmount;
        this.objectMapper = objectMapper;
        this.stockRepo = stockRepo;

        if (maxAmount < 1 || maxAmount == Integer.MAX_VALUE) {
            log.error("Max amount is expected to be between 1 and {}. " +
                    "Check the config.", maxAmount);
            System.exit(1);
        }
    }

    @PostConstruct
    private void checkDB() {
        Long instruments_amount = stockRepo.count().block();
        if (Objects.nonNull(instruments_amount) && instruments_amount < 1) {
            log.error("Database is empty, nothing to trade");
            System.exit(1);
        }
        InstrumentEntry record = stockRepo.findAll()
                .filter(entry ->
                        entry.amount() > maxAmount || entry.amount() < 0
                ).next()
                .block();
        if (Objects.nonNull(record)) {
            log.error("Instrument {} amount of {} is unacceptable. " +
                            "Amount should be from 0 to {}.",
                    record.name(), record.amount(), maxAmount);
            System.exit(1);
        }
    }

    @Override
    public Mono<String> publishCurrentStockState(String assignedId) {
        return stockRepo.findAll()
                .collectMap(InstrumentEntry::name, InstrumentEntry::amount)
                .flatMap(map -> {
                    try {
                        return Mono.just(
                                new FixStockStateReport(
                                        assignedId,
                                        objectMapper.writeValueAsString(map)
                                ).asFix()
                        );
                    } catch (JsonProcessingException e) {
                        return Mono.error(new RuntimeException("Failed to serialize state", e));
                    } catch (FixMessageMisconfiguredException e) {
                        return Mono.error(new RuntimeException("Failed make a fix message", e));
                    }
                });
    }

    @Override
    public Mono<FixResponse> processTradeRequest(FixRequest request, String assignedId) {
        try {
            FixResponse response = new FixResponse(
                    assignedId,                 // sender
                    request.getSender(),        // receiving service id
                    request.getSenderSubId(),   // receiving client id
                    request.getInstrument(),
                    request.getAction(),
                    request.getAmount(),
                    FixResponse.MSG_ORD_FILLED,
                    FixResponse.UNSPECIFIED
            );
            return stockRepo.findByName(request.getInstrument())
                    .flatMap(entry -> prepareResponse(entry, request, response))
                    .switchIfEmpty(Mono.defer(() -> {
                        response.setOrdStatus(FixResponse.MSG_ORD_REJECTED);
                        response.setRejectionReason(FixResponse.INSTRUMENT_NOT_SUPPORTED);
                        return Mono.just(response);
                    }));
        } catch (FixMessageMisconfiguredException e) {
            log.error("Response creation failed: '{}'", e.getMessage());
            return Mono.empty();
        }
    }

    private Mono<FixResponse> prepareResponse(InstrumentEntry entry,
                                              FixRequest request,
                                              FixResponse response) {
        if (request.getAction() == FixRequest.SIDE_BUY) {
            if (entry.amount() >= request.getAmount()) {
                return updateStockQuantity(entry,
                        entry.amount() - request.getAmount()
                ).thenReturn(response);
            } else {
                response.setOrdStatus(FixResponse.MSG_ORD_REJECTED);
                response.setRejectionReason(FixResponse.EXCHANGE_LACKS_REQUESTED_AMOUNT);
                return Mono.just(response);
            }
        } else if (request.getAction() == FixRequest.SIDE_SELL) {
            if (entry.amount() + request.getAmount() <= maxAmount) {
                return updateStockQuantity(entry,
                        entry.amount() + request.getAmount()
                ).thenReturn(response);
            } else {
                response.setOrdStatus(FixResponse.MSG_ORD_REJECTED);
                response.setRejectionReason(FixResponse.TOO_MUCH);
                return Mono.just(response);
            }
        } else {
            response.setOrdStatus(FixResponse.MSG_ORD_REJECTED);
            response.setRejectionReason(FixResponse.ACTION_UNSUPPORTED);
            return Mono.just(response);
        }
    }

    private Mono<InstrumentEntry> updateStockQuantity(InstrumentEntry entry, int updatedAmount) {
        InstrumentEntry updatedEntry = new InstrumentEntry(
                entry.id(), entry.name(), updatedAmount
        );
        return stockRepo.save(updatedEntry);
    }
}
