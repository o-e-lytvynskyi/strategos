# PERF-10 reference reports

The owner accepted [GitHub Actions run 45088024574](https://github.com/o-e-lytvynskyi/strategos/actions/runs/45088024574)
on 2026-11-25 as the reference for PERF-10. It measured commit
`bd23adc254a5c4d5f65c33785a7683e77faafdf0` on Ubuntu 24.04 with an AMD EPYC 7763,
four logical CPUs and Temurin 21.0.13+9. The normal workload measured 24,000 ticks across four
factions with 150 population and 30 finished buildings each. The stress workload measured
6,000 ticks with twice that scale. Both censuses and all applicable budgets passed.

The host C p95 was 6.748 ms in the normal workload and 10.505 ms in the stress workload.
The four gameplay means were 6,188, 5,744, 4,795 and 6,771 B/s against 102,400 B/s per client.
The 100-unit path check passed at three ticks p95 with 200 requests queued.
[Paired JMH run 45088571229](https://github.com/o-e-lytvynskyi/strategos/actions/runs/45088571229)
passed all 30 configurations on the same candidate.

The JSON files preserve the reported measurements. The journal and CPU-source paths use only
their final components. The original runtime logs and journals were included in the workflow
artifact. This acceptance applies to PERF-10 on this runner class. Existing thresholds remain
in use.

Recheck the reports with `python tools/m7_perf_reports.py perf/m7`.

| File | SHA-256 of original report |
|---|---|
| `m7_multiplayer.json` | `58015981686c357a00913a832217f5937454cdcc4c9bd75971dc4ea917d88b18` |
| `m7_multiplayer_stress.json` | `091807738e7251eeb750fe4be5b14ff0a3ba6e261004e1c8e9cf3529dd9cef01` |
| `path_budget_d.json` | `6484bae28ca8552314d846b5dce47a25451ff87bea7817349e43372c45689318` |
