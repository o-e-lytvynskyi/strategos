"""Report the two S11 native connections from a local JFR JSON export."""

import argparse
from bisect import bisect_right
from collections import Counter, defaultdict
from datetime import datetime, timezone
import json
import math
from pathlib import Path
import re
import sys

from s11_reports import LIMIT, SCENES, TERRAIN, TICKS, TPS, require, validate_scene

TICK_EVENT = "strategos.S11Tick"
PACKET_EVENT = "minecraft.PacketSent"
SUMMARY_EVENT = "minecraft.NetworkSummary"
PEERS = ("A", "B")
EPOCH = datetime(1970, 1, 1, tzinfo=timezone.utc)
CUSTOM_PAYLOAD = "minecraft:custom_payload"


def timestamp(value):
    instant = re.fullmatch(r"(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d)(?:\.(\d{1,9}))?(Z|[+-]\d\d:\d\d)", value)
    require(instant is not None, "invalid JFR timestamp")
    whole = datetime.fromisoformat(instant[1] + instant[3]) - EPOCH
    return (whole.days * 86_400 + whole.seconds) * 1_000_000_000 + int((instant[2] or "").ljust(9, "0"))


def window(events, scene):
    ticks = sorted(
        (event["values"] for event in events
         if event["type"] == TICK_EVENT and event["values"]["scene"] == scene),
        key=lambda values: values["coreTick"],
    )
    require(len(ticks) == TICKS + 1, "run needs 1,201 measurement boundaries")
    first = ticks[0]["coreTick"]
    require(first > 200 and all(tick["coreTick"] == first + i for i, tick in enumerate(ticks)),
            "measurement core ticks must be consecutive and follow warm-up")
    boundaries = [timestamp(tick["startTime"]) for tick in ticks]
    require(all(a < b for a, b in zip(boundaries, boundaries[1:])), "measurement timestamps must increase")
    last = ticks[-1]
    workload = {key: last[key] for key in ("hits", "placements", "maxUnits", "movingSamples", "fogEdgeSamples")}
    require(workload["maxUnits"] == 600 and workload["movingSamples"] > 0, "missing 600-unit moving workload")
    require(workload["placements" if scene == "s11_construction" else "hits"] > 0, "missing measured activity")
    if scene == "s11_fog":
        require(workload["fogEdgeSamples"] > 0, "missing measured fog edge")
    return first, boundaries, workload, {"A": last["panA"], "B": last["panB"]}


def report_peer(events, peer, address, boundaries):
    by_type = defaultdict(Counter)
    gameplay = [0] * TICKS
    terrain = [0] * TICKS
    full_jfr_bytes = full_jfr_packets = summary_bytes = summary_packets = 0
    summaries = 0
    for event in events:
        kind, values = event["type"], event["values"]
        if values.get("remoteAddress") != address:
            continue
        if kind == SUMMARY_EVENT:
            summary_bytes += values["sentBytes"]
            summary_packets += values["sentPackets"]
            summaries += 1
        elif kind == PACKET_EVENT:
            size = values["bytes"]
            require(type(size) is int and size >= 0, f"peer {peer}: packet size")
            full_jfr_bytes += size
            full_jfr_packets += 1
            index = bisect_right(boundaries, timestamp(values["startTime"])) - 1
            if 0 <= index < TICKS:
                require(values["protocolId"] == "play" and values["packetDirection"] == "clientbound",
                        f"peer {peer}: connection did not stay in play")
                packet = values["packetId"]
                channel = terrain if packet in TERRAIN else gameplay
                channel[index] += size
                by_type[packet]["bytes"] += size
                by_type[packet]["packets"] += 1

    require(summaries and summary_bytes > 0 and summary_packets > 0, f"peer {peer}: missing connection summary")
    require(by_type.get(CUSTOM_PAYLOAD, {}).get("bytes", 0) > 0, f"peer {peer}: missing play payloads")
    game_bytes = sum(gameplay)
    rate = game_bytes * TPS / TICKS
    return {
        "gameplayBytes": game_bytes,
        "terrainBytes": sum(terrain),
        "gameplayMeanBytesPerSecond": rate,
        "gameplayPeakBytesPerTick": max(gameplay),
        "gameplayMaxOneSecondBytes": max(sum(gameplay[i:i + TPS]) for i in range(TICKS - TPS + 1)),
        "passesBudget": rate <= LIMIT,
        "gameplayPerTick": gameplay,
        "terrainPerTick": terrain,
        "types": dict(sorted(by_type.items())),
        "wholeRecording": {
            "packetEventBytes": full_jfr_bytes,
            "packetEvents": full_jfr_packets,
            "connectionSummaryBytes": summary_bytes,
            "connectionSummaryPackets": summary_packets,
            "summaryMinusPacketEventsBytes": summary_bytes - full_jfr_bytes,
            "summaryMinusPacketEventsPackets": summary_packets - full_jfr_packets,
        },
    }


def compare(scene, events, addresses, mock):
    require(scene in SCENES, "unknown S11 scene")
    require(set(addresses) == set(PEERS) and all(isinstance(value, str) and value for value in addresses.values())
            and len(set(addresses.values())) == 2, "map exactly two separate connections to A and B")
    validate_scene(scene, mock)
    first, boundaries, workload, distances = window(events, scene)
    peers = {}
    for slot, peer in enumerate(PEERS):
        result = report_peer(events, peer, addresses[peer], boundaries)
        baseline = mock["viewers"][slot]
        result["mockViewer"] = slot
        result["mockGameplayMean"] = baseline["gameplayMeanBytesPerSecond"]
        result["nativeMinusMockGameplayMean"] = result["gameplayMeanBytesPerSecond"] - result["mockGameplayMean"]
        result["mockCustomPayloadBytes"] = sum(
            counts["bytes"] for name, counts in baseline["types"].items() if name.startswith("payload/")
        )
        if scene == "s11_pan":
            distance = distances[peer]
            require(result["terrainBytes"] > 0 and math.isfinite(distance) and distance > 0,
                    f"peer {peer}: no measured terrain delivery or pan travel")
            result["panBlocksPerGameSecond"] = distance * TPS / TICKS
        peers[peer] = result
    return {
        "scene": scene,
        "firstTick": first,
        "measuredTicks": TICKS,
        "wallSeconds": (boundaries[-1] - boundaries[0]) / 1_000_000_000,
        "limitBytesPerSecond": LIMIT,
        "workload": workload,
        "peers": peers,
    }


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("scene", choices=SCENES)
    cli.add_argument("events", type=Path)
    cli.add_argument("local_addresses", type=Path)
    cli.add_argument("mock_scene", type=Path)
    cli.add_argument("result", type=Path)
    args = cli.parse_args()
    try:
        output = compare(args.scene, read(args.events)["recording"]["events"],
                         read(args.local_addresses), read(args.mock_scene))
        args.result.write_text(json.dumps(output, indent=2) + "\n", encoding="utf-8")
    except (KeyError, TypeError, ValueError, OSError) as failure:
        print(f"S11 native: {failure}", file=sys.stderr)
        return 1
    for peer, result in output["peers"].items():
        print(f"{peer}: {result['gameplayMeanBytesPerSecond']:.2f} gameplay B/s "
              f"({result['nativeMinusMockGameplayMean']:+.2f} from mock)")
    return 0 if all(item["passesBudget"] for item in output["peers"].values()) else 1


if __name__ == "__main__":
    sys.exit(main())
