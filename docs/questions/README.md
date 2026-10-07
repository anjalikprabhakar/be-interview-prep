# Questions

| # | Spec | Branch | Time budget |
|---|------|--------|-------------|
| 1 | [Task Manager API](q1-task-api.md) | `feature/q1-task-api` | 15 min |
| 2 | [URL Shortener](q2-url-shortener.md) | `feature/q2-url-shortener` | 15 min |
| 3 | [Authentication & Roles](q3-auth.md) | `feature/q3-auth` | 25 min |
| 4 | [Product Catalog](q4-product-catalog.md) | `feature/q4-product-catalog` | 25 min |
| 5 | [Order Service](q5-order-service.md) | `feature/q5-order-service` | 40 min |

Each file has the requirements and acceptance criteria copied from the assignment, then a **recommended design** with alternatives, an API contract, a test plan, a commit plan, and **interview prep**.

The design sections are recommendations, not requirements. The assignment says the design decisions are yours, so change anything you disagree with *before* implementing. In the interview you must be able to defend every choice.

## Flow per question
```
git switch main && git pull
/solve-question qN        # branch → design check → implement → test → self-review → PR → merge
/explain-code qN          # drill yourself before moving on
```

## Cross-cutting decisions (made in Q1, reused everywhere)
- **One error format:** `ApiError { timestamp, status, error, message, path, fieldErrors[] }` produced by `GlobalExceptionHandler`. Q3's 401/403 handlers write the same shape.
- **Schema is owned by Flyway** (`ddl-auto: validate`), so the DB structure is explicit and reviewable in each PR.
- **H2 in-memory DB:** zero setup for the reviewer. Q5's bonus is to run against a real DB with Testcontainers.
