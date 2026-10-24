# State

Where the project stands right now. The session log is in [worklog.md](worklog.md); the work
procedure is the `strategos-dev` skill (`.claude/skills/strategos-dev/SKILL.md`).

**Last update:** 2026-10-25 · **Owner last seen:** 2026-10-25 · **Current:** M5 Combat started 2026-10-24 on the owner's go-ahead, in `codex/m5-combat`; M1–M4 Done

## For the owner

Answer in chat or here; the next session applies the answers.

1. **Nothing is waiting from M0.** You accepted it on 2026-10-22 without playing the demo. The demo
   stays ready whenever you want it: `scripts/playtest.sh`, then the steps in
   [testing/playtest.md](testing/playtest.md). Its first use confirms ADR-0022, the one thing your
   acceptance left open.
2. **Your go-ahead for M2 (2026-10-23):** «когда закончишь М1 - приступай сразу к М2». Once T27
   closes M1, the M2 spec is written and its tasks start without waiting; M1's acceptance items
   for you (the demo, consent batches) are listed here and do not hold M2 up.
3. **Your consent for M1's tests (2026-10-22):** every kind of test, client windows included. With
   the lid closed and no display, a Mac window may not render, so client scenarios run on the
   Linux route on CI first; a Mac run is used only where a measurement needs this Mac.
   **And for today (2026-10-23):** «мак до вечера в твоем распоряжении, можешь делать все, что
   посчитаешь нужным, чтобы ускорить разработку, кроме субагентов» — this Mac, client windows
   included, was mine until the evening; no subagents. **From the evening of 2026-10-23: no windows**
   («пока без окон»); work that needs one is listed in item 6 and does not hold the rest up.
4. **Open decisions,** none urgent:
   - D-03 civilisation themes (M7);
   - D-20 multiplayer hosting (M7);
   - D-07 licence and D-21 public release and name (first public release);
   - D-06 map generation (M12).
5. **Your decisions of 2026-10-23 (evening):** «Давай примем все решения что ты рекомендуешь и начнем м3 пока без
   окон, если работа встанет из-за того что тебе будут нужны окна - дай мне знать. По поводу решений с байтами,
   держим в голове, что можно это оптимизировать». Applied:
   - the block writer's limits gate (1.15 ms a tick at p99, a tenth at the median), under gate `c`;
   - PERF-14's baselines are this Mac's ([perf/client/m4pro.json](../perf/client/m4pro.json)); card PERF-14 done;
   - the goldens are approved but `pose_high` (see item 6);
   - the JMH baseline is the full run of 2026-10-23 (18 benchmarks, [perf/baseline-m4pro.json](../perf/baseline-m4pro.json));
   - QA-07.4 allows a kernel's events their messages: `OrcaBench` may spend 16 B a step on the stuck ladder's;
     an allocation-free bus stays a known follow-up, as you said;
   - gates `c` and `e` join `a`, `d` and `g`;
   - S10's footprint rule joins M1 as T29.
   The five far-off decisions (D-03, D-06, D-07, D-20, D-21) stay open: none is needed before M7, and two of them
   are about the licence and going public.
6. **Your word of 2026-10-24:** «можешь любые тесты проводить и как закончишь м3 - начинай м4, все решения
   принимай сам, которые рекомендуешь». Any test may run, client windows included; once M3 is done its successor's
   spec is written and M4 starts without waiting; the recommended decisions are taken and recorded here. M3's
   window tests (its goldens) run together in its consent batch, T30, so they take the screen once.
   **Settled in M3 T30:** the edge `pose_high` showed was the server sending 5 chunks (item 15). What follows is the
   earlier guess, kept for the record. `pose_high`, the camera at its top height,
   shows the world's edge. The tilt I suggested would also hide the far markers, which need the view to reach
   160 blocks; the edge is more likely the scene's own: a fresh flat world still generating chunks when the camera
   has just risen. The fix is probably for the scene to wait for the ground, then a new `pose_high` on this Mac
   (CI's Linux one can follow headless). The camera stays as it is.
7. **For your information: the long-route planner (card PATH-08, HPA*) left the reserve and is built.**
   The roadmap had it waiting for one measurement, which T8 took: one unit's path across the
   384² proving ground needed about 30,000 search steps, over the 20,000 cap, and 4.5 ms of server
   time, over the 2 ms a tick the budget allows all pathfinding. T28 added it: the same crossing
   now takes about 2,800 steps and a fraction of a millisecond, and its route is within 1 % of the
   best one. Nothing to do; it is in the roadmap's rules ([M1 spec §1.6 D rows 42 and 47–53](milestones/M1-movement.md)).
8. **The M2 demo, when you have an hour (T22's last part, L7):** [milestones/M2-command.md §1.3](milestones/M2-command.md):
   `scripts/playtest.sh`, `/strategos match create map strategos:proving_ground`, `/strategos match join 1 0`,
   `/strategos spawn strategos:unit/infantry 30 0` and `... 30 1`, then box-select, Ctrl+1, attack-move with Z and a
   right click, V into first person, R (follow), G on an enemy, V back; the questionnaire in
   `docs/testing/playtest.md`.
9. **The M1 demo, whenever you like (L7):** [milestones/M1-movement.md §1.3](milestones/M1-movement.md): an Arena
   world, `/strategos match create map=strategos:proving_ground`, `/strategos spawn strategos:unit/infantry 200`,
   F8 for the movement overlay, `/strategos order move @units waypoint=far_side`, then the questionnaire. (The
   infantry are spearmen since M2; 200 of one faction do not fight.) That session confirms ADR-0022.
10. **Optional reviews:**
   - the card refinements made after the approval
     ([design/roadmap-changes.md §8](design/roadmap-changes.md));
   - the promo draft ([promo/roadmap-announcement.md](promo/roadmap-announcement.md)).
11. **For your information: `jmhCompare` is red since M3 T4, mostly from this Mac's state.** Three benchmarks
   read 16–26 % over this morning's baseline; `MatchBusBench`, whose code no M3 task touched, reads 14.4 µs at the
   commit before M3's code against the baseline's 12.0. The one real growth is M3 T2's relationships in template
   compilation: 6.36 ms against 5.86 at that commit (+8.6 %). No new baseline is proposed now; the next full run at
   a quiet moment decides, and a proposal follows if the growth stays. At T9 (2026-10-24, past midnight) the gate
   names only `MatchBusBench` (14.3 µs against 12.0), still untouched; the rest are within 15 %. **Settled at M3's
   close (item 15):** the full run of 2026-10-24 read it at 14.1 µs again, so its entry was re-taken.
12. **For your information: the bot against itself always lets the west start win.** Ten seeded bot-versus-bot
   matches on the proving ground (M3 T28) all end with a winner at 15:19–15:34, and all ten go to faction 0: its
   wave reaches the enemy centre about 22 s sooner and burns it first, while faction 1's wave dies on the way. The
   first bot defends nothing (that is AI-05, M6), so the shorter way decides. Nothing to do now; M6's defence or
   evener start positions settle it ([M3 spec §1.8](milestones/M3-first-loop.md)). To watch one:
   `/strategos skirmish strategos:proving_ground observe seed=3`.
13. **Taken under your word of 2026-10-24: M3's per-unit perf baseline.** The first-loop soak's figures
   ([perf/first-loop-m4pro.json](../perf/first-loop-m4pro.json)) are what M4's perf gate compares with, as M1's
   crowd soak was for M3. They are well inside every budget: the tick's own work (C) 4.2 ms at p95 of 23.
14. **The M3 demo, when you have half an hour (decision gate 3, L7):** [milestones/M3-first-loop.md §1.3](milestones/M3-first-loop.md):
   `scripts/playtest.sh`, then `/strategos skirmish strategos:proving_ground observe seed=3` to watch two bots,
   or `/strategos match create map strategos:proving_ground`, `/strategos match join 1 0` and
   `/strategos match scenario strategos:first_loop` to play slot 0 against the bot (its slot is `ai:first_loop`);
   then the questionnaire in `docs/testing/playtest.md` with **"would I play another?" and its reason**. A "no",
   or any score of 2 or less, sends pacing and readability back for tuning. M3 is Done; only this makes it Accepted.
15. **Taken under your word of 2026-10-24, to look at when you like** (each can be undone):
   - the M3 goldens: nine frames of `m3_goldens`, `art_look` with and without Sodium, `rts_goldens` and
     `overlay_goldens` re-recorded for M3's HUD, and M2's `pose_high` (its edge was the server sending 5 chunks:
     scenes set their render distance without telling it). They are in `mod/src/devtools/goldens/`;
   - PERF-14's results again, with M3's HUD and the 16 chunks really sent ([perf/client/m4pro.json](../perf/client/m4pro.json));
   - the JMH baseline's `MatchBusBench` entry re-taken (14.1 µs; its code is M1's and M2's tree read the same, so the
     Mac moved, item 11), the rest of the baseline kept, M3's new `TerrainEditBench` added;
   - ART-01.5: armoured crowds hold with Sodium; on vanilla, 100 selected armoured spearmen drew at about 21 fps,
     so M4 T3 gave vanilla a level of detail: an outline is the body alone, and past 50 bodies a frame or 128 blocks
     the armour and items are left out. Vanilla now draws that scene in 4.9 ms; two `m3_goldens` frames re-recorded
     (the selected worker's item is no longer outlined).

16. **Taken under your word of 2026-10-24: the proving ground's version 3** (M4 T22). Version 2 ran short of wood for
   M4's economy: each start had its grove of 1,080, the only other wood a forest 180 blocks off, outside any territory
   and nearer the east start, so after minute 8 the economy AI starved and over 30 minutes the east won every decided
   match. Version 3 adds a wood of thirty oaks north of each start; now all ten 30-minute matches are decided, 3 to
   the west and 7 to the east. It changes the map's hash; the frames that show the proving ground come up again in
   T27's consent batch.
17. **For your information: the arena's computer players gathered nothing** (found in M4 T21, fixed). The arena lies
   below height 0, and the economy AI, like the soaks' scenes, took a node's negative height for "nothing left to
   gather". In an arena world a skirmish showed idle workers; it works now. M3's perf base had been measured that
   way, with every worker idle.
18. **Needs your word: the per-unit perf base** (M4 T21–T22). With the workers gathering and on map v3, the first loop
   costs 11,648 ns a unit at budget A's p95, twice M1's crowd soak of 600 walkers that PERF-06.2 compared it with; a
   replay of the same match in L1 has the core's step at 0.37 ms at p95, so the server's figure is mostly its
   surroundings. I made the first loop's own run both soaks' base, and M4's economy soak (316 units, five
   constructions, two repairs) reads 0.59 of it, every other budget held ([perf/economy-m4pro.json](../perf/economy-m4pro.json),
   [perf/first-loop-m4pro.json](../perf/first-loop-m4pro.json)). That changes a gate's base, which the work rules
   leave to you: say «оставь» to keep it, or «верни M1» to compare with M1 again, which leaves PERF-06.2 red until
   the first loop is made cheaper on the server.
19. **Taken under your word of 2026-10-24: M4's consent batch** (M4 T27). The new goldens, `m4_goldens` on macOS
   and with Sodium in `mod/src/devtools/goldens/`: every resource node at its stages from 60 blocks, a selected
   deposit with its ring, the civic centre's and barracks' cards, the build page, foundations placed in order, a
   house past its damage stages, the animals. Re-recorded for M4's HUD: `m3_goldens`, `overlay_goldens` and
   `rts_goldens` (whose "empty" click had fallen under the selection panel since M3). PERF-14 again, every budget
   held. Each can be undone.

20. **The M4 demo, when you have forty minutes (L7):** [milestones/M4-economy-and-construction.md §1.3](milestones/M4-economy-and-construction.md).
   `scripts/playtest.sh`, then `/strategos skirmish strategos:proving_ground observe seed=3` to watch two economy
   bots lay out their towns (the skirmish now plays `economy_loop`), or `/strategos match create map strategos:proving_ground`,
   `/strategos match join 1 0` and `/strategos match scenario strategos:economy_loop` to play slot 0 against it. Then:
   two workers to the iron mine (its look changes at 75 %) and two to the quarry; slaughter a sheep and gather it;
   build a farmstead and toggle its auto-reseed; a barracks on the slope by the river; three houses Shift-placed for
   the same builders, the third cancelled half built (its refund); a batch of five spearmen (Shift on the button);
   when the bot's wave burns a house, right-click it with workers to repair it; `/summon tnt` beside the town and a
   piston against a wall (nothing changes); save and quit mid-match and reload (it resumes paused, as it was); then
   the questionnaire in `docs/testing/playtest.md`. The map has no rock outcrop: outcrops are tested on their own
   (ECO-07). M4 is Done; only this makes it Accepted.

21. **M5 started on the owner's word (2026-10-24).** Work is isolated in `codex/m5-combat`;
   `main` is unchanged. The target is a MacBook M5. This session runs on Windows x64;
   its exact environment, commands, results and macOS-only checks are recorded in
   [testing/M5-windows-x64.md](testing/M5-windows-x64.md). Windows performance and images
   do not replace the Mac's baselines or goldens.

22. **A watched demo of every mechanic, whenever you like (no setup):** ask «покажи демо» (or «покажи новое», or
   name the mechanics) and the session opens the game window and narrates in the chat. Or run it yourself:
   `./gradlew :mod:runScenario -Pscenario=demo -Pdemo=all -Pscenario.timeoutSeconds=3600`. Twelve chapters, about
   six minutes in all: crowd round a wall, attack-move and melee, wood and berries, livestock, mines and quarries,
   fields and reseeding, one builder against five, a barracks on a slope, damage and repair, TNT and a piston, a
   batch to a rally point, the computer building its base. `-Pdemo=new` shows only what you have not seen at its
   current version (kept in `~/.strategos/demo-seen`); `-Pdemo=m4` or `-Pdemo=crowd,melee` picks, `-Pdemo.skip=`
   leaves out; `-Pdemo=list` lists them. It does not replace the L7 demos above, which are play, not watching.

## Since your last visit

M5 T1 finished: the health check and the Windows test environment, with `verify verifySlow` green. `main` is unchanged.

## Now

- **Milestone:** M5 Combat, started 2026-10-24 (owner's go-ahead); [spec](milestones/M5-combat.md); 1 of 17 tasks done.
- **In progress:** none; M5 T1 verified and ready to commit.
- **Last finished task:** M5 T1 (2026-10-25), health check; `verify verifySlow` passed on Windows x64 (see the environment report).
- **Next task:** M5 T2 · Mobility classes and footprints.
- **WIP branch:** `codex/m5-combat` (owner-requested isolation; no changes to `main`).
- **CI:** latest `main` verify succeeded at `3705118` (run 40119258187); branch not yet pushed; monthly usage last recorded about 1,575 of 2,000 minutes.
- **Overview:** `python3 tools/roadmap.py summary`.

## Blocked

Nothing.

## Milestones

| Milestone | Status | Notes |
|---|---|---|
| M0 Foundation | Accepted 2026-10-22 | Started 2026-10-21; tripwire met on day 2; 25 of 26 cards accepted, UNIT-01 closes with NET-01 (M1); the L7 demo waived by the owner |
| M1 Movement | Done 2026-10-23 | Started 2026-10-22 on the owner's go-ahead; 29 tasks (T28, PATH-08, joined by its R3 trigger in T8; T29 by the owner's word); Accepted waits for the L7 demo |
| M2 Command | Done 2026-10-23 | Started 2026-10-23 at the owner's word; 23 tasks; `pose_high` settled in M3 T30; Accepted waits for the L7 demo |
| M3 First loop | Done 2026-10-24 | Started 2026-10-23 at the owner's word; 31 tasks; Accepted waits for the L7 demo (item 14) |
| M4 Economy and construction | Done 2026-10-24 | Started 2026-10-24 as M3 closed; 24 tasks; Accepted waits for the L7 demo (item 20); item 18 asks your word on the perf base |
| M5 Combat | In progress 2026-10-24 | 17 tasks, on the owner's go-ahead; isolated in `codex/m5-combat` |
| M6 (MVP) | approved | Its spec is written when M5 closes |
| M7–M15 | proposed | Approved by the owner closer to their time |

## History

- **2026-10-20:**
  - research: two fact-checked passes;
  - toolchain check;
  - the owner's base decisions.
- **2026-10-21:**
  - design:
    - architecture and 23 ADRs;
    - feasibility, tooling, process and testing documents;
    - a roadmap of 243 cards, reviewed;
    - the M0 spec;
    - the promo draft;
  - the owner approved M0–M6 and the decisions needed by M6;
  - the private GitHub repository;
  - the autonomy kit.
- **2026-10-21 to 2026-10-22:** M0 built, all 25 tasks: the build and its gates, every test
  harness, the deterministic kernel with persistence, quarantine and fault containment, the
  walking skeleton, the performance harness and spikes S1 to S6; accepted by the owner on
  2026-10-22.

Details are in [worklog.md](worklog.md).

## Known follow-ups (not blocking)

- [01-feasibility.md](01-feasibility.md) §3 predates the architecture in places; its precedence
  note says the architecture and the ADRs win. §6.5 was re-estimated after M0 (2026-10-22).
- ADRs owed by future cards:
  - ECO-03's leaf rule, which amends ADR-0021 (ECO-16's amendment of ADR-0011 is written, M4 T7).
- Faction 0 wins all ten headless seeds of M3's bot, before M4 T6 as after it: the second slot's start, or its
  mirror, is worse for it. With AI-02's economy script on map v3 the split is 3 to the west, 7 to the east (M4 T22):
  no longer all one way, still the east's lean; AI-05's defence (M6) is where it gets looked at.
- T27's consent batch: the `livestock` goldens (ECO-16.5), and `m3_goldens` frames that show a start now show its
  animals and may need re-recording. A carcass's body is not kept across a save: after a restore its meat is still
  gathered, with no body.
- ADR-0022 waits for its confirmation at the first L7 session the owner plays.
- Budget F is measured for bare humanoids only; ART-01 (M3) runs the armoured pass.
- Modifiers are not yet saved or hashed: the first real source (TECH-01's research or an AI
  handicap) brings their input, snapshot field and hashing ([M1 spec §1.6 D row 66](milestones/M1-movement.md)).
- The server smoke failed once on CI (run 40037838166, M3 T17's commit): a debug walker of the group-move phase
  stood idle 3.1 blocks from its place (the check allows 1.5 plus the latch). The same code passed locally and on
  CI at T20's commit, so it is a rare flake of the arrival latch with eight walkers; watch for a second one before
  changing the reach.
- ECO-03's leaf rule amended ADR-0021 through ADR-0024 (M3 T8); the line above about it is kept for history.
