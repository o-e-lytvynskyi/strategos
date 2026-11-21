#!/usr/bin/env python3
"""Complete the three unmeasured T7 scenes without repeating 21 accepted measurements."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

from ci_dependency_cache import github_json


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def producer(run, jobs, manifest, repository, branch):
    require(all((run.get("id") == manifest["producer_run"],
                 run.get("head_sha") == manifest["source"],
                 run.get("repository", {}).get("full_name") == repository,
                 run.get("head_repository", {}).get("full_name") == repository,
                 run.get("head_branch") == branch,
                 run.get("event") == "workflow_dispatch",
                 run.get("path") == ".github/workflows/perf.yml")), "untrusted original measurement producer")
    perf = [j for j in jobs.get("jobs", []) if j.get("name") == "perf"]
    require(len(perf) == 1 and perf[0].get("conclusion") == "cancelled", "original must be the interrupted perf job")
    require(not any(s.get("conclusion") == "failure" for s in perf[0].get("steps", [])), "original has a failed step")


def reports(directory):
    paths = list(Path(directory).glob("*.json"))
    return {p.stem: p for p in paths}


def verdict(path):
    data = json.loads(path.read_text(encoding="utf-8"))
    require(data.get("test") == path.stem, "report identity mismatch")
    if path.stem == "prewarm_512_generated":
        require("verdict" not in data, "unexpected prewarm verdict")
    else:
        require(data.get("verdict") in ("pass", "report-only"), "failed, invalid or missing performance verdict")
    require(not data.get("violations"), "performance violations")
    return data


def completed(manifest, original, measured, output, run_id):
    original, measured = Path(original), Path(measured)
    old = reports(original / "mod/build/reports/perf")
    new = reports(measured / "mod/build/reports/perf")
    require(set(old) == set(manifest["completed"]), "original report inventory changed")
    require(set(new) == set(manifest["remaining"]), "completion must measure exactly the three unfinished scenes")
    require(len(old) + len(new) == 24 and not set(old).intersection(new), "incomplete or repeated suite")
    for name, path in old.items():
        require(hashlib.sha256(path.read_bytes()).hexdigest() == manifest["completed"][name], "original report bytes changed")
        verdict(path)
    log = (original / "mod/build/gametest-perf/logs/latest.log").read_text(encoding="utf-8")
    bars = re.findall(r"\[([+_ X!-]{24})\]", log)
    require(bars and bars[-1].count("+") == 21 and "X" not in bars[-1], "original 21 passing GameTests not proven")
    xml = ET.parse(measured / "mod/build/test-results/gametest-perf/TEST-gametest-perf.xml")
    cases = list(xml.iter("testcase"))
    names = [c.attrib["name"].removeprefix("perftests.") for c in cases]
    require(len(cases) == 3 and set(names) == set(manifest["remaining"].values()), "wrong completion GameTest inventory")
    require(not any(list(c) for c in cases), "completion GameTest failure/error/skip or unexpected child")
    for name, path in new.items():
        data = verdict(path)
        if name in manifest["ticks"]:
            require(data.get("ticks") == manifest["ticks"][name], "soak measurement was shortened")
    evidence = {"source": manifest["source"], "original_run": manifest["producer_run"],
                "completion_run": run_id, "cases": 24,
                "reports": {name: hashlib.sha256(path.read_bytes()).hexdigest()
                            for name, path in (old | new).items()},
                "settings": {"perf.ciFactor": 3, "soak.ticks": 2400, "loop.ticks": 3600,
                             "battle.ticks": 6000, "mvp.ticks": 36000}}
    Path(output).write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    print("Complete T7 L6: 21 preserved + 3 newly measured = 24; unchanged source, verdicts and soak lengths")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("producer", "complete"))
    parser.add_argument("--manifest", default="perf/t7-l6-completion.json")
    parser.add_argument("--repository")
    parser.add_argument("--branch")
    parser.add_argument("--original", default="build/t7-original")
    parser.add_argument("--measured", default="measured")
    parser.add_argument("--output", default="build/t7-l6-complete.json")
    parser.add_argument("--run", type=int)
    args = parser.parse_args()
    manifest = json.loads(Path(args.manifest).read_text(encoding="utf-8"))
    if args.command == "producer":
        base = f"repos/{args.repository}/actions/runs/{manifest['producer_run']}"
        producer(github_json(base), github_json(base + "/jobs?per_page=100"), manifest, args.repository, args.branch)
        print("Validated immutable interrupted T7 measurement producer")
    else:
        completed(manifest, args.original, args.measured, args.output, args.run)


if __name__ == "__main__":
    main()
