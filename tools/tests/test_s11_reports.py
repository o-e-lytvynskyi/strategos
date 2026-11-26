import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import s11_reports as reports


def scene(name="s11_pan"):
    viewers = []
    for viewer in range(4):
        viewers.append({
            "viewer": viewer,
            "packets": 1200,
            "bytes": 3600,
            "ours": 1200,
            "vanilla": 2400,
            "failures": 0,
            "terrainBytes": 1200,
            "gameplayBytes": 2400,
            "vanillaGameplayBytes": 1200,
            "gameplayMeanBytesPerSecond": 40,
            "gameplayPeakBytesPerTick": 2,
            "gameplayMaxOneSecondBytes": 40,
            "acceptedPanTicks": 1200,
            "totalPerTick": [3] * 1200,
            "terrainPerTick": [1] * 1200,
            "gameplayPerTick": [2] * 1200,
            "types": {
                "minecraft:level_chunk_with_light": {"packets": 400, "bytes": 1200},
                "minecraft:section_blocks_update": {"packets": 400, "bytes": 1200},
                "payload/strategos:unit_summary": {"packets": 400, "bytes": 1200},
            },
        })
    return {
        "scene": name, "warmupTicks": 200, "firstTick": 250, "lastTick": 1449,
        "measuredTicks": 1200, "ticksPerSecond": 20, "wallSeconds": 61.0,
        "minUnits": 512, "maxUnits": 600, "movingSamples": 60,
        "fogEdgeSamples": 30, "placements": 10, "hits": 42,
        "limitBytesPerSecond": 102400, "viewers": viewers,
    }


class ReportGuardTest(unittest.TestCase):
    def test_preserves_gameplay_block_updates_and_excludes_terrain(self):
        result = reports.validate_scene("s11_pan", scene())
        self.assertEqual(result["gameplayMeans"], [40] * 4)
        self.assertEqual(result["terrainBytes"], [1200] * 4)

    def test_rejects_truncated_window_duplicate_viewer_and_wrong_peak(self):
        for path, wrong in [((0, "totalPerTick"), [3] * 1199),
                            ((3, "viewer"), 1),
                            ((2, "gameplayMaxOneSecondBytes"), 39)]:
            with self.subTest(path=path):
                raw = scene()
                raw["viewers"][path[0]][path[1]] = wrong
                with self.assertRaises(ValueError):
                    reports.validate_scene("s11_pan", raw)

    def test_rejects_diluted_mean_and_a_gameplay_packet_counted_as_terrain(self):
        for field, wrong in [("gameplayMeanBytesPerSecond", 30),
                             ("terrainBytes", 2400)]:
            with self.subTest(field=field):
                raw = scene()
                raw["viewers"][0][field] = wrong
                with self.assertRaises(ValueError):
                    reports.validate_scene("s11_pan", raw)

    def test_rejects_empty_fog_and_construction_controls(self):
        for name, field in [("s11_fog", "fogEdgeSamples"), ("s11_construction", "placements")]:
            raw = copy.deepcopy(scene(name))
            raw[field] = 0
            with self.assertRaises(ValueError):
                reports.validate_scene(name, raw)
