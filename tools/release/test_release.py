"""Self-test of `release.py verify` (docs/RELEASE.md, "Release system"): what passes, and each way a release is refused.

    python -I -m unittest discover -s tools/release -p 'test_*.py'

Builds small stand-in releases in a temporary folder (a Linux archive, a Windows zip with a libmpv inside, the
libmpv source bundle), with the manifest, SHA256SUMS, and provenance as release.py writes them. No APK: reading one
needs the Android SDK, and the APK checks run in every real verification.
"""
import hashlib
import importlib.util
import io
import json
import pathlib
import sys
import tempfile
import unittest
import zipfile
from contextlib import redirect_stdout

HERE = pathlib.Path(__file__).resolve().parent
sys.dont_write_bytecode = True  # nothing written into the source tree
spec = importlib.util.spec_from_file_location("release", HERE / "release.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)

COMMIT = "0123456789abcdef0123456789abcdef01234567"
DLL = b"MZ stand-in for libmpv-2.dll"


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


class Verify(unittest.TestCase):
    def setUp(self):
        self.dir = pathlib.Path(tempfile.mkdtemp())
        zipped = io.BytesIO()
        with zipfile.ZipFile(zipped, "w") as z:
            z.writestr("reflux-0.9.0-beta.1-windows-x64/app/resources/libmpv-2.dll", DLL)
            z.writestr("reflux-0.9.0-beta.1-windows-x64/Reflux.exe", b"exe")
        self.files = {
            "reflux-0.9.0-beta.1-windows-x64.zip": (zipped.getvalue(), "windows", "zip"),
            "reflux-0.9.0-beta.1-linux-x64.tar.gz": (b"tar.gz stand-in", "linux", "tar.gz"),
            "reflux-0.9.0-beta.1-libmpv-source.tar.xz": (b"source stand-in", "source", "source"),
        }
        self.write()

    def write(self, dll_digest=None, subjects=None, sums=None, commit=COMMIT):
        artifacts = []
        for name, (data, platform, kind) in self.files.items():
            (self.dir / name).write_bytes(data)
            artifacts.append({"name": name, "platform": platform, "arch": "x64", "kind": kind, "size": len(data), "sha256": sha(data)})
        manifest = {"version": "0.9.0-beta.1", "androidPackage": "dev.rsxdevelops.reflux", "commit": commit, "artifacts": artifacts}
        (self.dir / "manifest.json").write_text(json.dumps(manifest))
        (self.dir / "SHA256SUMS").write_text(sums if sums is not None else "".join("%s  %s\n" % (a["sha256"], a["name"]) for a in artifacts))
        provenance = {
            "subject": subjects if subjects is not None else [{"name": a["name"], "digest": {"sha256": a["sha256"]}} for a in artifacts],
            "predicate": {"buildDefinition": {"resolvedDependencies": [
                {"uri": "git+https://github.com/Rifaque/reflux@" + COMMIT, "digest": {"gitCommit": COMMIT}},
                {"uri": "git+...#tools/libmpv", "digest": {"sha256": dll_digest or sha(DLL)}, "annotations": {"use": "windows libmpv-2.dll, built by tools/libmpv"}},
            ]}},
        }
        (self.dir / "provenance.intoto.json").write_text(json.dumps(provenance))

    def verify(self):
        out = io.StringIO()
        with redirect_stdout(out):
            code = release.verify(type("Args", (), {"dir": str(self.dir)})())
        return code, out.getvalue()

    def test_a_good_release_verifies(self):
        code, out = self.verify()
        self.assertEqual(0, code, out)
        self.assertIn("verified 3 artifacts", out)

    def test_a_changed_byte_is_refused(self):
        path = self.dir / "reflux-0.9.0-beta.1-linux-x64.tar.gz"
        path.write_bytes(b"tar.gz stand-iN")
        code, out = self.verify()
        self.assertEqual(1, code)
        self.assertIn("does not match the published", out)

    def test_a_libmpv_other_than_the_source_built_one_is_refused(self):
        self.write(dll_digest=sha(b"someone else's build"))
        code, out = self.verify()
        self.assertEqual(1, code)
        self.assertIn("not the source-built", out)

    def test_a_windows_zip_without_its_libmpv_source_is_refused(self):
        del self.files["reflux-0.9.0-beta.1-libmpv-source.tar.xz"]
        (self.dir / "reflux-0.9.0-beta.1-libmpv-source.tar.xz").unlink()
        self.write()
        code, out = self.verify()
        self.assertEqual(1, code)
        self.assertIn("source bundle is not published", out)

    def test_provenance_that_names_other_files_is_refused(self):
        self.write(subjects=[{"name": "something-else.zip", "digest": {"sha256": "0" * 64}}])
        code, out = self.verify()
        self.assertEqual(1, code)
        self.assertIn("subjects are not the manifest's artifacts", out)

    def test_a_file_in_sha256sums_but_not_the_manifest_is_refused(self):
        self.write(sums="".join("%s  %s\n" % (sha(d), n) for n, (d, _, _) in self.files.items()) + "%s  extra.bin\n" % ("0" * 64))
        code, out = self.verify()
        self.assertEqual(1, code)
        self.assertIn("in SHA256SUMS, not in the manifest", out)

    def test_a_manifest_without_a_full_commit_is_refused(self):
        self.write(commit="abc1234")
        code, out = self.verify()
        self.assertEqual(1, code)
        self.assertIn("names no full commit", out)


if __name__ == "__main__":
    unittest.main()
