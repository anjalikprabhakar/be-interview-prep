---
name: pr-review
description: Review an open PR locally (check it out, run the build, dispatch reviewer agents against the conventions) and post exactly one verdict review on GitHub. Use only when the user asks to review a PR, e.g. "/pr-review 3" or a PR URL.
argument-hint: <PR number | PR URL> (defaults to the current branch's PR)
disable-model-invocation: true
---

# PR review (local)

Review an open PR from your own machine and post one verdict. The method lives in `.claude/conventions/review-conventions.md`, so **read it first**: it covers what blocks, how to filter, the review count, and how to choose the posting mode. This skill covers only the local I/O: how you read the PR and how you post. Every changed line is also held to `.claude/conventions/comment-conventions.md`.

**Preconditions:** `gh auth status` succeeds, and `git status --porcelain` is empty (a clean working tree). Every command is plain `gh …`, `git …`, or `./mvnw …`.

## 1. Check out the PR
Take the PR number from `$ARGUMENTS` (a number or a pasted URL). If none is given, use `gh pr view --json number` for the current branch. Remember the current branch so you can return to it.

```
gh pr view <PR> --json number,title,body,author,headRefName,baseRefName,headRefOid,labels,mergeable,url
gh pr checkout <PR>
git fetch origin <baseRefName>
git diff --stat origin/<baseRefName>...HEAD
```
The PR's files are now on disk. Read changed files from the working tree, and take hunks from `git diff origin/<baseRefName>...HEAD -- <path>`. **Don't fetch file contents or diffs through the GitHub API.**

`mergeable` is the merge state (§0):
- If it's `CONFLICTING`, review nothing. Gather only the review count and the posting identity (step 2), post the conflict variant (step 5), and stop.
- If it's `UNKNOWN`, read it once more. If it's still `UNKNOWN`, carry on.

## 2. Gather context
- **Requirements (§1):**
  - The linked issue (`Closes #N` in the body): `gh issue view <N> --json title,body,labels,milestone,comments`
  - For `feature/qN-*` branches: `docs/questions/qN-*.md`
- **Existing discussion** (so settled points aren't re-raised). Read owner and name once with `gh repo view --json owner,name`, then substitute the literal values. `gh api graphql` doesn't fill `{owner}`/`{repo}`.
  ```
  gh api graphql -f owner='<owner>' -f name='<repo>' -F pr=<PR> -f query='
  query($owner: String!, $name: String!, $pr: Int!) {
    repository(owner: $owner, name: $name) {
      pullRequest(number: $pr) {
        comments(first: 100) { nodes { author { login } body } }
        reviews(first: 100) { nodes { author { login } state body } }
        reviewThreads(first: 100) { nodes { isResolved isOutdated comments(first: 50) { nodes { author { login } path line body } } } }
      }
    }
  }'
  ```
- **Review count (§4):** the number of review bodies and comments above that start with `# Verdict:`, plus one.
- **Who's posting:** `gh api user --jq .login`, compared with `author.login` from step 1.

## 3. Build
Run `./mvnw -q verify` on the checked-out head. A red build is a B1 blocking finding, so include the failing test names and the first assertion error.

## 4. Review
1. Load the **Conventions Skills** listed in `CLAUDE.md` under `## Project-specific`.
2. Choose **Reviewer Agents** using the **Label Routing** table there (from the PR labels and the changed paths).
3. Dispatch the agents **in parallel** with:
   - the diff range `origin/<base>...HEAD`
   - the changed-file list
   - the requirement summary
   - the conventions to apply
4. Check B9 yourself:
   - the PR body has all four template sections, and they match the code
   - `git log origin/<base>..HEAD --format='%s%n%b'` has meaningful messages and no AI attribution
5. Keep only **blocking** findings (§2), then filter them (§3):
   - drop anything you can't substantiate
   - drop points already raised in the discussion
   - check the open backlog:
     ```
     gh issue list --state open --limit 100 --json number,title,labels,updatedAt
     gh pr list --state open --limit 50 --json number,title,headRefName
     ```
     Read the body and comments of any candidate (`gh issue view <M> --json title,body,comments`) before deciding it already covers a finding.

## 5. Post the verdict
Write the body to `review-body.md` in the repo root, in the exact shape of `.claude/conventions/review-template.md`. Then post **once**, choosing the mode up front (§5):
```
gh pr review <PR> --comment         --body-file review-body.md   # author == viewer, either verdict
gh pr review <PR> --request-changes --body-file review-body.md   # FAIL, author != viewer
gh pr review <PR> --approve         --body-file review-body.md   # PASS, author != viewer
```
Delete `review-body.md`, then return to the branch you started from (`git switch -`).

## Output
One line to the user: the verdict, the review count, and the review URL. Nothing else.

## Project-specific
Every value this file names (Conventions Skills, Reviewer Agents, Label Routing) is registered once in `CLAUDE.md` under `## Project-specific`. Read it there. Nothing is restated here, so nothing here can fall out of step with it.
