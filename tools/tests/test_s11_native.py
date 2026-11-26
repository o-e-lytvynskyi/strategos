from datetime import datetime, timedelta, timezone
import json
import unittest

from test_s11_reports import scene
from s11_native import PACKET_EVENT, SUMMARY_EVENT, TICK_EVENT, compare

SCENE = "s11_pan"
ADDRESSES = {"A": "private-a", "B": "private-b"}
START = datetime(2026, 11, 22, 12, tzinfo=timezone.utc)


def event(kind, offset, **values):
    return {"type": kind, "values": {
        "startTime": (START + timedelta(milliseconds=offset)).isoformat(), **values,
    }}


def capture():
    events = [event(TICK_EVENT, i * 50, coreTick=i + 249, scene=SCENE) for i in range(1201)]
    events[-1]["values"].update(hits=50, placements=0, maxUnits=600, movingSamples=55,
                               fogEdgeSamples=0, panA=2400, panB=2398)
    for address in ADDRESSES.values():
        events.append(event(SUMMARY_EVENT, -100, remoteAddress=address, sentBytes=147, sentPackets=6))
        for offset, size, packet in (
            (-1, 11, "minecraft:custom_payload"),
            (0, 20, "minecraft:custom_payload"),
            (49, 18, "minecraft:section_blocks_update"),
            (50, 17, "minecraft:level_chunk_with_light"),
            (999, 61, "minecraft:add_entity"),
            (60_000, 20, "minecraft:custom_payload"),
        ):
            events.append(event(PACKET_EVENT, offset, remoteAddress=address, bytes=size,
                                packetId=packet, packetDirection="clientbound", protocolId="play"))
    return events


class NativeReportTest(unittest.TestCase):
    def test_native_buckets_use_exact_half_open_boundaries_and_hide_addresses(self):
        result = compare(SCENE, capture(), ADDRESSES, scene())
        for peer in ("A", "B"):
            measured = result["peers"][peer]
            self.assertEqual(measured["gameplayBytes"], 99)
            self.assertEqual(measured["gameplayMaxOneSecondBytes"], 99)
            self.assertEqual(measured["gameplayPeakBytesPerTick"], 61)
            self.assertEqual(measured["terrainBytes"], 17)
            self.assertEqual(measured["wholeRecording"]["summaryMinusPacketEventsBytes"], 0)
        self.assertEqual(result["peers"]["A"]["panBlocksPerGameSecond"], 40)
        self.assertNotIn("private-", json.dumps(result))

    def test_rejects_truncated_window_duplicate_peer_and_missing_gameplay(self):
        with self.assertRaisesRegex(ValueError, "boundaries"):
            compare(SCENE, capture()[1:], ADDRESSES, scene())
        with self.assertRaisesRegex(ValueError, "two separate"):
            compare(SCENE, capture(), {"A": "same", "B": "same"}, scene())
        no_custom = [item for item in capture() if item["values"].get("packetId") != "minecraft:custom_payload"]
        with self.assertRaisesRegex(ValueError, "missing play payloads"):
            compare(SCENE, no_custom, ADDRESSES, scene())
