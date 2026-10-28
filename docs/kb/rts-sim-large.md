# Large-unit navigation integration

## Clearance also governs eviction and formation recovery

The large grid excludes all columns whose eight neighbours intersect a footprint.
Checking only a unit's center during construction eviction can leave a stationary large
unit on a removed surface beside the building; it then has no start node for a path out.
Both commit-time eviction and the WORLD repair pass must test the same expanded footprint.
Infantry beside the building keeps its narrower clearance. `LargeIntegrationTest` covers
adjacent and diagonal placements through both eviction routes.

Formation slot validation is not the last assignment: stuck recovery can trade places.
Keep that trade within a mobility class, or validate both destinations for their recipients,
so a valid large destination is not exchanged for an infantry-only one. The integration
regression triggers a crowded trade and also preserves the same-class positive case.

## Publish an ordered changed-page list once

Finding the kth set bit by scanning from the beginning makes iteration over all changed
pages quadratic. Materialize ordered primitive indices when publishing the grid, then use
constant-time indexed reads. Keep this mutable cache per match; B8 rejects static arrays
even when their references are final. The existing halo regressions verify ordered page
notifications and immutable old grids.

## Cache the real invalidation inputs

A mobility lookup by template net index must be replaced only after a reload is accepted;
sparse/retired indices and unsupported query errors still matter. Empty-footprint eviction
can skip scanning only when no LARGE templates require boundary recovery.

Neighbouring large pages depend on full-height effective border voxel classes and footprint
bits, not just infantry surface hashes. Keep signatures for every edge and corner, conservatively
invalidate first publication and invalidate a decoded slice when a later capture replaces it
within the same batch. Differential fresh-grid tests catch stale ceiling and footprint state.
