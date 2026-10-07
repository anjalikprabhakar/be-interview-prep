---
name: testing-conventions
description: Test standards for this repo - which test type to use, naming, what every acceptance criterion needs, concurrency/cache/security test rules, and flakiness bans. Load before writing or reviewing anything under src/test.
---

# Testing standards

Reviewers cite these as `TS-<n>`.

- **TS-1, AC coverage:** every acceptance criterion has at least one test that **would fail if the AC were broken**. A test that can't fail proves nothing.
- **TS-2, right test type:**
  - business rules → Mockito unit test
  - HTTP contract (status, JSON shape, validation) → `@WebMvcTest` + `@MockitoBean`
  - real DB, Flyway, filters, end-to-end → `@SpringBootTest` + `@AutoConfigureMockMvc`
- **TS-3, naming:** `method_condition_expectedResult`, with a given/when/then layout and AssertJ / `jsonPath` assertions.
- **TS-4, error paths are first-class:** test 400 (field errors), 404, 409/410 where relevant, and that 500 doesn't leak details. Assert on the `ApiError` fields, not just the status.
- **TS-5, isolation:** each test sets up its own data (`@BeforeEach` cleanup or unique data). There are no order dependencies between tests. Use `@DirtiesContext` only with a written reason.
- **TS-6, concurrency tests:**
  - use `@SpringBootTest` **without** `@Transactional` (each thread must commit)
  - use an `ExecutorService` with a `CountDownLatch` start gate
  - call `future.get(timeout)` to surface exceptions
  - assert exact counts (for example, exactly 10 succeed and the final stock is 0)
- **TS-7, cache tests:** use `@MockitoSpyBean` on the repository and `verify(times(n))`, and clear the cache in `@BeforeEach`. Also test that an update or delete is visible on the next read.
- **TS-8, security tests:** prove 401 without a token, 403 with the wrong role, and 200 with the right one. The 401 and 403 bodies must be JSON in `ApiError` shape.
- **TS-9, no flakiness:** no `Thread.sleep` for synchronization, no real network calls, and no dependence on wall-clock edges. Dates are relative to `LocalDate.now()`.
- **TS-10, green before PR:** `./mvnw -q verify` passes locally. Never commit `@Disabled` without an issue reference.
