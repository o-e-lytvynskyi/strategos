# Allied damage needs frozen source provenance

Source audit for future M7 T7: Damage stores an attacker handle, and ShotStore preserves that
frozen packet until impact. It does not yet store the launch faction. M5 deliberately permits
a shot to land after its shooter dies. Entity handles include a generation; freed indices are
reused. Snapshots retain live unit rows, not every dead shooter's old faction.

Do not add a friendly-fire filter that merely looks up the live shooter at impact: ignoring
the generation attributes damage to a new occupant, while requiring a live shooter cancels
an otherwise valid frozen shot. Before installing final allied filtering, design explicit
source provenance and its save/hash/legacy migration policy. Do not silently drop legacy
shots, guess their faction or overwrite released fixtures to make that policy pass.

The final application path must cover units and buildings, delayed/collateral damage and
dead/reused shooters, rather than only AI targeting. Control grants are not team relations.
This note records the unresolved integration finding; it does not implement or accept T7.
