#!/usr/bin/env python3
"""Roadmap bookkeeping for Strategos.

    python3 tools/roadmap.py summary   progress per milestone, spec checkboxes, open decisions
    python3 tools/roadmap.py check     ROADMAP.md and the card files agree; statuses are valid

docs/ROADMAP.md is the source of truth for card statuses; docs/roadmap/Mx-*.md mirror them.
Standard library only. Exit code 1 when `check` finds a problem.
"""

import re
import sys
from collections import Counter, OrderedDict
from pathlib import Path

DOCS = Path(__file__).resolve().parent.parent / "docs"
STATUSES = ["proposed", "changed", "deferred", "rejected", "approved", "approved-foundation",
            "specified", "in_progress", "done", "accepted"]
DONE_LIKE = {"done", "accepted"}
MILESTONE_HEADING = re.compile(r"^### (M\d+) · (.+)$")
TABLE_ROW = re.compile(r"^\| ([A-Z]+-\d+) \| (.+?) \| (S|M|L) \| (.*?) \| (.+?) \|\s*$")
CARD_HEADING = re.compile(r"^#### ([A-Z]+-\d+) · (.+)$")
STATUS_LINE = re.compile(r"^- \*\*Status:\*\* (.+)$")
DONE_LINE = re.compile(r"^- \*\*Done:\*\* .+")
CHECKBOX = re.compile(r"\[( |x)\] ([A-Za-z0-9]+(?: [^·\[]*)?)")


def base(status):
    """'deferred (M10)' -> 'deferred'."""
    return status.strip().split(" (")[0].strip().strip("`")


def roadmap_cards():
    """{card id: (milestone, status)} from the per-milestone tables of ROADMAP.md."""
    cards, milestone = OrderedDict(), None
    in_features = False
    for line in (DOCS / "ROADMAP.md").read_text().splitlines():
        if line.startswith("## "):
            in_features = line.startswith("## Features per milestone")
            milestone = None
            continue
        if not in_features:
            continue
        m = MILESTONE_HEADING.match(line)
        if m:
            milestone = m.group(1)
            continue
        m = TABLE_ROW.match(line)
        if m and milestone:
            cards[m.group(1)] = (milestone, m.group(5).strip())
    return cards


def card_files():
    """{card id: (milestone, status, has_done_line, file)} from docs/roadmap/Mx-*.md."""
    cards = {}
    for path in sorted((DOCS / "roadmap").glob("M*.md")):
        milestone = path.name.split("-")[0]
        current, status, done = None, None, False

        def flush():
            if current:
                cards[current] = (milestone, status, done, path.name)

        for line in path.read_text().splitlines():
            m = CARD_HEADING.match(line)
            if m or line.startswith("## ") or line.startswith("### "):
                flush()
                current, status, done = (m.group(1) if m else None), None, False
                continue
            if current and status is None:
                s = STATUS_LINE.match(line)
                if s:
                    status = s.group(1).strip()
            if current and DONE_LINE.match(line):
                done = True
        flush()
    return cards


def open_decisions():
    """Decision ids whose owner answer is still pending."""
    pending, current = [], None
    for line in (DOCS / "ROADMAP.md").read_text().splitlines():
        m = re.match(r"^\*\*(D-\d\d|Theme bundle)", line)
        if m:
            current = m.group(1)
        if "**Owner:** pending" in line and current:
            pending.append(current)
        m = re.match(r"^\| (D-\d\d) .*\| pending \|\s*$", line)
        if m:
            pending.append(m.group(1))
    return pending


def spec_progress():
    """{milestone: (checked, total)} from the Progress section of each milestone spec."""
    result = {}
    for path in sorted((DOCS / "milestones").glob("M*.md")):
        text = path.read_text()
        m = re.search(r"^### [\d.]+ Progress\n(.*?)(?=^#{2,3} |\Z)", text, re.M | re.S)
        if not m:
            continue
        boxes = re.findall(r"\[( |x)\]", m.group(1))
        result[path.name.split("-")[0]] = (boxes.count("x"), len(boxes))
    return result


def check():
    problems = []
    road, files = roadmap_cards(), card_files()
    for cid, (ms, status) in road.items():
        if base(status) not in STATUSES:
            problems.append(f"{cid}: unknown status '{status}' in ROADMAP.md")
        if cid not in files:
            problems.append(f"{cid}: in ROADMAP.md ({ms}) but no card in docs/roadmap/")
            continue
        fms, fstatus, has_done, fname = files[cid]
        if fms != ms:
            problems.append(f"{cid}: ROADMAP.md says {ms}, the card lives in {fname}")
        if fstatus is None:
            problems.append(f"{cid}: no '- **Status:**' line in {fname}")
        elif base(fstatus) != base(status):
            problems.append(f"{cid}: ROADMAP.md '{status}' vs {fname} '{fstatus}'")
        if base(status) in DONE_LIKE and not has_done:
            problems.append(f"{cid}: status {base(status)} but no '- **Done:**' evidence line in {fname}")
    for cid, (fms, _, _, fname) in files.items():
        if cid not in road:
            problems.append(f"{cid}: card in {fname} but missing from ROADMAP.md")
    for p in problems:
        print("✗", p)
    print(f"{len(road)} cards checked, {len(problems)} problem(s)")
    return 1 if problems else 0


def summary():
    road = roadmap_cards()
    by_ms = OrderedDict()
    for cid, (ms, status) in road.items():
        by_ms.setdefault(ms, Counter())[base(status)] += 1
    progress = spec_progress()
    order = ["accepted", "done", "in_progress", "specified", "approved-foundation", "approved",
             "changed", "proposed", "deferred", "rejected"]
    print("Milestone  cards  " + "  ".join(s for s in order) + "  spec tasks")
    for ms, counts in by_ms.items():
        total = sum(counts.values())
        cells = "  ".join(f"{counts.get(s, 0):>{len(s)}}" for s in order)
        spec = progress.get(ms)
        spec_txt = f"{spec[0]}/{spec[1]}" if spec else "no spec"
        print(f"{ms:<9}  {total:>5}  {cells}  {spec_txt}")
    pending = open_decisions()
    print("Open decisions:", ", ".join(pending) if pending else "none")
    return 0


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "summary"
    sys.exit({"check": check, "summary": summary}.get(cmd, summary)())
