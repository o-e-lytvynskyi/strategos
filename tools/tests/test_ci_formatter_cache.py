"""Pinned formatter recovery accepts only a successful own-branch verify and exact approved bytes."""
import copy
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("ci_formatter_cache", Path(__file__).parents[1] / "ci_formatter_cache.py")
cache = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cache)

class FormatterCacheTest(unittest.TestCase):
    def producer(self):
        return {"id": 42, "repository": {"full_name": "owner/repo"}, "head_repository": {"full_name": "owner/repo"},
                "head_branch": "dev/test/m7", "path": ".github/workflows/verify.yml", "event": "workflow_dispatch",
                "status": "completed", "conclusion": "success"}

    def test_successful_own_verify_is_accepted(self):
        cache.validate_producer(self.producer(), "owner/repo", "dev/test/m7", 42)

    def test_foreign_failed_unfinished_or_wrong_producers_are_refused(self):
        for key, value in (("id", 41), ("repository", {"full_name": "other/repo"}),
                ("head_repository", {"full_name": "other/fork"}), ("head_branch", "main"),
                ("path", ".github/workflows/other.yml"), ("event", "pull_request"),
                ("status", "in_progress"), ("conclusion", "failure")):
            run = copy.deepcopy(self.producer())
            run[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                cache.validate_producer(run, "owner/repo", "dev/test/m7", 42)

    def files(self, root):
        source = root / "source" / "build" / "reports" / "central-endpoint"
        source.mkdir(parents=True)
        (source / "formatter.pom").write_bytes(b"pom")
        (source / "formatter.jar").write_bytes(b"jar")
        hashes = {"pom": hashlib.sha256(b"pom").hexdigest(), "jar": hashlib.sha256(b"jar").hexdigest()}
        return source, hashes

    def test_only_two_hash_checked_files_are_written_to_the_fixed_coordinate(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source, hashes = self.files(root)
            (source / "unrelated").write_bytes(b"ignored")
            with patch.object(cache, "HASHES", hashes):
                cache.restore(root / "source", root / "repository")
            files = sorted(p.relative_to(root / "repository").as_posix() for p in (root / "repository").rglob("*") if p.is_file())
            self.assertEqual(files, ["com/palantir/javaformat/palantir-java-format/2.102.0/palantir-java-format-2.102.0.jar",
                                    "com/palantir/javaformat/palantir-java-format/2.102.0/palantir-java-format-2.102.0.pom"])

    def test_one_bad_file_or_missing_file_causes_no_partial_publication(self):
        for missing in (True, False):
            with self.subTest(missing=missing), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                source, hashes = self.files(root)
                if missing:
                    (source / "formatter.jar").unlink()
                else:
                    (source / "formatter.jar").write_bytes(b"changed")
                with patch.object(cache, "HASHES", hashes), self.assertRaises((ValueError, FileNotFoundError)):
                    cache.restore(root / "source", root / "repository")
                self.assertFalse((root / "repository").exists())

    def test_source_and_destination_symlinks_cannot_redirect_reads_or_writes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source, hashes = self.files(root)
            outside = root / "outside"
            outside.mkdir()
            (source / "formatter.jar").unlink()
            (source / "formatter.jar").symlink_to(outside / "jar")
            with patch.object(cache, "HASHES", hashes), self.assertRaises(ValueError):
                cache.restore(root / "source", root / "repository")
            (source / "formatter.jar").unlink()
            (source / "formatter.jar").write_bytes(b"jar")
            destination = root / "repository"
            destination.symlink_to(outside, target_is_directory=True)
            with patch.object(cache, "HASHES", hashes), self.assertRaises(ValueError):
                cache.restore(root / "source", destination)
            self.assertEqual(list(outside.iterdir()), [])
