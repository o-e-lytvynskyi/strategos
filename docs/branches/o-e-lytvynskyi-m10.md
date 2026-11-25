# M10 on dev/o-e-lytvynskyi/m10

**Developer:** o-e-lytvynskyi (Claude Code, Opus) · **Branch:** dev/o-e-lytvynskyi/m10 · **Work:** M10 · **Base:** main @ 05cd50b; dev/o-e-lytvynskyi/m9 @ fdc2c38 is merged in after the claim (M10 stacks on M9) · **Claimed:** 2026-11-25 · **Status:** in progress

**Note:** the owner's word in the developer session, 2026-11-25: «давай начинать разработку с учетом переписывания вех и адр, если надо, цель такая: сейчас сделать 1 дефолтную нацию, начальную, самую простую и понятную по типу того, что у нас уже есть, и так же сделать фундамент для того, чтобы правильно и бестпрактисно, без переписывания ядра мы потом могли добавлять экстравагантные нации с отличающимся геймплеем. в остальном, следуй уже намеченым планам.» The claim covers the M10 spec and every M10 task. The milestone is reshaped on that word: one default civilisation (today's content, the simplest), and a civilisation foundation — a per-faction civilisation record and per-rule seams in the core, with the AI asking its civilisation's rules instead of assuming them — so distinctive civilisations with their own gameplay are added later without rewriting the core. The second civilisation and its art (CIV-02, ART-04, decision D-03) move to M14 beside CIV-03; the other M10 cards follow the roadmap. The card and ADR changes are written in the spec and listed under "Decisions taken" for the owner's veto.

## Now

- **Milestone:** M10, 2 of 32 tasks done; spec [M10](../milestones/M10-civilisations-and-depth.md).
- **In progress:** none.
- **Last finished task:** M10 T2 (2026-11-25).
- **Next task:** T3 · CIV-01 · Binding, roster and bonuses.
- **Merged from main last:** e18bcdd (2026-11-25, the claim's merge); dev/o-e-lytvynskyi/m9 @ fdc2c38 merged in at aec0d46.

## Depends on unfinished work

From the assessment before the claim (procedure §2a step 0). Every other dependency of the M10
cards (CAM-02, CMB-01, CMB-05, CORE-03, DATA-02, DATA-03, ECO-01, ECO-16, FOW-01, MATCH-01,
MATCH-02, ORD-03, ORD-04, PATH-09, QA-12, TECH-02, TECH-03, UI-08, UI-13, UNIT-06) is `done` or
`accepted` on `main`. D-13 is decided (day and night is the first twist); D-09 is decided.

| Milestone | What this work needs from it | Plan: independent / against a contract / after | `AFTER-M<n>` markers |
|---|---|---|---|
| M9 (handed in, [PR #58](https://github.com/o-e-lytvynskyi/strategos/pull/58)) | TERR-01 for TECH-04 (the City phase), BLD-10 for CMB-12 (healing at a temple), AI-08 for AI-09, PERF-16 for PERF-17; M9's code as a whole | stacked: dev/o-e-lytvynskyi/m9 @ fdc2c38 merged in at aec0d46; `origin/main` is merged in once #58 lands | none |
| M7 (spec only) | ART-03 (the first civilisation's art pass) for ART-04; MATCH-06 and NET-03 for per-player views of the new content (the technology screen, hotkeys) in multiplayer | ART-04 moves to M14 with CIV-02 (the owner's word above); multiplayer: independent over the existing single gateway, with `AFTER-M7` markers where per-player state matters | to be listed as written |
| M8 (claimed by `lev1pes`, foundations only) | FOW-02 team vision meets MATCH-16's night radius and its torch vision sources | against FOW-01's vision seam (`VisionGrid`); the team-vision policy is not touched; `AFTER-M8` where shared vision meets the night radius | to be listed as written |

## Log

Newest first; one entry per session in the worklog's shape.

### 2026-11-25 — M10 T1–T2

- **M10 T2 · Civilisations as content** — CIV-01 in progress. `CivId`, `RawCiv`, `CivDef`, `CivRoles`, `StartKit`, `CivChecks`; `strategos/civs` read, compiled after templates and techs, linted (11 cases); `TemplateSet.civs()`; the default civilisation `strategos:default` ("Settlers") in `civs/default.json` and built in, 14 roles, today's first-skirmish civilisation entities as its kit. **The implied default:** a set whose civilisations are exactly the built-in default has no `$civs` entry, so no template-set version or pin changed; any other set carries `$civs` and the view a `+c` mark. Tests: `CivDefLoadTest` 7, `CivLintTest` 11, `CivSetVersionTest` 5, `TemplateSetViewTest` +6, `ShippedCivsTest` 5 (L2), payload size +1. `verify` green, 134 s.
- **M10 T1 · Health check moves** — M9 §10.4 items 1 and 3. `MatchState` 815 → 761 lines: the capture, gate, breach and territory state moved by moves only into `PlayState` (78 lines), reached by `MatchState.play()`; 34 files, callers renamed. New `RestoreEqualsLiveTest`: all 200 seeds of `m0-200` restored at tick 1,000 through the save codec and stepped 500 ticks beside the live match, and the proving-ground soak scene restored at 600 of 1,200, hashes equal at every tick; 8 s, so in `verify`; no divergence. `verify verifySlow` green, 275 s.
- **Next:** T2.

### 2026-11-25 — M10 spec

- **M10 · spec** — [M10-civilisations-and-depth.md](../milestones/M10-civilisations-and-depth.md), 32 tasks; [ADR-0028](../adr/0028-civilisations-and-rule-seams.md) Proposed (civilisations as content, eight rule seams, a closed variant catalogue, a test-only probe civilisation); architecture §8.9; ADR-0010 and ADR-0021 amended. Cards: CIV-05 added; CIV-02 and ART-04 `deferred (M14)`; CIV-01, AI-09, QA-16, PERF-17 rewritten (spec §1.6); 17 cards `specified`; CIV-04 (M15) depends on CIV-05; D-03 needed by M14. `roadmap.py check` 246 cards, 0 problems; `verifyFast` green after the merge of M9. A reviewer pass on the spec and ADR found twelve points (placement seam in the pure `PlacementRules`, the centre as a class token, `DefeatRule.holds` with the latch, the commit at placement for self-building, a stateless direct delivery at `headHome`, one owner for the population reservation, the template view's SHA, kit mirroring, the wildcard roster, a source-id table, T9 split, T22 after T20); all applied.
- **Next:** T1.

### 2026-11-25 — M10 claim

- **M10 · claim** — the owner's word above; assessment of unfinished work in the table; the core's readiness for civilisations that differ by rules was surveyed first (two read-only audits): the content model (templates with components, faction-scoped modifiers, data resources, phases as technologies) is ready for data differences; rules are match-wide (`MatchRules`) with no per-faction part, and the AI assumes houses, dropsites and builders — seams per rule are enough, no core rewrite.
- **Next:** the M10 spec once the claim merges.

## For the owner

1. **Merge the claim pull request** (`Claim: M10`): it adds this file only. Development starts after it.
2. **M9's hand-in, [PR #58](https://github.com/o-e-lytvynskyi/strategos/pull/58)**, waits for a maintainer review; M10 stacks on it, so its review comes first.
3. **ART-03 (M7) and D-03.** ART-03, the first civilisation's art pass, still names D-03, which now waits for M14. Options: (a) ART-03 becomes the default civilisation's art pass in D-04's vanilla-faithful look, without D-03 (recommended: the default civilisation is today's content); (b) ART-03 waits for D-03 in M14. Blocks nothing in M10; M7 is not claimed.
4. **The default civilisation's name** in game: "Settlers" (one lang key; spec §2.1). Veto or rename at any time.
5. **`main`'s records after M6** (a maintainer session): M6 was merged in [PR #55](https://github.com/o-e-lytvynskyi/strategos/pull/55) on 2026-11-15, but `docs/branches/o-e-lytvynskyi-m6-t2-t17.md` on `main` still says `ready for review` and STATE.md still shows M6 as not open. `claims.py check` fails on it, so the `records` gate is red on every pull request — this claim and #58 included — until the maintainer sets `merged 2026-11-15` and folds M6 into STATE.md.

## Decisions taken

- 2026-11-25 · The claim branch is cut from `origin/main` and dev/o-e-lytvynskyi/m9 is merged into it after the claim merges — the claim pull request must add the branch file only, and M10 needs M9's code (TERR-01, BLD-10, AI-08, PERF-16) — undo: wait for #58 to merge and take M9 through `origin/main` instead.

- 2026-11-25 · M10 reshaped on the owner's word (spec §1.5–1.6): CIV-05 added (the rule seams, size L); CIV-02 and ART-04 `deferred (M14)`, D-03 with them; CIV-01 gains roles, start kits, the roster rule, the default civilisation and a compatibility criterion; AI-09 plays the default to City and the probe to Town; QA-16 is the mirror per start slot while one civilisation is playable; PERF-17 is the M10 perf gate without a second civilisation; CIV-04 depends on CIV-05 — undo: restore the cards from `main` and drop CIV-05.
- 2026-11-25 · The foundation is a closed catalogue of rule variants behind eight seams, chosen per civilisation in data (ADR-0028), not data-only civilisations and not branches on the civilisation in systems — the owner's goal is distinctive gameplay later without a core rewrite — undo: ADR-0028 rejected, CIV-05 dropped.
- 2026-11-25 · The probe civilisation lives in the test fixtures only — it proves the seams without shipping content the owner has not designed — undo: move it into `src/main` data.
- 2026-11-25 · Ranks replace each other, they do not compound (spec §2.8) — the card asks for an explicit choice; replacing keeps balance readable — undo: compound in `RankModifiers`.
- 2026-11-25 · trial reviewer: pass — the spec review found twelve real points, each checked against the code before applying.
- 2026-11-25 · Three content GameTests removed the house to test a reload; the default civilisation's lint now refuses a set that loses a role's template, so they remove `quarry_large` (no role, no unlock) instead — the intent (a reload that drops an unused template) is kept; a data pack that removes a role's template while shipping the default is now refused, as ADR-0028 intends — undo: lint roles leniently.
- 2026-11-25 · The civilisation's name key is `strategos.civ.default`, the lang file's `strategos.<kind>.<name>` convention; phases reuse `strategos.phase.0–2` — undo: rename the key.
- 2026-11-25 · trial implementer: pass — M10 T2, green on the first hand-back; it reported the GameTest change it had to make instead of hiding it.
- 2026-11-25 · trial implementer: pass — M10 T1's moves and the restore check, green on the first hand-back, diff read.
- 2026-11-25 · `docs/design/roadmap-changes.md` is left as it is: its §1 records the draft-to-roadmap conversion of 2026-10-21 and its §8 holds scope-neutral refinements; M10's reshape is an owner-approved change, recorded under the ROADMAP's M10 table and in the spec's §1.5–1.6 — undo: add a dated block there.
- 2026-11-25 · The milestone is named "Civilisations foundation and depth"; the card file keeps its path — links stay valid — undo: rename back.

## Pitfalls

None yet.

## Review

Written by the validation (`strategos-maintain` §3): gate results, findings, recommendation.
