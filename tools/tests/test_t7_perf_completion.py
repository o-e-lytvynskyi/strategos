import copy
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from t7_perf_completion import completed, producer


class CompletionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.old = self.root / "old"
        self.new = self.root / "new"
        self.manifest = {"producer_run": 7, "source": "abc", "completed": {},
                         "remaining": {n: n for n in ("battle_soak", "mvp_soak", "group_paths_budget_d")},
                         "ticks": {"battle_soak": 6000, "mvp_soak": 36000}}
        for root in (self.old, self.new):
            (root / "mod/build/reports/perf").mkdir(parents=True)
        for i in range(21):
            path = self.report(self.old, f"old_{i}", {"verdict": "pass"})
            self.manifest["completed"][path.stem] = hashlib.sha256(path.read_bytes()).hexdigest()
        for name in self.manifest["remaining"]:
            self.report(self.new, name, {"verdict": "pass", "ticks": self.manifest["ticks"].get(name)})
        log = self.old / "mod/build/gametest-perf/logs/latest.log"
        log.parent.mkdir(parents=True)
        log.write_text("[" + "+" * 21 + "_  ]", encoding="utf-8")
        self.xml = self.new / "mod/build/test-results/gametest-perf/TEST-gametest-perf.xml"
        self.xml.parent.mkdir(parents=True)
        self.xml.write_text('<testsuite>' + ''.join(f'<testcase name="perftests.{n}"/>' for n in self.manifest["remaining"]) + '</testsuite>', encoding="utf-8")

    def report(self, root, name, data):
        path = root / "mod/build/reports/perf" / (name + ".json")
        path.write_text(json.dumps({"test": name, **data}), encoding="utf-8")
        return path

    def complete(self):
        completed(self.manifest, self.old, self.new, self.root / "evidence.json", 8)

    def test_complete_and_reject_modified_original(self):
        self.complete()
        self.report(self.old, "old_0", {"verdict": "pass", "changed": True})
        with self.assertRaisesRegex(ValueError, "bytes changed"):
            self.complete()

    def test_missing_extra_invalid_failed_and_shortened(self):
        original = (self.new / "mod/build/reports/perf/mvp_soak.json").read_bytes()
        for data in ({"verdict": "invalid", "ticks": 36000}, {"verdict": "fail", "ticks": 36000}, {"verdict": "pass", "ticks": 2400}):
            self.report(self.new, "mvp_soak", data)
            with self.assertRaises(ValueError):
                self.complete()
        path = self.new / "mod/build/reports/perf/mvp_soak.json"
        path.unlink()
        with self.assertRaises(ValueError):
            self.complete()
        path.write_bytes(original)
        self.report(self.new, "unexpected", {"verdict": "pass"})
        with self.assertRaises(ValueError):
            self.complete()

    def test_xml_failure_skip_and_wrong_inventory(self):
        original = self.xml.read_text(encoding="utf-8")
        for child in ("failure", "error", "skipped"):
            self.xml.write_text(original.replace('/>', f'><{child}/></testcase>', 1), encoding="utf-8")
            with self.assertRaises(ValueError):
                self.complete()
        self.xml.write_text(original.replace('battle_soak', 'other'), encoding="utf-8")
        with self.assertRaises(ValueError):
            self.complete()

    def test_untrusted_or_failed_producer(self):
        run = {"id": 7, "head_sha": "abc", "repository": {"full_name": "a/b"}, "head_repository": {"full_name": "a/b"}, "head_branch": "dev/test", "event": "workflow_dispatch", "path": ".github/workflows/perf.yml"}
        jobs = {"jobs": [{"name": "perf", "conclusion": "cancelled", "steps": []}]}
        producer(run, jobs, self.manifest, "a/b", "dev/test")
        for key in ("head_sha", "head_branch", "event", "path"):
            bad = copy.deepcopy(run)
            bad[key] = "wrong"
            with self.assertRaises(ValueError):
                producer(bad, jobs, self.manifest, "a/b", "dev/test")
        jobs["jobs"][0]["steps"] = [{"conclusion": "failure"}]
        with self.assertRaises(ValueError):
            producer(run, jobs, self.manifest, "a/b", "dev/test")
