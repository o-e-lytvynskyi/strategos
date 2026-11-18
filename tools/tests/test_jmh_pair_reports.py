"""A stale case count must not hide a missing benchmark, changed measurement, or unrelated archived report."""

import copy
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).parents[1]))
spec = importlib.util.spec_from_file_location("jmh_pair_reports", Path(__file__).parents[1] / "jmh_pair_reports.py")
reports = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reports)


class JmhPairReportsTest(unittest.TestCase):
    def fixture(self, root):
        sources = root / "sources"
        sources.mkdir()
        (sources / "Bench.java").write_text("@Benchmark\npublic void run() {}\n", encoding="utf-8")
        manifest = root / "suite.json"
        manifest.write_text(json.dumps({"schema": 1, "benchmarkMethods": 1, "configurations": [
            {"benchmark": "p.Bench.run", "params": {"mode": "one"}},
            {"benchmark": "p.Bench.run", "params": {"mode": "two"}},
        ]}), encoding="utf-8")
        evidence = root / "evidence"
        evidence.mkdir()
        (evidence / "control-commit.txt").write_text("a" * 40, encoding="utf-8")
        (evidence / "candidate-commit.txt").write_text("b" * 40, encoding="utf-8")
        rows = []
        for mode in ("one", "two"):
            row = {name: 1 for name in reports.FIELDS}
            row.update(benchmark="p.Bench.run", params={"mode": mode},
                       primaryMetric={"score": 10.0, "scoreUnit": "ns/op"})
            rows.append(row)
        for checkout in ("control", "candidate"):
            path = root / checkout / reports.RESULT
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps(rows), encoding="utf-8")
        return manifest, sources, rows

    def test_complete_parameter_matrix_preserves_original_report_bytes(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            manifest, sources, _ = self.fixture(root)
            original = (root / "candidate" / reports.RESULT).read_bytes()
            result = reports.compare(root, manifest, sources, "a" * 40, "b" * 40)
            self.assertEqual(result["configurations"], 2)
            self.assertEqual((root / "candidate" / reports.RESULT).read_bytes(), original)

    def test_missing_extra_duplicate_parameters_or_changed_measurement_are_refused(self):
        for problem in ("missing", "extra", "duplicate", "parameters", "settings", "unit", "score"):
            with self.subTest(problem=problem), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                manifest, sources, rows = self.fixture(root)
                bad = copy.deepcopy(rows)
                if problem == "missing": bad.pop()
                elif problem == "extra": bad.append(dict(bad[0], benchmark="p.Other.run"))
                elif problem == "duplicate": bad.append(copy.deepcopy(bad[0]))
                elif problem == "parameters": bad[0]["params"]["mode"] = "missing-mode"
                elif problem == "settings": bad[0]["forks"] = 2
                elif problem == "unit": bad[0]["primaryMetric"]["scoreUnit"] = "ms/op"
                elif problem == "score": bad[0]["primaryMetric"]["score"] = float("nan")
                (root / "candidate" / reports.RESULT).write_text(json.dumps(bad), encoding="utf-8")
                with self.assertRaises(ValueError):
                    reports.compare(root, manifest, sources, "a" * 40, "b" * 40)

    def test_changed_source_inventory_or_commit_cannot_reinterpret_old_results(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            manifest, sources, _ = self.fixture(root)
            with self.assertRaises(ValueError):
                reports.compare(root, manifest, sources, "a" * 40, "c" * 40)
            (sources / "Extra.java").write_text("@Benchmark\npublic void missing() {}\n", encoding="utf-8")
            with self.assertRaises(ValueError):
                reports.compare(root, manifest, sources, "a" * 40, "b" * 40)

    def test_reuse_requires_only_the_known_wrapper_failure_after_both_measurements_succeeded(self):
        run = {"id": 42, "repository": {"full_name": "owner/repo"},
               "head_repository": {"full_name": "owner/repo"}, "head_branch": "dev/test/m7",
               "event": "workflow_dispatch", "path": ".github/workflows/perf.yml", "head_sha": "b" * 40}
        steps = [{"name": name, "conclusion": "success"} for name in
                 ("Build both benchmark jars before measuring", "Control JMH", "Candidate JMH")]
        steps.append({"name": "Require comparable complete results", "conclusion": "failure"})
        jobs = {"jobs": [{"name": "paired-jmh", "status": "completed", "conclusion": "failure", "steps": steps}]}
        self.assertEqual(reports.validate_producer(run, jobs, "owner/repo", "dev/test/m7", 42), "b" * 40)
        for problem in ("fork", "measurement", "performance"):
            bad_run, bad_jobs = copy.deepcopy(run), copy.deepcopy(jobs)
            if problem == "fork": bad_run["head_repository"]["full_name"] = "outsider/fork"
            elif problem == "measurement": bad_jobs["jobs"][0]["steps"][1]["conclusion"] = "failure"
            else: bad_jobs["jobs"][0]["steps"][-1]["name"] = "Existing 15 percent and allocation gates"
            with self.subTest(problem=problem), self.assertRaises(ValueError):
                reports.validate_producer(bad_run, bad_jobs, "owner/repo", "dev/test/m7", 42)


if __name__ == "__main__":
    unittest.main()
