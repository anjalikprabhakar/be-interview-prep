---
name: self-review
description: Review the branch diff before raising a PR — correctness, concurrency, security, conventions, tests, acceptance-criteria coverage and commit hygiene. Use when the user says "review my changes", "self review", "check before PR", or before raise-pr.
argument-hint: [q1..q5 | TICKET-ID]
---

# Self-review

1. Collect:
   - `git diff main...HEAD`, `git diff`, `git status --porcelain`
   - `git log main..HEAD --oneline`
2. In parallel:
   - Delegate the deep review to the `code-reviewer` subagent, giving it the diff scope and the spec path (`docs/questions/<q>*.md`).
   - Run `./mvnw -q verify`.
3. Check these yourself:
   - **AC coverage:** for each acceptance criterion in the spec, name the code and the test that satisfy it.
   - **Commit hygiene:** every message is imperative and meaningful. Flag `fix`, `changes`, `final`, and `wip`. If you find bad messages, **don't rewrite pushed history**. Only suggest `git commit --amend` for the *latest unpushed* commit, and only with the user's approval.
   - **Explainability:** flag code that's clever but hard to explain in an interview, and suggest a simpler form.
   - **Spec drift:** if the code differs from the spec's Decisions table, update the table.
4. Report:
```
🔴 Blocker: failing tests, bugs, races, security, missing AC
🟡 Should fix: conventions, missing edge-case tests, unclear code
🟢 Nit
```
   Give `file:line`, the problem, and the fix for each finding.
5. Offer to fix blockers and should-fix items. After fixing, re-run verify and commit with meaningful messages.
