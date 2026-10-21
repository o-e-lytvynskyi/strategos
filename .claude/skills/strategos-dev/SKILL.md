---
name: strategos-dev
description: Autonomous work loop for the Strategos Minecraft mod. Use for any work session in this repository — «продолжай», «работай над strategos», «следующая задача», implementing roadmap cards, writing a milestone spec, running spikes, fixing a red build. It orients from docs/STATE.md, picks the next ready task, implements it test-first, verifies it, records progress (spec checkboxes, card statuses, worklog, STATE), commits and pushes, and reports to the owner in Russian.
---

# Strategos: the autonomous work loop

The owner visits rarely. Every session must leave the repository in a state that the next
session — or the owner — can pick up from the repository alone:
- `main` is green;
- docs/STATE.md and docs/worklog.md are current;
- every finished step is committed and pushed.

The rules, roles and statuses are in [docs/03-design-process.md](../../../docs/03-design-process.md).
The hard rules are in the project [CLAUDE.md](../../../CLAUDE.md). This skill is the procedure.

## 1. Orient (start of every session)

1. Check the working tree:
   - `git status` must be clean, otherwise see §9 Recovery;
   - `git pull --ff-only`;
   - `git branch -a`: a `wip/*` branch means a task was interrupted (§9).
2. Read docs/STATE.md: "For the owner", "Now", "Blocked". Read the newest entry of docs/worklog.md.
3. Collect what the owner changed since the last worklog entry:
   - `git log --format='%h %an %ad %s' --since=<that date>`;
   - their edits in docs/ROADMAP.md (statuses, decision answers, `> Owner, …` notes);
   - answers written into STATE.md;
   - anything they said in chat.
4. Run `python3 tools/roadmap.py check` (it must pass; fix the docs first if it fails) and
   `python3 tools/roadmap.py summary`.
5. Once the build exists: `./gradlew verifyFast`. If it is red, fixing it is the first task.

## 2. Pick the next task

Take the first that applies:

1. **Red `main` or red CI**: fix it.
2. **Owner input to apply**: decisions, notes on cards, a consent batch, approvals. Apply them,
   update the affected docs, commit.
3. **The current milestone's spec**: the first unchecked task in its "Progress" list whose
   dependencies are checked and which is **ready**:
   - its cards are `approved`, `specified` or `in_progress` — never `proposed`, `changed`,
     `deferred` or `rejected`;
   - it waits for no open owner decision;
   - it needs no client window. A task that does goes to the consent batch in "For the owner",
     and you take the next task.
4. **No spec for the current milestone**: write it (§4).
5. **All tasks of the milestone are done**: close the milestone (§7), then continue with the
   next one.

If nothing is ready, stop and report what is blocked and on whom. Never invent work outside the
approved roadmap; ideas go to "For the owner" as proposals.

## 3. Do the task

1. **Read before you start:**
   - the task's section in the milestone spec;
   - the cards it implements, including their acceptance criteria and test levels;
   - the architecture sections and ADRs it cites.
2. **Verify every Minecraft or NeoForge API before using it.** Grep the decompiled sources:
   `mod/build/moddev/artifacts/neoforge-<version>-sources.jar`, the `-sources.jar` exactly.
   Unpack it once into `build/mc-src/`, which git ignores.
3. **Write tests first, at the lowest level that can express the behaviour:** L1 before L3,
   pure maths before screenshots.
4. **Iterate in small steps:**
   - run `./gradlew verifyFast` after each edit batch;
   - run the task's own verification command at the end;
   - run `./gradlew verify` before every commit to `main`.
5. **Split oversized tasks.** If a task cannot fit in one session (the spec targets
   300–1,500 lines of change), split it in the spec first (`T5b` → `T5b.1`, `T5b.2`), commit
   the split, then do the first part.
6. **Use workflows sparingly.** Big multi-agent workflows burn the owner's usage quota and have
   been cut short by limits. Prefer doing the work inline. Use a small review workflow only for
   risky changes: an architecture change, a spike conclusion, or a milestone close.

## 4. Write a milestone spec

Write a spec when the next milestone with approved cards has none.

- **Inputs:**
  - the milestone's approved cards in docs/roadmap/Mx-*.md;
  - docs/04-architecture.md and the ADRs;
  - the previous milestone's spec and results.

  Use docs/milestones/M0-foundation.md as the template.
- **Output: docs/milestones/Mx-<slug>.md**, with these sections:
  - goal and demo;
  - scope, with every deviation from the cards (deviations may move or clarify scope, never
    add it);
  - the design down to files and classes;
  - tests per level;
  - tasks T1..Tn, each with dependencies, a verification command and a size;
  - an acceptance checklist;
  - risks with fallbacks;
  - owner inputs and the consent batch;
  - card → task traceability;
  - a "Progress" section with `- [ ] Tn` checkboxes.
- **Afterwards:**
  - set the milestone's `approved` cards to `specified`, both in ROADMAP.md and in the card
    files;
  - run `tools/roadmap.py check`;
  - commit `Mx: specify <milestone>`.
- **If the spec shows that a card must change scope:** set that card to `changed`, add a quoted
  note under its milestone table in ROADMAP.md, and put it in "For the owner". Specify the rest.

## 5. Record progress

Do this after each task, before committing.

1. **Spec.** Tick the task in "Progress": `[x] T3 (2026-11-02)`.
2. **Cards** (ROADMAP.md is the source of truth; mirror every change in the card file):
   - a card whose first task starts goes to `in_progress`;
   - a card whose acceptance criteria all pass at their stated levels goes to `done`;
   - add `- **Done:** <date> — <evidence>` to the card: test names, report or spike paths, the
     spec task;
   - `accepted` is set only after the owner has played the milestone demo;
   - run `python3 tools/roadmap.py check`.
3. **Measurements.** Spike numbers go to `docs/spikes/Sx-*.md`, and the related Proposed ADR gets
   a dated note or becomes Accepted, in the same commit. Never raise a threshold yourself.
4. **docs/worklog.md.** Append to today's entry: the tasks and cards finished, results with
   numbers (tests run, `verify` time, performance figures), problems, and the next task.
5. **docs/STATE.md.** Update "Now" (milestone, last finished task, next task, WIP branch),
   "For the owner", "Blocked" and "Last update".
6. **Knowledge.**
   - General Minecraft or NeoForge pitfalls go to the pet-projects knowledge base
     (`../../.claude/kb/minecraft-mod.md`, a separate repository) when the session runs inside
     pet_projects.
   - Project facts go to the spec, the architecture or an ADR.

## 6. Commit and push

- **Message:** `Mx Tn: <imperative summary>`, in English, with no assistant traces. One commit per
  task; long tasks get one commit per green step.
- **Push** to the private origin after every commit. The owner allows this without asking.
- **Never push a red `main`:** `verify` must pass first.
- **Unfinished work** (the session must end mid-task): commit it to a branch `wip/Mx-Tn`, push
  the branch, and name it in STATE "Now".
- **Knowledge base:** pet-projects knowledge-base changes are committed and pushed in their own
  repository.

## 7. Close a milestone

1. Check the exit conditions: every task is ticked, every card is `done`, and the milestone's
   Done gate passes. Gates are cumulative: earlier milestones' automated gates still pass.
2. Write "Results" in the spec: the numbers, the deviations, and the follow-ups.
3. Update STATE.md: the milestone is Done. In "For the owner", put:
   - the demo script to play (Acceptance);
   - the consent batch;
   - any baselines or goldens to approve.
4. Record planned against actual effort in STATE. After M0, re-estimate 01-feasibility.md.
5. Move cards to `accepted` only when the owner has played the demo.
6. Tags and releases are the owner's call.

## 8. Stop conditions

Stop the current line of work, record it, and report when:
- **nothing is ready** — everything is blocked on the owner;
- **a task fails three times.** The same task failed verification after three honest attempts:
  - write the analysis to STATE "Blocked" and to the worklog;
  - take another ready task if there is one, otherwise stop;
- **the M0 tripwire fires** (the walking skeleton is not end to end two weeks after T1 started).
  Apply the spec's simplification order, record it, and continue;
- **a performance budget (ADR-0019) goes red.** No new features until it is fixed; the fix is
  the next task;
- **the work would break a hard rule:**
  - clean-room, or a threshold raised to get green;
  - scope beyond the approved cards, or a new runtime dependency;
  - a client window without consent;
  - anything public.

  Stop that line, ask in "For the owner", and take other ready work;
- **usage or context is running low.** Finish the current step, commit or push a WIP branch,
  update STATE and the worklog, then stop.

## 9. Recovery

- **Dirty tree at the start.**
  - If STATE "Now" names the interrupted task, continue it.
  - Otherwise inspect the changes: never discard the owner's edits. If it stays unclear, ask.
- **A `wip/Mx-Tn` branch:**
  - check it out and rebase it onto `main`;
  - finish the task and get `verify` green;
  - merge it fast-forward and delete the branch, locally and remotely.
- **`main` is red:** find the breaking commit (`git bisect run ./gradlew verifyFast`) and fix it
  before anything else.
- **`tools/roadmap.py check` fails:** repair the docs first; ROADMAP.md wins over the card files.

## 10. The owner's queue ("For the owner" in STATE.md)

Number each item. Each item says what is needed, why, the options and your recommendation, and
what is blocked until the owner answers. The kinds of item:

| Kind | Content |
|---|---|
| Decision | Options and a recommendation |
| Consent batch | The client-window runs, their expected duration, and the phrase that starts them ("скажи «запускай консент-батч» и отойди от клавиатуры на ~N минут") |
| Demo | The steps to play; the result goes into the milestone's Acceptance |
| Approval | Performance baselines, goldens, ADR thresholds, `changed` cards |
| Proposal | Ideas that would add scope |

The owner answers in chat or by editing STATE.md or ROADMAP.md. Apply the answers at the next
Orient.

## 11. Report (end of session, in Russian)

Keep the report short:
- **What was done:** tasks and cards, with numbers.
- **The state of `main`:** green or red, plus CI.
- **What is next.**
- **What needs the owner:** the numbered "For the owner" items.
- **Links:** STATE.md, today's worklog entry, the commits.
