# Review conventions

This file is the method behind `/pr-review`: what blocks, how findings are filtered, how reviews are counted, and how the verdict is posted. The skill itself covers only reading the PR and posting.

## §0 Merge state
Read `mergeable` from `gh pr view <PR> --json mergeable`.
- **`CONFLICTING`**: review nothing. Post the conflict variant of the template (verdict FAIL) and stop. Reviewing code that will change during conflict resolution wastes the review.
- **`UNKNOWN`**: GitHub is still computing it. Read it once more. If it's still `UNKNOWN`, continue with the review.
- **`MERGEABLE`**: review normally.

## §1 Scope and sources of truth
- Review **only the diff** against the base (`git diff origin/<base>...HEAD`), plus the unchanged code it calls or breaks.
- Requirements come from:
  1. the linked issue (`Closes #N`), if there is one
  2. for assignment branches (`feature/qN-*`), `docs/questions/qN-*.md`: its acceptance criteria, plus any user-approved overrides recorded in its Decisions table
  3. the PR description
- Standards come from the Conventions Skills and conventions files listed in `CLAUDE.md` → `## Project-specific`.

## §2 What blocks
A finding is **blocking** only if it falls into one of these categories:

| # | Category | Examples |
|---|---|---|
| B1 | **Build or tests** | `./mvnw -q verify` fails; a test is flaky, disabled, or `@Disabled` without reason |
| B2 | **Acceptance criteria** | an AC isn't implemented, or has no test that would fail if it broke |
| B3 | **Correctness** | wrong result, wrong status code, unhandled null, a transaction boundary that breaks all-or-nothing |
| B4 | **Concurrency** | lost update (read-modify-write), check-then-act without a DB constraint, missing idempotency guard, lock-order deadlock |
| B5 | **Security** | hard-coded secret, missing authz, role escalation, a password or token logged or returned, injection, user enumeration |
| B6 | **API contract** | an error not in the `ApiError` format, a client error returned as 500, an internal detail leaked, a breaking change to an earlier question's endpoint |
| B7 | **Data integrity** | an edited merged migration, an invariant not enforced by a constraint, entity and schema mismatch |
| B8 | **Regression** | earlier questions' behaviour or tests broken (for example, Q3 security making Q1 tests fail) |
| B9 | **PR discipline** | template sections missing or inaccurate (the description claims something the code doesn't do), meaningless commit messages (`fix`, `changes`, `final`, `wip`), AI attribution trailers or footers |
| B10 | **Standards that cause defects** | violations of the coding standards or comment conventions that produce a B1–B8 problem, or make the code unexplainable (dead code, misleading comments) |

**Never blocking:** style preferences, naming bikeshedding, optional refactors, "could also add" features beyond the spec. These aren't posted at all.

## §3 Filter (applied before posting)
Drop a finding if any of these is true:
1. **It can't be substantiated.** Every finding needs `path:line`, a concrete failure scenario (input or interleaving → wrong outcome), and a fix. If you can't write the scenario, drop it. "Might", "could potentially", and "consider" mean it gets dropped.
2. **It's already in the discussion.** It was raised before in a comment, review, or thread (resolved or not). Don't repeat settled points.
3. **It's already tracked.** An open issue or PR covers it. Read that issue's body and comments before deciding it does. If it does, mention the issue number under "Already tracked" instead of raising a finding.
4. **It's outside the diff**, unless the diff breaks it (B8).

## §4 Review count
Review N = (the number of existing PR review bodies **and** PR comments whose body starts with `# Verdict:`) + 1. The count goes in the heading so the history of re-reviews is visible.

## §5 Posting mode
Decide this before writing the body. Post exactly **once** per run.

| Situation | Command |
|---|---|
| Viewer is the PR author (self-review) | `gh pr review <PR> --comment` (GitHub forbids approving or requesting changes on your own PR) |
| Viewer isn't the author, verdict FAIL (including conflicts) | `gh pr review <PR> --request-changes` |
| Viewer isn't the author, verdict PASS | `gh pr review <PR> --approve` |

## §6 Verdict
- **PASS**: zero blocking findings after filtering, and the build is green.
- **FAIL**: one or more blocking findings, a red build, or a merge conflict.
