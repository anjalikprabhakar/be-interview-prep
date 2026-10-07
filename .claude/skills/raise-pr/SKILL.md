---
name: raise-pr
description: Push the feature branch and open a GitHub PR into main using the assignment's 4-section template (Problem / Approach / Decisions & trade-offs / How to test), then add the PR link to the README table. Use only when the user explicitly asks to raise/open/create a PR.
argument-hint: <q1..q5 | TICKET-ID> [--draft]
disable-model-invocation: true
---

# Raise PR

Args: `$ARGUMENTS`

## 1. Pre-flight (stop and report on any failure)
- `gh auth status` shows you're logged in.
- `git remote -v` shows `origin` pointing at `be-interview-prep`. If there's no remote, tell the user to create the repo first. Don't create a public repo without explicit approval.
- The branch is a `feature/qN-…` branch, not main.
- The working tree is clean (commit or ask first).
- `./mvnw -q verify` passes. Never open a PR with red tests.

## 2. Push
`git push -u origin <branch>`. Never force-push.

## 3. PR body
Fill `.github/pull_request_template.md` **exactly** (the same four headings), using the spec (`docs/questions/<q>*.md`), `git diff main...HEAD --stat`, and the real test results:

```markdown
## Problem
<1–2 lines>

## Approach
<Key classes and the request flow, for example: `TaskController` → `TaskService` → `TaskRepository`; `GlobalExceptionHandler` maps errors to `ApiError`.>

## Decisions & trade-offs
- **<Decision>:** chose X over Y because …   (2–4 bullets from the spec's Decisions table, matching what was actually built)

## How to test
./mvnw verify
<1–3 sample curl requests with expected statuses>
Tests: `XxxTest`, `YyyConcurrencyTest` (what each one proves)
```
Write it to a scratchpad file.

## 4. Create
```
gh pr create --base main --head <branch> --title "Q<N>: <Question name>" --body-file <file> [--draft]
```
Titles: `Q1: Task Manager API`, `Q2: URL Shortener`, `Q3: Authentication & Roles`, `Q4: Product Catalog`, `Q5: Order Service`.

## 5. README link
Update the question's row in `README.md` with `[#<n>](<pr-url>)`, then commit `Add Q<N> PR link to README` and push to the same branch. The link then lands on main with the merge.

## 6. Report
Give the PR URL. Suggest `/merge-pr` after the user has looked over the diff on GitHub.
