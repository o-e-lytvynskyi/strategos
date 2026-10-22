# Golden journal re-baselines

- 2026-10-22: walking_skeleton, canary — First goldens: the binary journal format of M1 T1
- 2026-10-22: walking_skeleton, canary, crossing_synthetic — M1 T8: crossing_synthetic added, the first golden with terrain pages and path requests; walking_skeleton and canary re-recorded in journal format 2 and snapshot schema 2 with every checkpoint hash unchanged (a match without terrain keeps M0's hashes)
- 2026-10-22: walking_skeleton, canary, crossing_synthetic — M1 T28: once any path is requested the state hash mixes the last tick a terrain page changed (paths delivered after a change are checked against the current grid); crossing_synthetic changes, the M0 goldens keep their checkpoints
- 2026-10-22: walking_skeleton, canary, crossing_synthetic — M1 T9: units walk on surfaces (MotionSystem: floors, steps, turn rate, stuck monitor) and paths are pulled from the unit's exact point; crossing_synthetic changes, walking_skeleton is byte-identical, and canary's tick-1200 snapshot only lists each unit's keys in another order (the unit codec gained its motion field), with every checkpoint and the live hash unchanged
