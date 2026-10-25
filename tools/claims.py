#!/usr/bin/env python3
"""Who works on what: the claims of developer branches.

    python3 tools/claims.py [--fetch] list    every claim: branch, developer, work, status, last commit
    python3 tools/claims.py [--fetch] owner   the "For the owner" sections of every open claim

A claim is a branch dev/<developer>/<work> pushed to origin whose docs/branches/ holds a file with
`**Branch:** dev/<developer>/<work>` in its header (template: docs/branches/README.md). Reservations
by the owner are in docs/STATE.md "Assignments". Standard library only.
"""

import datetime
import re
import subprocess
import sys

FIELD = re.compile(r"\*\*(Developer|Branch|Work|Status|Base):\*\*\s*([^·\n]+)")
OPEN_STATUSES = ("claimed", "in progress", "ready for review")
STALE_DAYS = 7


def git(*args):
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def branches():
    out = git("for-each-ref", "refs/remotes/origin/dev/",
              "--format=%(refname:short)\t%(committerdate:short)")
    for line in out.splitlines():
        ref, date = line.split("\t")
        yield ref, ref.removeprefix("origin/"), date


def branch_file(ref, name):
    """The branch file whose header names this branch, as (path, text), or None."""
    listing = git("ls-tree", "--name-only", ref, "docs/branches/")
    for path in listing.splitlines():
        if not path.endswith(".md") or path.endswith("README.md"):
            continue
        text = git("show", f"{ref}:{path}")
        fields = dict(FIELD.findall(text.split("\n## ", 1)[0]))
        if fields.get("Branch", "").strip().strip("`") == name:
            return path, text, {k: v.strip().strip("`") for k, v in fields.items()}
    return None


def claims():
    for ref, name, date in branches():
        found = branch_file(ref, name)
        if found is None:
            yield name, date, None, None, {"Status": "no branch file: not a claim"}
            continue
        path, text, fields = found
        yield name, date, path, text, fields


def stale(date):
    age = datetime.date.today() - datetime.date.fromisoformat(date)
    return age.days > STALE_DAYS


def section(text, heading):
    match = re.search(rf"^## {re.escape(heading)}\n(.*?)(?=^## |\Z)", text, re.M | re.S)
    return match.group(1).strip() if match else ""


def cmd_list():
    rows = list(claims())
    if not rows:
        print("No developer branches on origin. Reservations: docs/STATE.md \"Assignments\".")
        return
    for name, date, path, _, fields in rows:
        status = fields.get("Status", "?")
        flag = " (stale)" if status in OPEN_STATUSES and stale(date) else ""
        print(f"{name}\n  developer: {fields.get('Developer', '?')} · work: {fields.get('Work', '?')}"
              f" · status: {status}{flag} · last commit {date}"
              + (f" · base: {fields['Base']}" if "Base" in fields else "")
              + (f"\n  file: {path}" if path else ""))
    print("\nReservations by the owner: docs/STATE.md \"Assignments\".")


def cmd_owner():
    any_items = False
    for name, _, path, text, fields in claims():
        if text is None or fields.get("Status") not in OPEN_STATUSES:
            continue
        items = section(text, "For the owner")
        if items and items.lower() not in ("none.", "nothing.", "- none."):
            any_items = True
            print(f"## {name} ({fields.get('Developer', '?')}, {path})\n{items}\n")
    if not any_items:
        print("No open claim asks the owner anything.")


def main(argv):
    args = [a for a in argv if a != "--fetch"]
    if "--fetch" in argv:
        git("fetch", "--prune", "origin")
    command = args[0] if args else "list"
    if command == "list":
        cmd_list()
    elif command == "owner":
        cmd_owner()
    else:
        print(__doc__)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
