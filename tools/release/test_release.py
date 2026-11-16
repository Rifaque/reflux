"""Self-test of `release.py verify` (docs/RELEASE.md, "Release system"): what passes, and each way a release is refused.

    python -I -m unittest discover -s tools/release -p 'test_*.py'

Builds small stand-in releases in a temporary folder (a Linux archive, a Windows zip with a libmpv inside, the
libmpv source bundle, an AAB as the store artifact), with the manifest, SHA256SUMS (public files only), and
provenance as release.py writes them. No APK: reading one needs the Android SDK, and the APK checks run in every real
verification; the identity rules are tested on their own (check_apk_identity).
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
            "reflux-0.9.0-beta.1-android.aab": (b"aab stand-in", "android", "aab"),
        }
        self.write()

    def write(self, dll_digest=None, subjects=None, sums=None, commit=COMMIT):
        artifacts = []
        for name, (data, platform, kind) in self.files.items():
            (self.dir / name).write_bytes(data)
            artifacts.append({"name": name, "platform": platform, "arch": "x64", "kind": kind, "size": len(data), "sha256": sha(data),
                              "distribution": "store" if kind in ("apk", "aab") else "github-release"})
        manifest = {"schema": 2, "version": "0.9.0-beta.1", "tag": "v0.9.0-beta.1", "androidPackage": "dev.rsxdevelops.reflux", "commit": commit, "artifacts": artifacts}
        (self.dir / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
        public = [a for a in artifacts if a["distribution"] == "github-release"]
        (self.dir / "SHA256SUMS").write_text(sums if sums is not None else "".join("%s  %s\n" % (a["sha256"], a["name"]) for a in public), newline="\n")
        provenance = {
            "subject": subjects if subjects is not None else [{"name": a["name"], "digest": {"sha256": a["sha256"]}} for a in artifacts],
            "predicate": {"buildDefinition": {"resolvedDependencies": [
                {"uri": "git+https://github.com/Rifaque/reflux@" + COMMIT, "digest": {"gitCommit": COMMIT}},
                {"uri": "git+...#tools/libmpv", "digest": {"sha256": dll_digest or sha(DLL)}, "annotations": {"use": "windows libmpv-2.dll, built by tools/libmpv"}},
            ]}},
        }
        (self.dir / "provenance.intoto.json").write_text(json.dumps(provenance))

    def verify(self, folder=None, public=False):
        out = io.StringIO()
        with redirect_stdout(out):
            code = release.verify(type("Args", (), {"dir": str(folder or self.dir), "public": public})())
        return code, out.getvalue()

    def public_dir(self):
        """What the draft job attaches and a person downloads: stage-public's output."""
        target = pathlib.Path(tempfile.mkdtemp()) / "public"
        with redirect_stdout(io.StringIO()):
            code = release.stage_public(type("Args", (), {"dir": str(self.dir), "out": str(target)})())
        self.assertEqual(0, code)
        return target

    def test_a_good_release_verifies(self):
        code, out = self.verify()
        self.assertEqual(0, code, out)
        self.assertIn("verified 4 artifacts", out)

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
        self.write(sums="".join("%s  %s\n" % (sha(d), n) for n, (d, _, k) in self.files.items() if k != "aab") + "%s  extra.bin\n" % ("0" * 64))
        code, out = self.verify()
        self.assertEqual(1, code)
        self.assertIn("in SHA256SUMS, not in the manifest", out)

    def test_a_manifest_without_a_full_commit_is_refused(self):
        self.write(commit="abc1234")
        code, out = self.verify()
        self.assertEqual(1, code)
        self.assertIn("names no full commit", out)


    def test_the_manifest_says_where_each_artifact_is_distributed(self):
        manifest = json.loads((self.dir / "manifest.json").read_text())
        where = {a["kind"]: a["distribution"] for a in manifest["artifacts"]}
        self.assertEqual({"zip": "github-release", "tar.gz": "github-release", "source": "github-release", "aab": "store"}, where)
        self.assertEqual("store", release.distribution_of({"kind": "apk"}))
        self.assertEqual("github-release", release.distribution_of({"kind": "zip"}))

    def test_sha256sums_lists_only_the_public_files_and_sha256sum_c_passes_on_them(self):
        names = [line.split()[1] for line in (self.dir / "SHA256SUMS").read_text().splitlines()]
        self.assertEqual(3, len(names))
        self.assertNotIn("reflux-0.9.0-beta.1-android.aab", names)
        public = self.public_dir()
        for line in (public / "SHA256SUMS").read_text().splitlines():  # `sha256sum -c SHA256SUMS`, with no flags
            digest, name = line.split()
            self.assertEqual(digest, sha((public / name).read_bytes()))

    def test_a_store_artifact_in_sha256sums_is_refused(self):
        self.write(sums="".join("%s  %s\n" % (sha(d), n) for n, (d, _, _) in self.files.items()))
        code, out = self.verify()
        self.assertEqual(1, code)
        self.assertIn("lists only the public release's files", out)

    def test_a_public_release_directory_verifies_without_the_store_artifacts(self):
        public = self.public_dir()
        self.assertEqual({"SHA256SUMS", "manifest.json", "provenance.intoto.json"} | {n for n, (_, _, k) in self.files.items() if k != "aab"},
                         {p.name for p in public.iterdir()})
        code, out = self.verify(public, public=True)
        self.assertEqual(0, code, out)
        self.assertIn("verified 3 artifacts", out)
        # ... and as a full build it is refused: the AAB is missing.
        code, out = self.verify(public)
        self.assertEqual(1, code)
        self.assertIn("android.aab: missing", out)

    def test_a_public_release_missing_a_public_artifact_is_refused(self):
        public = self.public_dir()
        (public / "reflux-0.9.0-beta.1-linux-x64.tar.gz").unlink()
        code, out = self.verify(public, public=True)
        self.assertEqual(1, code)
        self.assertIn("linux-x64.tar.gz: missing", out)

    def test_a_public_release_carrying_the_aab_or_an_unknown_file_is_refused(self):
        public = self.public_dir()
        (public / "reflux-0.9.0-beta.1-android.aab").write_bytes(self.files["reflux-0.9.0-beta.1-android.aab"][0])
        (public / "notes.txt").write_text("x")
        code, out = self.verify(public, public=True)
        self.assertEqual(1, code)
        self.assertIn("a store artifact is on the public release", out)
        self.assertIn("notes.txt: not part of the release", out)

    def test_a_store_artifact_marked_for_the_public_release_is_refused(self):
        manifest = json.loads((self.dir / "manifest.json").read_text())
        manifest["artifacts"][-1]["distribution"] = "github-release"
        (self.dir / "manifest.json").write_text(json.dumps(manifest))
        code, out = self.verify()
        self.assertEqual(1, code)
        self.assertIn("distribution github-release, a aab is store", out)


class PackageIdentity(unittest.TestCase):
    GOOD = {"package": "dev.rsxdevelops.reflux", "versionCode": 7, "versionName": "1.0.0", "debuggable": False, "signerSha256": "ab" * 32}
    WANT = {"versionCode": 7, "versionName": "1.0.0"}

    def test_the_permanent_package_passes(self):
        self.assertEqual([], release.check_apk_identity(self.GOOD, self.WANT))

    def test_a_debug_suffixed_package_is_refused(self):
        bad = release.check_apk_identity(dict(self.GOOD, package="dev.rsxdevelops.reflux.debug"), self.WANT)
        self.assertEqual(1, len(bad))
        self.assertIn("exactly 'dev.rsxdevelops.reflux'", bad[0])

    def test_a_debuggable_apk_a_wrong_version_and_a_wrong_signer_are_refused(self):
        self.assertIn("debuggable", release.check_apk_identity(dict(self.GOOD, debuggable=True), self.WANT))
        self.assertTrue(release.check_apk_identity(dict(self.GOOD, versionCode=8), self.WANT))
        self.assertEqual([], release.check_apk_identity(self.GOOD, self.WANT, "AB" * 32))
        self.assertTrue(release.check_apk_identity(self.GOOD, self.WANT, "cd" * 32))

    def test_a_signer_written_as_keytool_prints_it_is_the_same_certificate(self):
        colons = ":".join(["AB"] * 32)
        self.assertEqual([], release.check_apk_identity(self.GOOD, self.WANT, colons))
        self.assertTrue(release.check_apk_identity(self.GOOD, self.WANT, ":".join(["CD"] * 32)))


CHANGELOG = """# Changelog

## Unreleased

- Next thing, [linked](https://example.com) and `coded`.

## 0.9.0-beta.1 — 2026-12-01

- The player is **lighter**: [controls](docs/x.md) go away while you watch.
- Second change.

More text under the list.

## 0.9.0-alpha.2 — 2026-10-20

- Old.
"""


class MetadataProvider(unittest.TestCase):
    """A release carries Trakt's production Client ID; a rehearsal needs none; the ID itself is never recorded."""

    def test_a_release_without_the_client_id_fails_clearly(self):
        for environ in ({}, {"REFLUX_TRAKT_CLIENT_ID": "  "}):
            with self.assertRaises(SystemExit) as refused:
                release.metadata_provider("v0.9.0-beta.1", environ)
            self.assertIn("REFLUX_TRAKT_CLIENT_ID", str(refused.exception.code))

    def test_a_rehearsal_builds_without_one(self):
        self.assertEqual("no client id", release.metadata_provider(None, {}))

    def test_the_manifest_says_whether_one_was_supplied_never_what_it_is(self):
        said = release.metadata_provider("v0.9.0-beta.1", {"REFLUX_TRAKT_CLIENT_ID": "abc123-not-real"})
        self.assertEqual("client id supplied", said)
        self.assertNotIn("abc123", said)


class Notes(unittest.TestCase):
    def test_the_versions_section_is_found_and_bounded(self):
        heading, body = release.changelog_section(CHANGELOG, "0.9.0-beta.1")
        self.assertEqual("0.9.0-beta.1 — 2026-12-01", heading)
        self.assertIn("Second change.", body)
        self.assertNotIn("Old.", body)
        self.assertNotIn("Next thing", body)

    def test_a_version_without_a_section_fails(self):
        with self.assertRaises(SystemExit) as raised:
            release.changelog_section(CHANGELOG, "0.9.0-beta.2")
        self.assertIn("no section for 0.9.0-beta.2", str(raised.exception))
        with self.assertRaises(SystemExit):  # a prefix of another version is not that version
            release.changelog_section(CHANGELOG, "0.9.0")

    def test_the_unreleased_section_stands_in_only_when_allowed(self):
        with self.assertRaises(SystemExit):
            release.changelog_section(CHANGELOG, "0.9.0-beta.2")
        heading, body = release.changelog_section(CHANGELOG, "0.9.0-beta.2", allow_unreleased=True)
        self.assertEqual("Unreleased", heading)
        self.assertIn("Next thing", body)

    def test_an_empty_section_fails(self):
        with self.assertRaises(SystemExit):
            release.changelog_section("## 1.0.0 — 2027-01-01\n\n## 0.9.0 — x\n- a\n", "1.0.0")

    def test_the_store_text_is_plain_whole_items_within_the_limit(self):
        _, body = release.changelog_section(CHANGELOG, "0.9.0-beta.1")
        text = release.store_text(body)
        self.assertEqual("• The player is lighter: controls go away while you watch.\n• Second change.\n• More text under the list.", text)
        short = release.store_text(body, 70)
        self.assertEqual("• The player is lighter: controls go away while you watch.", short)
        self.assertLessEqual(len(release.store_text("- " + "word " * 200)), 500)
        self.assertTrue(release.store_text("- " + "word " * 200).endswith("…"))

    def test_the_release_notes_carry_the_section_install_pointers_and_verification(self):
        _, body = release.changelog_section(CHANGELOG, "0.9.0-beta.1")
        notes = release.release_notes("0.9.0-beta.1", body)
        self.assertIn("## What's new in Reflux 0.9.0-beta.1\n", notes)
        self.assertIn("Second change.", notes)
        self.assertIn("https://reflux.rsxdevelops.dev/download", notes)
        self.assertIn("sha256sum -c SHA256SUMS", notes)
        self.assertIn("gh attestation verify <file> -R Rifaque/reflux --signer-workflow Rifaque/reflux/.github/workflows/release.yml", notes)

    def test_the_notes_verb_writes_both_files(self):
        folder = pathlib.Path(tempfile.mkdtemp())
        (folder / "CHANGELOG.md").write_text(CHANGELOG, encoding="utf-8")
        args = type("Args", (), {"version": "0.9.0-beta.1", "changelog": str(folder / "CHANGELOG.md"), "allow_unreleased": False,
                                 "out_notes": str(folder / "notes.md"), "out_store": str(folder / "store.txt"), "limit": 500})()
        with redirect_stdout(io.StringIO()):
            self.assertEqual(0, release.notes(args))
        self.assertIn("Install", (folder / "notes.md").read_text(encoding="utf-8"))
        self.assertTrue((folder / "store.txt").read_text(encoding="utf-8").startswith("• The player"))


class PublishRecord(Verify):
    """release/published/<version>.json: the verified manifest verbatim, never edited."""

    def record(self, folder=None, out=None, **extra):
        self.out = out or pathlib.Path(tempfile.mkdtemp()) / "published"
        args = {"dir": str(folder or self.dir), "out": str(self.out), "expect_commit": None, "github_release": None}
        args.update(extra)
        buffer = io.StringIO()
        with redirect_stdout(buffer):
            try:
                code = release.publish_record(type("Args", (), args)())
            except SystemExit as exit_:
                code = str(exit_)
        return code, buffer.getvalue()

    def test_the_record_is_the_manifest_byte_for_byte_from_a_build_or_a_public_release(self):
        for folder in (self.dir, self.public_dir()):
            code, out = self.record(folder)
            self.assertEqual(0, code, out)
            self.assertEqual((self.dir / "manifest.json").read_bytes(), (self.out / "0.9.0-beta.1.json").read_bytes())

    def test_a_tampered_manifest_is_refused_and_nothing_is_written(self):
        manifest = json.loads((self.dir / "manifest.json").read_text())
        manifest["artifacts"][0]["sha256"] = "0" * 64
        (self.dir / "manifest.json").write_text(json.dumps(manifest))
        code, out = self.record()
        self.assertEqual(1, code)
        self.assertIn("does not match", out)
        self.assertFalse(self.out.exists())

    def test_a_manifest_for_another_version_or_commit_is_refused(self):
        manifest = json.loads((self.dir / "manifest.json").read_text())
        manifest["version"] = "0.9.0-beta.2"
        (self.dir / "manifest.json").write_text(json.dumps(manifest))
        code, out = self.record()
        self.assertEqual(1, code)
        self.assertIn("not an artifact of version 0.9.0-beta.2", out)
        self.write()
        code, out = self.record(expect_commit="f" * 40)
        self.assertEqual(1, code)
        self.assertIn("is not the expected", out)

    def test_an_existing_record_with_other_content_is_never_overwritten_but_the_same_is_a_no_op(self):
        code, _ = self.record()
        self.assertEqual(0, code)
        target = self.out / "0.9.0-beta.1.json"
        before = target.read_bytes()
        code, out = self.record(out=self.out)
        self.assertEqual(0, code)
        self.assertIn("nothing to do", out)
        target.write_bytes(before + b" ")
        code, _ = self.record(out=self.out)
        self.assertIn("never edited", code)
        self.assertEqual(before + b" ", target.read_bytes())

    def test_github_asset_digests_are_compared_with_the_manifest(self):
        manifest = json.loads((self.dir / "manifest.json").read_text())
        public = self.public_dir()
        good = [{"name": a["name"], "digest": "sha256:" + a["sha256"]} for a in manifest["artifacts"] if a["distribution"] == "github-release"]
        good += [{"name": n, "digest": "sha256:" + sha((public / n).read_bytes())} for n in release.RECORDS]
        self.assertEqual([], release.compare_release_assets(public, manifest, good))
        bad = [dict(good[0], digest="sha256:" + "0" * 64)] + good[1:-1] + [{"name": "reflux-0.9.0-beta.1-android.aab", "digest": "sha256:1"}]
        problems = "\n".join(release.compare_release_assets(public, manifest, bad))
        self.assertIn("has digest", problems)
        self.assertIn("android.aab is not part of the public release", problems)
        self.assertIn("has no asset provenance.intoto.json", problems)
        self.assertTrue(release.compare_release_assets(public, manifest, [dict(good[0], digest=None)] + good[1:]))


if __name__ == "__main__":
    unittest.main()
