# be-interview-prep: guide for Claude

This repo is the **Backend Interview Prep Assignment** (`docs/Backend_Interview_Prep_Assignment.pdf`): five Spring Boot features, each shipped as one branch → one PR → one merge, in order Q1 → Q5. There's one spec per question in `docs/questions/`.

**What's assessed:** working code, clean structure, tests, Git/PR discipline, and above all whether the user *understands* the code. Optimise for code the user can explain. Choose the simplest correct design, and record every decision with the alternatives considered.

**Time budget:** 2 hours total (Q1 15m, Q2 15m, Q3 25m, Q4 25m, Q5 40m). Don't gold-plate. Do the core requirements first and optional extras only if asked.

## Stack
- Java 21 (the assignment requires 17+), **Spring Boot 3.5.x** (the assignment requires 3.x, so don't upgrade to 4), Maven wrapper
- Spring Web, Validation, Data JPA, Flyway, H2 (in-memory)
- Tests: JUnit 5, Mockito, AssertJ, MockMvc (`spring-boot-starter-test`)
- Base package: `org.example`
- Add question-specific dependencies **on that question's branch**, not on main (for example `spring-boot-starter-security` in Q3, `spring-boot-starter-cache` + Caffeine in Q4)

## Commands
| Task | Command |
|---|---|
| Build + all tests | `./mvnw -q verify` |
| Single test class | `./mvnw -q test -Dtest=ClassNameTest` |
| Run app | `./mvnw spring-boot:run` |

Maven needs JDK 21. The machine default Java is 11, so if `JAVA_HOME` isn't set, prefix commands with `JAVA_HOME="C:/Program Files/Java/jdk-21"` (Bash). Run `./mvnw -q verify` before saying anything is done, and never claim tests pass without running them.

## Package layout (feature-first)
```
org.example
├── common/exception/   # GlobalExceptionHandler, ApiError, NotFoundException… (created in Q1, reused by all)
├── task/               # Q1
├── shortener/          # Q2
├── auth/  user/        # Q3
├── product/            # Q4
└── order/              # Q5
```
Each feature has a `Controller`, `Service`, `Repository`, entity, `dto/` (Java `record`s), and a mapper if needed.

## API conventions
- Paths are `/api/<plural>`, except Q2's redirect, which lives at the root (`/{code}`).
- Use DTO records with Jakarta validation. Never return JPA entities from controllers.
- Status codes: 201 + `Location` for create, 204 for delete, 400 for validation, 401/403 for auth, 404 for unknown, 409 for conflict, 410 for expired.
- **One error format everywhere** (`ApiError`): `{ timestamp, status, error, message, path, fieldErrors: [{field, message}] }`. Security 401/403 must use it too (via the entry point and access-denied handler).
- Controllers are thin and business logic lives in services. Use constructor injection. Put `@Transactional` on service writes.
- Schema changes go through Flyway (`src/main/resources/db/migration/V<n>__*.sql`). Never edit a migration that's already merged.

## Testing conventions
- Each question needs at least one automated test. Aim for: one test per acceptance criterion, plus the error paths.
- Name tests `method_condition_expectedResult`.
- Concurrency ACs (Q2 visit count, Q5 stock) need a real multi-threaded test against the Spring context and DB, not mocks.

## Git workflow (from the assignment, so follow it exactly)
- Branches (always from the latest `main`): `feature/q1-task-api`, `feature/q2-url-shortener`, `feature/q3-auth`, `feature/q4-product-catalog`, `feature/q5-order-service`
- **Small, meaningful, imperative commits**: `Add create task endpoint`, `Return field errors for invalid input`. Never use `fix`, `changes`, `final`, or `wip`.
- PR into `main` using `.github/pull_request_template.md` (Problem / Approach / Decisions & trade-offs / How to test).
- Self-review the diff, merge, then `git switch main && git pull` before the next question.
- Keep the `README.md` table up to date with each PR link.

## Skills
| Skill | Use |
|---|---|
| `/solve-question q1` | End-to-end harness for one question: branch → design → implement → test → review → PR → merge |
| `/review-ticket q1` | Check a question spec against the codebase and list ambiguities |
| `/plan-ticket q1` | File-by-file plan and test plan |
| `/implement-api` | Build the feature layer by layer with small commits |
| `/write-tests` | Unit, MockMvc, and concurrency tests |
| `/db-migration` | Flyway migration |
| `/self-review` | Review the diff before the PR |
| `/raise-pr q1` | Push and open the PR from the template, and update the README link |
| `/merge-pr` | Merge, pull main, and prepare for the next question |
| `/address-pr-comments` | Handle review feedback |
| `/explain-code q1` | Interview drill: request flow, decisions, concurrency, "what if you remove this line" |
| `/video-script` | 2-minute video script once all five are merged |
