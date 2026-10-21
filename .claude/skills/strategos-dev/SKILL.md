---
name: strategos-dev
description: Autonomous work loop for the Strategos Minecraft mod. Use for any work session in this repository — «продолжай», «работай над strategos», «начинай M0», «следующая задача», implementing roadmap cards, writing a milestone spec, running spikes or a consent batch, fixing a red build. It orients from docs/STATE.md, picks the next ready task, implements it test-first, verifies it, records progress (spec checkboxes, card statuses, worklog, STATE), commits and pushes, and reports to the owner in Russian.
---

# Strategos: the autonomous work loop

The owner visits rarely. Every session must leave the repository so that the next session — or
the owner — can pick up from the repository alone:
- `main` is green;
- docs/STATE.md and docs/worklog.md are current;
- every finished step is committed and pushed.

Conventions (roles, statuses, evidence) are in
[docs/03-design-process.md](../../../docs/03-design-process.md), "Autonomous development". The
hard rules are in the project [CLAUDE.md](../../../CLAUDE.md). This skill is the procedure.

## 1. Orient (start of every session)

1. **Lock.**
   - If `build/session.lock` exists and is younger than 3 hours, another session is working.
     Stop and say so.
   - Otherwise write the current date and time into it. `build/` is git-ignored.
   - Remove the lock when the session ends.
2. **Git:**
   - `git status`: a dirty tree goes to §9;
   - `git pull --ff-only`;
   - if the branch is ahead of `origin`, push first: a commit from the last session never
     reached GitHub;
   - `git branch -a`: a `wip/*` branch goes to §9.
3. **Read:**
   - docs/STATE.md: "For the owner", "Since your last visit", "Now", "Blocked";
   - the newest entry of docs/worklog.md. Its date is the last session.
4. **Owner input since then:**
   - commits not made by you: `git log --since=<date>`;
   - their edits in docs/ROADMAP.md (statuses, decision answers, `> Owner, …` notes);
   - answers written into STATE.md;
   - what they say in this chat.

   If the owner speaks in this session, record "Owner last seen: <today>" in STATE.md, and
   start a fresh "Since your last visit" after your report.
5. **Docs check.** `python3 tools/roadmap.py check` must pass; if it fails, repair the docs
   first. Then run `python3 tools/roadmap.py summary`.
6. **Build check.** Once the build exists, run `./gradlew verifyFast`. Red means the first task
   is to fix it.

## 2. Pick the next task

**Gate.** Implementing a milestone starts only after the owner's go-ahead for that milestone,
for example «начинай M0», recorded in STATE.md "Now" as `<milestone> started <date>
(owner's go-ahead)`.
- **If the owner gives the go-ahead in this session:** record it, then pick.
- **If the go-ahead is still pending:** do only documentation and planning work that is ready,
  for example the next milestone's spec. Otherwise report and stop.
- **A plain «продолжай»** continues work that is already started. It is not a go-ahead for a
  new milestone.

Then take the first that applies:

1. **Red `main` or red CI**: fix it.
2. **Owner input to apply**: decisions, notes on cards, consent given («запускай консент-батч»),
   approvals. Apply them, update the affected docs, commit.
3. **The started milestone's spec**: the first unchecked task in its "Progress" list whose
   dependencies are checked and which is **ready**:
   - its cards are `approved`, `specified` or `in_progress` — never `proposed`, `changed`,
     `deferred` or `rejected`;
   - it waits for no open owner decision;
   - it needs no client window. A task that does goes to the consent batch in "For the owner",
     and you take the next task.
4. **No spec for the next milestone**: write it (§4). This is planning, so it needs no
   go-ahead.
5. **All tasks of the milestone are done**: close the milestone (§7).

If nothing is ready, stop and report what is blocked and on whom. Never invent work outside the
approved roadmap; ideas go to "For the owner" as proposals.

## 3. Do the task

1. **Mark it started.** Put "Now: <milestone> <task> in progress since <date>" in STATE.md before
   the first code edit. If the session dies, the next one knows what the dirty tree is.
2. **Read** the task's section in the spec, the cards it implements (acceptance criteria and
   test levels), and the architecture sections and ADRs it cites.
3. **Verify engine APIs** before using them, in the decompiled sources.
   - Before T1 there is no build; T1's build files are fully specified in the M0 spec.
   - After T1, unpack the sources jar once and grep it:

     ```
     ./gradlew :mod:createMinecraftArtifacts
     mkdir -p build/mc-src && unzip -q -o mod/build/moddev/artifacts/neoforge-<ver>-sources.jar -d build/mc-src
     ```

     Use exactly `-sources.jar`: a `*sources*` glob also matches `…-resources.jar`.
4. **Tests first,** at the lowest level that can express the behaviour: L1 before L3.
5. **Small steps:**
   - `./gradlew verifyFast` after each edit batch;
   - the task's verification command at the end;
   - `./gradlew verify` before every commit to `main`.

   If `verify` exceeds its 5-minute budget, record the time in the worklog and add a fix task.
   Never skip `verify`.
6. **When reality disagrees with the plan:**
   - **An API does not exist in 1.21.1, or the spec is wrong:** correct the spec, and record
     the deviation in its deviations section with the reason and the source path. If the
     architecture changes, write an ADR, or a dated note on a Proposed one.
   - **An acceptance criterion cannot be tested as written:**
     - A scope-neutral refinement (another test level, a measuring method, a wording) may be
       applied. Note it in docs/design/roadmap-changes.md "Refinements".
     - Anything that changes what the card delivers sets the card to `changed` and goes to
       "For the owner"; continue with other work.
   - **A test fails only sometimes:** rerun once to confirm, then treat it as a bug and fix it
     next. Never retry until green, never mark it flaky, never raise a threshold.
7. **Oversized tasks.** The spec targets 300–1,500 lines of change. Split a larger task in the
   spec first (`T5b` → `T5b.1`, `T5b.2`), commit the split, then do the first part.
8. **Clean-room.** If a search result or a link leads into GPL or all-rights-reserved mod code
   (Reign of Nether, MineColonies, Recruits, 0 A.D.), close it without reading it.
9. **Workflows sparingly.** Multi-agent workflows burn the owner's usage quota; limits have cut
   several short. Work inline. Use a small review workflow only for risky changes: an
   architecture change, a spike conclusion, a milestone close.

## 4. Write a milestone spec

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
  - **a section headed `### <n> Progress`** with `[ ] Tn` checkboxes, which `tools/roadmap.py`
    counts.
- **Afterwards:**
  - the milestone's `approved` cards become `specified`, in ROADMAP.md and in the card files;
  - run `tools/roadmap.py check`;
  - commit `Mx: specify <milestone>`;
  - add "For the owner: Mx is specified and ready; say «начинай Mx»".
- **If the spec shows that a card must change scope:** set it to `changed`, add a quoted note
  under its milestone table in ROADMAP.md, and put it in "For the owner". Specify the rest.

## 5. Record progress

Do this after each task, before committing.

1. **Spec.** Tick the task in "Progress": `[x] T3 (2026-11-02)`. When T1 starts, record the M0
   tripwire in STATE "Now": `T1 started <date>; walking skeleton due <date + 14 days>`.
2. **Cards** (ROADMAP.md is the source of truth; mirror every change in the card file):
   - a card whose first task starts goes to `in_progress`;
   - a card whose acceptance criteria all pass at their levels goes to `done`, plus
     `- **Done:** <date> — <evidence>` in the card: test names, report or spike paths, the spec
     task;
   - `accepted` is set only after the owner has played the milestone demo;
   - run `python3 tools/roadmap.py check`.
3. **Measurements.** Spike numbers go to `docs/spikes/Sx-*.md`. The related Proposed ADR gets a
   dated note, or becomes Accepted, in the same commit. Screenshots the owner should see go to
   `docs/media/<milestone>/`, small PNGs only.
4. **docs/worklog.md.** Append to today's entry, newest first:
   - the tasks and cards finished;
   - the results with numbers: tests run, `verify` time, performance figures;
   - the problems met;
   - the next task.
5. **docs/STATE.md:**
   - "Now": the milestone, the last finished task, the next task, any WIP branch;
   - "Since your last visit": one line per finished task or milestone;
   - "For the owner", "Blocked", and "Last update".
6. **Knowledge.**
   - General Minecraft or NeoForge pitfalls go to the pet-projects knowledge base,
     `../../.claude/kb/minecraft-mod.md`, a separate repository. Commit and push it there.
   - Project facts go to the spec, the architecture or an ADR.

## 6. Commit and push

- **Stage only the files you changed**, by path, never `git add -A`. If `git status` shows edits
  you did not make (the owner may be editing in their IDE), leave them unstaged and mention them
  in STATE.
- **Scan for secrets before committing:** `git diff --cached` must contain no tokens, passwords
  or keys. The project needs none.
- **Message:** `Mx Tn: <imperative summary>`, in English, with no assistant traces. One commit per
  task; long tasks get one commit per green step.
- **Push** after every commit; the owner allows it. On a rejected push:
  - run `git pull --rebase`;
  - resolve conflicts keeping the owner's statuses, decisions and notes;
  - push again;
  - never `--force` on `main`.
- **Never push a red `main`:** `verify` passes first.
- **Unfinished work** (the session must end mid-task): commit it to `wip/Mx-Tn`, push the branch,
  and name it in STATE "Now".

## 7. Close a milestone

1. Check the exit conditions: every task is ticked, every card is `done`, and the Done gate
   passes. Gates are cumulative: earlier milestones' automated gates must still pass.
2. **Health check:**
   - classes over 800 lines;
   - ArchUnit exceptions;
   - TODOs;
   - code that disagrees with the architecture or the ADRs;
   - growth in `verify` time;
   - CI minutes used this month on GitHub Free: 2,000 a month for private repositories. Above
     80 %, move CI to nightly.

   Fixes become the first tasks of the next milestone.
3. Write "Results" in the spec: the numbers, the deviations, the follow-ups.
4. Update STATE.md: the milestone is Done. In "For the owner", add:
   - the demo with exact commands (for example `scripts/playtest.sh`, then the steps);
   - the consent batch;
   - baselines and goldens to approve;
   - «начинай M(x+1)», if its spec is ready.
5. Record planned against actual effort. After M0, re-estimate docs/01-feasibility.md.
6. Cards become `accepted` only when the owner has played the demo. Tags and releases are the
   owner's call.

## 8. Stop conditions

Stop the current line of work, record it in STATE "Blocked" and the worklog, and report when:
- **nothing is ready** — everything is blocked on the owner;
- **a task fails three times.** The same task failed verification after three honest attempts.
  Write the analysis, take another ready task if there is one, otherwise stop;
- **the M0 tripwire fires.** Apply the spec's simplification order, record it, and continue;
- **a performance budget (ADR-0019) goes red.** No new features until it is fixed; the fix is
  the next task;
- **the work would break a hard rule:**
  - clean-room, or a threshold raised to get green;
  - scope beyond approved cards, or a new runtime dependency;
  - a client window without consent;
  - anything public.

  Stop that line, ask in "For the owner", and take other ready work;
- **the environment fails:**
  - a full disk (`df -h`);
  - no network;
  - a broken Gradle cache that `./gradlew --stop` and one `--refresh-dependencies` run do not
    fix.

  Record it and stop. Never delete caches outside the project;
- **usage or context is running low.** Finish the current step, commit or push a WIP branch,
  update STATE and the worklog, remove the lock, stop.

## 9. Recovery

- **Dirty tree, and STATE "Now" names a task in progress:** that is the interrupted task.
  1. Read the diff.
  2. Run `./gradlew verifyFast`.
  3. Finish the task, or revert your own half-done edits.
- **Dirty tree otherwise:** these may be the owner's edits. Never discard them. If they are
  plainly docs answers (statuses, decisions), apply them; otherwise leave them and ask.
- **A `wip/Mx-Tn` branch:**
  1. Check it out and rebase it onto `main`.
  2. Finish the task and get `verify` green.
  3. Merge it fast-forward, then delete the branch locally and on `origin`.
- **`main` is red:** `git bisect run ./gradlew verifyFast` finds the breaking commit. Fix it first.
- **A lock older than 3 hours:** a dead session left it. Replace it and continue.

## 10. The owner's queue ("For the owner" in STATE.md)

Number each item. Each says what is needed, why, the options with your recommendation, and what
is blocked until the owner answers. Work that does not depend on an item continues. Never decide
an open owner decision yourself.

| Kind | Content |
|---|---|
| Go-ahead | «начинай Mx» for a specified milestone |
| Decision | Options and a recommendation |
| Consent batch | The client-window runs, their duration, and the phrase «запускай консент-батч» (the owner then steps away from the keyboard) |
| Demo | Exact commands and steps; the owner's verdict goes into the milestone's Acceptance |
| Approval | Performance baselines, goldens, ADR thresholds, `changed` cards |
| Proposal | Ideas that would add scope |

## 11. Report (end of session, in Russian)

Keep the report short:
- **Done:** tasks and cards, with numbers.
- **The state of `main`:** green or red, plus CI.
- **What is next.**
- **What needs the owner:** the numbered "For the owner" items.
- **Links:** STATE.md, today's worklog entry, the commits.

Then remove `build/session.lock`.
