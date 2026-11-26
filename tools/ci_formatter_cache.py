#!/usr/bin/env python3
"""Recover only the pinned formatter coordinate from a successful own-branch verify report."""
import argparse
import hashlib
from pathlib import Path

HASHES = {
    "pom": "fe8d312e5310946775e6ba35053b6358f166336accd944b02ee82a0064d2c3f8",
    "jar": "8074d7e21832edcad422f8dbf6002d296551963bec410daad97317210a55b5c1",
}
COORDINATE = Path("com/palantir/javaformat/palantir-java-format/2.102.0")


def validate_producer(run, repository, branch, run_id):
    expected = (run.get("id") == run_id,
                run.get("repository", {}).get("full_name") == repository,
                run.get("head_repository", {}).get("full_name") == repository,
                run.get("head_branch") == branch, run.get("event") == "workflow_dispatch",
                run.get("path") == ".github/workflows/verify.yml",
                run.get("status") == "completed", run.get("conclusion") == "success")
    if not all(expected):
        raise ValueError("formatter producer must be a successful same-repository/branch dispatched verify")


def no_symlinks(path):
    for current in (path, *path.parents):
        if current.is_symlink():
            raise ValueError("formatter cache paths cannot contain symlinks")


def restore(source, repository):
    source, repository = Path(source), Path(repository)
    no_symlinks(source)
    no_symlinks(repository)
    approved = []
    for extension, expected in HASHES.items():
        path = source / "build" / "reports" / "central-endpoint" / ("formatter." + extension)
        no_symlinks(path)
        if path.stat().st_size > 1024 * 1024:
            raise ValueError("formatter file exceeds one MiB")
        data = path.read_bytes()
        if hashlib.sha256(data).hexdigest() != expected:
            raise ValueError("formatter checksum mismatch: " + extension)
        target = repository / COORDINATE / ("palantir-java-format-2.102.0." + extension)
        no_symlinks(target)
        approved.append((target, data))
    for target, data in approved:
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    print("Recovered two checksum-approved formatter files; versions and test gates unchanged")


def main():
    from ci_dependency_cache import github_json, positive_run

    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    validate = commands.add_parser("validate")
    validate.add_argument("--run", type=positive_run, required=True)
    validate.add_argument("--repository", required=True)
    validate.add_argument("--branch", required=True)
    recover = commands.add_parser("restore")
    recover.add_argument("--source", type=Path, required=True)
    recover.add_argument("--repository", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "validate":
        run = github_json(f"repos/{args.repository}/actions/runs/{args.run}")
        validate_producer(run, args.repository, args.branch, args.run)
        print("Validated successful same-branch formatter producer")
    else:
        restore(args.source, args.repository)


if __name__ == "__main__":
    main()
