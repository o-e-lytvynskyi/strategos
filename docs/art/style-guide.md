# Style guide — the placeholder look (ART-01)

One page that every unit, building and HUD asset follows until the civilisation art pass (ART-03).
The direction is D-04 as the owner chose it on 2026-10-21: vanilla-faithful, with custom models only
where vanilla has nothing that reads. The rules here are checked where a test can check them
(`UnitLookTest`, `StyleGuideRangesTest`, the blueprint lint); the rest is the owner's look in the
demo (ART-01.4).

## 1. Texels, palette and render types

- **16 × 16 texels per block face**, vanilla's density. No higher-resolution textures, no
  smoothed or painted ones beside vanilla's.
- **The vanilla palette.** Colours come from vanilla blocks and items. Faction colours are the seven
  of §2, nothing else.
- **Vanilla render types only**: solid, cutout and translucent blocks, vanilla's entity, armour and
  item renderers. No custom shaders. The placement ghost and the HUD use vanilla's GUI and debug
  quads.
- **Every asset is original or vanilla's own, referenced at run time.** Nothing is copied from
  another game or mod (ADR-0005). Textures the mod ships are drawn for it.

## 2. Faction colour

The owner of a body or a building reads at a glance from 24 to 64 blocks up:

| Faction | Colour (RGB) | Dye for banners |
|---|---|---|
| 0 | `#3B82F6` blue | blue |
| 1 | `#EF4444` red | red |
| 2 | `#22C55E` green | lime |
| 3 | `#EAB308` yellow | yellow |
| 4 | `#A855F7` purple | purple |
| 5 | `#F97316` orange | orange |
| 6 | `#06B6D4` cyan | cyan |

- The same colour marks the selection outline, the HUD's faction swatch and the unit's leather.
- Every unit of a civilisation (`Organic`) wears a **leather tunic dyed its faction's colour**.
- A shield carries the faction's dye as its banner's base.
- The debug walker is a test body: it wears nothing, so the goldens of M1 and M2 keep their look.

## 3. Role

The role reads from what a body wears and holds, never from text:

| Role | Wears | Holds at rest | Holds at work |
|---|---|---|---|
| Worker | the dyed tunic | nothing | the tool of its work (iron axe for wood, pickaxe for stone and iron, hoe for fields, the mace while building) and its load in the other hand: a log, berries, wheat, cobblestone, raw iron |
| Spearman | an iron helmet, the dyed tunic and dyed leggings | a trident, vanilla's nearest spear, and a shield with the faction's banner | the same |

A new role adds a row here first: one silhouette change (headgear, a held item or a shield) that
reads at 64 blocks, and no two roles alike.

## 4. Buildings

Placeholder blueprints are generated in code (`PlaceholderBlueprints`) from **non-flammable
blocks**, shipped as structure files, and rise block by block in build order. Owner-built
blueprints may replace them later (an owner task); they keep the same ranges.

| Class | Footprint, longer side | Height | Blocks | The placeholder |
|---|---|---|---|---|
| Field | 4–8 | 1–2 | 16–128 | 5 × 2 × 5, 50 blocks |
| House | 4–7 | 4–7 | 60–200 | 5 × 5 × 5, 96 blocks |
| Barracks (production) | 7–11 | 5–9 | 150–400 | 9 × 6 × 7, 217 blocks |
| Storehouse (wood, stone, iron) | 5–8 | 4–6 | 80–200 | 6 × 5 × 6, 128 blocks (BLD-08) |
| Farmstead (food) | 5–8 | 3–6 | 60–160 | 6 × 4 × 5, 88 blocks (BLD-08) |
| Civic centre | 9–15 | 6–10 | 300–800 | 11 × 7 × 11, 442 blocks |
| Deposit (quarry, iron mine) | 3–6 | 2–4 | 18–90 | 3 × 2 × 3, 18 blocks; 5 × 3 × 5, 59 blocks (ECO-08: spawned, never built) |

- **They read at camera heights of 24–64 blocks**: a house is at least 4 blocks high, so its
  silhouette stands out from the ground at the top height; a civic centre is the largest thing in
  a town.
- **They rise at a steady, visible rate**: with one builder, 1.5–6 blocks a second over the
  building's `Cost/time` (the placeholders: 2.5, 3.2, 3.6 and 2.9). Slower looks stalled; faster
  looks like a blink.
- A door marker sits in the doorway's lower cell, a spawn marker above it for trainers; the two
  lowest outer layers stay solid (the blueprint lint, BLD-01).

## 5. The HUD

- Icons are vanilla items, named by a template's `Identity/icon` and drawn at whole multiples of
  16 pixels (8 in the resource bar). Resources use an apple, an oak log, cobblestone and an iron
  ingot.
- Panels are dark and translucent with a thin border (`HudTheme`); text is vanilla's font, white,
  the accent gold, warnings red.
