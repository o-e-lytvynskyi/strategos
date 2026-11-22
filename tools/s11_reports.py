"""Reconcile each S11 packet total and rolling window against its recorded samples."""

import argparse
import json
import math
from pathlib import Path
import re
import sys

SCENES = ("s11_battle", "s11_pan", "s11_fog", "s11_construction")
TICKS = 1200
TPS = 20
LIMIT = 100 * 1024
TERRAIN = frozenset({
    "minecraft:level_chunk_with_light", "minecraft:light_update", "minecraft:forget_level_chunk",
    "minecraft:chunk_batch_start", "minecraft:chunk_batch_finished",
    "minecraft:set_chunk_cache_center", "minecraft:set_chunk_cache_radius",
})


def require(condition, message):
    if not condition:
        raise ValueError(message)


def nonnegative_integer(value):
    return type(value) is int and value >= 0


def validate_scene(scene, report):
    require(report["scene"] == scene, f"{scene}: report identity")
    require(report["measuredTicks"] == TICKS and report["ticksPerSecond"] == TPS
            and report["warmupTicks"] == 200 and report["lastTick"] - report["firstTick"] + 1 == TICKS,
            f"{scene}: measurement window")
    require(math.isfinite(report["wallSeconds"]) and report["wallSeconds"] > 0
            and 0 < report["minUnits"] <= report["maxUnits"] == 600
            and 0 < report["movingSamples"] <= TICKS // TPS, f"{scene}: workload")
    require(report["limitBytesPerSecond"] == LIMIT, f"{scene}: existing E limit")
    activity = "placements" if scene == "s11_construction" else "hits"
    require(report[activity] > 0, f"{scene}: no measured {activity}")
    if scene == "s11_fog":
        require(0 < report["fogEdgeSamples"] <= TICKS // TPS, f"{scene}: no measured fog edge")
    viewers = report["viewers"]
    require([viewer["viewer"] for viewer in viewers] == [0, 1, 2, 3], f"{scene}: four distinct viewers")
    result = {"gameplayMeans": [], "terrainBytes": []}
    for viewer in viewers:
        label = f"{scene} viewer {viewer['viewer']}"
        total = viewer["totalPerTick"]
        terrain = viewer["terrainPerTick"]
        gameplay = viewer["gameplayPerTick"]
        for lane in (total, terrain, gameplay):
            require(len(lane) == TICKS and all(map(nonnegative_integer, lane)), f"{label}: tick samples")
        require(all(t == a + b for t, a, b in zip(total, terrain, gameplay)), f"{label}: channel split")
        types = viewer["types"]
        for count in types.values():
            require(nonnegative_integer(count["bytes"]) and nonnegative_integer(count["packets"]),
                    f"{label}: malformed packet count")
        packet_bytes = sum(count["bytes"] for count in types.values())
        packet_count = sum(count["packets"] for count in types.values())
        terrain_bytes = sum(count["bytes"] for key, count in types.items() if key in TERRAIN)
        ours = sum(count["bytes"] for key, count in types.items() if key.startswith("payload/strategos:"))
        game_bytes = sum(gameplay)
        mean = game_bytes * TPS / TICKS
        second = max(sum(gameplay[i:i + TPS]) for i in range(TICKS - TPS + 1))
        require(viewer["failures"] == 0 and viewer["packets"] == packet_count > 0
                and viewer["bytes"] == sum(total) == packet_bytes and viewer["vanilla"] == packet_bytes - ours
                and viewer["ours"] == ours > 0
                and types.get("payload/strategos:unit_summary", {}).get("bytes", 0) > 0,
                f"{label}: packet totals or missing Strategos state")
        require(viewer["terrainBytes"] == sum(terrain) == terrain_bytes
                and viewer["gameplayBytes"] == game_bytes
                and viewer["vanillaGameplayBytes"] == game_bytes - ours, f"{label}: channel totals")
        require(viewer["gameplayPeakBytesPerTick"] == max(gameplay)
                and viewer["gameplayMaxOneSecondBytes"] == second
                and math.isclose(viewer["gameplayMeanBytesPerSecond"], mean, rel_tol=0, abs_tol=1e-9),
                f"{label}: peak or mean does not match samples")
        require(mean <= LIMIT, f"{label}: budget E exceeded ({mean:.2f})")
        if scene == "s11_pan":
            require(viewer["acceptedPanTicks"] == TICKS and terrain_bytes > 0,
                    f"{label}: pan workload or terrain delivery missing")
        result["gameplayMeans"].append(mean)
        result["terrainBytes"].append(terrain_bytes)
    return result


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("reports", type=Path)
    cli.add_argument("--commit", required=True)
    args = cli.parse_args()
    try:
        require(re.fullmatch(r"[0-9a-f]{40}", args.commit), "invalid measured commit")
        expected = {f"{scene}.json" for scene in SCENES}
        actual = {path.name for path in args.reports.glob("s11_*.json")} - {"s11_complete.json"}
        require(actual == expected, "missing or unexpected S11 scenes")
        scenes = {
            scene: validate_scene(scene, json.loads((args.reports / f"{scene}.json").read_text(encoding="utf-8")))
            for scene in SCENES
        }
        output = {"commit": args.commit, "scenes": scenes}
        (args.reports / "s11_complete.json").write_text(json.dumps(output, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(output))
    except (ValueError, KeyError, TypeError, OSError) as failure:
        print(f"S11: {failure}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
