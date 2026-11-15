import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("ai_soak_reports", Path(__file__).parents[1] / "ai_soak_reports.py")
reports = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reports)
COMMIT = "a" * 40


class AiSoakEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def fixture(self, root, seed, commit=COMMIT):
        (root / reports.XML).parent.mkdir(parents=True, exist_ok=True)
        (root / reports.XML).write_text('<testsuite><testcase name="twenty_seeds_of_normal_against_normal"/></testsuite>')
        (root / reports.LOG).parent.mkdir(parents=True, exist_ok=True)
        (root / reports.LOG).write_text(f'aisoak seed {seed}: conquered at tick 100; the journal replays to its hash\n'
                                      'aisoak: 1 matches, 1 by conquest, 0 to the limit, 100 game ticks in all\n')
        result = reports.inspect(root, seed, commit)
        (root / reports.SUMMARY).parent.mkdir(parents=True, exist_ok=True)
        (root / reports.SUMMARY).write_text(json.dumps(result))
        return root

    def full_set(self):
        for seed in reports.SEEDS:
            self.fixture(self.root / f'aisoak-{seed}-reports', seed)

    def test_complete_set_has_every_seed_once(self):
        self.full_set()
        self.assertEqual(reports.aggregate(self.root, COMMIT)['seeds'], list(range(1, 21)))

    def test_missing_shard_is_rejected(self):
        self.fixture(self.root / 'aisoak-1-reports', 1)
        with self.assertRaises(ValueError): reports.aggregate(self.root, COMMIT)

    def test_mixed_commits_are_rejected(self):
        self.full_set()
        self.fixture(self.root / 'aisoak-20-reports', 20, 'b' * 40)
        with self.assertRaises(ValueError): reports.aggregate(self.root, COMMIT)

    def test_duplicate_or_wrong_completed_seed_is_rejected(self):
        root = self.fixture(self.root, 3)
        for text in ['aisoak seed 3: conquered; the journal replays to its hash\n' * 2,
                     'aisoak seed 4: conquered; the journal replays to its hash\n']:
            (root / reports.LOG).write_text(text)
            with self.assertRaises(ValueError): reports.inspect(root, 3, COMMIT)

    def test_failed_skipped_empty_or_truncated_xml_is_rejected(self):
        root = self.fixture(self.root, 1)
        for inner in ['<failure/>', '<error/>', '<skipped/>']:
            (root / reports.XML).write_text('<testsuite><testcase name="twenty_seeds_of_normal_against_normal">'
                                          + inner + '</testcase></testsuite>')
            with self.assertRaises(ValueError): reports.inspect(root, 1, COMMIT)
        (root / reports.XML).write_text('<testsuite/>')
        with self.assertRaises(ValueError): reports.inspect(root, 1, COMMIT)

    def test_truncated_xml_is_rejected(self):
        root = self.fixture(self.root, 1)
        (root / reports.XML).write_text('<testsuite><testcase')
        with self.assertRaises(reports.ET.ParseError): reports.inspect(root, 1, COMMIT)

    def test_missing_final_summary_is_rejected(self):
        root = self.fixture(self.root, 1)
        (root / reports.LOG).write_text('aisoak seed 1: conquered; the journal replays to its hash\n')
        with self.assertRaises(ValueError): reports.inspect(root, 1, COMMIT)

    def test_changed_evidence_is_rejected_even_when_xml_passes(self):
        self.full_set()
        path = self.root / 'aisoak-1-reports' / reports.LOG
        path.write_text(path.read_text() + 'changed after manifest\n')
        with self.assertRaises(ValueError): reports.aggregate(self.root, COMMIT)


if __name__ == '__main__': unittest.main()
