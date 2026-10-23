---
name: strategos-dev
description: Autonomous work loop for the Strategos Minecraft mod. Use for any work session in this repository — «продолжай», «работай над strategos», «начинай M0», «следующая задача», implementing roadmap cards, writing a milestone spec, running spikes or a consent batch, fixing a red build. It orients from docs/STATE.md, picks the next ready task, implements it test-first, verifies it, records progress (spec checkboxes, card statuses, worklog, STATE), commits and pushes, repeats until a stop condition, and reports to the owner in Russian.
---

# Strategos: the autonomous work loop

The owner visits rarely. Every session must leave the repository so that the next session — or
the owner — can pick up from the repository alone:
- `main` is green;
- docs/STATE.md and docs/worklog.md are current;
- every finished step is committed and pushed.

Paths below are relative to the repository root. The conventions (roles, statuses, evidence)
are in docs/03-design-process.md, "Autonomous development". The hard rules are in CLAUDE.md.
This skill is the procedure.

Commands below are spelled the way the project allowlist (`.claude/settings.json`) expects.
Spelled differently — an absolute path instead of `~`, a `cd` in front — they may stop an
unattended session at a prompt.

## 1. Orient (start of every session)

1. **Lock.** The lock file is `.session.lock` in the repository root; git ignores it.
   - If it holds a time less than 3 hours old, another session is working. Stop and say so.
   - Otherwise write the time to it: `date -u +%Y-%m-%dT%H:%M:%SZ > .session.lock`.
   - Rewrite it after every commit. Delete it (`rm -f .session.lock`) before the final report.
2. **Git:**
   - `git status`: a dirty tree goes to §9;
   - `git pull --ff-only`;
   - if `main` is ahead of `origin`, push first: an earlier commit never reached GitHub;
   - `git branch -a`: a `wip/*` branch goes to §9.
3. **Read:**
   - docs/STATE.md: "For the owner", "Since your last visit", "Now", "Blocked";
   - the newest docs/worklog.md entry. Its date is the last session.
4. **Owner input:**
   - **The owner's commits.** Every assistant commit starts with `Mx Tn:`, `Mx:` or `Docs:`
     (§6); commits dated 2026-10-21 or earlier are the design work and predate that rule. The
     owner's commits are the others:
     `git log --since=<last session> --format='%h %s' | grep -vE '^[0-9a-f]+ (M[0-9]+( T[0-9a-z.]+)?|Docs): '`.
   - their edits in docs/ROADMAP.md (statuses, decision answers, `> Owner, …` notes);
   - answers written into STATE.md;
   - whatever they say in this chat.
   - **If the owner speaks in this session**, update STATE.md right away:
     - set `Owner last seen: <today>`;
     - replace "Since your last visit" with `Nothing yet: the owner was here on <today>.`

     §5 then adds a line for each task finished after that.
5. **Docs check.** `python3 tools/roadmap.py check` must pass; if it fails, repair the docs
   first. Then run `python3 tools/roadmap.py summary`.
6. **Build and CI check.**
   - Once the build exists (M0 T1 on), run `./gradlew verifyFast`. Red means the first task is
     to fix it.
   - Once CI exists (M0 T18 on), read the newest run on `main`: the GitHub MCP server's
     `actions` tools, or `gh run list --branch main --limit 3`, then `gh run view <id>
     --log-failed` for a red run. Red goes first (§2). If neither is available, "Now" says
     `CI: not readable` and the owner's setup item stays open.

## 2. Pick the next task

**Gate.** Implementing a milestone starts only after the owner's go-ahead for that milestone
(«начинай Mx»). A plain «продолжай» continues a started milestone and never starts a new one.
While the go-ahead is pending, do only ready planning work, such as the next milestone's spec,
or stop and report.

When the owner gives the go-ahead in this session, apply it before picking:
- **STATE.md:**
  - the header becomes `**Current:** <milestone> — started <date> (owner's go-ahead)`;
  - "Now" follows the template in §5.5;
  - delete the answered "For the owner" item and renumber the rest;
  - delete its "Blocked" line;
  - the "Milestones" table row becomes `in progress`.
- **docs/ROADMAP.md:** the "Owner approval" note says `<milestone> started <date>` instead of
  "Implementation has not started".
- **README.md "Status":** says the milestone is in progress.
- **Commit:** `Mx: start on the owner's go-ahead`. It is a documentation commit, so
  `tools/roadmap.py check` is its gate.

Then take the first that applies:

1. **Red `main` or red CI**: fix it.
2. **Owner input to apply**: decisions, notes on cards, consent given («запускай консент-батч»),
   approvals. Apply them, update the affected docs, commit `Docs: …`.
3. **The started milestone's spec**: the first unchecked task in its "Progress" list whose
   dependencies are checked and which is **ready**:
   - its cards are `approved`, `approved-foundation`, `specified` or `in_progress` — never
     `proposed`, `changed`, `deferred` or `rejected`;
   - it waits for no open owner decision;
   - it needs no client window. A task that does goes to the consent batch in "For the owner",
     and you take the next task.

   A card's "Depends on" orders the plan. Inside a specified milestone, the spec's task
   dependencies decide what is ready: M0 T1 starts CORE-06 before CORE-01.
4. **No spec for the next milestone**: write it (§4). This is planning, so it needs no
   go-ahead.
5. **All tasks of the milestone are done**: close the milestone (§7).

If nothing is ready, stop and report what is blocked and on whom. Never invent work outside the
approved roadmap; ideas go to "For the owner" as proposals.

## 3. Do the task

1. **Mark it started** in STATE "Now" (`In progress: Mx Tn since <date>`) before the first code
   edit. If the session dies, the next one knows what the dirty tree is.
2. **Read** the task's section in the spec, the cards it implements (acceptance criteria and
   test levels), and the architecture sections and ADRs it cites.
3. **Verify engine APIs before using them.**
   - **Before the first build** there are no sources. T1's build files and names are fully
     given in the M0 spec (§4, Appendix A).
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
   - `./gradlew verify` before every commit to `main`, once it exists (M0 T3);
   - `./gradlew verify verifySlow verifySoak`, what CI runs, before pushing a change that touches
     movement, pathing, orders or the tick, since the slow map tests (`maptests.*` on the proving
     ground) and the soak are not in `verify`: M1 T21's avoidance passed `verify` and failed
     `maptests.fifty_units_one_by_one` on CI;
   - `./gradlew :core:jmh :core:jmhCompare` before closing a task that changes the core's step,
     its codecs or a `@HotPath` kernel. No M1 task ran it, and M1 grew `MovementBench.step600` by
     58 % and `SnapshotCodecBench.encode500` by 29 % against M0's approved baseline unnoticed until
     M1's close. A red `jmhCompare` goes to "For the owner" the same day: the cause, and a proposed
     baseline (`perf/baseline-<machine>.proposed.json`) when the growth is the features' own cost;
   - `./gradlew :core:balanceSheet` after changing a template number, a relationship or a provenance
     note (M3 T2): `BalanceSheetTest` fails while `docs/balance/` is stale, and the sheet's diff in
     the commit is what the owner reviews, like a golden. A new number needs its provenance note in
     `strategos/balance/provenance.json` in the same change.

   Before that, the gate is `verifyFast` plus the task's verification command (M0 spec §0.4,
   "Bootstrap").

   If `verify` exceeds its 5-minute budget, record the time in the worklog and put the choice in
   "For the owner": accept the time, or move a run to `verifySlow`, which changes the Done gate
   (spec R20). Never move a run out of `verify` yourself.
6. **When reality disagrees with the plan:**
   - **An API does not exist in 1.21.1, or the spec is wrong:** correct the spec.
     - Add a row to its table of implementation findings: in M0, §1.6 D, next free number. A
       cut or a deferral goes to §1.5.
     - If the architecture changes, write an ADR, or a dated note on a Proposed one.
   - **An acceptance criterion cannot be tested as written:**
     - A scope-neutral refinement (another test level, a measuring method, a wording) may be
       applied. Record it in docs/design/roadmap-changes.md §8 "Refinements", in a dated block.
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
  - scope, and a differences table for every deviation from the cards (deviations may move or
    clarify scope, never add it);
  - the design down to files and classes;
  - tests per level;
  - tasks T1..Tn, each with dependencies, a verification command and a size;
  - an acceptance checklist;
  - risks with fallbacks;
  - owner inputs and the consent batch;
  - card → task traceability;
  - **a section headed `### <n> Progress`** with `[ ] Tn` checkboxes, in the order tasks may
    start, which `tools/roadmap.py` counts.
- **Afterwards:**
  - the milestone's `approved` cards become `specified`, in ROADMAP.md and the card files;
  - run `tools/roadmap.py check`;
  - commit `Mx: specify <milestone>`;
  - add a "For the owner" item: Mx is specified and ready; say «начинай Mx».
- **If the spec shows that a card must change scope:** set it to `changed`, add a quoted note
  under its milestone table in ROADMAP.md, and put it in "For the owner". Specify the rest.

## 5. Record progress

Do this after each task, before committing.

1. **Spec.** Tick the task in "Progress": `[x] T3 (2026-11-02)`. When M0 T1 starts, record the
   tripwire in "Now".

   The tripwire fires when T9 is not green after two weeks of M0 work: 10 days with `M0 T…`
   commits, counted by `git log --format=%cs --grep='^M0 T' | sort -u | wc -l`.
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
4. **docs/worklog.md.** Each session adds its own entry at the top, below the introduction:
   - headed `## <date> — <milestone> <tasks>`; two entries on the same date are fine;
   - one bullet per finished task, newest first, followed by a final `- **Next:** …`;
   - each task bullet gives: the cards; the results with numbers (tests run, `verify` time,
     performance figures); problems met.

   Example: `- **M0 T1 · Build skeleton** — QA-01, QA-03 in progress; verifyFast 41 s, :mod:test
   3 tests; the wrapper checksum matched.`
5. **docs/STATE.md.**
   - **"Now"** always has exactly this shape:

     ```
     - **Milestone:** M0 Foundation, started <date> (owner's go-ahead) | not started; spec <link>; <n> of <total> tasks done.
     - **Tripwire (M0 only):** T1 started <date>; T9 green <date> | not yet; M0 work days <n> of 10.
     - **In progress:** M0 <task> since <date> | none.
     - **Last finished task:** M0 <task> (<date>), commit <hash> | none.
     - **Next task:** M0 <task> · <title>.
     - **WIP branch:** none | wip/Mx-Tn.
     - **CI:** green | red | not readable | none yet; minutes used this month <n> of 2,000 | not readable.
     - **Overview:** `python3 tools/roadmap.py summary`.
     ```
   - **"Since your last visit":** add one line per finished task or milestone.
   - **The rest:** update "For the owner", "Blocked" and "Last update".
6. **Knowledge.**
   - General Minecraft or NeoForge pitfalls go to the pet-projects knowledge base. When the file
     `/Users/oleksandrlytvynskyi/work/pet_projects/.claude/kb/minecraft-mod.md` exists (a local
     session), add them there and commit by path in its own repository,
     `o-e-lytvynskyi/pet-projects-kb`. It is private, so pushing is allowed:

     ```
     git -C ~/work/pet_projects add .claude/kb/minecraft-mod.md
     git -C ~/work/pet_projects commit -m "Knowledge base: <summary>"
     git -C ~/work/pet_projects push
     ```
   - Project facts go to the spec, the architecture or an ADR.

## 6. Commit, push, repeat

- **Stage only the files you changed**, by path, never `git add -A`. If `git status` shows edits
  you did not make (the owner may be editing in their IDE), leave them unstaged and mention them
  in STATE.
- **Scan for secrets before committing:** `git diff --cached` must contain no tokens, passwords
  or keys. The project needs none.
- **Message prefixes:**
  - `Mx Tn: <imperative summary>` for a task;
  - `Mx: …` for milestone work: spec, start, close;
  - `Docs: …` for project-wide documents.

  English, with no assistant traces. One commit per task; long tasks get one commit per green
  step.
- **Push** after every commit; the owner allows it. On a rejected push:
  - run `git pull --rebase`;
  - resolve conflicts keeping the owner's statuses, decisions and notes;
  - push again;
  - never `--force` on `main`.
- **Never push a red `main`:** the gate of §3.5 passes first.
- **Unfinished work** (the session must end mid-task): commit it to `wip/Mx-Tn`, push the branch,
  and name it in STATE "Now".
- **Then go back to §2** and take the next ready task. Report (§11) only when a stop condition
  (§8) holds.

## 7. Close a milestone

1. **Exit conditions:**
   - every task is ticked;
   - every card is `done`, except cards whose open criteria the spec says never block Done
     (consent and CI items). Those stay `in_progress` and are named in "For the owner";
   - the Done gate passes. Gates are cumulative: earlier milestones' automated gates must
     still pass.
2. **Health check:**
   - classes over 800 lines;
   - ArchUnit exceptions;
   - TODOs;
   - code that disagrees with the architecture or the ADRs;
   - growth in `verify` time;
   - CI minutes this month on GitHub Free (2,000 for private repositories). Above 80 %, move CI
     to nightly.

   Fixes become the first tasks of the next milestone.
3. Write "Results" in the spec: the numbers, the deviations, the follow-ups.
4. Update STATE.md: the milestone is Done. In "For the owner", add:
   - the demo with exact commands, for example `scripts/playtest.sh`, then the steps;
   - the consent batch;
   - baselines and goldens to approve;
   - «начинай M(x+1)», if its spec is ready.
5. Record planned against actual effort. After M0, re-estimate docs/01-feasibility.md.
6. Cards become `accepted` only on the owner's word: after the demo, or when the owner accepts the
   milestone without it, which the spec's Results record. Tags and releases are the owner's call.

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
    fix;
  - a command blocked by a permission prompt nobody answers.

  Record it and stop. Never delete caches outside the project;
- **usage or context is running low.** Finish the current step, commit or push a WIP branch,
  update STATE and the worklog, then stop.

## 9. Recovery

- **Dirty tree, and STATE "In progress" names a task:** that is the interrupted task.
  1. Read the diff.
  2. Run `./gradlew verifyFast`.
  3. Finish the task, or revert your own half-done edits.
- **Dirty tree otherwise:** these may be the owner's edits. Never discard them. If they are
  plainly docs answers (statuses, decisions), apply them; otherwise leave them and ask.
- **A `wip/Mx-Tn` branch:**
  1. Check it out and rebase it onto `main`.
  2. Finish the task and get the gate green.
  3. Merge it fast-forward, then delete the branch locally and on `origin`.
- **`main` is red:**
  1. `git bisect start HEAD <last commit known green>`; never start from older than the M0 T1
     commit, because earlier commits have no build.
  2. `git bisect run ./gradlew verifyFast`.
  3. Fix it before anything else.
- **A lock older than 3 hours:** a dead session left it. Overwrite it and continue.

## 10. The owner's queue ("For the owner" in STATE.md)

Number each item. Each says what is needed, why, the options with your recommendation, and what
is blocked until the owner answers. Work that does not depend on an item continues. Never decide
an open owner decision yourself.

| Kind | Content |
|---|---|
| Go-ahead | «начинай Mx» for a specified milestone |
| Decision | Options and a recommendation |
| Consent batch | The client-window runs, their duration, and the phrase «запускай консент-батч» (the owner then steps away from the keyboard); a static scene is repeated two or three times, never more (D-22) |
| Demo | Exact commands and steps; the owner's verdict goes into the milestone's Acceptance |
| Approval | Performance baselines, goldens, ADR thresholds, `changed` cards |
| Proposal | Ideas that would add scope |

## 11. Report (end of session, in Russian)

First delete the lock (`rm -f .session.lock`): nothing runs after the final message. Then report briefly:
- **Done:** tasks and cards, with numbers.
- **The state of `main`:** green or red, plus CI.
- **What is next.**
- **What needs the owner:** the numbered "For the owner" items.
- **Links**, as GitHub URLs the owner can open anywhere:
  - `https://github.com/o-e-lytvynskyi/strategos/blob/main/docs/STATE.md`;
  - `https://github.com/o-e-lytvynskyi/strategos/blob/main/docs/worklog.md`;
  - `https://github.com/o-e-lytvynskyi/strategos/commit/<hash>` for each commit.
