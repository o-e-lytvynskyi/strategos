#!/usr/bin/env python3
"""Strict complete-suite/identity checks around the unchanged Gradle JMH performance gate."""

import argparse
import hashlib
import json
import math
from pathlib import Path
import re

from ci_dependency_cache import github_json, positive_run

RESULT = Path("core/build/reports/jmh/result.json")
FIELDS = ("mode", "threads", "forks", "jvm", "jvmArgs", "jdkVersion", "jmhVersion",
          "warmupIterations", "warmupTime", "warmupBatchSize", "measurementIterations",
          "measurementTime", "measurementBatchSize")


def validate_producer(run, jobs, repository, branch, run_id):
    if not (run.get("id") == run_id
            and (run.get("repository") or {}).get("full_name") == repository
            and (run.get("head_repository") or {}).get("full_name") == repository
            and run.get("head_branch") == branch and run.get("event") == "workflow_dispatch"
            and run.get("path") == ".github/workflows/perf.yml"
            and re.fullmatch(r"[0-9a-f]{40}", run.get("head_sha", ""))):
        raise ValueError("archived measurements must come from this branch's own dispatched perf workflow")
    matched = [j for j in jobs.get("jobs", []) if j.get("name") == "paired-jmh"]
    if len(matched) != 1 or matched[0].get("status") != "completed" or matched[0].get("conclusion") != "failure":
        raise ValueError("expected the completed paired job with its known wrapper failure")
    steps = matched[0].get("steps", [])
    for name in ("Build both benchmark jars before measuring", "Control JMH", "Candidate JMH"):
        if len([s for s in steps if s.get("name") == name and s.get("conclusion") == "success"]) != 1:
            raise ValueError("both original measurement phases must have succeeded")
    if [s.get("name") for s in steps if s.get("conclusion") == "failure"] != ["Require comparable complete results"]:
        raise ValueError("cannot reuse a failed measurement or bypass an actual performance gate")
    return run["head_sha"]


def key(row):
    if not isinstance(row.get("benchmark"), str) or not isinstance(row.get("params", {}), dict):
        raise ValueError("invalid benchmark configuration")
    params = row.get("params", {})
    if any(not isinstance(k, str) or not isinstance(v, str) for k, v in params.items()):
        raise ValueError("invalid benchmark parameters")
    return row["benchmark"], tuple(sorted(params.items()))


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def compare(root, manifest_path, sources, control_sha, candidate_sha):
    root, manifest_path, sources = Path(root), Path(manifest_path), Path(sources)
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    configurations = manifest["configurations"]
    expected = {key(row) for row in configurations}
    methods = {name for name, _ in expected}
    declared = sum(len(re.findall(r"^\s*@Benchmark\b", p.read_text(encoding="utf-8"), re.MULTILINE))
                   for p in sources.rglob("*.java"))
    if (manifest.get("schema") != 1 or not expected or len(expected) != len(configurations)
            or len(methods) != manifest.get("benchmarkMethods") or declared != len(methods)):
        raise ValueError("suite manifest must cover every declared method and unique parameter configuration")
    result = {"configurations": len(expected), "benchmarkMethods": len(methods), "manifestSha256": digest(manifest_path)}
    pairs = {}
    for checkout, expected_sha in (("control", control_sha), ("candidate", candidate_sha)):
        recorded = (root / "evidence" / f"{checkout}-commit.txt").read_text(encoding="utf-8").strip()
        if not re.fullmatch(r"[0-9a-f]{40}", expected_sha) or recorded != expected_sha:
            raise ValueError("measurement commit identity changed")
        path = root / checkout / RESULT
        if path.stat().st_size > 16 * 1024**2:
            raise ValueError("JMH report exceeds its bounded data window")
        rows = json.loads(path.read_text(encoding="utf-8"))
        keyed = {key(row): row for row in rows}
        if len(keyed) != len(rows) or keyed.keys() != expected:
            raise ValueError("missing, extra or duplicate full-suite benchmark configurations")
        for row in rows:
            metric = row.get("primaryMetric", {})
            score = metric.get("score")
            if type(score) not in (int, float) or not math.isfinite(score) or score <= 0:
                raise ValueError("invalid measured primary score")
        pairs[checkout] = keyed
        result[checkout] = {"commit": recorded, "reportSha256": digest(path)}
    for configuration, before in pairs["control"].items():
        after = pairs["candidate"][configuration]
        for field in FIELDS:
            if field not in before or field not in after or before[field] != after[field]:
                raise ValueError(f"measurement settings differ: {configuration}, {field}")
        if before["primaryMetric"]["scoreUnit"] != after["primaryMetric"]["scoreUnit"]:
            raise ValueError("measurement units differ")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    producer = commands.add_parser("producer")
    producer.add_argument("--run", type=positive_run, required=True)
    producer.add_argument("--repository", required=True)
    producer.add_argument("--branch", required=True)
    producer.add_argument("--github-output", type=Path, required=True)
    report = commands.add_parser("reports")
    report.add_argument("--root", type=Path, required=True)
    report.add_argument("--manifest", type=Path, required=True)
    report.add_argument("--sources", type=Path, required=True)
    report.add_argument("--control", required=True)
    report.add_argument("--candidate", required=True)
    report.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "producer":
        if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", args.repository):
            parser.error("invalid repository name")
        base = f"repos/{args.repository}/actions/runs/{args.run}"
        sha = validate_producer(github_json(base), github_json(base + "/jobs?per_page=100"),
                                args.repository, args.branch, args.run)
        with args.github_output.open("a", encoding="utf-8") as output:
            output.write(f"candidate_sha={sha}\n")
        print(f"Validated original measurements of {sha}; only the wrapper failed")
    else:
        result = compare(args.root, args.manifest, args.sources, args.control, args.candidate)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print(f"{result['configurations']} complete matching configurations; original report bytes preserved")


if __name__ == "__main__":
    main()
