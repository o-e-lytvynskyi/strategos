# ADR-0005: Clean-room development and licensing policy

Date: 2026-10-21

## Status

Accepted

## Context

- **The closest prior art is not open to copy.**
  - Reign of Nether, MineColonies, Structurize, BlockUI and Ancient Warfare 2 are GPL-3.0.
  - Villager Recruits and its family are all rights reserved.
  - 0 A.D.'s code and data are GPL-2.0-or-later, and its art is CC BY-SA 3.0.
- **An AI coding assistant that has such code in its context can reproduce it** without anyone
  noticing ([research/README.md §8](../research/README.md)).
- **The GPL is no escape hatch.** Relicensing Strategos under the GPL would not make copying
  clean: the FSF reads a GPL plug-in that forms one program with a proprietary host (Minecraft) as
  problematic ([research/mechanics.md §3](../research/mechanics.md)).
- **Some prior art may be read.** i113w Camera Lib, Freecam, Better Mine Team, MineFortress and
  PathWeaver are MIT, and RVO2 is Apache-2.0.
- **Strategos's own licence is the owner's decision D-07** (the recommendation is MPL-2.0 for
  code, assets all rights reserved with permission for modpacks). The repository stays private
  until then.

## Decision

1. **No forbidden sources, ever.** Implementation and design sessions never open the GPL or
   all-rights-reserved sources listed above, not even on the web. The behavioural notes in
   [research/](../research/) are the only input from them.
2. **Permissive sources may be read and adapted.** MIT and Apache-2.0 sources may be adapted with
   their licence and notice kept in `NOTICE` and changes marked in the file headers.
   - Today only RVO2's ORCA is adapted, as a port in `dev.strategos.core.sim.move`.
   - Camera Lib and Freecam may be read; the chosen camera design needs no code from them.
3. **No assets or data are copied,** and every number is re-derived at Minecraft scale. Templates
   say so where a value has a 0 A.D. or AoE counterpart.
4. **Every subsystem records its design sources:** which research note, paper or permissive
   project it follows. [04-architecture.md](../04-architecture.md) does this per section. The MVP
   gate (QA-13) checks the provenance notes.
5. **"Age of Empires", "AoE" and "0 A.D." never appear** in the mod's name, logo or primary tags.
   The README may credit them as inspiration.
6. **The GPL route is not taken.**

## Consequences

- Some mechanisms are re-invented from behaviour descriptions instead of read from working code,
  for example RoN's path worker pool and 0 A.D.'s template merge. This costs design time, which the
  research and this architecture have already spent.
- The licence decision D-07 stays fully open: nothing in the code forces a copyleft licence.
- Contamination is a process risk (R16). Code that mirrors the structure or names of a forbidden
  project is deleted and re-implemented from notes.
- `NOTICE` exists from the first commit that adds adapted code.

## Alternatives considered

| Alternative | Why not |
|---|---|
| License Strategos under GPL-3.0 and reuse RoN or MineColonies code | The FSF caveat about plug-ins in a proprietary host makes the route doubtful; it would also bind the owner's licence choice early |
| Read GPL sources "just to check" behaviour | The assistant can reproduce what it reads; the notes already describe the behaviour |
| Copy 0 A.D. template values as a balance starting point | GPL data; and Minecraft scale differs (speeds, ranges, block sizes), so the numbers would be wrong anyway |
| Copy prior-art assets as placeholders | Licence violation and visual debt; vanilla models and textures serve as placeholders |
