# State

The project-wide picture, kept by the maintainer and updated at merges. Who works on what is in the branch files, one per claim ([branches/](branches/README.md))
(`python3 tools/claims.py list`); live progress is in each developer's branch file ([branches/](branches/)). The session
log is in [worklog.md](worklog.md); the work procedure is the `strategos-dev` skill (`.claude/skills/strategos-dev/SKILL.md`).

**Last update:** 2026-10-29 · **Owner last seen:** 2026-10-29 · **Milestones done:** M0–M5 (M0 accepted) · **Open for claims:** none

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
Items 11–22 come from the M5 branch file's "For the owner" 3–14; references inside M5 text use the branch's numbers (branch n = item n + 8).

11. **M5 (dev/o-e-lytvynskyi/m5):** Commit 287bf54 on this branch contains the home-directory JVM path in one re-taken perf/baseline-m4pro.json row; cc04642 removes it. The history is pushed and is not rewritten (AGENTS.md); the repository is private. Nothing needed unless the history should be scrubbed before any visibility change.
12. **M5 (dev/o-e-lytvynskyi/m5):** Obsolete: `dev/o-e-lytvynskyi/fix-main` (one docs commit 526dc72 proposing a release of lev1pes's fix-main, made moot by PR #21) and the local worktrees `strategos-fix-main`, `strategos-m5-content` (its T3b.2 draft is now in 95484c3) and `strategos-m5-control`. Recommend deleting them; left in place until you say so.
13. **M5 (dev/o-e-lytvynskyi/m5):** Done 2026-10-28 (b): `SpatialBench.rebuild1000` is bimodal across JMH forks on this Mac: twice today one fork read 7.4–7.7 us while the other read 4.8 (baseline 4.79), failing the 15 % gate on the average; each single rerun passed with both forks at 4.7–4.9. No code touched the spatial index. Options: (a) leave it — a red run is rerun once by the rule (current); (b) give that benchmark more forks so one outlier fork cannot carry the average (a measurement change, your call); (c) investigate the bimodality (memory layout of the grid arrays). Recommend (b) with 5 forks for this benchmark only.
14. **M5 (dev/o-e-lytvynskyi/m5):** Done 2026-10-28: replicate each unit's stance to its owner so the card's active stance is always right (now it shows the stance the player's own card last sent, else the template's). A small protocol addition; not in the approved cards' wording, so it waits for you.
15. **M5 (dev/o-e-lytvynskyi/m5):** Done 2026-10-28 (ring spacing by body width; stand-still 84 → 46 ticks): in duels a horseman sometimes stands ~80 ticks beside the back-rank archer it is focused on before striking. Probably a melee chase/ring-slot detail for the large class; to look at with T13's battle AI unless you want it earlier.
16. **M5 (dev/o-e-lytvynskyi/m5):** Done 2026-10-28 in part (gatherer ratio 7 : 11; trees on the proving ground still run out): from T9, the battle bot (T13) now weighs strength against defence and pulls back a doomed wave; the economy bot's wood-starved late economy (24 workers, other stocks in the thousands) remains — a candidate for AI-02's next tuning, not M5 scope.
17. **M5 (dev/o-e-lytvynskyi/m5):** Horsemen cannot damage buildings (no `Attack/Structure` torch; only spearmen burn buildings, CMB-03). Recommend keeping it: the horseman is the raider of workers and archers, the spearman takes buildings. Say if cavalry should carry torches too.
18. **M5 (dev/o-e-lytvynskyi/m5):** Performance watch for M6: the battle soak's per-unit A is 1.139 × M4's (gate 1.15) and its cost is movement and unit AI, not combat; 300 units crowding a ford in one burst can stall one large unit past the stuck invariant. Both are inputs for M6's spec health check, not M5 blockers.
19. **M5 (dev/o-e-lytvynskyi/m5):** Done 2026-10-28 (b): the battle bot could not finish a match against itself: after the first waves annihilate each other only archers remain, which cannot burn a centre, and the bot's wood no longer buys spearmen. Options: (a) leave it (the scenario is player against bot, which ends); (b) keep a spearman reserve for buildings and train spearmen late (a small T13-scope change). Recommend (b) in M6's first tasks, together with item 8's wood starvation.
20. **M5 (dev/o-e-lytvynskyi/m5):** Done 2026-10-28 (b, interleaved; 0.294–0.297 of 0.30): `perftests.idle_300_costs_a_third` fails on main itself on this Mac whenever other apps load the CPU (0.31–0.35 at foreign CPU 0.11–0.13; 0.155 at 0.004), though its foreign-CPU guard only invalidates above 0.25. Options: (a) run perf only on a quiet machine (as now); (b) lower the guard's invalid line for this ratio test, or measure idle and walking interleaved in one run so both halves see the same load (a measurement change: yours). Recommend (b), interleaved.
21. **M5 (dev/o-e-lytvynskyi/m5):** `perftests.first_loop_soak`'s per-unit A gate (base 11,648 + 15 %) lies inside this Mac's run-to-run spread on unchanged code (11,123–13,399 ns, foreign CPU 0.10–0.14), so it will fail now and then without a code change. Options: (a) keep it and rerun once on a red as the rule allows; (b) re-measure the base as the median of several runs on a quiet machine (a baseline decision: yours). Recommend (b) before M6's first perf gate.
22. **M5 (dev/o-e-lytvynskyi/m5):** Demo for your playtest: `scripts/playtest.sh`, then `/strategos match create map strategos:proving_ground`, `/strategos match join 1 0`, `/strategos match scenario strategos:battle_at_the_ford` (spec §1.3); the M5 chapters alone: `./gradlew :mod:runScenario -Pscenario=demo -Pdemo=m5 -Pscenario.timeoutSeconds=1200`. Your verdict (win once, lose once, the questionnaire, the silhouettes at 60 blocks for UNIT-07 check 4) goes into the spec's Acceptance.

## Since the owner's last visit

Nothing yet: the owner was here on 2026-10-29.

## Milestones

| Milestone | Status | Held by | Spec | Notes |
|---|---|---|---|---|
| M0 Foundation | Accepted 2026-10-22 | the owner's sessions (`o-e-lytvynskyi`) | [M0](milestones/M0-foundation.md) | Started 2026-10-21; tripwire met on day 2; 25 of 26 cards accepted, UNIT-01 closes with NET-01 (M1); the L7 demo waived by the owner (item 2) |
| M1 Movement | Done 2026-10-23 | the owner's sessions (`o-e-lytvynskyi`) | [M1](milestones/M1-movement.md) | Started 2026-10-22 on the owner's go-ahead; 29 tasks (T28, PATH-08, joined by its R3 trigger in T8; T29 by the owner's word); Accepted waits for the L7 demo (item 5) |
| M2 Command | Done 2026-10-23 | the owner's sessions (`o-e-lytvynskyi`) | [M2](milestones/M2-command.md) | Started 2026-10-23 at the owner's word; 23 tasks; `pose_high` settled in M3 T30; Accepted waits for the L7 demo (item 4) |
| M3 First loop | Done 2026-10-24 | the owner's sessions (`o-e-lytvynskyi`) | [M3](milestones/M3-first-loop.md) | Started 2026-10-23 at the owner's word; 31 tasks; Accepted waits for the L7 demo (item 7) |
| M4 Economy and construction | Done 2026-10-24 | the owner's sessions (`o-e-lytvynskyi`) | [M4](milestones/M4-economy-and-construction.md) | Started 2026-10-24 as M3 closed; 24 tasks, closed by T28 (2026-10-24: Results written, [§8.1](milestones/M4-economy-and-construction.md); the health check's follow-ups are M5's first tasks); Accepted waits for the L7 demo (item 9); item 8 asks your word on the perf base |
| M5 Combat | Done 2026-10-28 | the owner's sessions (`o-e-lytvynskyi`) | [M5](milestones/M5-combat.md) | 24 tasks; merged by [PR #33](https://github.com/o-e-lytvynskyi/strategos/pull/33) without a separate maintainer review; the owner closed it 2026-10-29; Accepted waits for the L7 playtest (item 22) |
| M6 (MVP) | approved | — | — | Its spec is written when M5 closes |
| M7–M15 | proposed | — | — | Approved by the owner closer to their time |

Overview of the roadmap: `python3 tools/roadmap.py summary`.

## Open for claims

Milestones whose tasks any developer may claim (AGENTS.md "Developers and claims"), opened on the owner's word.
Planning work (the next milestone's spec) is always claimable.

- None open. M6 opens on the owner's word.

## Standing restrictions

None. The defaults of AGENTS.md "Autonomy by default" apply: everything a developer session needs is allowed —
client windows, the recommended decisions, subagents, any test, no pauses for reports. A restriction the owner gives («без окон», «без субагентов»)
goes here with its date and its end («до вечера», «до конца M5»), and is deleted when it ends.

## Decisions taken

Decisions taken on a recommendation and merged into `main` (from the branch files at each merge, marked with the
branch), and the maintainer's own: what, why, how to undo. The owner vetoes by answering here; a veto becomes a
`fix-…` claim. The list is cleared once the owner has seen it.

- (dev/o-e-lytvynskyi/m5) 2026-10-28 · The large-class grid is built lazily (see the log); behaviour-neutral by test against an eager grid. Undo by publishing eagerly in MatchTerrain.publishAll.
- (dev/o-e-lytvynskyi/m5) 2026-10-28 · JMH forks per benchmark class (owner's choice, For the owner 5 (b)). Undo by restoring `-f 2` in build-logic's jmh task.
- (dev/o-e-lytvynskyi/m5) 2026-10-28 · Golden images taken after looking at each frame (AGENTS.md "a golden image you checked"): `m5_goldens` (macos, macos-sodium) and `art_look`'s `look_m5_24`/`look_m5_64`, with masks on `rider_high`'s two horizon corners and one spot of `volley_impact`, and a 0.01 threshold on `ford_battle` (a live bot battle). Undo by deleting mod/src/devtools/goldens/*/m5_goldens and the two look_m5 frames, or tightening goldens.json.
- (dev/o-e-lytvynskyi/m5) 2026-10-28 · PERF-14's "ours" in the observer battle scene is bars p50 plus HUD p50 (nothing is selected, so the rts-minus-moving split has no counterpart). Undo by adding a selection step to the scene.
- (dev/o-e-lytvynskyi/m5) 2026-10-28 · trial implementer: pass — the L4 scenes written without a window, every risk named in advance (the arrow jitter among them); two framing rounds after the orchestrator's runs.
- (dev/o-e-lytvynskyi/m5) 2026-10-28 · The battle scenario's L1 winner check plays the player's side with a small scripted general (attack-move, then spearmen at the centre) against the shipped bot; a bot-only match stalls (For the owner 11). The skirmish seats `ai:battle` in human slots of scenarios that use it. Undo in BattleAtTheFordTest / Skirmishes.
- (dev/o-e-lytvynskyi/m5) 2026-10-28 · trial implementer: pass — T15 and the bot fix; it kept the scripted side honestly when bot v bot would not end, and reported it.
- (dev/o-e-lytvynskyi/m5) 2026-10-28 · trial implementer: pass — T14 measured everything asked and named its thin margins; it skipped the slow gates after touching core, which the orchestrator ran.
- (dev/o-e-lytvynskyi/m5) 2026-10-28 · The barracks trains the whole roster (the cards made archer and horseman templates but named no trainer; 0 A.D.'s barracks trains infantry and cavalry). Undo in barracks.json.
- (dev/o-e-lytvynskyi/m5) 2026-10-28 · Battle-bot details: strength = Σ template cost × HP fraction; defence 600 and launch 0.6 untuned starting values; new soldiers do not join a wave already out; difficulty levels are separate script files (only battle.json ships). Undo in battle.json / Tactics.
- (dev/o-e-lytvynskyi/m5) 2026-10-28 · trial implementer: pass — T13, with the barracks gap raised as a question rather than worked around in shipped data.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · Spec finding 13 (the flash derived from received health, the target trailer); the first profile of `AttackLinesBench.extract300` added to perf/baseline-m4pro.json (home path rewritten). Undo by removing that row.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · trial implementer: pass — T12; it found and removed a boxing allocation the 0 B/op gate would have caught, and added the kernel to the gate's list.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · trial implementer: pass — T11 measured the raid sizes in L1 before choosing the L3 one, and reported the horsemen's zero building damage instead of tuning it.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · The bell: AllClear is faction-wide (it ends every bell order of the faction and releases every unit any bell called in); a ring with no worker in reach is accepted and still sounds; the first-person ring cancels vanilla's bell and the `BELL` fx sounds it; `TemplateLint` admits the bell's block entity (still refuses others); "no body" lives in BodyRegistry since `BodyMode` was only a design name. Undo per point in Bell / TownBell / TemplateLint.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · trial implementer: pass with a note — T10b complete, but it handed back before its own long gate run finished; the orchestrator awaited it and ran JMH.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · Garrison details §2.9 leaves open: any of the holder's classes admits (archers and spearmen as Infantry, horsemen refused); a full building fails the order on the way, not at command time; Garrison replaces orders, those queued behind it stay and run on release; orders given to a unit inside wait until release (attacks on an inside unit are refused); walking in ignores blows; non-lethal damage to an inside unit is ignored, lethal still kills; an empty Ungarrison counts as 20 at the rate limiter. Undo per rule in GarrisonRules / Garrisoning.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · trial reviewer: pass — two major and five minor findings on T10a, each with a scenario; one (the latch) turned out covered already, which the implementer showed with a test.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · trial implementer: pass — T10a and its review round, with a self-found JMH regression fixed before reporting.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · The economy bot's minimum wave raised to 14 spearmen (data only) so it overcomes the centre's arrows; neither the arrows nor the test moved. Undo in ai/economy.json.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · A town volley's range is checked at selection, not again at release (a target a block past the edge still takes the arrow). Undo by a release-time check for building shooters.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · trial implementer: pass — T9, and it reported the bot regression with its cause instead of relaxing the test.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · A spread attacker re-picks among the current targets of its spread's other attackers (the order's group id), not a saved original set, so nothing new is persisted; with none left it falls back to its stance's nearest enemy. A grouped ATTACK may now finish by the crowd-arrival rule when stuck, as grouped moves do. Undo with a persisted spread set.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · trial implementer: pass — T8 complete with a backward-compatible force flag; it flagged the limiter's default branch instead of editing an unnamed file.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · Tuned the §2.7 roster (above) to satisfy the 170-of-200 counters at equal cost; cost counted as food + wood + iron. Undo by reverting the provenance rows and rerunning DuelTest.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · Melee bonuses as numeric template leaves `Attack/Melee/bonus/<Class>` (the spec named "a template bonus" with no mechanism). Undo with the leaf.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · trial implementer: pass — T7, and on review it traced the L1/L3 gap to its own harness and fixed the harness rather than the threshold.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · The mounted rider is the unit renderer's own humanoid in a forced seated pose (armour, items, faction colours and LOD carry over); seat height 0.62 and the untinted horse wait for T17's images. The card's "inventory entry" is the engine-surface entity list: units have no spawn egg (noSummon). Undo in MountRenderer.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · PATH-11 check 3 reads "no ladder rung above repath" as rungs 2–5 at zero plus the existing stuck-under-100-ticks measure. Undo by a stricter reading in MapTests.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · trial implementer: pass — T6 with two follow-up rounds; it found the body-budget race when asked to fix rather than rename.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · A flight's point and its end are measured from the attacker (§2.4: "back to the order when it is 12 blocks off"), not from where the unit stood; StanceTest's passive case and WorkerModelTest now assert that. Undo by planning from the unit.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · The card's active stance is what the client knows (the stance its card last sent, else the template's); stances are not replicated. Proposal in For the owner 6. Undo n/a.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · trial implementer: pass — T5b in one round, reported the gate's one red row honestly instead of retrying.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · Spec finding 12 (stance details the table leaves open) and the template SHA re-pinned for the worker's passive stance (8bb9790c…); RangedOrdersTest's receding dummy moved inside sight (22 → 11 blocks) because the out-of-vision rule now ends that chase as specified. Undo with finding 12.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · trial reviewer: pass — found a real livelock and three determinism/cost defects in T5a, each with a scenario; read-only, no Gradle while JMH ran.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · trial implementer: pass — T5a plus its review round; it found the compiler's repeated path split behind the JMH regression instead of proposing a baseline.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · High ground uses the units' motion Y (the floor on terrain) and applies only to ranged attacks, as §2.3 states; the card's breakdown is the template tooltip (per-template knobs), the situational bonus is shown there as rates. Undo with the card's data knobs.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · Fix the reconciler GameTest's race (wait for the match's queued writes) rather than retry or mark it flaky. Undo is not recommended.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · trial implementer: pass — T4 met the brief, found the floor-Y convention and reported a GameTest failure it could not pin; the orchestrator traced it to a pre-existing race.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · Shot arcs render at AFTER_ENTITIES (the entity cutout render type fits there) and in any camera mode; size and orientation are checked in T17's images. Undo by moving the stage.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · trial implementer: pass — T3c.2 verified every engine name in the sources with file:line, reported the coordinate convention it found, and stayed inside its files.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · Re-take only the `ShotBench.volley60{mode=miss}` row of perf/baseline-m4pro.json (0.827 → 1.085 us/op): the growth is the 60 `ShotLanded` records the spec requires for misses, confirmed in two runs; limits unchanged. Undo by restoring the row from 95484c3.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · Fix `jmhCompare` to report missing benchmarks (it never did); a stricter gate, not a weaker one. Undo is not recommended.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · trial implementer: pass — T3c.1 matched the brief with no deviations, kept old constructors and bytes, named its one test-assertion change; the orchestrator read the core diff.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · Spec traceability for CMB-04 check 4 names the existing GameTest `combattests.archer_volley_replays_as_the_core_predicts` (M2's "as the core predicts" convention: the headless replay is the L1 prediction) instead of the planned `a_volley_damages_as_l1_predicts`. Undo by renaming the test and the row.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · Re-pin the builtin template set's SHA (e4624330…) and size 17 for the archer; the canonical form changed only by the appended template. Undo with the archer.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · trial runner: pass — three bounded gate/JMH runs with exact failing tests, pinned values and per-fork evidence; no retries or edits.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · Persist optional shot release origin/tick for future impact feedback across death/restore; preserve old snapshot and FX bytes when the extension is absent. Deliver once to viewers of either endpoint and clear client arcs on match changes; no past-launch resync is added. Undo through a reviewed feedback contract change.
- (dev/o-e-lytvynskyi/m5) 2026-10-27 · Derive flat-world target velocity from transient pre-/post-MOVEMENT positions at release. Preserve the existing pose-only flat snapshot/hash contract; terrain still uses MotionStore velocity. Undo by a reviewed motion-state version change. Bound ranged damage to finite float-safe values so accepted templates and modifiers cannot fail when converted to a damage packet.
- (dev/o-e-lytvynskyi/m5) 2026-10-26 · Establish first Mac profiles for active-shot hashing and preferred/collateral/miss volley60 cases from the full measured run. All existing profile scores and limits remain unchanged; remove only these four rows to undo. Keep T2's proposed baseline as its historical decision artifact.
- (dev/o-e-lytvynskyi/m5) 2026-10-26 · Use available standard worker gpt-6-sol for the unchanged tier-B contract after the earlier model's usage limit; runner and independent reviewer remained available. Trial implementer/reviewer/runner pass: scoped integration, actual continuation tests, representative combat fixture, bounded summaries and unchanged gates verified by the orchestrator.
- (dev/o-e-lytvynskyi/m5) 2026-10-26 · Accept the shot golden only after an independent SplitMix64/Box-Muller calculation matched 126/500 hits, first 32 outcomes and exact damage 126/256. The nearest circle-boundary margin is 0.000171829 in squared distance, well above floating-point rounding differences. Undo by replacing the test golden through a reviewed behavior change; statistical bounds and seed stay fixed.
- (dev/o-e-lytvynskyi/m5) 2026-10-26 · Use circular body-radius footprints with a body-height check and frozen aim Y to avoid cross-layer hits; preferred full target handle precedes ascending any-faction collateral. This refines the approved landing-point model. Undo by revising the collision policy and its explicit circle/bridge/friendly-collateral tests.
- (dev/o-e-lytvynskyi/m5) 2026-10-26 · Continue ready M5 tasks once this branch's full functional and performance gates are green, including both historical failure counterexamples. Keep the separate Linux main investigation assigned and unresolved; its release proposal is optional coordination, not a reason to idle validated M5 work. Undo by pausing features until that separate claim is delivered.
- (dev/o-e-lytvynskyi/m5) 2026-10-26 · trial implementer: pass — integration and performance briefs stayed within named source/test files, preserved old query/reload behavior, demonstrated meaningful regression cases and differential terrain checks, and returned focused/full-fast evidence without commits or records edits.
- (dev/o-e-lytvynskyi/m5) 2026-10-26 · trial reviewer: pass — found and then verified fixes for eviction, mixed slots and quadratic page access; final cache/signature review has no findings, with the orchestrator reading source/test diffs.
- (dev/o-e-lytvynskyi/m5) 2026-10-26 · trial runner: pass — final full gates, isolated controls/diagnostics, targeted perf and full JMH ran serially with bounded summaries; no unrequested retries or baseline edits. Corrected corpus counts distinguish 1,939 functional tests from two extra perf tests.
- (dev/o-e-lytvynskyi/m5) 2026-10-26 · Establish TerrainEditBench's two-grid Mac profile at 481.023 us/op after reducing the initial recovery cost from 1027.918, confirming a 290.034 us/op same-host T1 control and passing absolute D (0.427499 ms p99 / 2 ms) and idle ratio (0.218951 / 0.30). This is the new class's measured cost within the planned roughly twofold rebuild; owner delegated recommended baseline decisions under AGENTS.md. Keep the 15% limit and every other existing score/budget unchanged. ADR-0019 and a retained proposal record it; undo by restoring only this benchmark's prior row from 5437a1f. Final full comparison is still required.
- (dev/o-e-lytvynskyi/m5) 2026-10-26 · Split mobility recovery and measured performance work; keep acceptance and code delivery atomic so no red intermediate implementation is committed. Undo by collapsing the two progress entries; the gates and feature scope remain unchanged.
- (dev/o-e-lytvynskyi/m5) 2026-10-26 · Recover reviewed T1 source and tests selectively; keep historical Windows artifacts in the preserved branch and require fresh current-host evidence. Undo by reverting this recovery commit; no pushed history is rewritten.
- (dev/o-e-lytvynskyi/m5) 2026-10-26 · trial runner: pass — baseline, deliberate red tests and final gates ran sequentially with compact verdicts, timings and no unrequested retries; the negative checks failed before the fixes and passed afterwards.
- (dev/o-e-lytvynskyi/m5) 2026-10-26 · trial reviewer: pass — reviewed immutable T1 source without edits/builds, verified semantic preservation and scene guard behavior; T2 findings are being validated separately.
- (dev/o-e-lytvynskyi/m5) 2026-10-26 · Follow the owner's explicit takeover authorization through a release PR followed by an assignment PR; retain all historical branches and evidence. Undo by rejecting the release or assigning M5 back by PR.
- (dev/o-e-lytvynskyi/m5) 2026-10-26 · Prefer selective reuse of legacy T1-T2 over reimplementation, subject to review and current verification. Keep Windows evidence labeled and sanitize personal paths without changing numbers; discard the import if validation reveals an unsuitable design.

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
    developer's row to claims.md (since 2026-10-29: only the branch file, which is the claim) and is merged before any development
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
