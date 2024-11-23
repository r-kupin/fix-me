package com.rokupin.exchange;

import com.rokupin.exchange.model.InstrumentEntry;
import com.rokupin.exchange.repo.StockRepo;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers
@DataR2dbcTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RepoTest {

    @Container
    static final MariaDBContainer<?> mariaDB = new MariaDBContainer<>("mariadb:latest")
            .withCopyFileToContainer(MountableFile
                    .forClasspathResource("testcontainers/create-schema.sql"), "/docker-entrypoint-initdb.d/init.sql");

    @Autowired
    StockRepo stockRepo;

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:mariadb://"
                + mariaDB.getHost() + ":" + mariaDB.getFirstMappedPort()
                + "/" + mariaDB.getDatabaseName());
        registry.add("spring.r2dbc.username", mariaDB::getUsername);
        registry.add("spring.r2dbc.password", mariaDB::getPassword);
    }

    @Order(1)
    @Test
    public void testRepoExists() {
        assertNotNull(stockRepo);
    }

    @Order(2)
    @Test
    public void testCount() {
        stockRepo.count()
                .log()
                .as(StepVerifier::create)
                .expectNextMatches(p -> p == 2)
                .verifyComplete();
    }

    @Order(3)
    @Test
    public void testFindByFistName() {
        stockRepo.findByName("TEST1")
                .log()
                .as(StepVerifier::create)
                .expectNextCount(1)
                .verifyComplete();
    }

    @Order(4)
    @Test
    public void testFindByFistNameAndLastNameNoResult() {
        stockRepo.findByName("TEST3")
                .log()
                .as(StepVerifier::create)
                .expectNextCount(0)
                .verifyComplete();
    }

    @Order(5)
    @Test
    public void testUpdateStock() {
        StepVerifier.create(stockRepo.findByName("TEST1"))
                .assertNext(instrument -> {
                    assert instrument != null;
                    assert instrument.amount() == 1;

                    stockRepo.save(new InstrumentEntry(
                            instrument.id(),
                            instrument.name(),
                            instrument.amount() + 99)
                    ).subscribe();
                })
                .verifyComplete();

        StepVerifier.create(stockRepo.findByName("TEST1"))
                .assertNext(updated -> {
                    assert updated != null;
                    assert updated.amount() == 100; // Verify the updated amount
                })
                .verifyComplete();
    }
}
