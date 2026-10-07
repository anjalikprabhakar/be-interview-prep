---
name: java-spring-conventions
description: Coding standards for this repo's Java 21 / Spring Boot 3 code - layering, DTOs, validation, error handling, persistence, transactions, concurrency, security, logging. Load before writing or reviewing production code under src/main.
---

# Java & Spring Boot coding standards

Reviewers cite these as `JS-<n>`. A violation blocks a PR only when it causes a defect (see `.claude/conventions/review-conventions.md` §2).

## Structure
- **JS-1, feature-first packages:** `org.example.<feature>` holds the controller, service, repository, entity, and `dto/`. Shared code goes in `org.example.common`.
- **JS-2, thin controllers:** controllers handle HTTP mapping, `@Valid`, status codes, and `Location` headers, and nothing else. Business rules live in services.
- **JS-3, no entities over HTTP:** requests and responses are Java `record` DTOs. Map entities to DTOs inside the service, within the transaction.
- **JS-4, constructor injection only:** use `private final` fields and no field `@Autowired`. Don't use Lombok on entities.
- **JS-5, small and explainable:** no speculative abstractions (generic base services, mappers frameworks, or interfaces with one implementation) unless the spec needs them.

## Validation & errors
- **JS-6:** input rules are Jakarta constraints on request DTOs, each with a human-readable `message`. Invariants are also enforced in the DB (`NOT NULL`, `UNIQUE`, `CHECK`, lengths).
- **JS-7:** every error leaves via `GlobalExceptionHandler` as `ApiError {timestamp, status, error, message, path, fieldErrors[]}`. Security errors (401/403) use the same shape via the entry point and access-denied handler.
- **JS-8:** client mistakes are never a 500. Map domain exceptions to statuses: `NotFoundException` → 404, conflicts → 409, expired → 410.
- **JS-9:** 500 responses carry a generic message. Log the exception server-side, and never put the exception message or stack trace in the body.

## Persistence
- **JS-10:** Flyway owns the schema (`ddl-auto: validate`). Add new `V<n>__*.sql` files and never edit a merged migration. Name constraints and indexes explicitly.
- **JS-11:** use `@Enumerated(EnumType.STRING)`, `BigDecimal` for money, and `Instant` for timestamps (truncated to the column precision), with server-set `createdAt` mapped `updatable = false`.
- **JS-12:** use `@Transactional` on service writes and `@Transactional(readOnly = true)` on reads. Don't call transactional or cacheable methods via `this.` (the proxy is bypassed). Keep domain exceptions unchecked so they roll back.
- **JS-13:** `open-in-view` is off, so load what you need inside the service. Watch for N+1 queries when mapping collections.

## Concurrency
- **JS-14:** counters and stock change through **one atomic statement** (`UPDATE … SET x = x + 1`, or `… WHERE stock >= :qty` checking the affected row count). Never read-modify-write in Java.
- **JS-15:** uniqueness and idempotency are guaranteed by DB constraints, not by "exists?" checks. Catch `DataIntegrityViolationException` outside the failed transaction.
- **JS-16:** when locking multiple rows, lock in a consistent order (sort by id).

## Security
- **JS-17:** no secrets in source or committed config. Use `${ENV_VAR}` without defaults. Tests generate their own keys.
- **JS-18:** hash passwords with BCrypt, and never log or return the hash. Use generic login failure messages.
- **JS-19:** authentication is stateless (`SessionCreationPolicy.STATELESS`). Clients can never choose their own role.

## Logging & style
- **JS-20:** use SLF4J `Logger` with placeholders (`log.info("Task {} created", id)`), not `System.out`/`printStackTrace`. Don't log PII, tokens, or passwords.
- **JS-21:** use Java 21 idioms where they improve clarity: records, `switch` expressions, pattern-matching `instanceof`, text blocks in tests, `Stream.toList()`.
- **JS-22:** no unused imports, dead code, or commented-out code. Follow comments per `.claude/conventions/comment-conventions.md`.
