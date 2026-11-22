"""Require complete PERF-10 reports before accepting a measurement run."""

import argparse
import json
from pathlib import Path
import sys

from s11_reports import LIMIT, require

BASE = "m7_multiplayer"
STRESS = "m7_multiplayer_stress"


def read(folder, name):
    report = json.loads((folder / f"{name}.json").read_text(encoding="utf-8"))
    require(report["test"] == name and report["verdict"] == "pass" and not report["violations"],
            f"{name}: invalid or failed measurement")
    return report


def soak(folder, name, population, buildings, samples):
    report = read(folder, name)
    require((report["factions"], report["populationPerFactionAtStart"], report["buildingsPerFactionAtStart"])
            == (4, population, buildings), f"{name}: wrong starting scale")
    require(report["samples"] == samples and report["ticks"] == samples + 200,
            f"{name}: incomplete measurement window")
    require(report["terrainExcludedFromE"], f"{name}: missing terrain accounting")
    workload = report["workload"]
    require(workload["samples"] * 20 == samples, f"{name}: missing workload censuses")
    require([faction["faction"] for faction in workload["factions"]] == list(range(4)),
            f"{name}: incomplete faction census")
    for faction in workload["factions"]:
        require(faction["population"]["mean"] >= population and faction["livingUnits"]["min"] > 0
                and faction["finishedBuildings"]["mean"] >= buildings, f"{name}: workload fell below scale")

    return report["budgets"]


def check_budget(budget, percentile, limit_ns):
    require(budget["percentile"] == percentile and budget["limit"] == limit_ns
            and 0 < budget["ns"] <= limit_ns and budget["verdict"] == "pass",
            "missing or exceeded time budget")


def verify(folder):
    budgets = soak(folder, BASE, 150, 30, 24_000)
    for key, percentile, limit in (("a", 0.50, 5), ("a_p99", 0.99, 10), ("b", 0.95, 17),
                                   ("c", 0.95, 23), ("d", 0.99, 2)):
        check_budget(budgets[key], percentile, limit * 1_000_000)
    require(len(budgets["e"]) == 4 and all(
        item["limit"] == LIMIT and 0 < item["bytesPerSecond"] <= LIMIT
        and 0 < item["totalGameplayBytes"] <= LIMIT * 1_200 for item in budgets["e"]
    ), "budget E needs four passing, active recipients")
    stress = soak(folder, STRESS, 300, 60, 6_000)
    check_budget(stress["c"], 0.95, 50_000_000)
    paths = read(folder, "path_budget_d")
    require(0 < paths["p99"] <= paths["p99Limit"] == 2_000_000 and 0 < paths["group_p95_ticks"] <= 5,
            "path main-thread or 100-unit order latency budget failed")
    return {
        "hostP95Nanoseconds": budgets["c"]["ns"],
        "clientGameplayBytesPerSecond": [item["bytesPerSecond"] for item in budgets["e"]],
        "stressHostP95Nanoseconds": stress["c"]["ns"],
        "orderLatencyP95Ticks": paths["group_p95_ticks"],
    }


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("reports", type=Path)
    args = cli.parse_args()
    try:
        print(json.dumps(verify(args.reports)))
    except (ValueError, KeyError, TypeError, OSError) as failure:
        print(f"PERF-10: {failure}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
