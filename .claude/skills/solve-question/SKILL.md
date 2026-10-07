---
name: solve-question
description: End-to-end harness for one assignment question (q1–q5) - branch from latest main, confirm design, implement with small commits, test, self-review, raise PR with the template, update README, merge, pull main. Use when the user says "solve q1", "do question 3", "start Q5".
argument-hint: <q1|q2|q3|q4|q5>
disable-model-invocation: true
---

# Solve question `$ARGUMENTS`

| Arg | Spec | Branch | Budget |
|---|---|---|---|
| q1 | `docs/questions/q1-task-api.md` | `feature/q1-task-api` | 15m |
| q2 | `docs/questions/q2-url-shortener.md` | `feature/q2-url-shortener` | 15m |
| q3 | `docs/questions/q3-auth.md` | `feature/q3-auth` | 25m |
| q4 | `docs/questions/q4-product-catalog.md` | `feature/q4-product-catalog` | 25m |
| q5 | `docs/questions/q5-order-service.md` | `feature/q5-order-service` | 40m |

The whole assignment has a 2-hour budget, so move fast. There are only **three checkpoints (⏸)**. Note the start time and report elapsed time at each one.

## 1. Branch
```
git switch main && git pull        # skip pull if no remote yet
git switch -c <branch>
```
Refuse to start if the working tree is dirty or the previous question's PR isn't merged (the order Q1 → Q5 matters).

## 2. Design check ⏸
Read the spec file and `CLAUDE.md`. Look at what earlier questions already built (`common/exception`, security config, `Product`). In **≤ 10 lines**, show:
- the endpoints
- the 2–3 key decisions, taken from the spec's "Decisions" table
- anything the spec doesn't cover, along with your pick

Ask: "Go with this design?" The user must own these decisions for the interview.

## 3. Implement with small commits
Follow the `implement-api` skill. **Commit after each step in the spec's "Commit plan"** using imperative messages (`Add create task endpoint`). Each commit must compile. Run `./mvnw -q verify` at least before the last commit.

For Q3, update earlier questions' tests to authenticate in the same PR. For Q5, evict the Q4 product cache on stock changes.

## 4. Tests
Follow the `write-tests` skill. Cover every acceptance criterion in the spec's "Test plan". Concurrency ACs need real multi-threaded `@SpringBootTest` tests. `./mvnw -q verify` must be green.

## 5. Self-review ⏸
Run the `self-review` skill on `git diff main...HEAD`. Fix blockers, re-run verify, and commit fixes with meaningful messages. Then show:
- the AC → test mapping
- `git log main..HEAD --oneline`
- the elapsed time

Ask: "Raise the PR?"

## 6. PR + README
Follow `raise-pr` with `$ARGUMENTS`. It pushes, opens the PR with the 4-section template, and commits the README PR link on the branch.

## 7. Merge ⏸
Show the PR URL. Ask "Merge now?", then follow `merge-pr`.

## 8. Wrap-up
Print three likely interview questions about *this* code with short answers, taken from the spec's "Interview prep" and adjusted to what was actually built. Suggest running `/explain-code $ARGUMENTS` before starting the next question.

## Rules
- Build only what the spec asks for. Do bonus items only if the user asks and time remains.
- If you deviate from the spec's recommended design, update the spec's Decisions table so the PR and the interview story match the code.
- Never force-push, and never commit directly to main.
