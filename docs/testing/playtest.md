# Playtest protocol (L7)

How the owner tries a milestone under real multiplayer conditions, and how the result is
recorded so that sessions compare (QA-09). M0's demo is the walking skeleton: units created and
ordered by command, seen alike by two clients, surviving a restart.

## Starting

One command starts everything, from the repository root:

```
scripts/playtest.sh
```

It builds the mod and its launch scripts, starts the dedicated dev server in this terminal and,
once the server is up, two clients that join it by Quick Play: Dev1 (`runClientJoin`) and Dev2
(`runClient2`). The server console stays in the terminal: type `op Dev1` there, and `stop` to
end. The first run writes `mod/run/server/server.properties` with a flat world and offline
mode, because the dev clients use offline names. Client logs go to `mod/run/client/console.log`
and `mod/run/client2/console.log`.

## The M0 demo

1. In the server console: `op Dev1`.
2. Both clients join `localhost:25565` by themselves.
3. As Dev1: `/strategos match create 64 64`, then `/strategos debug spawn 3`, then
   `/strategos debug move ~20 ~`. Both clients see three humanoids walk 20 blocks and stop; the
   chat shows `accepted 3/3` after each order.
4. `/strategos hash` prints the tick and the state hash. `/strategos debug replay` prints
   `replay OK` with the same hash.
5. *(Arrives with M0 T9, persistence.)* Stop the server (`stop`), start it again with
   `mod/build/moddev/runServer.sh` in the same terminal, and rejoin from each client's
   multiplayer screen. `/strategos match list` shows the match `PAUSED` with the same tick;
   `/strategos hash` prints the same hash as before the stop. `/strategos match resume m1`, then
   `/strategos debug move ~-10 ~`: the units walk back.
6. `/tick freeze` stops the units; `/tick step 5` moves them exactly five ticks;
   `/tick unfreeze` lets them walk on.
7. Dev1 disconnects, creates a singleplayer world with cheats on, runs
   `/strategos match create 32 32` and `/strategos debug spawn 3`, saves and quits to the title
   screen, and creates a second world: there `/strategos match list` shows no match and no unit
   stands there (nothing carries over between worlds).
8. Fill in the questionnaire and the report below.

Other commands: `/strategos match pause`, `resume` and `dispose` (with an optional id such as
`m1`), `/strategos debug stop`, and `/strategos match list`, which shows each match's tick and its
units and bodies.

## Checklist

- [ ] The server and both clients started from `scripts/playtest.sh`.
- [ ] Both clients saw the same three units walk the same 20 blocks and stop.
- [ ] `hash` and `debug replay` printed the same hash.
- [ ] *(From T9.)* After the restart the match came back `PAUSED`, with the same tick and hash,
      and walked on after `resume`.
- [ ] `/tick freeze`, `step 5` and `unfreeze` did what they say.
- [ ] The second singleplayer world showed no match and no unit.
- [ ] No crash, no error in the server console, no desync (see below).

## Questionnaire

Score each item 1 (poor) to 5 (great), or `n/a`, with one line of reason.

| Item | Score | Reason |
|---|---|---|
| Control feel: do orders do what you meant, without fuss? | | |
| Readability at the default camera height: can you tell units and their state apart? | | |
| Waiting: is there time spent waiting for nothing? | | |
| Decisions that mattered: did any choice change the outcome? | | |
| The opponent's fairness and threat | | |
| Minecraft feel: does it still feel like Minecraft? | | |

Would I play another? Yes or no, with a reason:

M0 has no opponent and few decisions; `n/a` is the honest score there.

## Report template

```
Date:
Milestone and commit:
Machines (server, clients):
Checklist: <items that failed, or "all passed">
Questionnaire: <the table above>
Bugs: <one line each: severity, what happened, how to reproduce>
Desync seen: no | yes: <what, how long>
Other notes:
```

## Bug severity

| Severity | Meaning | Example |
|---|---|---|
| P0 | Data loss or a crash | the server crashes on `debug spawn`; a saved match is gone after a restart |
| P1 | A feature is broken | `debug move` is accepted but the units never walk |
| P2 | Wrong but usable | a unit stops half a block short of its target |
| P3 | Cosmetic | a unit faces the wrong way while it stands still |

## Desync

The roadmap counts a desync when a viewer's pose or health of a unit differs from the core state
for more than 10 ticks. By eye: the same unit stands visibly apart on the two clients for more than
half a second, or `/strategos match list` shows a different number of bodies than units for
longer than that. Example: a unit still standing on Dev2's screen while it walks on Dev1's.
