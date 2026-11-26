"""Self-test of libmpv_cache_key.sh on copies of the real files: what must change the release cache key does, and what
must not does not.

    python3 -I -m unittest discover -s tools/ci -p 'test_*.py'
"""
import pathlib
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "tools" / "ci" / "libmpv_cache_key.sh"
FILES = ["tools/libmpv/Dockerfile", "tools/libmpv/build.sh", "tools/libmpv/sources.txt", "tools/libmpv/README.md",
         "desktop/app/packaging/libmpv-windows.properties"]
PROPERTIES = "desktop/app/packaging/libmpv-windows.properties"


class CacheKeyTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = pathlib.Path(self.tmp.name)
        for f in FILES:
            (self.root / f).parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / f, self.root / f)
        self.base = self.key()

    def key(self):
        result = subprocess.run(["bash", str(SCRIPT), str(self.root)], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout.strip()

    def change(self, path, edit):
        file = self.root / path
        file.write_bytes(edit(file.read_bytes()))

    def replace_pin(self, name):
        def edit(data):
            text = data.decode()
            old = re.search(rf"^{name}=([0-9a-f]{{64}})", text, re.M).group(1)
            return text.replace(f"{name}={old}", f"{name}={'0' * 64}").encode()
        self.change(PROPERTIES, edit)

    def test_the_key_is_a_versioned_sha256(self):
        self.assertRegex(self.base, r"^libmpv-v2-[0-9a-f]{64}$")

    def test_a_readme_change_keeps_the_key(self):
        self.change("tools/libmpv/README.md", lambda d: d + b"\nAnother sentence.\n")
        self.assertEqual(self.key(), self.base)

    def test_a_comment_in_the_properties_keeps_the_key(self):
        self.change(PROPERTIES, lambda d: re.sub(rb"^#[^\n]*", b"# a reworded comment (docs/elsewhere.md)", d, count=1, flags=re.M)
                    + b"# one more comment\n")
        self.assertEqual(self.key(), self.base)

    def test_windows_line_endings_in_the_properties_keep_the_key(self):
        self.change(PROPERTIES, lambda d: d.replace(b"\r\n", b"\n").replace(b"\n", b"\r\n"))
        self.assertEqual(self.key(), self.base)

    def test_the_build_definition_changes_the_key(self):
        for path in ["tools/libmpv/Dockerfile", "tools/libmpv/build.sh", "tools/libmpv/sources.txt"]:
            with self.subTest(path=path):
                self.setUp()
                self.change(path, lambda d: d + b"\n# changed\n")
                self.assertNotEqual(self.key(), self.base)

    def test_either_pin_changes_the_key(self):
        for name in ["sourceSha256", "dllSha256"]:
            with self.subTest(pin=name):
                self.setUp()
                self.replace_pin(name)
                self.assertNotEqual(self.key(), self.base)

    def test_a_missing_pin_fails_rather_than_making_a_key(self):
        self.change(PROPERTIES, lambda d: re.sub(rb"^sourceSha256=.*$", b"", d, flags=re.M))
        result = subprocess.run(["bash", str(SCRIPT), str(self.root)], capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout.strip(), "")
        self.assertIn("sourceSha256 missing", result.stderr)


if __name__ == "__main__":
    unittest.main()
