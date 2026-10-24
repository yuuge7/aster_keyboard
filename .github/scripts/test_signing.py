"""Exercise secret restoration and protection against identity replacement."""

import base64
import importlib.util
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location("aster_signing", Path(__file__).resolve().parents[2] / "scripts/signing.py")
signing = importlib.util.module_from_spec(spec)
spec.loader.exec_module(signing)


class SigningTest(unittest.TestCase):
    def setUp(self):
        directory = self.enterContext(tempfile.TemporaryDirectory())
        self.key = Path(directory, "aster-release.jks")
        self.properties = Path(directory, "key.properties")
        self.enterContext(patch.multiple(signing, KEYSTORE=self.key, PROPERTIES=self.properties))

    def secrets(self, **overrides):
        return {
            "ANDROID_KEYSTORE_BASE64": base64.b64encode(b"test-keystore-bytes").decode(),
            "ANDROID_STORE_PASSWORD": "test-store-password",
            "ANDROID_KEY_PASSWORD": "test-key-password",
            "ANDROID_KEY_ALIAS": "aster",
            **overrides,
        }

    def test_restoration_preserves_key_bytes_and_relative_path(self):
        with patch.dict(os.environ, self.secrets(), clear=True):
            signing.restore()
        self.assertEqual(self.key.read_bytes(), b"test-keystore-bytes")
        self.assertIn("storeFile=aster-release.jks\n", self.properties.read_text())
        self.assertIn("keyAlias=aster\n", self.properties.read_text())

    def test_existing_identity_is_never_replaced(self):
        for existing in (self.key, self.properties):
            with self.subTest(file=existing.name):
                existing.write_bytes(b"original")
                with patch.object(sys, "argv", ["signing.py", "restore"]):
                    with self.assertRaisesRegex(SystemExit, "Refusing to overwrite"):
                        signing.main()
                self.assertEqual(existing.read_bytes(), b"original")
                existing.unlink()

    def test_missing_or_malformed_secrets_create_no_files(self):
        for environment in ({}, self.secrets(ANDROID_KEYSTORE_BASE64="not-base64!")):
            with self.subTest(environment=bool(environment)):
                with patch.dict(os.environ, environment, clear=True), self.assertRaises(SystemExit):
                    signing.restore()
                self.assertFalse(self.key.exists())
                self.assertFalse(self.properties.exists())

    def test_password_values_escape_properties_syntax_and_unicode(self):
        self.assertEqual(signing.properties_value(" space\\line\n\t\r"), "\\ space\\\\line\\n\\t\\r")
        self.assertEqual(signing.properties_value("é😀"), "\\u00e9\\ud83d\\ude00")


if __name__ == "__main__":
    unittest.main()
