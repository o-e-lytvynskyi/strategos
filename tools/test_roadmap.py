"""Regression checks for roadmap parsing on non-UTF-8 hosts and empty documents."""

import contextlib
import io
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import roadmap


class RoadmapTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.docs = Path(self.tmp.name)
        (self.docs / "roadmap").mkdir()
        self.doc_patch = patch.object(roadmap, "DOCS", self.docs)
        self.doc_patch.start()
        self.addCleanup(self.doc_patch.stop)
        roadmap.DUPLICATES.clear()
        roadmap.RESERVE.clear()

    def fixture(self, card_status="specified"):
        (self.docs / "ROADMAP.md").write_text(
            "## Features per milestone\n### M5 · Combat\n"
            "| CMB-04 | Ranged combat | L | CMB-01 | specified |\n",
            encoding="utf-8",
        )
        (self.docs / "roadmap" / "M5-combat.md").write_text(
            "#### CMB-04 · Ranged combat\n- **Status:** " + card_status + "\n",
            encoding="utf-8",
        )

    def check(self):
        with contextlib.redirect_stdout(io.StringIO()):
            return roadmap.check()

    def test_utf8_documents_are_parsed_when_the_host_default_is_cp1251(self):
        self.fixture()
        original = Path.read_text

        def host_read(path, encoding=None, errors=None):
            return original(path, encoding=encoding or "cp1251", errors=errors)

        with patch.object(Path, "read_text", host_read):
            self.assertEqual(1, len(roadmap.roadmap_cards()))
            self.assertEqual(0, self.check())

    def test_empty_documents_fail_instead_of_succeeding_with_zero_cards(self):
        (self.docs / "ROADMAP.md").write_text("# Roadmap\n", encoding="utf-8")
        self.assertEqual(1, self.check())

    def test_a_status_mismatch_fails(self):
        self.fixture("approved")
        self.assertEqual(1, self.check())


if __name__ == "__main__":
    unittest.main()
