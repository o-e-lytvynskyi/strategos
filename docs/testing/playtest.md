# Playtest protocol (L7): the MVP

How the owner, and two friends, try the first skirmish under real conditions, and how the result is
recorded so that sessions compare (QA-09). It serves M6's gate ([M6 §1.3](../milestones/M6-first-skirmish.md)):
one match per level, the audio checklist (UI-21.4), a newcomer's first five minutes (UI-14.4), and a
report with no score of 2 or less.

The demo chapters of M6 (`-Pdemo=m6`: Research, Town, Fog, Raid, Siege, Tower, Notices) show each mechanic
alone and are a quick way to see what to look for before a match; they replace no part of this protocol.

## Starting

From the repository root. The singleplayer route (Easy, Normal) and the dedicated route (Hard, and any
level the owner wants on a server) are the two ways in:

- **Singleplayer:** start the dev client, title screen, **Skirmish**: map *First skirmish*, AI level, the rest
  default, **Start**. The arena world is created the first time and reused after; the progress bar runs and the
  match begins in the dark (fog).
- **Dedicated server:** `scripts/playtest.sh` builds the mod, starts the dev server in this terminal and, once it
  is up, two clients by Quick Play (Dev1, Dev2). In the server console: `op Dev1`; then, as Dev1,
  `/strategos skirmish strategos:first_skirmish level=<easy|normal|hard>`. `stop` ends the server. Client logs:
  `mod/run/client/console.log`, `mod/run/client2/console.log`.

Close every other heavy program first, and note the machine: a slow frame rate colours every score below.

## 1. The owner's matches: one per level

Three matches against the computer player, **Easy, Normal, Hard**, each played to its end. At least one of the
three is on a dedicated server; **win on Easy**. Per match, note the start and end times, then follow the
script below at your own pace. Between matches, fill in the questionnaire for the match just played.

| Step | What to do | What must be true |
|---|---|---|
| 1 | Start the skirmish at the level | The match starts within two clicks of the title screen; the ground is dark |
| 2 | Scout with the horseman | Explored ground stays dim; the enemy shows only while seen |
| 3 | Gather all four resources; research a gathering technology at the storehouse | The feed names the research and chimes; the rate rises |
| 4 | Build five village buildings; open the phase panel on the civic centre | The checklist is green; Town researches; every player gets the notice |
| 5 | Research iron weapons and chainmail at the barracks | The spearmen show the tiers |
| 6 | Hold the first wave: the bar fills while it musters at its camp, the horn sounds | The camp has a campfire and two banners; the bar empties as the wave marches; soldiers, a defence tower and the bell hold it |
| 7 | Build the arsenal, train two rams, raze the enemy's buildings | The match ends; the end screen shows victory or defeat and the statistics |
| 8 | Quit to the title screen, start a second skirmish | It is a match of its own; nothing carries over |

Level checks, besides the script. **Easy:** the owner wins, with time left to look around. **Normal:** a
match with decisions: at least one choice (where to put the tower, when to ring the bell) changes what
happens. **Hard:** the computer is stronger by timing, reaction and decisions, never by free resources: when
it wins, say what it did that you did not.

## 2. Audio checklist (UI-21.4)

Play with the game's volume at its default and the *Hostile Creatures* and *Friendly Creatures* sliders at
100 %. Each sound plays at most once per three seconds; two events inside that window make one sound. Tick
each when you heard it **unprompted** (without looking at the feed), at the moment it should.

| Event | Sound (vanilla) | Listen for | Heard |
|---|---|---|---|
| Under attack | the bell (`block.bell.use`, low) | a deep bell as a unit or building is hit, and not again for three seconds | [ ] |
| A wave announced | the raid horn (`event.raid.horn`) | the horn when the raid bar appears, not a second time during the march | [ ] |
| A phase reached | the challenge toast (`ui.toast.challenge_complete`) | the toast fanfare when Town is researched, whoever researched it | [ ] |
| Research finished | the level-up chime (`entity.player.levelup`, quiet) | a soft chime at the storehouse or barracks research | [ ] |
| A unit trained | the villager cheer (`entity.villager.celebrate`, quiet) | a short cheer as a batch steps out | [ ] |
| A building finished | the anvil (`block.anvil.use`, higher pitch, quiet) | a clang when the last block goes in | [ ] |
| Economy notices (idle worker, housing needed) | none, on purpose | the feed shows them and nothing sounds | [ ] |

Also tick: no sound is missing for an event that has a line in the feed; none plays for an event that is
silent; the bell is audible over the fighting; no sound is painful at the default volume; the sliders mute
and lower them.

## 3. The friends' test (UI-14.4)

Two friends who have never seen the game play **one match each**, on Easy, with **the hints alone**: no
manual, no explaining, no pointing. You sit beside them and say nothing but "go on" and "what are you
thinking?". Write down the clock time of each moment below; stop the test at ten minutes or at the first
time they say they are stuck for good.

First five minutes, pass if the friend, unaided:

- [ ] selects a unit and sends it somewhere (the *first order* hint)
- [ ] sends workers to gather wood or food, and sees the resources rise
- [ ] notices an idle worker and puts it back to work (the *idle worker* hint)
- [ ] builds a house before training stops for housing (the *build a house* hint)
- [ ] trains at least one more unit
- [ ] opens the key-bindings overlay when asked "how would you ...?" and finds the answer

Record, per friend: age band and which strategy games they know; the clock time of each tick above, or of
the point where they stalled; every moment they asked a question or said "what does that do?"; every hint
they dismissed unread; whether they would play again, and why. A friend who stalls on one of the first five
steps is a finding on the hints, never on the friend.

## 4. Questionnaire

Score each item 1 (poor) to 5 (great), or `n/a`, with one line of reason, **per level played**. A score of 2
or less is a finding: it needs a bug or a card before the milestone closes.

| Item | Easy | Normal | Hard | Reason |
|---|---|---|---|---|
| Control feel: do orders do what you meant, without fuss? | | | | |
| Readability at the default camera height: units, their state, what is hostile | | | | |
| Fog: is what you do not see clear, and fair? | | | | |
| The feed and the sounds: did you learn of everything that mattered? | | | | |
| Waiting: is there time spent waiting for nothing? | | | | |
| Decisions that mattered: did any choice change the outcome? | | | | |
| The opponent: its openings, its waves and their warning, its defence | | | | |
| The level: is it what its name promises? | | | | |
| Minecraft feel: does it still feel like Minecraft? | | | | |
| Performance: frame rate and stutter in the biggest fight | | | | |

Would I play another? Yes or no, with a reason:

## 5. The report

```
Date:
Milestone and commit:
Machines (server, clients):
Matches: <level, result, length, singleplayer or dedicated>
Script (section 1): <steps that failed, or "all passed">
Audio checklist (section 2): <events not heard or wrong, or "all heard">
Friends (section 3): <per friend: ticks, stalls, questions>
Questionnaire: <the table above>
Bugs: <one line each: severity, what happened, how to reproduce>
Desync seen: no | yes: <what, how long>
Would I play another: <yes or no, why>
Other notes:
```

A report with a score of 2 or less, a P0 or a P1 open, or Easy not won does not pass the gate: the findings
go to the milestone's findings list, each with its fix or its decision.

## Bug severity

| Severity | Meaning | Example |
|---|---|---|
| P0 | Data loss, a crash, or a match that cannot be finished | the server crashes mid-wave; a saved match is gone after a restart; the match never ends after the last civic centre falls |
| P1 | A feature is broken | the raid bar never shows; research takes the cost and never completes; fog shows enemies it should hide |
| P2 | Wrong but usable | a ram stops half a block short of its target; a sound plays twice inside three seconds |
| P3 | Cosmetic | a banner faces the wrong way; a feed line is clipped at the screen's edge |

## Desync

The roadmap counts a desync when a viewer's pose or health of a unit differs from the core state for more
than 10 ticks. By eye: the same unit stands visibly apart on the two clients for more than half a second, or
`/strategos match list` shows a different number of bodies than units for longer than that. Example: a unit
still standing on Dev2's screen while it walks on Dev1's.
