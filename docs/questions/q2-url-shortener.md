# Q2: URL Shortener

**Branch:** `feature/q2-url-shortener` · **Time:** 15 min · **PR title:** `Q2: URL Shortener`

## Requirements (from the assignment)
- Submit a long URL, optionally with an **expiry date**, and get back a **short code** and a **short URL**.
- Visiting the short URL **redirects** to the original URL.
- Count every visit. A **stats** endpoint shows the original URL, the visit count, and the created date.
- Short codes are **≤ 8 chars, unique, URL-safe**.
- Reject invalid URLs. Handle **unknown** and **expired** codes with an appropriate status.

## Acceptance criteria
- [ ] Shortening the same URL twice behaves the way you decided it should, and you can explain why.
- [ ] Visit counts stay accurate when many people open the same link at once.
- [ ] At least one automated test.

**Optional:** let users choose their own custom short code.

---

## Recommended design

### Data
`V2__create_short_links.sql`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT identity | PK |
| code | VARCHAR(8) NOT NULL | **UNIQUE constraint**, the real uniqueness guarantee |
| original_url | VARCHAR(2048) NOT NULL | |
| visit_count | BIGINT NOT NULL DEFAULT 0 | |
| created_at | TIMESTAMP NOT NULL | |
| expires_at | TIMESTAMP NULL | null means it never expires |

### API
| Method | Path | Success | Errors |
|---|---|---|---|
| POST | `/api/urls` `{ "url": "...", "expiresAt": "2026-12-31T00:00:00Z" }` | 201 `{ code, shortUrl, originalUrl, expiresAt }` | 400 for an invalid URL or a past expiry |
| GET | `/{code}` | **302** + `Location: <original>` | 404 unknown, **410 Gone** expired |
| GET | `/api/urls/{code}/stats` | 200 `{ originalUrl, visitCount, createdAt, expiresAt }` | 404 |

`shortUrl` = `${app.base-url}/{code}`, configured in `application.yml` (default `http://localhost:8080`).
Restrict the redirect mapping to `/{code:[A-Za-z0-9]{1,8}}` so it can't swallow `/api/**` or other root paths.

### Key logic
- **Code generation:** 7 random Base62 characters from `SecureRandom` (62⁷ ≈ 3.5 trillion combinations). Insert the row. If the UNIQUE constraint fires (`DataIntegrityViolationException`), retry up to 5 times.
- **URL validation:** parse with `java.net.URI`. Require an `http`/`https` scheme and a non-empty host. Reject `javascript:`, `ftp:`, and relative URLs.
- **Visit count (the concurrency AC):** a single atomic SQL statement:
  ```java
  @Modifying
  @Query("update ShortLink s set s.visitCount = s.visitCount + 1 where s.id = :id")
  int incrementVisits(Long id);
  ```
  The DB row lock makes each increment atomic, so no update is lost.

### Decisions & alternatives
| Decision | Chosen | Alternatives | Why |
|---|---|---|---|
| **Same URL twice** | **A new code every time** | Return the existing code (dedupe) | Each link has its own expiry and its own stats. Dedupe would merge the stats of unrelated sharers, give a confusing result when expiries differ, and need a lookup plus a race guard. Trade-off: more rows. |
| Code generation | Random Base62 + retry on collision | Base62(id): sequential and guessable, enumerable · Hash(url) truncated: collisions, and it forces dedupe | Unpredictable, short, and stateless. The unique constraint makes it correct even under races. |
| Redirect status | **302** | 301 permanent | Browsers cache 301 and skip the server, so visits wouldn't be counted. |
| Expired | **410 Gone** | 404 | Tells the client that it existed but is no longer valid. |
| Visit counting | Atomic `UPDATE … +1` | Read-modify-write (lost updates) · `@Version` optimistic lock (retries under a hot link) · pessimistic lock (serializes reads) · a visits table (insert per visit, the best for analytics and write scaling) · Redis `INCR` | Simplest correct option. One statement, no retries. |
| Collision retry transaction | `shorten()` is **not** `@Transactional`; each `saveAndFlush` commits on its own | One `@Transactional` around the loop | A constraint violation marks the surrounding transaction rollback-only, so every retry inside it would fail too. |
| URL validation | Custom `@HttpUrl` constraint (parses with `java.net.URI`) | Check in the service · `@URL` from Hibernate Validator (accepts `ftp:` etc.) | Returns a 400 field error in the same shape as the other validation errors, and only allows `http`/`https`. |
| Expired link visits | 410, and the visit is **not** counted | Count it anyway | The visitor never reached the target. |
| Stats after expiry | **200** with the stats | 410 like the redirect | The owner can still see the final numbers; expiry only stops redirects. |
| `Location` on create | `/api/urls/{code}/stats` | The short URL itself | The stats endpoint is the API resource describing the link; the short URL is already in the body. |

## Test plan
| AC | Test | Type |
|---|---|---|
| Concurrent visits | `ShortLinkConcurrencyTest.redirect_100ConcurrentVisits_countIs100`: 100 threads, `CountDownLatch` start gate, call the service, then assert the count is 100 | `@SpringBootTest` |
| Same URL twice | `shorten_sameUrlTwice_returnsDifferentCodes` | `@SpringBootTest` + MockMvc |
| Redirect | `redirect_knownCode_returns302WithLocation` | MockMvc |
| Unknown / expired | `redirect_unknown_returns404`, `redirect_expired_returns410` | MockMvc |
| Invalid URL | `shorten_invalidUrl_returns400` | `@WebMvcTest` |

## Commit plan
1. `Add short link entity and migration`
2. `Add shorten endpoint with random Base62 codes`
3. `Redirect short codes and count visits atomically`
4. `Add stats endpoint and handle expired links with 410`
5. `Add URL shortener tests including concurrent visit count`

## Interview prep
- **Why does the count stay accurate?** `UPDATE … SET visit_count = visit_count + 1` is evaluated inside the DB under a row lock. Two transactions can't both read 5 and write 6. Contrast that with `link.setVisitCount(link.getVisitCount()+1); save()`, where both threads read 5 and the result is 6 (a lost update).
- **What if you remove the UNIQUE constraint?** Two concurrent shortens could generate the same code (very unlikely, but possible) and both insert. Redirects would then be ambiguous (`findByCode` throws `IncorrectResultSize`).
- **What if you remove `@Modifying`?** Spring Data treats the query as a select, and it fails at runtime.
- **Why does `@Modifying` need a transaction?** An update query needs an active transaction (`TransactionRequiredException` otherwise).
- **What if a code expires between lookup and redirect?** That's harmless: a single visit at the boundary.
- **Scaling:** cache code → URL (it never changes), and move counting to async or Redis.
- **Live change ideas:** make codes 6 chars; return the existing code for duplicate URLs; add a custom alias.
