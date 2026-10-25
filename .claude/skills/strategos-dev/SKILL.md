---
name: strategos-dev
description: Developer work loop for the Strategos Minecraft mod, for any developer and any tool (the owner's own agent sessions included). Use for any development session in this repository — «продолжай», «работай над strategos», «возьми задачу», «следующая задача», implementing roadmap cards, writing a milestone spec, running spikes or a consent batch, fixing a red build. It orients, claims or continues work on the developer's own dev/* branch, implements it test-first, verifies it, records progress in the branch file, commits and pushes the branch, hands finished work in for review, repeats until a stop condition, and reports.
---

# Strategos: the developer loop

Every developer — a person with an agent, or an agent session the owner starts — works the same
way: on their own branch, with their own records, handing work in for review. Nobody but the
maintainer writes to `main` (the `strategos-maintain` skill). Every session must leave the
branch so that the next session — or the reviewer — can pick up from the repository alone:
- the branch is pushed, and green at its last task commit;
- its branch file is current;
- every finished step is committed.

Paths are relative to the repository root. The rules — roles, hard rules, autonomy defaults,
client windows, context economy, claims — are in AGENTS.md; delegation is in docs/agents.md;
the conventions (statuses, evidence) are in docs/03-design-process.md. This skill is the
procedure, for every tool: an agent without skills reads this file directly.

Commands are spelled the way the project allowlist (`.claude/settings.json`) expects. Spelled
differently — an absolute path instead of `~`, a `cd` in front — they may stop an unattended
session at a prompt.

"Your branch" is `dev/<developer>/<work>`; "your branch file" is
`docs/branches/<developer>-<work>.md`. `<developer>` is a short lowercase handle, the same on
every claim (`claude-owner`, `alex`, …).

## 1. Orient (start of every session)

1. **Lock.** The lock file is `.session.lock` in the working copy's root; git ignores it.
   - If it holds a time less than 3 hours old, another session works in this copy. Stop and say so.
   - Otherwise write the time to it: `date -u +%Y-%m-%dT%H:%M:%SZ > .session.lock`.
   - Rewrite it after every commit. Delete it (`rm -f .session.lock`) before the final report.
2. **Git:**
   - `git status`: a dirty tree goes to §9;
   - `python3 tools/claims.py --fetch list`: the registry docs/claims.md on `main` — yours and
     everyone else's — and `gh pr list --author @me` for your open pull requests;
   - on your branch: if `origin/main` moved since your last merge of it, `git merge origin/main`
     and re-run `./gradlew verifyFast`; if the branch is ahead of `origin`, push first.
3. **Read, by heading** (`grep -n '^## '`, then a range — never the whole file):
   - docs/STATE.md: "Open for claims", "Standing restrictions", "Blocked", and the
     "For the owner" items that name your branch (answers to your questions land there);
   - your branch file: "Now", "For the owner", the newest log entry.
4. **The owner's input** for you: answers in STATE or your branch file, notes on cards in
   docs/ROADMAP.md, the review's findings in your branch file's "Review", whatever is said in
   this chat. Apply them first (§2).
5. **Docs check.** `python3 tools/roadmap.py check` must pass; if it fails on your branch, repair
   it first. Then `python3 tools/roadmap.py summary`.
6. **Build check.** `./gradlew verifyFast` on your branch; red is the first thing to fix. CI does
   not run on `dev/*` pushes; it runs on your pull request once you hand in (§12), and on `main`.
   Read `main`'s newest run (`gh run list --branch main --limit 3`): red `main` is claimable
   work of the highest priority (§2).

## 2. Pick the next work

Take the first that applies:

1. **The owner's input or review findings** for your branch: apply them, record them, commit.
2. **Red `main` or red CI on `main`**, if nobody has claimed it: claim `fix-main` (§2a) and fix it.
3. **Your open claim's next task:** the first unchecked task in the spec's "Progress" whose
   dependencies are checked and which is **ready**:
   - its cards are `approved`, `approved-foundation`, `specified` or `in_progress` — never
     `proposed`, `changed`, `deferred` or `rejected`;
   - it waits for no open owner decision;
   - a client window is no obstacle unless "Standing restrictions" forbids windows: then the
     window part goes to your branch file's "For the owner" as a consent batch, and you take the
     next task. Window runs follow AGENTS.md "Client windows".

   A card's "Depends on" orders the plan. Inside a specified milestone, the spec's task
   dependencies decide what is ready.
4. **Your claim is done:** close it (§7 for a milestone), hand it in (§12), and go on to 5.
5. **A new claim** (§2a): the first ready, unclaimed, unreserved work — tasks of a milestone in
   STATE "Open for claims", in the spec's order; otherwise the next milestone's spec if none is
   written and none is claimed (planning needs no opening).

If nothing is ready, stop and report what is blocked and on whom. Never invent work outside the
approved roadmap; ideas go to "For the owner" as proposals.

## 2a. Claim work (before any development, by pull request)

0. **Assess what the work needs from unfinished milestones** — before claiming a milestone or
   tasks. For each card and task: its "Depends on", and the classes, data and behaviour the spec's
   design uses. Anything delivered by a milestone that is not yet merged into `main` (in progress,
   handed in, or not started) is a dependency on unfinished work. Write the result into the branch
   file's "Depends on unfinished work": per item, the milestone, what is needed, and the plan —
   *independent* (build it without the dependency), *against a contract* (code to an interface or
   a core seam the other milestone will fill; a fake in the tests), or *after* (cannot start until
   that milestone lands). Order the claimed tasks so independent work comes first; claim only what
   has a plan. Work whose plan is *after* is not claimed yet.
1. `python3 tools/claims.py --fetch list` and `gh pr list --search claims.md`: no `assigned` row and
   no open claim pull request covers the work, and no `reserved` row keeps it for someone else (a
   reservation in your name is yours to claim). Work `assigned` to someone else is never taken
   without their explicit approval (AGENTS.md "Developers and claims").
2. **The base:** `origin/main`, or your own branch if the work depends on it (the header's
   `Base`).
3. `git switch -c dev/<developer>/<work> <base>`; `<work>` is short: `m5`, `m5-t1-t6`, `m6-spec`,
   `fix-main`.
4. The claim commit, and nothing else in it: your row in docs/claims.md (`assigned`, today, your
   branch; or your `reserved` row turned `assigned`), your branch file from the template in
   docs/branches/README.md (`Status: claimed`, "Now" filled in). `python3 tools/claims.py check
   --base origin/main` passes. Commit `Mx: claim <work>` (or `Docs: claim <work>`), push with `git
   push -u origin dev/<developer>/<work>`.
5. Open the claim pull request: `gh pr create --base main --head dev/<developer>/<work> --title
   "Claim: <work>" --body "<what, the base, the tasks>"`.
6. **Wait for the merge before developing.** Meanwhile go back to §2 for other work you already
   hold, or prepare without code (read the spec, list the APIs to verify). If nothing else is
   yours, stop (§8: nothing ready) and say the claim waits for a maintainer.
7. Merged: `git merge origin/main` into your branch, `Status: in progress` at the first task, go
   to §3. Rejected (an overlap merged first): delete nothing, set your branch file to `released`,
   and pick again.

## 3. Do the task

1. **Mark it started** in your branch file's "Now" (`In progress: Mx Tn since <date>`) before the
   first code edit; set the header's `Status: in progress` on the first task. If the session
   dies, the next one knows what the dirty tree is.
2. **Read** the task's section in the spec, the cards it implements (acceptance criteria and
   test levels), and the architecture sections and ADRs it cites — by range — and the
   docs/kb/ file of the area you touch.
3. **Verify engine APIs before using them** (or send the names to `api-lookup`).
   - **Minecraft and NeoForge:** after the first build, unpack the sources jar once, and again
     after any `clean`, then grep it:

     ```
     ./gradlew :mod:createMinecraftArtifacts
     mkdir -p build/mc-src && unzip -q -o mod/build/moddev/artifacts/neoforge-<ver>-sources.jar -d build/mc-src
     ```

     Use exactly `-sources.jar`: a `*sources*` glob also matches `…-resources.jar`.
   - **FML, the event bus and ModDevGradle** are not in that jar. Check their names with
     `javap` on the cached jars:

     ```
     find ~/.gradle/caches/modules-2/files-2.1 \( -name 'loader-4.0.45.jar' -o -name 'bus-8.0.5.jar' -o -name 'moddev-gradle-2.0.148.jar' \)
     javap -cp <jar> net.neoforged.fml.ModList
     ```
4. **Tests first,** at the lowest level that can express the behaviour: L1 before L3.
5. **Small steps:**
   - `./gradlew verifyFast` after each edit batch;
   - the task's verification command at the end;
   - `./gradlew verify` before every task commit;
   - `./gradlew verify verifySlow verifySoak` before committing a change that touches movement,
     pathing, orders or the tick: the slow map tests (`maptests.*` on the proving ground) and the
     soak are not in `verify` (M1 T21's avoidance passed `verify` and failed
     `maptests.fifty_units_one_by_one` on CI);
   - `./gradlew :core:jmh :core:jmhCompare` before closing a task that changes the core's step,
     its codecs or a `@HotPath` kernel (M1 grew `MovementBench.step600` by 58 % unnoticed). A
     red `jmhCompare` is recorded the same day: the cause, and a proposed baseline
     (`perf/baseline-<machine>.proposed.json`) when the growth is the features' own cost — a
     "Decisions taken" line if you recommend it;
   - `./gradlew :core:balanceSheet` after changing a template number, a relationship or a
     provenance note: `BalanceSheetTest` fails while `docs/balance/` is stale. A new number needs
     its provenance note in `strategos/balance/provenance.json` in the same change;
   - `./gradlew :mod:test --tests '*BlueprintFilesTest*' -Dstrategos.blueprints.update=true` after
     changing a placeholder blueprint in `PlaceholderBlueprints`: it rewrites the shipped `.nbt`
     files, and the template set's pinned SHA in `TemplateSetTest` changes with it.

   If `verify` exceeds its 5-minute budget, record the time and put the choice in "For the
   owner": accept the time, or move a run to `verifySlow`, which changes the Done gate. Never
   move a run out of `verify` yourself.
6. **When reality disagrees with the plan:**
   - **An API does not exist in 1.21.1, or the spec is wrong:** correct the spec — a row in its
     table of implementation findings (§1.6 D, next free number); a cut or a deferral goes to
     §1.5. If the architecture changes, write an ADR, or a dated note on a Proposed one.
   - **An acceptance criterion cannot be tested as written:** a scope-neutral refinement
     (another test level, a measuring method, a wording) may be applied and is recorded in
     docs/design/roadmap-changes.md §8 "Refinements", in a dated block. Anything that changes
     what the card delivers sets the card to `changed` and goes to "For the owner"; continue with
     other work.
   - **A test fails only sometimes:** rerun once to confirm, then treat it as a bug and fix it
     next. Never retry until green, never mark it flaky, never raise a threshold.
7. **Oversized tasks.** The spec targets 300–1,500 lines of change. Split a larger task in the
   spec first (`T5b` → `T5b.1`, `T5b.2`), commit the split, then do the first part.
8. **Unfinished dependencies in code.** Where a task builds against something an unfinished
   milestone will deliver, mark the spot with `AFTER-M<n>: <what to finish or validate once M<n>
   lands>` in a comment (code) or a line (docs, tests). It is not a `TODO`: it names the milestone
   it waits for, `python3 tools/claims.py after` lists them, and each one is also a line in the
   branch file's "Depends on unfinished work". Keep the marked part small: a seam, a fake, a
   disabled assertion with its reason — never a hidden behaviour change. Prefer independent
   functionality wherever the design allows it.
9. **Clean-room.** If a search result or a link leads into GPL or all-rights-reserved mod code
   (Reign of Nether, MineColonies, Recruits, 0 A.D.), close it without reading it.
10. **Delegate the routine** (docs/agents.md; the roster is a demo and may change). The
   orchestrator keeps steps 2, 6 and 7 and the design; it sends:
   - engine-name checks of step 3 to `api-lookup`, several names in one request;
   - code a brief can describe completely to `implementer` (the brief format is in
     docs/agents.md); small edits stay inline;
   - every Gradle run of step 5 whose full log is not needed to `runner`, in the background
     when long;
   - the records of §5 to `recorder`, with every fact listed;
   - risky changes (architecture, `@HotPath`, payloads, over ~500 lines) to `reviewer` before
     the commit.

   Read every worker's diff before committing it. Log each use as a trial in your branch file's
   "Decisions taken" (`trial <role>: pass | fail — <why>`, criteria in docs/agents.md "Role
   status"); improving a role, adding one or taking one out of demo follows the `new-agent`
   skill, on your branch, by pull request. Multi-agent workflows stay rare: they burn the usage
   quota. A standing «без субагентов» means all of this is done inline.

## 4. Write a milestone spec (a `<m>-spec` claim)

- **Inputs:** the milestone's approved cards in docs/roadmap/Mx-*.md; docs/04-architecture.md and
  the ADRs; the previous milestone's spec and results. Use docs/milestones/M0-foundation.md as
  the template.
- **Output: docs/milestones/Mx-<slug>.md**, with these sections: goal and demo; scope, and a
  differences table for every deviation from the cards (deviations may move or clarify scope,
  never add it); the design down to files and classes; tests per level; tasks T1..Tn, each with
  dependencies, a verification command and a size; an acceptance checklist; risks with
  fallbacks; owner inputs and the consent batch; card → task traceability; **a section headed
  `### <n> Progress`** with `[ ] Tn` checkboxes, in the order tasks may start, which
  `tools/roadmap.py` counts.
- **Afterwards:** the milestone's `approved` cards become `specified`, in ROADMAP.md and the card
  files; `tools/roadmap.py check`; commit `Mx: specify <milestone>`; your branch file's "For the
  owner": "Mx is specified; open it for claims"; hand in (§12).
- **If the spec shows that a card must change scope:** set it to `changed`, add a quoted note
  under its milestone table in ROADMAP.md, and put it in "For the owner". Specify the rest.

## 5. Record progress

After each task, before committing; `recorder` may type it from the facts you give it. You never
edit STATE.md or worklog.md: the maintainer folds your branch file into them at the merge.

1. **Spec.** Tick the task in "Progress": `[x] T3 (2026-11-02)`.
2. **Cards** (ROADMAP.md is the source of truth; mirror every change in the card file):
   - a card whose first task starts goes to `in_progress`;
   - a card whose acceptance criteria all pass at their levels goes to `done`, plus
     `- **Done:** <date> — <evidence>` in the card: test names, report or spike paths, the spec
     task;
   - `accepted` is set only by the maintainer on the owner's word;
   - run `python3 tools/roadmap.py check`.
3. **Measurements.** Spike numbers go to `docs/spikes/Sx-*.md`. The related Proposed ADR gets a
   dated note, or becomes Accepted, in the same commit. Screenshots worth seeing go to
   `docs/media/<milestone>/`, small PNGs only.
4. **Your branch file:**
   - "Now" in the template's shape;
   - "Log": the session's entry at the top, `### <date> — <milestone> <tasks>`, one bullet per
     finished task, newest first, then `- **Next:** …`; each bullet gives the cards, the results
     with numbers (tests run, `verify` time, performance figures), problems met. Example:
     `- **M0 T1 · Build skeleton** — QA-01, QA-03 in progress; verifyFast 41 s, :mod:test 3
     tests; the wrapper checksum matched.`
   - "Decisions taken", "For the owner", "Pitfalls" as they arise.
5. **Knowledge.** General Minecraft, NeoForge, toolchain or simulation pitfalls go to the file of
   their area in docs/kb/ (index: docs/kb/README.md), in the same commit; list the entry in your
   branch file's "Pitfalls". Project facts go to the spec, the architecture or an ADR.

## 6. Commit, push, repeat

- **Stage only the files you changed**, by path, never `git add -A`. If `git status` shows edits
  you did not make, leave them unstaged and mention them in your branch file.
- **Scan for secrets before committing:** `git diff --cached` must contain no tokens, passwords
  or keys. The project needs none.
- **Message prefixes:** `Mx Tn: <imperative summary>` for a task; `Mx: …` for milestone work
  (claim, spec, close); `Docs: …` for project-wide documents. English, no traces of the agent or
  its tool. One commit per task; long tasks get one commit per green step.
- **Push your branch** after every commit. Never push `main`; never `--force`. On a rejected push
  (someone else pushed to your branch, e.g. a review fix): `git pull --no-rebase`, resolve
  keeping their fix, push again.
- **Never commit a red task:** the gate of §3.5 passes first.
- **Unfinished work** (the session must end mid-task): commit it as `WIP Mx Tn: <state>`, push,
  and say so in "Now". The next session finishes it and the task's own commit follows.
- **Then go back to §2.** Tasks are documented (§5), not reported; reports follow §11: after
  every milestone, and when a stop condition (§8) holds.

## 7. Close a milestone (on your branch)

1. **Exit conditions:** every task is ticked; every card is `done`, except cards whose open
   criteria the spec says never block Done (consent and CI items), which stay `in_progress` and
   are named in "For the owner"; the Done gate passes. Gates are cumulative: earlier milestones'
   automated gates must still pass. `AFTER-M<n>` markers: those whose milestone is merged by now
   are resolved first (merge `origin/main`, finish or validate, remove the marker); the rest are
   listed in the milestone report and stay in the branch file, so the hand-in says exactly what
   waits for which milestone.
2. **Health check:** classes over 800 lines; ArchUnit exceptions; TODOs; code that disagrees with
   the architecture or the ADRs; growth in `verify` time. Fixes become the first tasks of the
   next milestone's spec.
3. Write "Results" in the spec: the numbers, the deviations, the follow-ups, planned against
   actual effort.
4. Your branch file's "For the owner": the demo with exact commands (`scripts/playtest.sh`, then
   the steps, and the new demo chapters as `-Pdemo=m<x>`, §7a); the consent batch, if windows
   were restricted; baselines and goldens you took as "Decisions taken"; «open M(x+1)», if its
   spec is ready.
5. Hand in (§12), with the milestone report (§11.1) as the pull request's body.

## 7a. Show the demo (someone asks to see the mechanics)

The demo is a scene of the L4 harness, `demo`, one chapter per mechanic
(`mod/src/devtools/java/dev/strategos/devtools/demo/`); it opens a game window, and the request
to see it is the consent.

1. **Pick:** `-Pdemo=all`, `new` (what the viewer has not seen at its version), ids or groups
   (`m4`, `economy`, `crowd,melee`); `-Pdemo.skip=` takes ids or groups out. The chapter list:
   `DemoCatalog.CHAPTERS`, or a run with `-Pdemo=list`.
2. **Run** in the background, one Gradle build at a time:
   `./gradlew :mod:runScenario -Pscenario=demo -Pdemo=<pick> -Pscenario.timeoutSeconds=3600 > <scratchpad>/demo.log 2>&1`.
3. **Narrate:** follow the log's `DEMO start`, `DEMO say` and `DEMO end` lines and tell the
   viewer each in a line of their language: what the chapter shows and what to look at. Only
   the lines that change what is on the screen.
4. **After:** `build/scenario/out/result.json` lists the chapters shown and failed. A failed
   chapter is a bug in the chapter or in the mod: say which, and fix it like any other.
5. **Keep it current:** a milestone's close adds a chapter for each new visible mechanic (a class
   implementing `DemoChapter`, a line in `DemoCatalog.CHAPTERS`) and bumps the version of a
   chapter whose scene changed. Tasks between closes do not touch the demo.

## 8. Stop conditions

Stop the current line of work, record it in your branch file ("Now", the log, "For the owner"),
and report when:
- **nothing is ready** — everything is blocked on the owner or claimed by others;
- **a task fails three times.** The same task failed verification after three honest attempts.
  Write the analysis, take another ready task if there is one, otherwise stop;
- **a performance budget (ADR-0019) goes red.** No new features until it is fixed; the fix is
  the next task;
- **the work would break a hard rule:** clean-room, or a threshold raised to get green; scope
  beyond approved cards, or a runtime dependency whose licence is not MIT/Apache-2.0
  compatible; a client window while "Standing restrictions" forbids it; anything public. Stop
  that line, ask in "For the owner", and take other ready work;
- **the environment fails:** a full disk (`df -h`); no network; a broken Gradle cache that
  `./gradlew --stop` and one `--refresh-dependencies` run do not fix; a command blocked by a
  permission prompt nobody answers. Record it and stop. Never delete caches outside the project;
- **usage or context is running low.** Finish the current step, commit (WIP if needed) and push,
  update your branch file, then stop.

## 9. Recovery

- **Dirty tree, and your branch file's "In progress" names a task:** that is the interrupted
  task. Read the diff (`git diff --stat`, then the files), run `./gradlew verifyFast`, finish the
  task or revert your own half-done edits.
- **Dirty tree otherwise:** these may be someone else's edits. Never discard them; leave them
  and ask.
- **A `WIP` commit at your branch's tip:** finish the task, then its own commit.
- **Your branch is red:** `git bisect start HEAD <last green task commit>`, `git bisect run
  ./gradlew verifyFast`, fix it before anything else.
- **`main` is red:** §2 item 2.
- **A lock older than 3 hours:** a dead session left it. Overwrite it and continue.
- **Old `wip/*` branches** on `origin` predate this procedure; leave them to the maintainer.

## 10. "For the owner" (in your branch file)

Number each item. Each says what is needed, why, the options with your recommendation, and what
is blocked until the owner answers. Work that does not depend on an item continues. Choices with
a recommended option are not queued: take them and record them in "Decisions taken". The
maintainer lifts open items into STATE.md, where the answers land.

| Kind | Content |
|---|---|
| Opening | «open Mx for claims» for a specified milestone |
| Decision | Options and a recommendation, for what AGENTS.md keeps for the owner |
| Consent batch | Only while "Standing restrictions" forbids windows: the client-window runs, their duration, and the phrase «запускай консент-батч»; a static scene is repeated two or three times, never more (D-22) |
| Demo | Exact commands and steps; the owner's verdict goes into the milestone's Acceptance |
| Approval | `changed` cards and anything AGENTS.md keeps for the owner |
| Proposal | Ideas that would add scope |

## 11. Reports

Reports go in the language of whoever runs the session. There are exactly two kinds; a finished
task (T) is documented in the repository (§5) and never reported on its own.

### 11.1 The milestone report (mandatory, after every milestone or other claim handed in)

Written at the hand-in (§12) as the pull request's body, copied into the branch file's log, and
posted in chat; then work continues with the next claim — no pause, no waiting for an answer:
- **Built:** the milestone, its cards and their statuses;
- **Gates:** `verify`, `verifySlow`, `verifySoak`, `jmhCompare`, L4 — results with numbers and times;
- **Deviations:** spec findings, refinements, cut or deferred scope;
- **Decisions taken:** each with how to undo it, and the agent trials;
- **For the owner:** the numbered items, the demo with exact steps;
- **Links:** the pull request, the branch file, the spec's "Results".

### 11.2 The stop report (when a stop condition holds)

First delete the lock (`rm -f .session.lock`): nothing runs after the final message. Then report
briefly:
- **Done:** tasks and cards, with numbers.
- **The state of your branch:** green or red; handed in or not; the pull request.
- **What is next.**
- **What needs the owner:** your branch file's "For the owner" items.
- **Links**, as GitHub URLs:
  `https://github.com/o-e-lytvynskyi/strategos/blob/<branch>/docs/branches/<file>`,
  `https://github.com/o-e-lytvynskyi/strategos/commit/<hash>` for each commit, the pull request.

## 12. Hand in, release

1. **Hand in** a finished claim:
   - the gate of its last task passed, and, for a milestone, `./gradlew verify verifySlow
     verifySoak` and (if the core's hot paths changed) `:core:jmhCompare`;
   - merge `origin/main` once more if it moved, and re-run `verify`;
   - the branch file's header `Status: ready for review`, "Now" final, commit `Mx: hand in
     <work>`, push;
   - open a pull request to `main`: `gh pr create --base main --head <branch> --title "<work>:
     <summary>" --body-file <scratchpad file>` (or the web UI); the body is the milestone report
     (§11.1), also posted in chat. CI runs on it;
   - then go back to §2 and claim the next work; stack it on this branch if it depends on it.
2. **Review findings** arrive in the branch file's "Review" and in the pull request: fix them on
   the branch (`Mx Tn: review fix — <what>`), push, and say so in the pull request.
3. **Release** a claim you stop: `Status: released <date>` and a log line on why in your branch
   file, push; then a pull request to `main` turning your docs/claims.md row to `released` (title
   `Release: <work>`). Approving someone's takeover of your work is the same release, or a
   comment on their claim pull request saying so.
4. **The `done` row** is written by the maintainer at the merge.
