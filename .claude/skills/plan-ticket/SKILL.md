---
name: plan-ticket
description: Turn a reviewed ticket or assignment question into a concrete, file-by-file implementation plan with a test plan and commit plan. Use after review-ticket, or when the user asks to "plan", "break down", or "design" a ticket/question.
argument-hint: <q1..q5 | TICKET-ID>
---

# Plan ticket

Ticket: `$ARGUMENTS`

1. Read the spec (`docs/questions/<q>*.md` or `docs/tickets/<ID>.md`) and `CLAUDE.md`. Question specs already contain a recommended design, so check it against the **current** code instead of re-deriving it. Reuse what earlier questions built.
2. Append or update an `## Implementation plan` section in the spec file:

```markdown
## Implementation plan
### Dependencies to add (pom.xml)
- …
### Files (in order)
| # | File | New/Modify | Purpose |
|---|------|-----------|---------|
| 1 | src/main/resources/db/migration/V<next>__….sql | new | |
| 2 | org/example/<feature>/<Entity>.java | new | |
| … | | | |
### Tests
| AC | Test class#method | Type |
### Commits
1. `Add …`
2. `…`
### Changes to earlier code
- … (for example, existing tests need auth in Q3)
```
3. Keep the plan proportional to the time budget in the spec. Flag anything that won't fit.
4. Show the plan and wait for approval.
