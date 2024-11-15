package com.rokupin.exchange.repo;

import com.rokupin.exchange.model.InstrumentEntry;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;


public interface StockRepo extends ReactiveCrudRepository<InstrumentEntry, Long> {
    @Query("select * from stock where name=:nm")
    Mono<InstrumentEntry> findByName(@Param("nm") String name);
}
