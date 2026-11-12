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

Flat matches retain only poses in MotionStore; their vx/vz are not a current velocity.
Capture the target position with the transient firing intent and derive displacement after
MOVEMENT there. Do not populate persisted terrain motion fields just for flat shots: that
changes released movement hashes and snapshot bytes. The pending intent is drained in
COMBAT and adds no state at the FINAL snapshot boundary.

## Resolve a point against the current body

A circular radius test avoids treating the corners of a body's bounding square as hits.
Also test the current body-height interval so units below a bridge cannot receive an impact
aimed at its upper layer. Prefer the original complete live target handle, then use stable
spatial candidate order for collateral; a recycled index is not the old target.

## Validate values before freezing a float damage packet

Ranged templates use doubles, but Damage stores finite nonnegative floats. Reject a raw
ranged amount above Float.MAX_VALUE at load time and clamp modifier growth to that bound.
Finite modifiers can still overflow and then multiply by zero within one stage, producing
NaN. Typed ranged accessors fail closed for that result: zero damage/range/spread, minimum
positive projectile speed, and bounded integral timers. Accepted content must not crash
COMBAT when its packet or firing intent is constructed.
