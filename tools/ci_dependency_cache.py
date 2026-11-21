#!/usr/bin/env python3
"""Optional trusted Maven-module hand-off; it is never test acceptance or a dependency/version fallback."""

import argparse
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import tarfile

MAX_BYTES = 2 * 1024**3
MAX_MEMBERS = 100_000


def validate_producer(run, jobs, repository, branch, run_id):
    expected = (
        run.get("id") == run_id,
        run.get("repository", {}).get("full_name") == repository,
        run.get("head_repository", {}).get("full_name") == repository,
        run.get("head_branch") == branch,
        run.get("event") == "workflow_dispatch",
        run.get("path") == ".github/workflows/nightly.yml",
    )
    if not all(expected):
        raise ValueError("dependency producer must be this repository/branch's dispatched full-ci workflow")
    for name in ("validate-harness", "nightly"):
        matched = [j for j in jobs.get("jobs", []) if j.get("name") == name]
        if len(matched) != 1 or matched[0].get("status") != "completed" or matched[0].get("conclusion") != "success":
            raise ValueError(f"dependency producer job {name} did not succeed")


def restore(archive_path, caches):
    caches = Path(caches).resolve()
    caches.mkdir(parents=True, exist_ok=True)
    modules = caches / "modules-2"
    if modules.is_symlink():
        raise ValueError("dependency destination cannot be a symlink")
    with tarfile.open(archive_path, "r:gz") as archive:
        members = []
        total = 0
        # Validate the complete bounded member list before writing even the first module.
        for member in archive:
            path = PurePosixPath(member.name)
            if (not path.parts or path.is_absolute() or ".." in path.parts or "\\" in member.name
                    or path.parts[0] != "modules-2" or not (member.isfile() or member.isdir())
                    or (len(path.parts) == 1 and not member.isdir())):
                raise ValueError(f"unsafe dependency archive member: {member.name!r}")
            target = (caches / member.name).resolve()
            if not target.is_relative_to(modules):
                raise ValueError("dependency member escapes its module directory")
            total += member.size
            if member.size < 0 or total > MAX_BYTES or len(members) >= MAX_MEMBERS:
                raise ValueError("dependency archive exceeds bounded module limits")
            members.append(member)
        if not members:
            raise ValueError("dependency archive is empty")
        archive.extractall(caches, members=members, filter="data")
    print(f"Restored {len(members)} module entries ({total} bytes); no credentials or project outputs")


def positive_run(value):
    if not re.fullmatch(r"[1-9][0-9]{0,19}", value):
        raise argparse.ArgumentTypeError("cache run must be a positive numeric workflow run id")
    return int(value)


def github_json(path):
    # The runner's authenticated CLI handles credentials; no token is written to disk or command arguments.
    result = subprocess.run(["gh", "api", path], check=True, text=True, capture_output=True)
    return json.loads(result.stdout)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    validate = commands.add_parser("validate")
    validate.add_argument("--run", type=positive_run, required=True)
    validate.add_argument("--repository", required=True)
    validate.add_argument("--branch", required=True)
    extract = commands.add_parser("restore")
    extract.add_argument("--archive", type=Path, required=True)
    extract.add_argument("--caches", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "validate":
        if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", args.repository):
            parser.error("invalid repository name")
        base = f"repos/{args.repository}/actions/runs/{args.run}"
        validate_producer(github_json(base), github_json(base + "/jobs?per_page=100"),
                          args.repository, args.branch, args.run)
        print(f"Validated successful same-branch nightly dependency producer {args.run}; not an acceptance verdict")
    else:
        restore(args.archive, args.caches)


if __name__ == "__main__":
    main()
