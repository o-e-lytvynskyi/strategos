# M10 on dev/o-e-lytvynskyi/m10

**Developer:** o-e-lytvynskyi (Claude Code, Opus) · **Branch:** dev/o-e-lytvynskyi/m10 · **Work:** M10 · **Base:** main @ 05cd50b; dev/o-e-lytvynskyi/m9 @ fdc2c38 is merged in after the claim (M10 stacks on M9) · **Claimed:** 2026-11-25 · **Status:** claimed

**Note:** the owner's word in the developer session, 2026-11-25: «давай начинать разработку с учетом переписывания вех и адр, если надо, цель такая: сейчас сделать 1 дефолтную нацию, начальную, самую простую и понятную по типу того, что у нас уже есть, и так же сделать фундамент для того, чтобы правильно и бестпрактисно, без переписывания ядра мы потом могли добавлять экстравагантные нации с отличающимся геймплеем. в остальном, следуй уже намеченым планам.» The claim covers the M10 spec and every M10 task. The milestone is reshaped on that word: one default civilisation (today's content, the simplest), and a civilisation foundation — a per-faction civilisation record and per-rule seams in the core, with the AI asking its civilisation's rules instead of assuming them — so distinctive civilisations with their own gameplay are added later without rewriting the core. The second civilisation and its art (CIV-02, ART-04, decision D-03) move to M14 beside CIV-03; the other M10 cards follow the roadmap. The card and ADR changes are written in the spec and listed under "Decisions taken" for the owner's veto.

## Now

- **Milestone:** M10, spec not written yet.
- **In progress:** none.
- **Last finished task:** none.
- **Next task:** after the claim merges: merge dev/o-e-lytvynskyi/m9, then specify M10 (`docs/milestones/M10-*.md`) with the reshaped cards and the civilisation ADR.
- **Merged from main last:** 05cd50b (2026-11-25).

## Depends on unfinished work

From the assessment before the claim (procedure §2a step 0). Every other dependency of the M10
cards (CAM-02, CMB-01, CMB-05, CORE-03, DATA-02, DATA-03, ECO-01, ECO-16, FOW-01, MATCH-01,
MATCH-02, ORD-03, ORD-04, PATH-09, QA-12, TECH-02, TECH-03, UI-08, UI-13, UNIT-06) is `done` or
`accepted` on `main`. D-13 is decided (day and night is the first twist); D-09 is decided.

| Milestone | What this work needs from it | Plan: independent / against a contract / after | `AFTER-M<n>` markers |
|---|---|---|---|
| M9 (handed in, [PR #58](https://github.com/o-e-lytvynskyi/strategos/pull/58)) | TERR-01 for TECH-04 (the City phase), BLD-10 for CMB-12 (healing at a temple), AI-08 for AI-09, PERF-16 for PERF-17; M9's code as a whole | stacked: dev/o-e-lytvynskyi/m9 is merged into this branch after the claim merges; `origin/main` is merged in once #58 lands | none |
| M7 (spec only) | ART-03 (the first civilisation's art pass) for ART-04; MATCH-06 and NET-03 for per-player views of the new content (the technology screen, hotkeys) in multiplayer | ART-04 moves to M14 with CIV-02 (the owner's word above); multiplayer: independent over the existing single gateway, with `AFTER-M7` markers where per-player state matters | to be listed as written |
| M8 (claimed by `lev1pes`, foundations only) | FOW-02 team vision meets MATCH-16's night radius and its torch vision sources | against FOW-01's vision seam (`VisionGrid`); the team-vision policy is not touched; `AFTER-M8` where shared vision meets the night radius | to be listed as written |

## Log

Newest first; one entry per session in the worklog's shape.

### 2026-11-25 — M10 claim

- **M10 · claim** — the owner's word above; assessment of unfinished work in the table; the core's readiness for civilisations that differ by rules was surveyed first (two read-only audits): the content model (templates with components, faction-scoped modifiers, data resources, phases as technologies) is ready for data differences; rules are match-wide (`MatchRules`) with no per-faction part, and the AI assumes houses, dropsites and builders — seams per rule are enough, no core rewrite.
- **Next:** the M10 spec once the claim merges.

## For the owner

1. **Merge the claim pull request** (`Claim: M10`): it adds this file only. Development starts after it.
2. **M9's hand-in, [PR #58](https://github.com/o-e-lytvynskyi/strategos/pull/58)**, waits for a maintainer review; M10 stacks on it, so its review comes first.
3. **`main`'s records after M6** (a maintainer session): M6 was merged in [PR #55](https://github.com/o-e-lytvynskyi/strategos/pull/55) on 2026-11-15, but `docs/branches/o-e-lytvynskyi-m6-t2-t17.md` on `main` still says `ready for review` and STATE.md still shows M6 as not open. `claims.py check` fails on it, so the `records` gate is red on every pull request — this claim and #58 included — until the maintainer sets `merged 2026-11-15` and folds M6 into STATE.md.

## Decisions taken

- 2026-11-25 · The claim branch is cut from `origin/main` and dev/o-e-lytvynskyi/m9 is merged into it after the claim merges — the claim pull request must add the branch file only, and M10 needs M9's code (TERR-01, BLD-10, AI-08, PERF-16) — undo: wait for #58 to merge and take M9 through `origin/main` instead.

## Pitfalls

None yet.

## Review

Written by the validation (`strategos-maintain` §3): gate results, findings, recommendation.
