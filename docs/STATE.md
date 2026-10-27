# State

The project-wide picture, kept by the maintainer and updated at merges. Who works on what is in [claims.md](claims.md)
(`python3 tools/claims.py list`); live progress is in each developer's branch file ([branches/](branches/)). The session
log is in [worklog.md](worklog.md); the work procedure is the `strategos-dev` skill (`.claude/skills/strategos-dev/SKILL.md`).

**Last update:** 2026-10-27 · **Owner last seen:** 2026-10-27 · **Milestones done:** M0–M4 (M0 accepted) · **Open for claims:** M5

## For the owner

Answer in chat or here; the next session applies the answers.

1. **For your information: the licence is decided (D-07, 2026-10-25).** PolyForm Noncommercial 1.0.0
   ([LICENSE](../LICENSE)): all rights stay yours; personal and other non-commercial use is allowed; contributions are
   voluntary and licensed to you under [CONTRIBUTING.md](../CONTRIBUTING.md) so the terms can change at the full
   release. "Strategos" stays the working title (D-21 open).
2. **The M0 demo, whenever you want it.** You accepted M0 on 2026-10-22 without playing it, so nothing is waiting. The
   demo stays ready: `scripts/playtest.sh`, then the steps in [testing/playtest.md](testing/playtest.md). Its first
   use confirms ADR-0022, the one thing your acceptance left open.
3. **Open decisions,** none urgent:
   - D-03 civilisation themes (M7);
   - D-20 multiplayer hosting (M7);
   - D-21 public release and name (first public release);
   - D-06 map generation (M12).
4. **The M2 demo, when you have an hour (T22's last part, L7):** [milestones/M2-command.md §1.3](milestones/M2-command.md):
   `scripts/playtest.sh`, `/strategos match create map strategos:proving_ground`, `/strategos match join 1 0`,
   `/strategos spawn strategos:unit/infantry 30 0` and `... 30 1`, then box-select, Ctrl+1, attack-move with Z and a
   right click, V into first person, R (follow), G on an enemy, V back; the questionnaire in
   `docs/testing/playtest.md`.
5. **The M1 demo, whenever you like (L7):** [milestones/M1-movement.md §1.3](milestones/M1-movement.md): an Arena
   world, `/strategos match create map=strategos:proving_ground`, `/strategos spawn strategos:unit/infantry 200`,
   F8 for the movement overlay, `/strategos order move @units waypoint=far_side`, then the questionnaire. (The
   infantry are spearmen since M2; 200 of one faction do not fight.) That session confirms ADR-0022.
6. **Optional reviews:**
   - the card refinements made after the approval
     ([design/roadmap-changes.md §8](design/roadmap-changes.md));
   - the promo draft ([promo/roadmap-announcement.md](promo/roadmap-announcement.md)).
7. **The M3 demo, when you have half an hour (decision gate 3, L7):** [milestones/M3-first-loop.md §1.3](milestones/M3-first-loop.md):
   `scripts/playtest.sh`, then `/strategos skirmish strategos:proving_ground observe seed=3` to watch two bots,
   or `/strategos match create map strategos:proving_ground`, `/strategos match join 1 0` and
   `/strategos match scenario strategos:first_loop` to play slot 0 against the bot (its slot is `ai:first_loop`);
   then the questionnaire in `docs/testing/playtest.md` with **"would I play another?" and its reason**. A "no",
   or any score of 2 or less, sends pacing and readability back for tuning. M3 is Done; only this makes it Accepted.
8. **Needs your word: the per-unit perf base** (M4 T21–T22). With the workers gathering and on map v3, the first loop
   costs 11,648 ns a unit at budget A's p95, twice M1's crowd soak of 600 walkers that PERF-06.2 compared it with; a
   replay of the same match in L1 has the core's step at 0.37 ms at p95, so the server's figure is mostly its
   surroundings. I made the first loop's own run both soaks' base, and M4's economy soak (316 units, five
   constructions, two repairs) reads 0.59 of it, every other budget held ([perf/economy-m4pro.json](../perf/economy-m4pro.json),
   [perf/first-loop-m4pro.json](../perf/first-loop-m4pro.json)). That changes a gate's base, which the work rules
   leave to you: say «оставь» to keep it, or «верни M1» to compare with M1 again, which leaves PERF-06.2 red until
   the first loop is made cheaper on the server.
9. **The M4 demo, when you have forty minutes (L7):** [milestones/M4-economy-and-construction.md §1.3](milestones/M4-economy-and-construction.md).
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
10. **A watched demo of every mechanic, whenever you like (no setup):** ask «покажи демо» (or «покажи новое», or
   name the mechanics) and the session opens the game window and narrates in the chat. Or run it yourself:
   `./gradlew :mod:runScenario -Pscenario=demo -Pdemo=all -Pscenario.timeoutSeconds=3600`. Twelve chapters, about
   six minutes in all: crowd round a wall, attack-move and melee, wood and berries, livestock, mines and quarries,
   fields and reseeding, one builder against five, a barracks on a slope, damage and repair, TNT and a piston, a
   batch to a rally point, the computer building its base. `-Pdemo=new` shows only what you have not seen at its
   current version (kept in `~/.strategos/demo-seen`); `-Pdemo=m4` or `-Pdemo=crowd,melee` picks, `-Pdemo.skip=`
   leaves out; `-Pdemo=list` lists them. It does not replace the L7 demos above, which are play, not watching.
11. **fix-main is merged (PR #21, 2026-10-27); one choice is yours, none urgent:** lev1pes's full Windows
   `verify verifySlow verifySoak` took 17 min 38 s (an ordinary `verify` about 5 min 48 s, serial). Accept that
   host cost, or ask for a test-placement proposal; no check moves. The two decisions above are open to your veto.

## Since the owner's last visit

Nothing yet: the owner was here on 2026-10-27.

## Milestones

| Milestone | Status | Held by | Spec | Notes |
|---|---|---|---|---|
| M0 Foundation | Accepted 2026-10-22 | the owner's sessions (`o-e-lytvynskyi`) | [M0](milestones/M0-foundation.md) | Started 2026-10-21; tripwire met on day 2; 25 of 26 cards accepted, UNIT-01 closes with NET-01 (M1); the L7 demo waived by the owner (item 2) |
| M1 Movement | Done 2026-10-23 | the owner's sessions (`o-e-lytvynskyi`) | [M1](milestones/M1-movement.md) | Started 2026-10-22 on the owner's go-ahead; 29 tasks (T28, PATH-08, joined by its R3 trigger in T8; T29 by the owner's word); Accepted waits for the L7 demo (item 5) |
| M2 Command | Done 2026-10-23 | the owner's sessions (`o-e-lytvynskyi`) | [M2](milestones/M2-command.md) | Started 2026-10-23 at the owner's word; 23 tasks; `pose_high` settled in M3 T30; Accepted waits for the L7 demo (item 4) |
| M3 First loop | Done 2026-10-24 | the owner's sessions (`o-e-lytvynskyi`) | [M3](milestones/M3-first-loop.md) | Started 2026-10-23 at the owner's word; 31 tasks; Accepted waits for the L7 demo (item 7) |
| M4 Economy and construction | Done 2026-10-24 | the owner's sessions (`o-e-lytvynskyi`) | [M4](milestones/M4-economy-and-construction.md) | Started 2026-10-24 as M3 closed; 24 tasks, closed by T28 (2026-10-24: Results written, [§8.1](milestones/M4-economy-and-construction.md); the health check's follow-ups are M5's first tasks); Accepted waits for the L7 demo (item 9); item 8 asks your word on the perf base |
| M5 Combat | Open for claims 2026-10-25 | open for claims | [M5](milestones/M5-combat.md) | Spec written as M4 closed, 17 tasks; nothing on `main` is in progress; the colleague claims it by pull request ([claims.md](claims.md)); first task M5 T1, the health check from M4's close, once the claim is merged |
| M6 (MVP) | approved | — | — | Its spec is written when M5 closes |
| M7–M15 | proposed | — | — | Approved by the owner closer to their time |

Overview of the roadmap: `python3 tools/roadmap.py summary`.

## Open for claims

Milestones whose tasks any developer may claim (AGENTS.md "Developers and claims"), opened on the owner's word.
Planning work (the next milestone's spec) is always claimable.

- **M5 Combat** — opened 2026-10-25 on the owner's word («разраб сам заасайнится на нее»): the colleague claims
  it by pull request. The owner's own agent sessions do not claim M5 unless the owner asks.

## Standing restrictions

None. The defaults of AGENTS.md "Autonomy by default" apply: everything a developer session needs is allowed —
client windows, the recommended decisions, subagents, any test, no pauses for reports. A restriction the owner gives («без окон», «без субагентов»)
goes here with its date and its end («до вечера», «до конца M5»), and is deleted when it ends.

## Decisions taken

Decisions taken on a recommendation and merged into `main` (from the branch files at each merge, marked with the
branch), and the maintainer's own: what, why, how to undo. The owner vetoes by answering here; a veto becomes a
`fix-…` claim. The list is cleared once the owner has seen it.

- 2026-10-26 · (dev/lev1pes/fix-main) Idle movement reuses proven resting ground geometry (`RestingGround`) and skips
  the avoidance solver only when zero velocity is conservatively feasible; separation, avoidance cadence and height
  easing are unchanged, the caches are derived and never saved. Undo: remove `RestingGround` and the shortcut.
- 2026-10-27 · (dev/lev1pes/fix-main) UNIT-05: the idle gate runs paced at 20 TPS with 2×200 unmeasured
  preconditioning ticks, the same for idle and walking; limits, samples and warm-up unchanged
  ([design/roadmap-changes.md](design/roadmap-changes.md)). Undo: revert the idle300 fixture in `PerfTests`.

## Blocked

Nothing.

## CI and budgets

- **CI policy (2026-10-25):** CI runs on pull requests to `main` only, never on pushes or drafts: `verify` on every
  hand-in, `records` on record changes, `full-ci` (`verifySlow`, `verifySoak`, `:core:slowTest`) and `perf` only when a
  maintainer adds their label. Tests during a milestone are local ([05-testing.md](05-testing.md)). Minutes: about
  1,575 of 2,000 used as of 2026-10-24 while private; a public repository's standard runners cost nothing.
- Performance baselines are the owner's Mac (Apple M4 Pro, 24 GB, macOS 26) only; other machines and Windows are
  untested (README "Platforms").

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
- **Settled items moved out of "For the owner" (2026-10-25):**
  - 2026-10-22, consent for M1's tests: every kind of test, client windows included; with the lid closed a Mac window
    may not render, so client scenarios run on the Linux route on CI first and a Mac run is used only where a
    measurement needs this Mac. 2026-10-23: «мак до вечера в твоем распоряжении, можешь делать все, что посчитаешь
    нужным, чтобы ускорить разработку, кроме субагентов» (this Mac, windows included, no subagents); from that
    evening «пока без окон», lifted by the word of 2026-10-24 below;
  - 2026-10-23, go-ahead for M2: «когда закончишь М1 - приступай сразу к М2»; the M2 spec was written once T27 closed
    M1 and M1's acceptance items did not hold M2 up;
  - 2026-10-23 (evening), decisions taken on recommendation («Давай примем все решения что ты рекомендуешь и начнем м3
    пока без окон…; по поводу решений с байтами, держим в голове, что можно это оптимизировать»): the block writer's
    limits gate (1.15 ms a tick at p99, a tenth at the median) under gate `c`; PERF-14's baselines are this Mac's
    ([perf/client/m4pro.json](../perf/client/m4pro.json)), card PERF-14 done; goldens approved but `pose_high`; the JMH
    baseline is the full run of 2026-10-23 (18 benchmarks, [perf/baseline-m4pro.json](../perf/baseline-m4pro.json));
    QA-07.4 allows a kernel's events their messages (`OrcaBench` may spend 16 B a step on the stuck ladder's; an
    allocation-free bus stays a known follow-up); gates `c` and `e` join `a`, `d` and `g`; S10's footprint rule joins M1
    as T29; the five far-off decisions (D-03, D-06, D-07, D-20, D-21) stayed open (D-07 since decided, see item 1);
  - 2026-10-24, word «можешь любые тесты проводить и как закончишь м3 - начинай м4, все решения принимай сам, которые
    рекомендуешь»: any test, windows included; M4 started when M3 was done; recommended decisions taken and recorded;
    M3's window tests run together in its consent batch, T30. The earlier guess about `pose_high` (the scene's own
    ground still generating) was wrong: the edge was the server sending 5 chunks, settled in M3 T30 (scenes set their
    render distance without telling the server); the camera stays as it is;
  - the long-route planner (card PATH-08, HPA*) left the reserve and was built: T8 measured one unit's path across the
    384² proving ground at about 30,000 search steps, over the 20,000 cap, and 4.5 ms of server time, over the 2 ms a
    tick the budget allows all pathfinding; T28 added it, the same crossing now takes about 2,800 steps and a fraction
    of a millisecond, within 1 % of the best route ([M1 spec §1.6 D rows 42 and 47–53](milestones/M1-movement.md));
  - `jmhCompare` red since M3 T4, mostly from this Mac's state: three benchmarks read 16–26 % over the morning's
    baseline; `MatchBusBench`, whose code no M3 task touched, reads 14.4 µs at the commit before M3's code against
    12.0; the one real growth is M3 T2's relationships in template compilation, 6.36 ms against 5.86 (+8.6 %). At T9
    the gate named only `MatchBusBench` (14.3 µs), the rest within 15 %. Settled at M3's close: the full run of
    2026-10-24 read it at 14.1 µs again, so its entry was re-taken (14.1 µs; M1's and M2's tree read the same, so the
    Mac moved), the rest of the baseline kept, M3's new `TerrainEditBench` added;
  - the bot against itself always lets the west start win: ten seeded bot-versus-bot matches on the proving ground
    (M3 T28) all end with a winner at 15:19–15:34, all ten to faction 0, whose wave reaches the enemy centre about 22 s
    sooner and burns it first while faction 1's wave dies on the way; the first bot defends nothing (AI-05, M6), so
    M6's defence or evener start positions settle it ([M3 spec §1.8](milestones/M3-first-loop.md)); to watch one:
    `/strategos skirmish strategos:proving_ground observe seed=3`;
  - taken under the word of 2026-10-24, M3's per-unit perf baseline: the first-loop soak's figures
    ([perf/first-loop-m4pro.json](../perf/first-loop-m4pro.json)) are what M4's perf gate compares with, as M1's crowd
    soak was for M3; well inside every budget, the tick's own work (C) 4.2 ms at p95 of 23 (item 8 revisits the base);
  - taken under the same word, each can be undone: the M3 goldens (nine frames of `m3_goldens`, `art_look` with and
    without Sodium, `rts_goldens` and `overlay_goldens` re-recorded for M3's HUD, M2's `pose_high`; in
    `mod/src/devtools/goldens/`); PERF-14's results again with M3's HUD and the 16 chunks really sent
    ([perf/client/m4pro.json](../perf/client/m4pro.json)); ART-01.5: armoured crowds hold with Sodium, on vanilla 100
    selected armoured spearmen drew at about 21 fps, so M4 T3 gave vanilla a level of detail (an outline is the body
    alone; past 50 bodies a frame or 128 blocks the armour and items are left out), now 4.9 ms, two `m3_goldens` frames
    re-recorded (the selected worker's item is no longer outlined);
  - taken under the same word, the proving ground's version 3 (M4 T22): version 2 ran short of wood for M4's economy
    (each start had its grove of 1,080, the only other wood a forest 180 blocks off, outside any territory and nearer
    the east start), so after minute 8 the economy AI starved and over 30 minutes the east won every decided match;
    version 3 adds a wood of thirty oaks north of each start, all ten 30-minute matches are decided, 3 to the west and
    7 to the east; it changes the map's hash, so the frames that show the proving ground came up again in T27's
    consent batch;
  - the arena's computer players gathered nothing (found in M4 T21, fixed): the arena lies below height 0, and the
    economy AI, like the soaks' scenes, took a node's negative height for "nothing left to gather"; M3's perf base had
    been measured that way, with every worker idle;
  - taken under the word of 2026-10-24, M4's consent batch (M4 T27): new goldens `m4_goldens` on macOS and with Sodium
    in `mod/src/devtools/goldens/` (every resource node at its stages from 60 blocks, a selected deposit with its ring,
    the civic centre's and barracks' cards, the build page, foundations placed in order, a house past its damage
    stages, the animals); `m3_goldens`, `overlay_goldens` and `rts_goldens` re-recorded for M4's HUD (`rts_goldens`'
    "empty" click had fallen under the selection panel since M3); PERF-14 again, every budget held; each can be undone;
  - the new way of working (word of 2026-10-25): rules are tool-neutral in [AGENTS.md](../AGENTS.md) (CLAUDE.md imports
    it; Codex reads it as is) and bind every developer equally, every developer is external, the owner's own sessions
    too, joining is in [onboarding.md](onboarding.md); assignment first, by pull request: a claim pull request adds the
    developer's row to [claims.md](claims.md) and their branch file and is merged before any development
    (`tools/claims.py check` refuses overlaps and takeovers without the holder's approval), work is built on
    `dev/<developer>/<work>` and handed in as a pull request with the milestone report, reviewed by another session
    with delegated workers (`strategos-maintain` §4) and merged on the owner's «влей»; claim and release pull requests
    are records a maintainer session checks («прими клеймы»; since 2026-10-26 a human still approves each merge); the main session
    orchestrates on Opus and delegates to Sonnet and Haiku workers, a **demo** roster ([agents.md](agents.md)), roles
    change by pull request and leave demo on trial evidence; windows, recommended decisions, subagents, any test and no
    pauses are allowed by default, with reports after every milestone; context economy is a rule; the Minecraft and
    RTS knowledge base is here now: [kb/](kb/README.md).

Details are in [worklog.md](worklog.md).

## Known follow-ups (not blocking)

- fix-main review (2026-10-27, minor): a differential test of the rest shortcut (state hashes with it on and off in a
  dense settled scene); `RestingGround`'s reliance on `SurfaceGrid.version()` rising with every change, unstated and
  untested, and its slot reuse after a unit's removal; `IdleBench` does not assert its units settled; UNIT-05 and the
  idle300 javadoc should name the pacing and the preconditioning; the `ProtectedBlockIndexTest` ratio before and
  after its probe change is not recorded.
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
