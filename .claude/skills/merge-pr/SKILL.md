---
name: merge-pr
description: Merge the current branch's PR into main, sync local main, and prepare for the next question. Use only when the user explicitly asks to merge.
argument-hint: [PR number] [--squash]
disable-model-invocation: true
---

# Merge PR

1. Find the PR: use `$ARGUMENTS`, or `gh pr view --json number,url,state,mergeable,headRefName,title`.
2. Pre-merge checks (stop and report on any failure):
   - The state is `OPEN` and `mergeable` is `MERGEABLE`.
   - `gh pr checks` passes (if CI exists).
   - The PR body has all four template sections filled in.
   - The README row for this question contains the PR link.
   - The latest `# Verdict:` review on the PR is **PASS** and covers the current head commit. Otherwise suggest `/pr-review` first.
3. Merge. Use a merge commit by default, because it keeps the small meaningful commits visible on main. Use `--squash` only if the user asked for it.
   ```
   gh pr merge <n> --merge --delete-branch
   ```
4. Sync:
   ```
   git switch main
   git pull
   ```
5. Run `./mvnw -q verify` on main to confirm it's still green.
6. Report: merged ✔, the main SHA, and the next question and its branch name (see `docs/questions/README.md`). After Q5, suggest `/video-script` and remind the user to add the YouTube link to the README.
