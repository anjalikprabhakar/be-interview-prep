# Q5: Order Service

**Branch:** `feature/q5-order-service` · **Time:** 40 min · **PR title:** `Q5: Order Service`

## Requirements (from the assignment)
- Products have limited stock. A customer places an order with **one or more items**.
- An order is **all-or-nothing**: either every item is reserved, or none is.
- Stock must **never go negative or be oversold**, even when many customers order the same product at the same moment.
- Clients may **retry** after a network failure. A retried request must **not create a duplicate order**. Design how a retry is recognised.
- Insufficient stock returns **409** with a clear message.
- **Cancelling** an order returns its stock.

## Acceptance criteria
- [ ] An automated test fires **50 simultaneous orders** for a product with **stock 10**. **Exactly 10 succeed**, and stock ends at **0**.
- [ ] Retrying the same request creates **only one order**.

**Optional:** implement a second concurrency approach and write a short comparison; run the tests against a real DB (Testcontainers + PostgreSQL).

---

## Recommended design

### Data
Reuse Q4's `products.stock`.
`V<next>__create_orders.sql` (next free Flyway version after Q4):
- `orders`: `id`, `customer` (`sub` from the JWT in Q3), `status VARCHAR(20)` (`PLACED`, `CANCELLED`), `idempotency_key VARCHAR(64) NOT NULL`, `created_at`, **`UNIQUE (customer, idempotency_key)`**
- `order_items`: `id`, `order_id FK`, `product_id FK`, `quantity INT CHECK (quantity > 0)`, `unit_price DECIMAL(10,2)`
- Optional extra safety: `ALTER TABLE products ADD CONSTRAINT chk_stock_non_negative CHECK (stock >= 0)`

### API
| Method | Path | Success | Errors |
|---|---|---|---|
| POST | `/api/orders`, header **`Idempotency-Key: <uuid>`**, body `{ "items": [ { "productId": 1, "quantity": 2 } ] }` | 201 (first) / **200 with the same order** (retry) | 400 missing key or empty items, 404 unknown product, **409 insufficient stock** |
| GET | `/api/orders/{id}` | 200 | 404 |
| POST | `/api/orders/{id}/cancel` | 200 | 404, 409 already cancelled |

409 body: `"message": "Insufficient stock for product 7 (requested 3)"`

### Key logic
**Reserve stock with an atomic conditional update** (one statement per item, inside one `@Transactional`):
```java
@Modifying
@Query("update Product p set p.stock = p.stock - :qty where p.id = :id and p.stock >= :qty")
int reserve(Long id, int qty);   // returns rows updated: 1 = reserved, 0 = not enough stock
```
```java
@Transactional
public OrderResponse place(String customer, String key, OrderRequest req) {
    // 1. idempotency: an existing order for (customer, key) is returned as is
    // 2. merge duplicate productIds, sort items by productId (consistent lock order, so no deadlock)
    // 3. for each item: if (repo.reserve(id, qty) == 0) throw new InsufficientStockException(id, qty);
    //    the exception rolls back every earlier reserve in this transaction (all-or-nothing)
    // 4. save the order + items with the idempotency key
}
```
**Concurrent retries with the same key:** both pass step 1, one commits, and the other hits the UNIQUE constraint. That one's transaction rolls back, including its stock decrements. Catch `DataIntegrityViolationException` **outside** the transactional method (in the controller or a wrapper), then load and return the existing order.

**Cancel:** `update Order o set o.status = 'CANCELLED' where o.id = :id and o.status = 'PLACED'`. Only if that returns 1, restore stock with `stock = stock + qty` for each item. This prevents double-cancel races from restoring stock twice.

**Q4 cache:** stock changes here bypass `ProductService`, so evict the product cache entries for the ordered products (or don't cache `stock`).

### Decisions & alternatives
| Decision | Chosen | Alternatives | Why |
|---|---|---|---|
| **Oversell prevention** | Atomic conditional `UPDATE … WHERE stock >= qty` | **Pessimistic lock** `@Lock(PESSIMISTIC_WRITE)` `SELECT … FOR UPDATE`, then check and decrement (good second approach for the bonus) · **Optimistic** `@Version` + retry (under 50-way contention most fail and must retry, so it's slow and failure-prone) · `synchronized`/`ReentrantLock` (single JVM only, broken with 2 instances) · Redis/distributed lock | One round-trip per item, no read-then-write gap, and the DB guarantees atomicity. Works across instances. |
| All-or-nothing | One `@Transactional`, and a runtime exception rolls back | Saga / compensation | Single DB, so a local transaction is enough. |
| **Retry recognition** | Client-generated `Idempotency-Key` header, unique per customer | Hash of the request body (two genuinely identical orders would be wrongly merged) · Server-issued order token (needs an extra round-trip) | The industry standard (Stripe style). The client controls intent, and the DB constraint makes it race-safe. |
| Retry response | 200 with the original order | 409 "duplicate" | The client just wants the result, so a retry should look like success. |
| Same key + different body | Return 422 (if you store a body hash) | Ignore | Optional. Mention it as a "with more time" item. |
| Deadlocks | Sort items by productId | — | Two orders locking rows A→B and B→A can deadlock. A consistent order prevents it. |
| **Transaction boundary** (as built) | `TransactionTemplate` inside `OrderService` | `@Transactional` method + a separate wrapper bean that catches the duplicate key · catching in the controller | The duplicate-key catch and the cache eviction must run *after* the transaction ends. With `@Transactional` that needs a second bean (a self-call bypasses the proxy). The template keeps it in one class. |
| Q4 cache (as built) | Evict the ordered products' cache entries after commit (place and cancel) | Don't cache stock · evict inside the transaction | Evicting before commit lets a concurrent GET re-cache the old stock. Small leftover window: a GET that read before the commit and writes the cache after the eviction (bounded by the 10 min TTL). |
| Other users' orders (as built) | 404 | 403 | Doesn't reveal that an order id exists. |
| Same key, different body (as built) | Original order returned | 422 via a stored body hash | Kept simple; "with more time" item. |
| Deleting an ordered product (as built) | 409 via a `DataIntegrityViolationException` handler | Soft delete · cascade | The new `order_items → products` FK would otherwise turn an ADMIN delete into a 500. Past orders must keep their product. |

## Test plan
| AC | Test | Type |
|---|---|---|
| **50 concurrent / stock 10** | `OrderConcurrencyTest.place_50ConcurrentOrdersStock10_exactly10Succeed`: fixed thread pool of 50, `CountDownLatch` start gate, each places qty 1 with its **own** idempotency key, then count successes == 10, `InsufficientStockException` == 40, stock == 0 | `@SpringBootTest` (no `@Transactional` on the test, because each thread needs a real commit) |
| **Retry → one order** | `place_sameIdempotencyKeyTwice_createsOneOrder` (sequential) and `…_concurrently_createsOneOrder` | `@SpringBootTest` |
| All-or-nothing | `place_secondItemOutOfStock_firstItemStockUnchanged` | `@SpringBootTest` |
| 409 | `place_insufficientStock_returns409WithMessage` | MockMvc |
| Cancel | `cancel_placedOrder_restoresStock`, `cancel_twice_restoresOnce` | `@SpringBootTest` |

Test tips: reset stock in `@BeforeEach`. Use `@DirtiesContext` sparingly. Never put `@Transactional` on concurrency tests, because the transaction rolls back and the threads can't see each other's data.

## Commit plan
1. `Add order and order item entities with migration`
2. `Reserve stock atomically when placing an order`
3. `Return 409 when stock is insufficient`
4. `Recognise retries with an Idempotency-Key header`
5. `Cancel orders and return stock`
6. `Add concurrent order and idempotency tests`

## Interview prep
- **Why can't stock go negative?** The `WHERE stock >= :qty` check and the decrement happen in **one statement** under the row lock. A second transaction waits for the lock, then re-evaluates the condition against the committed value.
- **What if you change it to `findById` → `if (stock >= qty)` → `setStock` → `save`?** Two threads both read stock=1, both pass, and both write 0, so two orders are placed for one unit. That's a classic lost update.
- **What if you remove `@Transactional`?** Each `reserve` commits on its own. If item 2 fails, item 1 is still reserved, which breaks all-or-nothing.
- **Why doesn't a checked exception roll back?** By default Spring rolls back only on `RuntimeException`/`Error`. Keep `InsufficientStockException` unchecked, or use `rollbackFor`.
- **Two retries arrive at the same millisecond?** The UNIQUE `(customer, idempotency_key)` constraint lets only one commit. The loser rolls back (its stock is restored) and returns the winner's order.
- **Pessimistic vs. atomic update?** Pessimistic holds the lock from `SELECT FOR UPDATE` until commit, so it needs 2 statements but lets you run arbitrary Java checks between them. The atomic update is the fastest but limited to what SQL can express.
- **Live change ideas:** cap quantity per item at 5; add an order total; forbid cancelling after 1 hour.
