# Q4: Product Catalog

**Branch:** `feature/q4-product-catalog` · **Time:** 25 min · **PR title:** `Q4: Product Catalog`

## Requirements (from the assignment)
- A product has a **name, category, price, stock, rating, created date**. **Seed 100 products** on startup.
- List with **pagination** and **sorting by any field**. The response includes the **total count** and the **number of pages**.
- Optional filters that **combine freely**: category, price range, in-stock only, name search.
- **Page size capped at 100.**
- Single-product lookups happen far more often than changes. **Make repeated lookups fast, but never return stale data** after an update or delete.

## Acceptance criteria
- [ ] Any combination of filters works in a single request.
- [ ] Repeated lookups of the same product don't query the DB every time, and you can show how you know.
- [ ] At least one automated test.

**Optional:** make the fast lookups work across several app instances (shared cache such as Redis).

---

## Recommended design

### Dependencies (add on this branch)
`spring-boot-starter-cache`, `com.github.ben-manes.caffeine:caffeine`

### Data
`V4__create_products.sql`: `id`, `name VARCHAR(200)`, `category VARCHAR(50)`, `price DECIMAL(10,2)`, `stock INT`, `rating DECIMAL(2,1)`, `created_at`, plus **indexes** on `category`, `price`, and `created_at`.
Use `BigDecimal` for price, never `double`.

**Seeding 100 products:** use `V5__seed_products.sql` with H2's `SYSTEM_RANGE(1, 100)`, or an `ApplicationRunner` that inserts only `if (repository.count() == 0)`. Recommended: the runner, because it's DB-agnostic, readable, and skipped when data already exists.

### API
| Method | Path | Notes |
|---|---|---|
| GET | `/api/products?category=books&minPrice=10&maxPrice=50&inStock=true&q=lamp&page=0&size=20&sort=price,desc` | all filters optional |
| GET | `/api/products/{id}` | **cached** |
| PUT | `/api/products/{id}` | **evicts / updates the cache** |
| DELETE | `/api/products/{id}` | **evicts the cache** |

Response:
```json
{ "content": [ ... ], "page": 0, "size": 20, "totalElements": 100, "totalPages": 5 }
```

### Key logic
- **Combinable filters:** a JPA `Specification`. Each filter returns `null` when its parameter is absent, and they're combined with `Specification.allOf(...)`. Repository: `JpaRepository<Product, Long>, JpaSpecificationExecutor<Product>`.
  - name search is `lower(name) like %q%`
  - inStock is `stock > 0`
- **Page size cap:** `spring.data.web.pageable.max-page-size: 100`, which silently clamps. The alternative is rejecting `size > 100` with 400. Choose one and explain it.
- **Sort by any field:** whitelist `name, category, price, stock, rating, createdAt`. An unknown field returns 400 rather than letting `PropertyReferenceException` become a 500.
- **Cache:**
  ```java
  @Cacheable(cacheNames = "products", key = "#id")      // getById returns a ProductResponse DTO (not the entity)
  @CachePut (cacheNames = "products", key = "#id")      // update writes the new value
  @CacheEvict(cacheNames = "products", key = "#id")     // delete removes it
  ```
  Caffeine spec: `maximumSize=10000,expireAfterWrite=10m`. The TTL is only a safety net, because correctness comes from evict/put.
- **"Show how you know":** a test with `@MockitoSpyBean ProductRepository` calls `getById(1)` 3 times and verifies `findById` ran **once**. Then it updates the product, calls again, and asserts the new value. You can also enable `spring.jpa.show-sql` or Hibernate statistics and show one `select` in the logs.

### Decisions & alternatives
| Decision | Chosen | Alternatives | Why |
|---|---|---|---|
| Filtering | JPA Specification | `@Query` with `(:cat is null or …)`: brittle, poor query plans · Querydsl: extra codegen · Criteria API by hand | Composable, type-safe enough, built in. |
| Cache | Spring Cache + Caffeine (in-process) | `ConcurrentMapCache` (no size or TTL limit) · Redis (multi-instance, the bonus) · Hibernate 2nd-level cache | Fastest and simplest for one instance, with bounded memory. |
| Staleness | `@CachePut` on update, `@CacheEvict` on delete | TTL only (stale for up to the TTL, which breaks the requirement) | Writes go through the service, so the cache changes on the same code path. |
| Cache value | DTO | Entity | Detached entities and lazy fields in a cache are a bug source, and the cached value must be immutable. |
| Count query | `Page` (runs `count(*)`) | `Slice` (no count) | The spec requires totals. Mention the cost at scale. |
| Page size > 100 | **Clamp** to 100 (`spring.data.web.pageable.max-page-size`) | Reject with 400 | Lenient for clients, and the response's `size` field shows the size actually applied. |
| Stable paging | `id` is always appended as the last sort key | Sort only by the requested fields | With ties (many products at one price) row order is undefined, so a product could show up on two pages or none. |
| Who can write | GET for any logged-in user; PUT/DELETE **ADMIN only** (`SecurityConfig`) | Any logged-in user | A catalog any USER could change or delete makes no sense, and it's one matcher line. |
| Invalid filters | 400 field error for `minPrice > maxPrice` or a negative price (`BadRequestException`) | Return an empty page | The client made a mistake; an empty page would hide it. |
| Category filter | Exact match on lowercase category codes (`?category=home`) | Case-insensitive (`lower(category)`) | An exact match can use `idx_products_category`; wrapping the column in `lower()` can't. Categories are codes, not free text. |
| Name search | Case-insensitive contains; `%` and `_` in the search text are escaped | Raw `like` | `q=%` would otherwise match every product. |
| Seed data | `ApplicationRunner`, only when the table is empty; values derived from the index (category `i % 5`, price `i + 0.99`, every 7th out of stock) | Random values · SQL seed migration | Predictable and explainable; tests use their own fixtures and don't depend on it. |
| Cache vs transaction | Known caveat: `@CachePut`/`@CacheEvict` aren't tied to the commit | `TransactionAwareCacheManagerProxy` | If the transaction rolled back after the cache write, the cache would hold a value the DB doesn't. Low risk here (the write is the last step); worth naming in the interview. |

**Caveats to know:** (1) Multiple instances each have their own Caffeine cache, so an update on instance A leaves B stale, which is why the bonus uses Redis or pub/sub eviction. (2) `@Cacheable` on a method called from **inside the same class** is bypassed, because Spring's proxy only intercepts calls coming from outside the bean. (3) Updates that bypass the service (direct SQL, Q5's stock decrement) don't evict. Either cache without `stock`, or evict from the order service. This connects to Q5.

## Test plan
| AC | Test | Type |
|---|---|---|
| Filters combine | `ProductListTest.list_categoryPriceRangeInStockAndName_returnsOnlyMatching` | `@SpringBootTest` + MockMvc |
| Paging metadata | `list_page0Size2_returnsTotalsAndPageCount` (5 total, 3 pages) | MockMvc |
| Size cap | `list_size500_isClampedTo100` | MockMvc |
| **Cache hit** | `ProductCacheTest.getById_calledTwice_hitsRepositoryOnce` | `@SpringBootTest` + `@MockitoSpyBean` |
| **Not stale** | `getById_afterUpdate_returnsNewValue`, `getById_afterDelete_returns404` | `@SpringBootTest` |

## Commit plan
1. `Add product entity, migration and seed 100 products`
2. `List products with pagination, sorting and page size cap`
3. `Add combinable filters with JPA specifications`
4. `Cache product lookups and evict on update and delete`
5. `Add catalog and cache tests`

## Interview prep
- **How do you know it's cached?** The spy-repository test verifies `findById` runs once across repeated calls. In the logs, `show-sql` prints one `select`.
- **What if you remove `@CacheEvict` on delete?** `GET` returns the deleted product from the cache until TTL expiry, so the data is stale.
- **Race:** a read populates the cache with old data between the DB commit and the eviction. Mitigations: `@CachePut` after commit, a short TTL, or `TransactionAwareCacheManagerProxy`, which defers cache writes until after commit.
- **`like '%q%'` and indexes:** a leading wildcard can't use a B-tree index. At scale you'd use full-text search or a trigram index.
- **Why does `count(*)` get slow?** On big tables it scans all matching rows. Alternatives are `Slice` or keyset pagination.
- **Live change ideas:** add a `minRating` filter; change the max page size to 50; make cache TTL configurable.
