---
name: address-pr-comments
description: Fetch review comments on a GitHub PR, triage them, apply fixes, and draft replies. Use when the user says "address review comments", "fix PR feedback", or gives a PR number with feedback to handle.
argument-hint: [PR number — defaults to current branch's PR]
---

# Address PR comments

1. Find the PR: use `$ARGUMENTS`, or run `gh pr view --json number,url,headRefName` for the current branch.
2. Fetch the feedback:
   - `gh pr view <n> --json reviews,comments`
   - `gh api repos/{owner}/{repo}/pulls/<n>/comments` for inline comments (path, line, body, id)
3. Triage each unresolved comment into one of these, and show the table to the user:
   | # | File:line | Reviewer ask | Category | Proposed action |
   Categories: **Fix** (valid, will change), **Discuss** (disagree or unclear, so draft a reply with reasoning), **Already done**, **Out of scope** (suggest a follow-up ticket).
4. After the user confirms, apply the fixes, keeping each change minimal and on point. Run `./mvnw -q verify`.
5. Commit with specific imperative messages (for example `Validate expiry date is in the future`, never `fix` or `address comments`) and push. Ask before pushing.
6. Draft a reply for each comment ("Fixed in <sha>: …" or the reasoning). Post replies with `gh` **only if the user approves**, because they're visible to the team.
