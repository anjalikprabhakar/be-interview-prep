---
name: implement-api
description: Implement a REST API feature in Spring Boot 3 following project conventions (migration, entity, DTO records, repository, service, controller, consistent error handling) with small meaningful commits. Use when the user asks to implement, build, or add an API/endpoint, or to implement a planned question/ticket.
argument-hint: <q1..q5 | TICKET-ID | endpoint description>
---

# Implement API

Task: `$ARGUMENTS`

## 0. Preconditions
- Read `CLAUDE.md` and the spec (`docs/questions/<q>*.md` or `docs/tickets/<ID>.md`). Follow the spec's design, API contract, and commit plan.
- Check that `git branch --show-current` is the feature branch (for example `feature/q1-task-api`). **Never work on main.**
- This is **Spring Boot 3.5**, so don't use Boot 4 artifacts or packages. Add only the dependencies this feature needs, and tell the user which ones.

## 1. Build bottom-up, committing per step
1. **Migration** `V<next>__<desc>.sql`: include constraints that enforce invariants (`UNIQUE`, `NOT NULL`, `CHECK`), plus indexes for lookups and filters. Never edit a merged migration.
2. **Entity**: JPA annotations, enums with `@Enumerated(STRING)`, `BigDecimal` for money, a server-set `createdAt`. Don't use Lombok `@Data` on entities.
3. **DTOs** as `record`s with Jakarta validation on requests.
4. **Repository**: derived queries. Use `@Modifying @Query` for atomic updates (counters, stock). Add `JpaSpecificationExecutor` for dynamic filters.
5. **Service**: business rules, `@Transactional` on writes, domain exceptions (`NotFoundException`, `ConflictException`, …). Map entities to DTOs here.
6. **Controller**: thin. Use `@Valid`, correct statuses, `ResponseEntity.created(uri)` for POST, and 204 for DELETE.
7. **Errors**: extend `common/exception/GlobalExceptionHandler` (create it in Q1) so every new exception maps to `ApiError` with the right status.

After each step, run `./mvnw -q compile`, then `git add <specific files>` and `git commit -m "<Imperative meaningful message>"`. Good messages: `Add create task endpoint`, `Return field errors for invalid input`. Never use: `fix`, `changes`, `final`, `wip`, `update`.

## 2. Tests
Use the `write-tests` skill. Cover each acceptance criterion, then commit with a message like `Add task API tests`.

## 3. Verify
- `./mvnw -q verify` passes.
- Grep the diff for `System.out`, `printStackTrace`, `TODO`, hardcoded secrets, and unused code.
- Note any deviation from the spec's design in the spec's Decisions table.

## 4. Report
List the files, show the endpoint table with a sample `curl`, give the test results, and print `git log main..HEAD --oneline`.

## Rules
- Prefer the simplest design the user can explain over a clever one.
- For concurrency-sensitive code, put a short comment on the *one line that makes it safe* (for example, the conditional `UPDATE`). Interviewers will ask "what if you remove this line?"
- Never log passwords, tokens, or secrets.
