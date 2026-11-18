"""Cloud cache hand-off must not authorize foreign code or extract outside Maven modules."""

import copy
import importlib.util
import io
import tarfile
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("ci_dependency_cache", Path(__file__).parents[1] / "ci_dependency_cache.py")
cache = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cache)


class DependencyCacheTest(unittest.TestCase):
    def producer(self):
        return {
            "id": 42,
            "head_repository": {"full_name": "owner/repo"},
            "repository": {"full_name": "owner/repo"},
            "head_branch": "dev/test/m7",
            "event": "workflow_dispatch",
            "path": ".github/workflows/nightly.yml",
        }

    def jobs(self):
        return {"jobs": [
            {"name": "nightly", "status": "completed", "conclusion": "success"},
            {"name": "validate-harness", "status": "completed", "conclusion": "success"},
        ]}

    def test_successful_producer_is_independent_of_unfinished_ai_aggregate(self):
        run = self.producer()
        run["status"] = "in_progress"
        cache.validate_producer(run, self.jobs(), "owner/repo", "dev/test/m7", 42)

    def test_wrong_repository_branch_workflow_trigger_or_run_is_refused(self):
        variants = []
        for key, value in (
            ("head_repository", {"full_name": "outsider/fork"}),
            ("repository", {"full_name": "outsider/fork"}),
            ("head_branch", "dev/other/work"),
            ("path", ".github/workflows/outsider.yml"),
            ("event", "pull_request"),
            ("id", 41),
        ):
            run = self.producer()
            run[key] = value
            variants.append(run)
        for run in variants:
            with self.subTest(run=run), self.assertRaises(ValueError):
                cache.validate_producer(run, self.jobs(), "owner/repo", "dev/test/m7", 42)

    def test_failed_or_missing_producer_job_is_refused(self):
        for name in ("nightly", "validate-harness"):
            jobs = copy.deepcopy(self.jobs())
            for job in jobs["jobs"]:
                if job["name"] == name:
                    job["conclusion"] = "failure"
            with self.subTest(name=name), self.assertRaises(ValueError):
                cache.validate_producer(self.producer(), jobs, "owner/repo", "dev/test/m7", 42)
        with self.assertRaises(ValueError):
            cache.validate_producer(self.producer(), {"jobs": []}, "owner/repo", "dev/test/m7", 42)

    def archive(self, root, entries):
        target = root / "modules.tar.gz"
        with tarfile.open(target, "w:gz") as archive:
            for name, kind, content in entries:
                info = tarfile.TarInfo(name)
                info.type = kind
                if kind == tarfile.REGTYPE:
                    info.size = len(content)
                    archive.addfile(info, io.BytesIO(content))
                else:
                    info.linkname = "../../outside"
                    archive.addfile(info)
        return target

    def test_only_module_files_are_restored(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = self.archive(root, [("modules-2/files-2.1/group/item.jar", tarfile.REGTYPE, b"jar")])
            cache.restore(archive, root / "caches")
            self.assertEqual((root / "caches/modules-2/files-2.1/group/item.jar").read_bytes(), b"jar")

    def test_traversal_other_caches_and_links_are_refused_before_any_write(self):
        invalid = [
            ("../outside", tarfile.REGTYPE),
            ("/tmp/outside", tarfile.REGTYPE),
            ("modules-2/../../outside", tarfile.REGTYPE),
            ("modules-2\\..\\outside", tarfile.REGTYPE),
            ("init.d/unsafe.gradle", tarfile.REGTYPE),
            ("modules-2/link", tarfile.SYMTYPE),
            ("modules-2/link", tarfile.LNKTYPE),
            ("modules-2", tarfile.REGTYPE),
        ]
        for name, kind in invalid:
            with self.subTest(name=name, kind=kind), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                archive = self.archive(root, [("modules-2/good", tarfile.REGTYPE, b"ok"), (name, kind, b"bad")])
                with self.assertRaises(ValueError):
                    cache.restore(archive, root / "caches")
                self.assertFalse((root / "caches/modules-2/good").exists())
                self.assertFalse((root / "outside").exists())

    def test_existing_destination_symlink_and_oversized_archive_are_refused(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = self.archive(root, [("modules-2/file", tarfile.REGTYPE, b"four")])
            with patch.object(cache, "MAX_BYTES", 3), self.assertRaises(ValueError):
                cache.restore(archive, root / "caches")
            outside = root / "outside"
            outside.mkdir()
            (root / "caches/modules-2").symlink_to(outside, target_is_directory=True)
            with self.assertRaises(ValueError):
                cache.restore(archive, root / "caches")
            self.assertFalse((outside / "file").exists())


if __name__ == "__main__":
    unittest.main()
