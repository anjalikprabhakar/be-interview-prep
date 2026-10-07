---
name: explain-code
description: Interview drill for code in this repo - explains a question's implementation (request-to-DB flow, every decision and its alternatives, concurrency behaviour, "what if you remove this line"), then quizzes the user. Use when the user says "explain q3", "prepare me for the interview", "quiz me", or "walk me through".
argument-hint: <q1..q5 | file path> [quiz]
---

# Explain code: `$ARGUMENTS`

The interviewer will open the PRs and ask: *Walk me through HTTP → DB. Why this approach, and what alternatives? What breaks with two simultaneous requests? What if you remove this line? Change this live.* If the user can't answer, the question counts as **not done**. Your job is to make the user able to answer without you.

Explain from the **actual code** on main or the branch, not from the spec. Where the code differs from the spec, say so.

## Part 1: Explain (keep it short and concrete, with `file:line` references)
1. **One sentence:** what the feature does.
2. **Request → DB trace:** for the main endpoint, trace filter chain (Q3+) → controller → validation → service → transaction boundary → repository → the SQL Hibernate runs → response mapping → status code. Show the actual SQL where it matters (for example, the atomic `UPDATE`).
3. **Decisions:** for each key decision, say what was chosen, which alternatives were considered, and why. Then say when the alternative would be better.
4. **Concurrency:** what happens when two requests hit at the same time, and which line makes it safe.
5. **"Remove this line":** pick the 5 most load-bearing lines (`@Valid`, `@Transactional`, `WHERE stock >= :qty`, `@CacheEvict`, `STATELESS`, the UNIQUE constraint…) and say exactly what breaks without each one.
6. **Live-change rehearsal:** suggest 2 likely live changes and list the files to touch for each.
7. **The 20-second video line:** the one piece of code to show and the one sentence to say.

## Part 2: Quiz (always offer it, and start it if the args include `quiz`)
Ask **one question at a time**, using the AskUserQuestion tool or plain chat, mixing:
- a trace question ("What happens after `@Valid` fails?")
- a "why" question ("Why 302 rather than 301?")
- a break-it question ("What if `reserve` were `findById` + `save`?")
- a live-change task ("Add a `priority` filter: which files?")

After each answer, say what was right, correct what was wrong with `file:line`, and move on. At the end, list weak spots to revisit.

Don't edit code in this skill. It's for building understanding.
