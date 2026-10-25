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


if __name__ == "__main__":
    unittest.main()
