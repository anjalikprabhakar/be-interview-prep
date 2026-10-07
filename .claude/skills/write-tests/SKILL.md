---
name: write-tests
description: Write JUnit 5 tests for Spring Boot 3 - Mockito unit tests, @WebMvcTest/MockMvc controller tests, @SpringBootTest integration tests, multi-threaded concurrency tests, cache-hit tests and security role tests. Use when the user asks for tests, or to cover a question/ticket's acceptance criteria.
argument-hint: <class | feature | q1..q5>
---

# Write tests

Target: `$ARGUMENTS`

1. Read the acceptance criteria and test plan in the spec (`docs/questions/<q>*.md`). Every AC needs at least one test that would **fail if the AC were broken**.
2. Follow the style of existing tests.
3. Pick the type:
   | Need | Use |
   |---|---|
   | Business rule | Mockito unit test |
   | Status codes, validation, JSON shape | `@WebMvcTest(XController.class)` + `@MockitoBean` service |
   | Real DB, filters, end-to-end | `@SpringBootTest` + `@AutoConfigureMockMvc` |
   | Concurrency (Q2 visits, Q5 stock) | `@SpringBootTest`, **no `@Transactional`**, `ExecutorService` + `CountDownLatch` |
   | Cache hits (Q4) | `@SpringBootTest` + `@MockitoSpyBean` repository + `verify(times(1))` |
   | Roles (Q3) | MockMvc + `.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))`, or a real token from `/api/auth/login` |
4. Name tests `method_condition_expectedResult`, with a given/when/then layout. Use AssertJ and `jsonPath`.
5. Run `./mvnw -q test -Dtest=<Class>`, then `./mvnw -q verify`. Report which AC each test covers.

## Concurrency template
```java
@SpringBootTest
class OrderConcurrencyTest {
    @Autowired OrderService orderService;
    @Autowired ProductRepository productRepository;

    @Test
    void place_50ConcurrentOrdersStock10_exactly10Succeed() throws Exception {
        Long productId = productRepository.save(new Product("Widget", 10 /* stock */)).getId();
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger(), rejected = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();                                   // all threads fire together
                try {
                    orderService.place("user", UUID.randomUUID().toString(), oneItem(productId, 1));
                    ok.incrementAndGet();
                } catch (InsufficientStockException e) {
                    rejected.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS); // surface unexpected exceptions
        pool.shutdown();

        assertThat(ok.get()).isEqualTo(10);
        assertThat(rejected.get()).isEqualTo(40);
        assertThat(productRepository.findById(productId).orElseThrow().getStock()).isZero();
    }
}
```

## Cache-hit template
```java
@SpringBootTest
class ProductCacheTest {
    @MockitoSpyBean ProductRepository repository;
    @Autowired ProductService service;

    @Test
    void getById_calledThreeTimes_hitsRepositoryOnce() {
        service.getById(1L); service.getById(1L); service.getById(1L);
        verify(repository, times(1)).findById(1L);
    }
}
```
Clear the cache in `@BeforeEach` (`cacheManager.getCache("products").clear()`) so tests don't depend on each other.
