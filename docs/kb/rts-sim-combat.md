# Analytic combat state

## Used-empty allocators are still state

A shot store may have no active rows while its next ID has advanced. Persist and hash the
allocator in that case: the next shot's ID and stateless RNG coordinates depend on it.
Only a pristine store may be omitted for compatibility with older saves and journals.
Snapshot rows and damage bonuses must be immutable; retain complete generation handles,
and do not discard an in-flight packet just because its shooter has died.

## Align release positions with the movement phase

UNIT_AI runs before MOVEMENT; COMBAT runs afterwards. If a launcher reads the old target
position in UNIT_AI but schedules impact at current tick plus flight ticks in COMBAT,
the target receives an extra movement step relative to that lead. For T3b, queue firing
intent during UNIT_AI and construct the frozen launch from current positions/velocity in
COMBAT after MOVEMENT. Keep the approved ceil(distance/speed) and velocity-times-flight
formula; verify a constant-velocity positive control before testing direction changes.

## Resolve a point against the current body

A circular radius test avoids treating the corners of a body's bounding square as hits.
Also test the current body-height interval so units below a bridge cannot receive an impact
aimed at its upper layer. Prefer the original complete live target handle, then use stable
spatial candidate order for collateral; a recycled index is not the old target.
