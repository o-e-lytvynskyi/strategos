# Golden journal re-baselines

- 2026-10-22: walking_skeleton, canary — First goldens: the binary journal format of M1 T1
- 2026-10-22: walking_skeleton, canary, crossing_synthetic — M1 T8: crossing_synthetic added, the first golden with terrain pages and path requests; walking_skeleton and canary re-recorded in journal format 2 and snapshot schema 2 with every checkpoint hash unchanged (a match without terrain keeps M0's hashes)
- 2026-10-22: walking_skeleton, canary, crossing_synthetic — M1 T28: once any path is requested the state hash mixes the last tick a terrain page changed (paths delivered after a change are checked against the current grid); crossing_synthetic changes, the M0 goldens keep their checkpoints
