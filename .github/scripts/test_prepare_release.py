"""Check release ordering, reruns, malformed input, and the CI file update."""

import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from prepare_release import release_version


BASELINE = "name: aster_keyboard\nversion: 1.1.0+2\n"


class ReleaseVersionTest(unittest.TestCase):
    def test_first_release_continues_existing_version(self):
        result = release_version(BASELINE, 1)
        self.assertEqual(result["title"], "Aster Keyboard v1.2")
        self.assertEqual(result["tag"], "v1.2")
        self.assertEqual(result["version_name"], "1.2.0")
        self.assertEqual(result["build_number"], "3")

    def test_every_push_has_a_distinct_increasing_version(self):
        results = [release_version(BASELINE, number) for number in range(1, 101)]
        self.assertEqual(len({result["tag"] for result in results}), 100)
        self.assertEqual([int(result["build_number"]) for result in results], list(range(3, 103)))
        self.assertEqual(results[-1]["tag"], "v1.101")

    def test_rerun_preserves_identity(self):
        self.assertEqual(release_version(BASELINE, 7), release_version(BASELINE, 7))

    def test_invalid_versions_and_run_numbers_fail(self):
        for source in ("", "version: 1.0\n", BASELINE + "version: 2.0.0+3\n"):
            with self.subTest(source=source), self.assertRaises(ValueError):
                release_version(source, 1)
        for run in (0, -1, 2_100_000_000):
            with self.subTest(run=run), self.assertRaises(ValueError):
                release_version(BASELINE, run)

    def test_ci_updates_pubspec_and_exposes_the_same_version(self):
        with tempfile.TemporaryDirectory() as directory:
            pubspec = Path(directory, "pubspec.yaml")
            output = Path(directory, "outputs.txt")
            pubspec.write_bytes(BASELINE.replace("\n", "\r\n").encode())
            environment = {**os.environ, "GITHUB_RUN_NUMBER": "9", "GITHUB_OUTPUT": str(output)}
            subprocess.run(
                [sys.executable, str(Path(__file__).with_name("prepare_release.py")), "--pubspec", str(pubspec)],
                env=environment, check=True, capture_output=True,
            )
            self.assertIn("version: 1.10.0+11", pubspec.read_text())
            self.assertIn("tag=v1.10\n", output.read_text())
            self.assertIn("build_number=11\n", output.read_text())


if __name__ == "__main__":
    unittest.main()
