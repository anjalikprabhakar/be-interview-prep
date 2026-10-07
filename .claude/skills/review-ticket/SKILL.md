---
name: review-ticket
description: Analyze a ticket or assignment question (q1–q5, GitHub issue, Jira key, or pasted text) for clarity, completeness, and testability against the current codebase. Produces a readiness verdict, gaps, ambiguities with recommended answers, and refined acceptance criteria. Use when the user says "review ticket", "review q2", "analyze this story", or before starting work.
argument-hint: <q1..q5 | issue-number | TICKET-ID | pasted text>
---

# Review ticket

Input: `$ARGUMENTS`

## 1. Get the ticket
- **`q1`–`q5`**: read the matching file in `docs/questions/` (see `docs/questions/README.md`). The source of truth is `docs/Backend_Interview_Prep_Assignment.pdf`, so check the spec against it.
- **GitHub issue** (a number or URL): run `gh issue view <n> --json number,title,body,labels,comments`.
- **Jira key**: use the Jira MCP tools if they're connected. Otherwise ask the user to paste the ticket.
- **Pasted text**: use it as given.

Don't invent requirements. If something is unclear, list it as a question.

## 2. Ground it in the codebase
Find what already exists that this ticket touches or can reuse, such as error handling, security config, entities, and migrations. Note what earlier work this ticket might break. For example, adding Spring Security makes every existing endpoint return 401.

## 3. Checklist
| Area | Check |
|---|---|
| Goal and scope | Is it clear what to build? What's explicitly *not* required? |
| Acceptance criteria | Are they specific and testable? Can each one be mapped to a test? |
| API contract | Methods, paths, request/response, status codes, error cases |
| Data | Tables, constraints (unique, check, not null), indexes, migrations |
| Validation | Field rules and formats |
| Concurrency | What happens with two simultaneous requests? Lost updates, races, duplicates |
| Security | Who can call it? Secrets? |
| Ambiguities | Decisions the spec leaves to you (for example, "same URL twice") |

## 4. Output
For **q1–q5**, append a `## Review notes` section to the question file. For anything else, write `docs/tickets/<ID>.md`. Then summarize in chat:

```markdown
## Review notes
**Verdict:** READY | NEEDS DECISIONS | NOT READY
**Ambiguities → recommended decision**
1. <question> → <recommendation> (alternative: …)
**Impact on existing code:** …
**Refined acceptance criteria:**
- [ ] Given … When … Then …
**Risks:** …
```
Don't implement anything in this skill.
