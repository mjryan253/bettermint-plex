# CLAUDE.md

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

## Working rules — mandatory at all times (repo-wide)

Follow the behavioral guidelines in this document — **Think Before Coding · Simplicity First · Surgical Changes · Goal-Driven Execution** — for **every** task in this repo, in any language or logic problem, at all times. Non-negotiable specifics:

- **Do only what was explicitly requested.** Never proactively edit, refactor, "harden," or "improve" code/scripts/docs the user didn't ask you to change. If you spot something worth doing, **propose it and wait** — don't just do it.
- **Surgical:** every changed line must trace directly to the request; leave adjacent code, comments, and formatting alone.
- **Think first:** state assumptions, surface tradeoffs, ask when unclear — before implementing.
- **Simplicity first;** verify against a defined success check before calling anything done.
- **No AI / co-author attribution, ever.** Never add `Co-Authored-By:` trailers, "🤖 Generated with…" lines, or any assistant/tool attribution to commit messages, PR descriptions, code, or docs. All work is attributed solely to the repo owner under their own GitHub identity.
- **Never push to a remote unless explicitly told to.** Local commits when asked are fine, but `git push` (any branch) requires an explicit instruction each time — never push on your own initiative.
- **`flatpak-spawn --host`** will be used to access anything on the local machine outside of the sandbox (nmap, mnamer, etc.) in discovery and read-only mode. Edits and writes done this way must be explicitly approved by the user.
- **Keep `docs/memory.md` current.** Read it at the start of every session. Whenever the user accepts a change (code, wiring, docs, config, decisions), record it in `docs/memory.md` — update the affected status sections and add a dated change-log entry — as part of the same piece of work, so future agents inherit the context.

