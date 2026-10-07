package org.example.shortener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real threads against the real database. Deliberately not @Transactional: each visit must commit
 * in its own transaction, exactly like concurrent HTTP requests would.
 */
@SpringBootTest
class ShortLinkConcurrencyTest {

    private static final int VISITS = 100;

    @Autowired
    ShortLinkService service;

    @Autowired
    ShortLinkRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void resolve_100ConcurrentVisits_countIs100() throws Exception {
        repository.saveAndFlush(new ShortLink("hot1234", "https://example.com", null));
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<String>> visits = new ArrayList<>();

        try {
            for (int i = 0; i < VISITS; i++) {
                visits.add(pool.submit(() -> {
                    startGate.await();
                    return service.resolve("hot1234");
                }));
            }
            startGate.countDown();
            for (Future<String> visit : visits) {
                assertThat(visit.get(30, TimeUnit.SECONDS)).isEqualTo("https://example.com");
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(repository.findByCode("hot1234")).get()
                .extracting(ShortLink::getVisitCount).isEqualTo((long) VISITS);
    }
}
