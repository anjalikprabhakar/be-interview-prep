# Q1: Task Manager API

**Branch:** `feature/q1-task-api` · **Time:** 15 min · **PR title:** `Q1: Task Manager API`

## Requirements (from the assignment)
- A task has a **title** (required, max 100 chars), a **description**, a **status** (To do / In progress / Done), a **due date** (cannot be in the past), and a **created date**.
- Create, list, get one, update, and delete tasks. Filter the list by status.
- Reject invalid input with a clear message for each invalid field.
- All errors (invalid input, not found, unexpected) return **one consistent JSON format** with the right HTTP status.

## Acceptance criteria
- [ ] Invalid input returns **400** with field-level messages.
- [ ] An unknown task returns **404**.
- [ ] At least one automated test.

**Optional:** interactive API docs (springdoc-openapi `2.8.x` → `/swagger-ui.html`).

---

## Recommended design

### Data
`V1__create_tasks.sql`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT identity | PK |
| title | VARCHAR(100) NOT NULL | the DB also enforces the limit |
| description | VARCHAR NULL | optional, no length rule (the assignment sets none) |
| status | VARCHAR(20) NOT NULL | `TO_DO`, `IN_PROGRESS`, `DONE` (enum stored as a string) |
| due_date | DATE NULL | |
| created_at | TIMESTAMP NOT NULL | set by the server, never by the client |

### API
| Method | Path | Success | Errors |
|---|---|---|---|
| POST | `/api/tasks` | 201 + `Location` | 400 |
| GET | `/api/tasks?status=TO_DO` | 200 (list, `status` optional) | 400 for an unknown status |
| GET | `/api/tasks/{id}` | 200 | 404 |
| PUT | `/api/tasks/{id}` | 200 | 400, 404 |
| DELETE | `/api/tasks/{id}` | 204 | 404 |

Request: `{ "title": "Write report", "description": "...", "status": "TO_DO", "dueDate": "2026-12-01" }`. `status` is **required** (no default), `description` and `dueDate` are optional.
Response: the same fields plus `id` and `createdAt`.

Error example:
```json
{ "timestamp": "...", "status": 400, "error": "Bad Request", "message": "Validation failed",
  "path": "/api/tasks", "fieldErrors": [ { "field": "title", "message": "must not be blank" } ] }
```

### Classes
`task/TaskController`, `TaskService`, `TaskRepository` (`findByStatus`), `Task` (entity), `TaskStatus` (enum), `dto/TaskRequest`, `dto/TaskResponse`
`common/exception/GlobalExceptionHandler`, `ApiError`, `FieldError`, `NotFoundException`

### Exceptions the handler must cover
| Exception | Status | Why |
|---|---|---|
| `MethodArgumentNotValidException` | 400 + fieldErrors | `@Valid` body failed |
| `HttpMessageNotReadableException` | 400 | malformed JSON, bad enum value, or bad date in the body |
| `MethodArgumentTypeMismatchException` | 400 | `?status=FOO`, `/api/tasks/abc` |
| `NotFoundException` | 404 | unknown id |
| `OptimisticLockingFailureException` | 409 | the row was deleted by a concurrent request between our read and our write |
| `Exception` (catch-all) | 500, generic message | never leak stack traces, and log the real error |

### Decisions & alternatives
| Decision | Chosen | Alternatives | Why |
|---|---|---|---|
| Error format | Custom `ApiError` record | Spring's `ProblemDetail` (RFC 9457) | Full control over the `fieldErrors` shape, and the same format can be written by the security handlers in Q3. ProblemDetail is a valid standard alternative. |
| Due date in past | `@FutureOrPresent` on the DTO | Check in the service | Declarative, and appears in field errors automatically. Trade-off: it's evaluated in server time zone. |
| Update | PUT (full replace) | PATCH (partial) | Simpler validation, because the whole body is validated the same way as create. |
| Status on create | Required, no default | Default to `TO_DO` | POST and full-replace PUT share one DTO and one set of rules, with no hidden defaults. |
| Description length | No limit | Arbitrary max (for example 2000) | The assignment sets no limit, so we don't invent a business rule. |
| Unknown paths, 405, 415 | Catch-all keeps the status of Spring's `ErrorResponse` exceptions | Extend `ResponseEntityExceptionHandler` | One small `instanceof` check keeps every Spring MVC error in the same `ApiError` format, with no extra base class. |
| Concurrent delete or update | 409 Conflict, retryable | 404 · let it be 500 | Hibernate checks the affected row count. If a parallel DELETE removed the row after our read, the write affects 0 rows and Spring throws `ObjectOptimisticLockingFailureException`. 409 tells the client to reload and retry. (Reproduced on H2 for both DELETE and PUT.) |
| Status filter | Optional `@RequestParam TaskStatus status` | Specification | Only one filter, so a derived query is enough. |
| Created date | `@PrePersist` / `@CreationTimestamp` | Client-supplied | The server owns this value. Clients can't fake it. |
| Pagination | Not added (not required) | `Pageable` | Out of scope for 15 minutes. Mention it as a "with more time" item. |

## Test plan
| AC | Test | Type |
|---|---|---|
| 400 + field messages | `TaskControllerTest.create_blankTitleAndPastDueDate_returns400WithFieldErrors` | `@WebMvcTest` |
| 400 too-long title | `create_title101Chars_returns400` | `@WebMvcTest` |
| 404 | `get_unknownId_returns404` | `@WebMvcTest` |
| Happy path | `create_validRequest_returns201WithLocation` | `@WebMvcTest` |
| Filter | `TaskApiIntegrationTest.list_filterByStatus_returnsOnlyMatching` | `@SpringBootTest` + MockMvc |

## Commit plan
1. `Add task entity and migration`
2. `Add create and get task endpoints`
3. `Add update, delete and list with status filter`
4. `Return consistent JSON errors with field-level messages`
5. `Add task API tests`

## Interview prep
- **Request → DB:** DispatcherServlet → `TaskController.create` → Jackson deserializes into `TaskRequest` → `@Valid` runs Bean Validation (it fails → `MethodArgumentNotValidException` → handler → 400) → `TaskService.create` maps it to an entity → `repository.save` → Hibernate `INSERT` inside the `@Transactional` boundary → entity mapped to `TaskResponse` → 201 + `Location`.
- **What if you remove `@Valid`?** Constraints aren't checked. A blank title reaches the DB, where `NOT NULL` passes for `""` but a >100-character title throws `DataIntegrityViolationException`, which becomes a 500.
- **What if you remove the catch-all handler?** Spring Boot's default `/error` JSON is returned, in a different shape. The "one consistent format" requirement is broken.
- **Two concurrent PUTs on the same task?** Last write wins (lost update). The fix is `@Version` optimistic locking → 409 on a stale write.
- **DELETE racing a DELETE or PUT on the same task?** Both requests pass `findById`, the first commits, and the second write affects 0 rows. Hibernate throws, and `GlobalExceptionHandler` maps it to **409**, not 500.
- **Why return a DTO instead of the entity?** It avoids exposing internal fields, lazy-loading serialization issues, and mass assignment (a client setting `createdAt`/`id`).
- **Live change ideas:** add a `priority` field; make `title` max 50; add a `dueBefore` filter.
