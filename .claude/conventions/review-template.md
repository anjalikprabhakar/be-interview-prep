# Review template

Copy the shape exactly. The first line must start with `# Verdict:`, because that's how the review count (review-conventions.md §4) is computed. Never add AI attribution.

## Standard review

```markdown
# Verdict: PASS | FAIL (review #<N>)

**PR:** #<number> <title> · **Head:** `<short sha>` · **Spec:** <linked issue or docs/questions/qN-*.md>

## Checks
| Check | Result |
|---|---|
| Build & tests (`./mvnw -q verify`) | ✅ <n> tests pass / ❌ <what failed> |
| Merge state | ✅ mergeable |
| Acceptance criteria | ✅ <k>/<k> covered / ❌ <which one is missing> |
| PR description (template) | ✅ / ❌ <what's missing or inaccurate> |
| Commits | ✅ meaningful, no AI attribution / ❌ <which> |

## Acceptance criteria
| AC | Implemented in | Proven by |
|---|---|---|
| <AC text> | `path/File.java:line` | `TestClass#method` |

## Blocking findings
<"None." or a numbered list:>
1. **[B<n> <category>] `path/to/File.java:<line>`: <one-line problem>**
   - **Scenario:** <input or interleaving> → <wrong outcome>
   - **Fix:** <concrete change>

## Already tracked
<"None." or "- <finding> → #<issue>">
```

## Conflict variant (review-conventions.md §0)

```markdown
# Verdict: FAIL (review #<N>)

**PR:** #<number> <title> · **Head:** `<short sha>`

This PR has merge conflicts with `<base>`, so it wasn't reviewed. Rebase or merge `<base>` into the branch, resolve the conflicts, push, and request a new review.
```
