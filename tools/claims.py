#!/usr/bin/env python3
"""Who works on what: the claims registry docs/claims.md and the developer branches.

    python3 tools/claims.py [--fetch] list       the registry on origin/main, with each branch's last commit
    python3 tools/claims.py check [--base <ref>] the registry in the working tree is valid: known statuses, no two
                                                 active rows covering the same work; against <ref> (a claim pull
                                                 request's base), work taken from another developer names their
                                                 hand-over in its note
    python3 tools/claims.py [--fetch] owner      the "For the owner" sections of the active claims' branch files

A claim is a row of docs/claims.md that reached main by a pull request (AGENTS.md "Developers and claims").
Work is written `M5` (every task of its spec), `M5 T1–T6` or `M5 T1, T3, T7` (tasks), `M6 spec`, or a free name
(`fix-main`, `agents-runner`). Standard library only. Exit code 1 when `check` finds a problem.
"""

import datetime
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REGISTRY = "docs/claims.md"
STATUSES = ("reserved", "assigned", "done", "released")
ACTIVE = ("reserved", "assigned")
STALE_DAYS = 7
ROW = re.compile(r"^\| (?!Work |---)(.+?) \| (.+?) \| (.+?) \| (.+?) \| (.+?) \| (.*?) \|\s*$")
TASK = re.compile(r"\[[ x]\] (T[0-9]+[0-9a-z.]*)")
RANGE = re.compile(r"^(M\d+)\s+(T[0-9a-z.]+)\s*[–-]\s*(T[0-9a-z.]+)$")
LIST = re.compile(r"^(M\d+)\s+(T[0-9a-z.]+(?:\s*,\s*T[0-9a-z.]+)*)$")


def git(*args):
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True, cwd=ROOT).stdout


def rows(text):
    """The registry's rows as dicts, in order."""
    out = []
    for line in text.splitlines():
        m = ROW.match(line)
        if m:
            work, dev, branch, status, since, note = (g.strip().strip("`") for g in m.groups())
            out.append({"work": work, "developer": dev, "branch": branch, "status": status,
                        "since": since, "note": note, "line": line})
    return out


def spec_tasks(milestone, read):
    """The task ids of a milestone's spec "Progress", in order, or None without a spec."""
    for path in sorted((ROOT / "docs/milestones").glob(f"{milestone}-*.md")):
        text = read(path.relative_to(ROOT).as_posix())
        progress = re.search(r"^### [\d.]+ Progress\n(.*?)(?=^#|\Z)", text, re.M | re.S)
        if progress:
            return TASK.findall(progress.group(1))
    return None


def units(work, read):
    """The set of work units a claim covers, so that overlaps are a set intersection."""
    work = work.strip()
    m = re.fullmatch(r"(M\d+)", work)
    if m:
        tasks = spec_tasks(m.group(1), read)
        return {f"{m.group(1)}:{t}" for t in tasks} if tasks else {f"{m.group(1)}:*"}
    m = RANGE.match(work)
    if m:
        tasks = spec_tasks(m.group(1), read) or []
        if m.group(2) in tasks and m.group(3) in tasks:
            i, j = tasks.index(m.group(2)), tasks.index(m.group(3))
            return {f"{m.group(1)}:{t}" for t in tasks[i:j + 1]}
        return {f"{m.group(1)}:*"}  # a range the spec does not know: assume the whole milestone
    m = LIST.match(work)
    if m:
        return {f"{m.group(1)}:{t.strip()}" for t in m.group(2).split(",")}
    return {work.lower()}


def overlaps(a, b, read):
    """True when two claims share a unit; a milestone without a spec (`M7:*`) covers all of it."""
    ua, ub = units(a, read), units(b, read)
    if ua & ub:
        return True
    wild_a = {u.split(":")[0] for u in ua if u.endswith(":*")}
    wild_b = {u.split(":")[0] for u in ub if u.endswith(":*")}
    return any(":" in u and u.split(":")[0] in wild_b for u in ua) \
        or any(":" in u and u.split(":")[0] in wild_a for u in ub)


def read_worktree(path):
    return (ROOT / path).read_text()


def check(base):
    problems = []
    current = rows(read_worktree(REGISTRY))
    for r in current:
        if r["status"] not in STATUSES:
            problems.append(f"{r['work']}: unknown status '{r['status']}' (one of {', '.join(STATUSES)})")
    active = [r for r in current if r["status"] in ACTIVE]
    for i, a in enumerate(active):
        for b in active[i + 1:]:
            if overlaps(a["work"], b["work"], read_worktree):
                problems.append(f"'{a['work']}' ({a['developer']}) and '{b['work']}' ({b['developer']}) overlap")
    if base:
        before = [r for r in rows(git("show", f"{base}:{REGISTRY}")) if r["status"] in ACTIVE]
        for r in active:
            for old in before:
                if old["developer"] != r["developer"] and overlaps(old["work"], r["work"], read_worktree) \
                        and f"handed over by {old['developer']}" not in r["note"]:
                    problems.append(f"'{r['work']}' takes work active for {old['developer']} on {base}: its note must "
                                    f"say 'handed over by {old['developer']}' with a link to their approval")
    for p in problems:
        print(p)
    print(f"{len(current)} claims checked, {len(problems)} problem(s)")
    return 1 if problems else 0


def last_commit(branch):
    out = git("for-each-ref", f"refs/remotes/origin/{branch}", "--format=%(committerdate:short)").strip()
    return out or None


def registry_on_main():
    return rows(git("show", f"origin/main:{REGISTRY}"))


def cmd_list():
    for r in registry_on_main():
        if r["status"] not in ACTIVE and r["since"] < str(datetime.date.today() - datetime.timedelta(days=30)):
            continue
        date = last_commit(r["branch"]) if r["branch"] not in ("—", "-") else None
        stale = ""
        if r["status"] == "assigned" and date:
            age = (datetime.date.today() - datetime.date.fromisoformat(date)).days
            stale = f" (stale: {age} days)" if age > STALE_DAYS else ""
        print(f"{r['work']} · {r['developer']} · {r['status']}{stale} · since {r['since']}"
              f" · {r['branch']}" + (f", last commit {date}" if date else "")
              + (f" · {r['note']}" if r["note"] else ""))
    print("\nPending claims are open pull requests to main touching docs/claims.md: gh pr list --search claims.md")


def cmd_owner():
    found = False
    for r in registry_on_main():
        if r["status"] != "assigned" or not last_commit(r["branch"]):
            continue
        ref = f"origin/{r['branch']}"
        for path in git("ls-tree", "--name-only", ref, "docs/branches/").splitlines():
            if path.endswith("README.md"):
                continue
            text = git("show", f"{ref}:{path}")
            if f"**Branch:** {r['branch']}" not in text and f"**Branch:** `{r['branch']}`" not in text:
                continue
            m = re.search(r"^## For the owner\n(.*?)(?=^## |\Z)", text, re.M | re.S)
            items = m.group(1).strip() if m else ""
            if items and items.lower() not in ("none.", "- none."):
                found = True
                print(f"## {r['work']} — {r['developer']} ({r['branch']}, {path})\n{items}\n")
    if not found:
        print("No active claim asks the owner anything.")


def main(argv):
    if "--fetch" in argv:
        git("fetch", "--prune", "origin")
    args = [a for a in argv if a != "--fetch"]
    command = args[0] if args else "list"
    if command == "check":
        base = args[args.index("--base") + 1] if "--base" in args else None
        return check(base)
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
