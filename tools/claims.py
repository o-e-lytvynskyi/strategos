#!/usr/bin/env python3
"""Who works on what: the claims, one branch file each in docs/branches/.

    python3 tools/claims.py [--fetch] list       the claims on origin/main, with each branch's last commit
    python3 tools/claims.py check [--base <ref>] [--head <branch>]
                                                 the branch files in the working tree are valid claims: header fields
                                                 present, known statuses, no two active claims covering the same
                                                 work, and only <branch> (the pull request's head; default
                                                 $GITHUB_HEAD_REF or the checked-out branch) may be "ready for
                                                 review"; against <ref> (a claim pull request's base), work taken
                                                 from another developer names their hand-over in its file
    python3 tools/claims.py [--fetch] owner      the "For the owner" sections of the active claims' branch files
    python3 tools/claims.py after [M<n>]         the AFTER-M<n> markers in the working tree, by the milestone they wait for

A claim is a branch file that reached main by a pull request (AGENTS.md "Developers and claims"; the rules are in
docs/branches/README.md). Work is written `M5` (every task of its spec), `M5 T1–T6` or `M5 T1, T3, T7` (tasks),
`M6 spec`, or a free name (`fix-main`, `agents-runner`). Standard library only. Exit code 1 when `check` finds a
problem.
"""

import datetime
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BRANCHES = "docs/branches"
STATUSES = ("reserved", "claimed", "in progress", "ready for review", "merged", "released")
ACTIVE = ("reserved", "claimed", "in progress", "ready for review")
DATED = ("merged", "released")
REQUIRED = ("Developer", "Branch", "Work", "Status")
STALE_DAYS = 7
FIELD = re.compile(r"\*\*([A-Z][a-z]+):\*\* (.*?)(?= · \*\*|\s*$)")
TASK = re.compile(r"\[[ x]\] (T[0-9]+[0-9a-z.]*)")
RANGE = re.compile(r"^(M\d+)\s+(T[0-9a-z.]+)\s*[–-]\s*(T[0-9a-z.]+)$")
LIST = re.compile(r"^(M\d+)\s+(T[0-9a-z.]+(?:\s*,\s*T[0-9a-z.]+)*)$")


def git(*args):
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True, cwd=ROOT).stdout


def parse(name, text):
    """A branch file's claim: its header fields, the status without its date, and the text for hand-over notes."""
    header = next((line for line in text.splitlines() if line.startswith("**Developer:**")), "")
    fields = {k: v.strip().strip("`") for k, v in FIELD.findall(header)}
    raw = fields.get("Status", "")
    status = next((s for s in STATUSES if raw == s or (s in DATED and raw.startswith(s + " "))), raw)
    note = re.search(r"^\*\*Note:\*\* (.*)$", text, re.M)
    return {"file": name, "fields": fields, "developer": fields.get("Developer", "").split(" ")[0],
            "branch": fields.get("Branch", "").split(" ")[0].strip("`"), "work": fields.get("Work", ""),
            "since": fields.get("Claimed", ""), "status": status, "raw_status": raw,
            "note": note.group(1).strip() if note else "", "text": text}


def claims_in(files):
    """The claims of {file name: text}, the README skipped, in file-name order."""
    return [parse(name, text) for name, text in sorted(files.items()) if name != "README.md"]


def files_in_worktree():
    return {p.name: p.read_text() for p in (ROOT / BRANCHES).glob("*.md")}


def files_at(ref):
    try:
        names = git("ls-tree", "--name-only", f"{ref}:{BRANCHES}").split()
    except subprocess.CalledProcessError:
        sys.exit(f"{ref} has no {BRANCHES}/ (an unknown ref, or one older than the branch files)")
    return {n: git("show", f"{ref}:{BRANCHES}/{n}") for n in names if n.endswith(".md")}


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


def same_branch(branch, head):
    """A fork's claim names `<login>/strategos:dev/...`; its pull request's head is the `dev/...` part."""
    return head is not None and (branch == head or branch.endswith(f":{head}"))


def problems_of(current, before, head, read):
    """What is wrong with the claims `current`; `before` are the claims on the pull request's base, or None."""
    problems = []
    ready_before = {o["file"] for o in before or [] if o["status"] == "ready for review"}
    for c in current:
        where = f"{BRANCHES}/{c['file']}"
        missing = [f for f in REQUIRED if not c["fields"].get(f)]
        if missing:
            problems.append(f"{where}: header lacks {', '.join(missing)}")
            continue
        if c["status"] not in STATUSES:
            problems.append(f"{where}: unknown Status '{c['raw_status']}' (one of {', '.join(STATUSES)})")
        elif c["status"] in DATED and not re.fullmatch(rf"{c['status']} \d{{4}}-\d\d-\d\d", c["raw_status"]):
            problems.append(f"{where}: Status '{c['raw_status']}' needs its date ('{c['status']} <yyyy-mm-dd>')")
        elif c["status"] == "ready for review" and not same_branch(c["branch"], head) \
                and (before is None or c["file"] in ready_before):
            # Ready on the base too: its delivery was merged without the records. A stacked branch carries the
            # earlier branch's ready file; that is fine while the base does not have it ready.
            problems.append(f"{where} is 'ready for review' on a branch other than the pull request's: its delivery "
                            f"was merged without the records (Status 'merged <date>')")
    active = [c for c in current if c["status"] in ACTIVE]
    for i, a in enumerate(active):
        for b in active[i + 1:]:
            if overlaps(a["work"], b["work"], read):
                problems.append(f"'{a['work']}' ({a['developer']}, {a['file']}) and '{b['work']}' "
                                f"({b['developer']}, {b['file']}) overlap")
    for c in active:
        for old in (o for o in before or [] if o["status"] in ACTIVE):
            if old["developer"] != c["developer"] and overlaps(old["work"], c["work"], read) \
                    and not re.search(rf"handed over by {re.escape(old['developer'])}\b", c["note"]):
                problems.append(f"'{c['work']}' ({c['file']}) takes work active for {old['developer']} on the base: "
                                f"its **Note:** must say 'handed over by {old['developer']}' with a link to their approval")
    return problems


def current_branch():
    return os.environ.get("GITHUB_HEAD_REF") or git("rev-parse", "--abbrev-ref", "HEAD").strip()


def check(base, head=None):
    current = claims_in(files_in_worktree())
    before = claims_in(files_at(base)) if base else None
    problems = problems_of(current, before, head, read_worktree)
    for p in problems:
        print(p)
    print(f"{len(current)} claims checked, {len(problems)} problem(s)")
    return 1 if problems else 0


def last_commit(branch):
    out = git("for-each-ref", f"refs/remotes/origin/{branch}", "--format=%(committerdate:short)").strip()
    return out or None


def claims_on_main():
    return claims_in(files_at("origin/main"))


def cmd_list():
    old = str(datetime.date.today() - datetime.timedelta(days=30))
    for c in claims_on_main():
        done = c["raw_status"].split(" ")[-1] if c["status"] in DATED else ""
        if c["status"] in DATED and done < old:
            continue
        date = last_commit(c["branch"]) if c["status"] in ACTIVE else None
        stale = ""
        if c["status"] != "reserved" and c["status"] in ACTIVE and date:
            age = (datetime.date.today() - datetime.date.fromisoformat(date)).days
            stale = f" (stale: {age} days)" if age > STALE_DAYS else ""
        print(f"{c['work']} · {c['developer']} · {c['raw_status']}{stale}"
              + (f" · since {c['since']}" if c["since"] else "")
              + f" · {c['branch']}" + (f", last commit {date}" if date else "")
              + (f" · {c['note']}" if c["note"] else ""))
    print("\nPending claims are open pull requests to main adding a branch file: gh pr list --search 'Claim: in:title'")


def cmd_owner():
    found = False
    for c in claims_on_main():
        if c["status"] not in ACTIVE or c["status"] == "reserved" or not last_commit(c["branch"]):
            continue
        ref = f"origin/{c['branch']}"
        try:
            text = git("show", f"{ref}:{BRANCHES}/{c['file']}")
        except subprocess.CalledProcessError:
            continue
        m = re.search(r"^## For the owner\n(.*?)(?=^## |\Z)", text, re.M | re.S)
        items = m.group(1).strip() if m else ""
        if items and items.lower() not in ("none.", "- none."):
            found = True
            print(f"## {c['work']} — {c['developer']} ({c['branch']}, {c['file']})\n{items}\n")
    if not found:
        print("No active claim asks the owner anything.")


def cmd_after(only):
    """AFTER-M<n> markers: spots built against a milestone not yet merged when they were written."""
    out = subprocess.run(["git", "grep", "-n", "-E", r"AFTER-M[0-9]+", "--", ".", ":!tools/claims.py",
                          ":!docs/branches/README.md"], capture_output=True, text=True, cwd=ROOT).stdout
    by_milestone = {}
    for line in out.splitlines():
        m = re.search(r"AFTER-(M[0-9]+)", line)
        if m and (only is None or m.group(1) == only):
            by_milestone.setdefault(m.group(1), []).append(line)
    for milestone in sorted(by_milestone, key=lambda k: int(k[1:])):
        print(f"## {milestone} ({len(by_milestone[milestone])})")
        print("\n".join(by_milestone[milestone]))
    if not by_milestone:
        print("No AFTER-M markers.")


def main(argv):
    if "--fetch" in argv:
        git("fetch", "--prune", "origin")
    args = [a for a in argv if a != "--fetch"]
    command = args[0] if args else "list"
    if command == "check":
        base = args[args.index("--base") + 1] if "--base" in args else None
        head = args[args.index("--head") + 1] if "--head" in args else current_branch()
        return check(base, head)
    if command == "after":
        cmd_after(args[1] if len(args) > 1 else None)
    elif command == "list":
        cmd_list()
    elif command == "owner":
        cmd_owner()
    else:
        print(__doc__)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
