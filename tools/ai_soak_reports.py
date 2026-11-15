#!/usr/bin/env python3
"""Validate complete, same-commit evidence for the twenty unchanged QA-12 AI seeds."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

SEEDS = tuple(range(1, 21))
XML = Path("mod/build/test-results/gametest-soak/TEST-gametest-soak.xml")
LOG = Path("mod/build/gametest-soak/logs/latest.log")
SUMMARY = Path("build/reports/ai-soak/shard.json")


def inspect(root, seed, commit):
    if seed not in SEEDS or not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("invalid seed or commit")
    xml_bytes = (root / XML).read_bytes()
    tree = ET.fromstring(xml_bytes)
    cases = tree.findall(".//testcase")
    if len(cases) != 1 or cases[0].get("name") != "twenty_seeds_of_normal_against_normal":
        raise ValueError("expected exactly one AI soak case")
    for tag in ("failure", "error", "skipped"):
        if list(tree.iter(tag)):
            raise ValueError("AI soak contains " + tag)
    log_bytes = (root / LOG).read_bytes()
    log = log_bytes.decode("utf-8")
    finished = re.findall(r"aisoak seed (\d+): [^\n]*; the journal replays to its hash", log)
    if finished != [str(seed)]:
        raise ValueError("missing, duplicate or unexpected completed seed: " + repr(finished))
    totals = re.findall(r"aisoak: (\d+) matches, (\d+) by conquest, (\d+) to the limit, (\d+) game ticks in all", log)
    if len(totals) != 1:
        raise ValueError("missing or duplicate final soak summary")
    matches, conquest, limit, ticks = map(int, totals[0])
    if matches != 1 or conquest + limit != 1 or ticks <= 0:
        raise ValueError("incomplete soak summary")
    return dict(seed=seed, commit=commit, cases=1, conquest=conquest, limit=limit, ticks=ticks,
                xml_sha256=hashlib.sha256(xml_bytes).hexdigest(),
                log_sha256=hashlib.sha256(log_bytes).hexdigest())


def aggregate(root, commit):
    directories = sorted(p for p in root.iterdir() if p.is_dir())
    expected = {f"aisoak-{seed}-reports" for seed in SEEDS}
    if {p.name for p in directories} != expected:
        raise ValueError("artifact set is not exactly the twenty seed reports")
    results = []
    for seed in SEEDS:
        directory = root / f"aisoak-{seed}-reports"
        recorded = json.loads((directory / SUMMARY).read_text(encoding="utf-8"))
        actual = inspect(directory, seed, commit)
        if recorded != actual:
            raise ValueError(f"seed {seed} manifest differs from its XML/log evidence or commit")
        results.append(actual)
    return dict(commit=commit, seeds=list(SEEDS), matches=len(results),
                ticks=sum(r["ticks"] for r in results), shards=results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("shard", "aggregate"))
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--seed", type=int)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = inspect(args.root, args.seed, args.commit) if args.mode == "shard" else aggregate(args.root, args.commit)
    except (ValueError, OSError, ET.ParseError) as error:
        parser.exit(1, "AI soak evidence rejected: " + str(error) + "\n")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("AI soak evidence verified:", result.get("matches", 1), "match(es)")


if __name__ == "__main__":
    main()
