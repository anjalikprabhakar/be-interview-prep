---
name: video-script
description: Write the ≤2-minute submission video script (repo tour, ~20s per question with the one key decision, one improvement) from the merged PRs, plus a final submission checklist. Use when all questions are merged or the user asks for the video script.
---

# Video script

1. Gather facts: `gh pr list --state merged --json number,title,url,body`, `git log --oneline main`, and `README.md`.
2. For each Q1–Q5, take the **single most important decision** from that PR's "Decisions & trade-offs" section and find the one code snippet that shows it (`file:line`).
3. Write `docs/video-script.md` (≈ 260–300 spoken words total, which is about 2 minutes):

```markdown
| Time | Show on screen | Say |
|------|----------------|-----|
| 0:00–0:10 | GitHub repo → Pull requests (closed) → README table | "…" |
| 0:10–0:30 | Q1 `GlobalExceptionHandler.java:NN` | "…" |
| 0:30–0:50 | Q2 `ShortLinkRepository.java:NN` atomic increment | "…" |
| 0:50–1:10 | Q3 `SecurityConfig.java:NN` | "…" |
| 1:10–1:30 | Q4 `ProductService.java:NN` cache annotations | "…" |
| 1:30–1:50 | Q5 `ProductRepository.java:NN` conditional UPDATE | "…" |
| 1:50–2:00 | — | "With more time I would …" |
```
   Each "Say" cell covers what the feature does and **why** it was built that way. Skip line-by-line walkthroughs.
4. Add recording tips: IDE font ≥ 16px, open the files in tabs beforehand in order, upload to YouTube as **Unlisted**.
5. Append the submission checklist from the assignment, ticking each item you can verify:
   - Public repo `be-interview-prep` with a README
   - 5 branches, 5 PRs, all merged into `main`
   - Every PR uses the template
   - The README explains how to run the app and the tests
   - All tests pass (run `./mvnw -q verify` on main)
   - The README has every PR link and the YouTube link
   - The user can explain every file (suggest `/explain-code qN quiz` for any weak ones)
6. Once the user gives you the YouTube link, put it on the `**Video:**` line of the README. Since this is a change to main, ask whether they want it via a small PR or a direct commit.
