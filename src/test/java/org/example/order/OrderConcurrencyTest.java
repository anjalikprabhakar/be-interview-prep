package org.example.order;

import org.example.order.dto.OrderItemRequest;
import org.example.order.dto.OrderRequest;
import org.example.product.Product;
import org.example.product.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real threads against the real database. Deliberately not @Transactional: each order must commit in its
 * own transaction, exactly like concurrent HTTP requests, or the threads could not see each other's writes.
 */
@SpringBootTest
class OrderConcurrencyTest {

    private static final int THREADS = 50;

    @Autowired
    OrderService service;

    @Autowired
    OrderRepository orders;

    @Autowired
    ProductRepository products;

    private Long productId;

    @BeforeEach
    void setUp() {
        productId = products.save(new Product("Limited Lamp", "home", new BigDecimal("25.00"), 10,
                new BigDecimal("4.0"))).getId();
    }

    /** Orders reference products (order_items FK), so remove ours: other test classes delete all products. */
    @AfterEach
    void removeOrders() {
        orders.deleteAll();
    }

    @Test
    void place_50ConcurrentOrdersStock10_exactly10Succeed() throws Exception {
        List<Callable<PlaceOrderResult>> calls = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            String customer = "customer" + i + "@example.com";
            calls.add(() -> service.place(customer, "key-" + customer, oneOf(productId)));
        }

        int succeeded = 0;
        int insufficientStock = 0;
        for (Future<PlaceOrderResult> future : runTogether(calls)) {
            try {
                assertThat(future.get(30, TimeUnit.SECONDS).created()).isTrue();
                succeeded++;
            } catch (ExecutionException ex) {
                // Anything other than "out of stock" (a deadlock, a lock timeout) fails the test here.
                assertThat(ex.getCause()).isInstanceOf(InsufficientStockException.class);
                insufficientStock++;
            }
        }

        assertThat(succeeded).isEqualTo(10);
        assertThat(insufficientStock).isEqualTo(40);
        assertThat(products.findById(productId).orElseThrow().getStock()).isZero();
        assertThat(orders.count()).isEqualTo(10);
    }

    @Test
    void place_sameIdempotencyKeyConcurrently_createsOneOrder() throws Exception {
        List<Callable<PlaceOrderResult>> calls = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            calls.add(() -> service.place("alice@example.com", "same-key", oneOf(productId)));
        }

        List<PlaceOrderResult> results = new ArrayList<>();
        for (Future<PlaceOrderResult> future : runTogether(calls)) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }

        assertThat(results).filteredOn(PlaceOrderResult::created).hasSize(1);
        assertThat(results).extracting(result -> result.order().id()).containsOnly(results.get(0).order().id());
        assertThat(orders.count()).isEqualTo(1);
        // The losing retries rolled back their reservations: only one unit is gone.
        assertThat(products.findById(productId).orElseThrow().getStock()).isEqualTo(9);
    }

    @Test
    void place_sameIdempotencyKeyConcurrentlyLastUnit_everyRetryGetsTheOrder() throws Exception {
        Long lastUnitId = products.save(new Product("Last Lamp", "home", new BigDecimal("25.00"), 1,
                new BigDecimal("4.0"))).getId();
        List<Callable<PlaceOrderResult>> calls = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            calls.add(() -> service.place("alice@example.com", "same-key", oneOf(lastUnitId)));
        }

        // A retry that waited on the row lock finds stock 0; it must still get the order, not a 409.
        List<PlaceOrderResult> results = new ArrayList<>();
        for (Future<PlaceOrderResult> future : runTogether(calls)) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }

        assertThat(results).filteredOn(PlaceOrderResult::created).hasSize(1);
        assertThat(results).extracting(result -> result.order().id()).containsOnly(results.get(0).order().id());
        assertThat(orders.count()).isEqualTo(1);
        assertThat(products.findById(lastUnitId).orElseThrow().getStock()).isZero();
    }

    private static OrderRequest oneOf(Long productId) {
        return new OrderRequest(List.of(new OrderItemRequest(productId, 1)));
    }

    /** All tasks wait at the start gate, then are released at once to maximise contention. */
    private static <T> List<Future<T>> runTogether(List<Callable<T>> calls) {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch startGate = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> call : calls) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    return call.call();
                }));
            }
            startGate.countDown();
            return futures;
        } finally {
            pool.shutdown();
        }
    }
}
