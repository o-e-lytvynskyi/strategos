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


class RowsTest(unittest.TestCase):
    def test_header_and_rule_are_skipped(self):
        text = ("| Work | Developer | Branch | Status | Since | Note |\n|---|---|---|---|---|---|\n"
                "| M5 | alex | `dev/alex/m5` | assigned | 2026-10-26 |  |\n")
        [row] = claims.rows(text)
        self.assertEqual((row["work"], row["branch"], row["status"], row["note"]),
                         ("M5", "dev/alex/m5", "assigned", ""))


def branch_file(branch, status):
    return f"# w on {branch}\n\n**Developer:** x · **Branch:** {branch} · **Work:** w · **Status:** {status}\n"


def row(branch, status):
    return {"work": branch.rsplit("/", 1)[-1], "developer": "x", "branch": branch, "status": status}


class RecordsTest(unittest.TestCase):
    def problems(self, rows, files, head=None):
        return claims.record_problems(rows, files, head)

    def test_status_of_a_branch_file(self):
        self.assertEqual(claims.branch_status(branch_file("dev/a/b", "merged 2026-10-28")), "merged")
        self.assertEqual(claims.branch_status(branch_file("dev/a/b", "ready for review")), "ready for review")
        self.assertEqual(claims.branch_status("**Status:** in progress · **Next:** T3\n"), "in progress")

    def test_consistent_records_pass(self):
        rows = [row("dev/a/m5", "assigned"), row("dev/a/m6", "done"), row("dev/b/m5", "released"),
                row("dev/c/m7", "reserved")]
        files = {"a-m5.md": branch_file("dev/a/m5", "claimed"),
                 "a-m6.md": branch_file("dev/a/m6", "merged 2026-10-28"),
                 "b-m5.md": branch_file("dev/b/m5", "released 2026-10-26")}
        self.assertEqual(self.problems(rows, files), [])

    def test_delivery_merged_without_records_is_caught(self):
        rows = [row("dev/a/m7-spec", "assigned")]
        files = {"a-m7-spec.md": branch_file("dev/a/m7-spec", "ready for review")}
        [problem] = self.problems(rows, files)
        self.assertIn("ready for review", problem)

    def test_the_hand_in_pull_request_itself_is_ready_for_review(self):
        rows = [row("dev/a/m7-spec", "assigned")]
        files = {"a-m7-spec.md": branch_file("dev/a/m7-spec", "ready for review")}
        self.assertEqual(self.problems(rows, files, head="dev/a/m7-spec"), [])

    def test_a_fork_head_matches_its_row(self):
        rows = [row("someone/strategos:dev/a/w", "assigned")]
        files = {"a-w.md": branch_file("someone/strategos:dev/a/w", "ready for review")}
        self.assertEqual(self.problems(rows, files, head="dev/a/w"), [])

    def test_done_row_needs_a_merged_branch_file_and_back(self):
        self.assertEqual(len(self.problems([row("dev/a/m6", "done")],
                                           {"f.md": branch_file("dev/a/m6", "ready for review")})), 1)
        self.assertEqual(len(self.problems([row("dev/a/m6", "assigned")],
                                           {"f.md": branch_file("dev/a/m6", "merged 2026-10-28")})), 1)

    def test_records_for_the_merge_set_both_sides(self):
        self.assertEqual(len(self.problems([row("dev/a/m6", "done")],
                                           {"f.md": branch_file("dev/a/m6", "ready for review")}, head="dev/a/m6")), 1)

    def test_released_row_needs_a_released_branch_file(self):
        self.assertEqual(len(self.problems([row("dev/b/m5", "released")],
                                           {"f.md": branch_file("dev/b/m5", "claimed")})), 1)

    def test_claimed_row_needs_its_branch_file(self):
        [problem] = self.problems([row("dev/a/m5", "assigned")], {})
        self.assertIn("no branch file", problem)

    def test_a_ready_file_without_a_row_is_caught(self):
        [problem] = self.problems([], {"f.md": branch_file("dev/a/x", "ready for review")})
        self.assertIn("ready for review", problem)


if __name__ == "__main__":
    unittest.main()
