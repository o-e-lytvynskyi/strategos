"""Tests of tools/claims.py: python3 -m unittest discover -s tools -p 'test_*.py'"""

import unittest

import claims

SPEC = "### 7.3 Progress\n\n- [ ] T1 · [ ] T2 · [x] T3 (2026-10-25) · [ ] T4\n- [ ] T5\n\n## 8. Next\n"


def read(path):
    if "M5-" in path:
        return SPEC
    raise FileNotFoundError(path)


claims.spec_tasks = lambda milestone, _read: claims.TASK.findall(SPEC.split("## 8.")[0]) if milestone == "M5" else None


class UnitsTest(unittest.TestCase):
    def test_milestone_is_every_task_of_its_spec(self):
        self.assertEqual(claims.units("M5", read), {f"M5:T{i}" for i in range(1, 6)})

    def test_range_follows_the_spec_order(self):
        self.assertEqual(claims.units("M5 T2–T4", read), {"M5:T2", "M5:T3", "M5:T4"})

    def test_unknown_range_covers_the_whole_milestone(self):
        self.assertEqual(claims.units("M5 T2–T9", read), {"M5:*"})

    def test_list_and_free_names(self):
        self.assertEqual(claims.units("M5 T1, T5", read), {"M5:T1", "M5:T5"})
        self.assertEqual(claims.units("fix-main", read), {"fix-main"})


class OverlapTest(unittest.TestCase):
    def test_milestone_and_its_task_overlap(self):
        self.assertTrue(claims.overlaps("M5", "M5 T3", read))

    def test_disjoint_tasks_do_not(self):
        self.assertFalse(claims.overlaps("M5 T1–T2", "M5 T3, T4", read))

    def test_milestone_without_spec_covers_its_tasks(self):
        self.assertTrue(claims.overlaps("M7", "M7 T2", read))
        self.assertFalse(claims.overlaps("M7", "M6 spec", read))

    def test_spec_claim_is_not_the_milestone(self):
        self.assertFalse(claims.overlaps("M6 spec", "M5", read))


def branch_file(branch, status, work="w", developer="x", extra=""):
    return (f"# {work} on {branch}\n\n**Developer:** {developer} (Codex) · **Branch:** {branch} · **Work:** {work}"
            f" · **Base:** main @ abc · **Claimed:** 2026-10-26 · **Status:** {status}\n{extra}\n## Now\n")


def problems(files, head=None, before=None):
    return claims.problems_of(claims.claims_in(files), claims.claims_in(before) if before else None, head, read)


class ParseTest(unittest.TestCase):
    def test_header_fields_status_and_note(self):
        c = claims.parse("a-m5.md", branch_file("`dev/alex/m5`", "merged 2026-10-28", "M5", "alex",
                                                "**Note:** delivered in #33\n"))
        self.assertEqual((c["developer"], c["branch"], c["work"], c["since"], c["status"], c["note"]),
                         ("alex", "dev/alex/m5", "M5", "2026-10-26", "merged", "delivered in #33"))

    def test_readme_is_not_a_claim(self):
        self.assertEqual([c["file"] for c in claims.claims_in({"README.md": "x", "a.md": branch_file("dev/a/w", "claimed")})],
                         ["a.md"])


class CheckTest(unittest.TestCase):
    def test_valid_claims_pass(self):
        files = {"a.md": branch_file("dev/a/m5", "claimed", "M5 T1–T2", "a"),
                 "b.md": branch_file("dev/b/m6", "merged 2026-10-28", "M6 spec", "b"),
                 "c.md": branch_file("dev/c/w", "released 2026-10-26", "M5 T3", "c"),
                 "d.md": branch_file("dev/d/x", "reserved", "M5 T4", "d")}
        self.assertEqual(problems(files), [])

    def test_unknown_or_undated_status(self):
        self.assertIn("unknown Status", problems({"a.md": branch_file("dev/a/w", "assigned")})[0])
        self.assertIn("needs its date", problems({"a.md": branch_file("dev/a/w", "merged")})[0])

    def test_missing_header_field(self):
        [p] = problems({"a.md": "# x\n\n**Developer:** a · **Status:** claimed\n"})
        self.assertIn("Branch, Work", p)

    def test_active_claims_must_not_overlap(self):
        files = {"a.md": branch_file("dev/a/m5", "claimed", "M5", "a"),
                 "b.md": branch_file("dev/b/m5", "in progress", "M5 T3", "b")}
        [p] = problems(files)
        self.assertIn("overlap", p)

    def test_finished_claims_do_not_block_the_work(self):
        files = {"a.md": branch_file("dev/a/m5", "released 2026-10-26", "M5", "a"),
                 "b.md": branch_file("dev/b/m5", "claimed", "M5", "b")}
        self.assertEqual(problems(files), [])

    def test_delivery_merged_without_records_is_caught(self):
        [p] = problems({"a.md": branch_file("dev/a/m7-spec", "ready for review")})
        self.assertIn("ready for review", p)

    def test_the_hand_in_pull_request_itself_is_ready_for_review(self):
        self.assertEqual(problems({"a.md": branch_file("dev/a/m7-spec", "ready for review")}, head="dev/a/m7-spec"), [])

    def test_a_stacked_branch_carries_an_unmerged_ready_file(self):
        before = {"a.md": branch_file("dev/a/m5", "claimed", "M5 T1", "a")}
        files = {"a.md": branch_file("dev/a/m5", "ready for review", "M5 T1", "a"),
                 "b.md": branch_file("dev/a/m5-next", "claimed", "M5 T2", "a")}
        self.assertEqual(problems(files, head="dev/a/m5-next", before=before), [])
        self.assertEqual(len(problems(files, head="dev/a/m5-next", before=files)), 1)

    def test_active_status_takes_no_trailing_text(self):
        self.assertIn("unknown Status", problems({"a.md": branch_file("dev/a/w", "claimed because x")})[0])

    def test_a_fork_head_matches_its_claim(self):
        files = {"a.md": branch_file("someone/strategos:dev/a/w", "ready for review")}
        self.assertEqual(problems(files, head="dev/a/w"), [])

    def test_takeover_needs_the_holders_hand_over(self):
        before = {"a.md": branch_file("dev/a/m5", "claimed", "M5", "a")}
        taken = {"a.md": branch_file("dev/a/m5", "released 2026-10-29", "M5", "a"),
                 "b.md": branch_file("dev/b/m5", "claimed", "M5", "b")}
        [p] = problems(taken, before=before)
        self.assertIn("handed over by a", p)
        taken["b.md"] = branch_file("dev/b/m5", "claimed", "M5", "b", "**Note:** handed over by abc, #18\n")
        self.assertEqual(len(problems(taken, before=before)), 1)
        taken["b.md"] = branch_file("dev/b/m5", "claimed", "M5", "b", "\n## Log\n- handed over by a, #18\n")
        self.assertEqual(len(problems(taken, before=before)), 1)
        taken["b.md"] = branch_file("dev/b/m5", "claimed", "M5", "b", "**Note:** handed over by a, #18\n")
        self.assertEqual(problems(taken, before=before), [])


if __name__ == "__main__":
    unittest.main()
