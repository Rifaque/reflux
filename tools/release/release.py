#!/usr/bin/env python3
"""Reflux's release tool (docs/RELEASE.md, "Release system"): one clean commit in, named and checksummed artifacts out.

    python -I tools/release/release.py build  [--signing-env FILE] [--out DIR] [--add FILE ...] [--allow-modified]
    python -I tools/release/release.py verify DIR [--public] [--expect-signer-sha256 HEX]
    python -I tools/release/release.py stage-public DIR --out PUBLIC_DIR
    python -I tools/release/release.py notes --version V [--allow-unreleased] [--out-notes FILE] [--out-store FILE]
    python -I tools/release/release.py publish-record DIR [--out release/published] [--expect-commit SHA] [--github-release TAG]

build
    Requires a clean tree (a release is never built from uncommitted changes). Reads the release identity from the
    Gradle `releaseInfo` task (the only place a version is derived), builds the Android release APK and app bundle
    (R8, resource shrinking, signed with the key named in --signing-env, a file of REFLUX_SIGNING_* lines that is
    passed to Gradle through its environment and never printed) and this machine's desktop archive, checks each
    artifact's identity, and writes into DIR (default build/release-out/<version>):

      reflux-<version>-android.apk         sideloadable APK (internal testing, Fire TV uploads)
      reflux-<version>-android.aab         Google Play bundle
      reflux-<version>-windows-x64.zip     or reflux-<version>-linux-x64.tar.gz, as this machine builds
      SHA256SUMS                           sha256sum format; lists only the files of the public GitHub release
      manifest.json                        release identity and every artifact (platform, arch, size, SHA-256,
                                           and "distribution": "github-release" or "store")
      provenance.intoto.json               in-toto statement, SLSA v1 provenance predicate (unsigned locally)

    --add includes an artifact built elsewhere (the Linux archive from tools/ci), checked against the same commit.
    --tag v<version> (the release workflow passes it) refuses a tag that is not v<version> or is not an annotated tag
    on this commit. The APK must be exactly the permanent package (never a debug-suffixed one) and not debuggable;
    --expect-signer-sha256 (or REFLUX_EXPECTED_SIGNER_SHA256) also refuses an APK signed by any other certificate.

Distribution (store-only Android): the public GitHub release carries only the artifacts whose distribution is
"github-release" (desktop archives, libmpv source) and the three records. The APK and AAB are distribution "store":
built, verified, attested, and listed in the manifest and provenance, but they reach the stores as workflow
artifacts and are never attached to the public release.

verify
    Re-hashes every artifact against SHA256SUMS and the manifest, checks the manifest's commit, version, and Android
    package identity, and exits non-zero on any difference: what a downloader or the release workflow runs. On a full
    build folder every artifact must be present. With --public (a downloaded GitHub release) every github-release
    artifact must be present and hashed, and a store artifact (APK, AAB) must NOT be there.

stage-public
    Copies the public release's files (github-release artifacts and the three records) into PUBLIC_DIR, with a
    SHA256SUMS of exactly those files, and verifies the result with --public. The draft job attaches that folder.

notes
    Writes the version's GitHub release notes (its CHANGELOG.md section, install pointers, verification commands) and
    the plain store "What's new" text (same section, plain, at most --limit characters, default 500, Google Play's).

publish-record
    Re-verifies DIR (a downloaded public release or a full build folder) and writes release/published/<version>.json
    as a byte-identical copy of its manifest.json. Refuses a tampered or inconsistent manifest, and a record that
    already exists with different content; identical content is a no-op. --github-release TAG also compares the
    GitHub release's asset digests (gh release view TAG --json assets) with the manifest.
"""
import argparse
import datetime
import hashlib
import json
import os
import pathlib
import platform
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
WINDOWS = platform.system() == "Windows"
PERMANENT_PACKAGE = "dev.rsxdevelops.reflux"  # the Android application ID; debug builds use a suffix and are never a release
STORE_KINDS = {"apk", "aab"}
RECORDS = ("SHA256SUMS", "manifest.json", "provenance.intoto.json")
VERSION_RE = re.compile(r"\d+\.\d+\.\d+(?:-[0-9A-Za-z.]+)?")
REPO = "Rifaque/reflux"
SITE = "https://reflux.rsxdevelops.dev"


def run(cmd, env=None, capture=True) -> str:
    done = subprocess.run(cmd, cwd=ROOT, env=env, capture_output=capture, text=True, encoding="utf-8", errors="replace")
    if done.returncode != 0:
        tail = (done.stdout or "")[-3000:] + (done.stderr or "")[-3000:]
        sys.exit("failed: " + " ".join(map(str, cmd[:3])) + "\n" + tail)
    return done.stdout or ""


def sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def signing_env(path: str) -> dict:
    env = dict(os.environ)
    for line in pathlib.Path(path).expanduser().read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            if key.strip().startswith("REFLUX_SIGNING_"):
                env[key.strip()] = value.strip()
    missing = [k for k in ("STORE_FILE", "STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD") if not env.get("REFLUX_SIGNING_" + k)]
    if missing:
        sys.exit("signing env lacks REFLUX_SIGNING_" + ", REFLUX_SIGNING_".join(missing))
    return env


def sdk_tool(name: str) -> pathlib.Path:
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        props = ROOT / "local.properties"
        if props.exists():
            m = re.search(r"^sdk\.dir=(.*)$", props.read_text(encoding="utf-8"), re.M)
            sdk = m.group(1).replace("\\:", ":").replace("\\\\", "\\") if m else None
    if not sdk:
        sys.exit("no Android SDK (ANDROID_HOME or local.properties)")
    tools = sorted((pathlib.Path(sdk) / "build-tools").iterdir(), key=lambda p: [int(x) if x.isdigit() else x for x in re.split(r"[.-]", p.name)])
    for suffix in (".bat", ".exe", ""):
        candidate = tools[-1] / (name + suffix)
        if candidate.exists():
            return candidate
    sys.exit("build-tools lacks " + name)


def apk_identity(apk: pathlib.Path) -> dict:
    badging = run([str(sdk_tool("aapt2")), "dump", "badging", str(apk)])
    m = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    sdk = re.search(r"targetSdkVersion:'(\d+)'", badging)
    debuggable = "application-debuggable" in badging
    certs = run([str(sdk_tool("apksigner")), "verify", "--print-certs", str(apk)])
    digest = re.search(r"certificate SHA-256 digest: ([0-9a-f]+)", certs)
    return {
        "package": m.group(1), "versionCode": int(m.group(2)), "versionName": m.group(3),
        "targetSdk": int(sdk.group(1)) if sdk else None, "debuggable": debuggable,
        "signerSha256": digest.group(1) if digest else None,
    }


def distribution_of(artifact: dict) -> str:
    """Where an artifact is given to people: "github-release" (the public release) or "store" (APK and AAB, via the stores)."""
    return artifact.get("distribution") or ("store" if artifact.get("kind") in STORE_KINDS else "github-release")


def check_apk_identity(identity: dict, expected: dict, signer: str = None) -> list:
    """Problems with an APK's identity: exactly the permanent package (so never a `.debug` build), the release's version, not debuggable."""
    problems = []
    if identity.get("package") != PERMANENT_PACKAGE:
        problems.append("package is %r, a release is exactly %r" % (identity.get("package"), PERMANENT_PACKAGE))
    for key, value in expected.items():
        if identity.get(key) != value:
            problems.append("%s is %r, expected %r" % (key, identity.get(key), value))
    if identity.get("debuggable"):
        problems.append("debuggable")
    # Either form of a fingerprint: keytool's colon-separated upper case, or plain hex (0.9.0-beta.1's first release
    # run failed on that difference alone, with the right certificate).
    def plain(fingerprint): return "".join(fingerprint.split()).replace(":", "").lower()
    if signer and plain(identity.get("signerSha256") or "") != plain(signer):
        problems.append("signed by certificate %s, expected %s" % (identity.get("signerSha256"), signer))
    return problems


def check_tag(tag: str, version: str, commit: str) -> list:
    """The release tag must be v<version>, an annotated tag, pointing at the built commit."""
    if tag != "v" + version:
        return ["tag %s is not v%s" % (tag, version)]
    problems = []
    kind = subprocess.run(["git", "cat-file", "-t", "refs/tags/" + tag], cwd=ROOT, capture_output=True, text=True).stdout.strip()
    if kind != "tag":
        problems.append("tag %s is not an annotated tag (VERSIONING.md)" % tag)
    target = subprocess.run(["git", "rev-parse", "--verify", tag + "^{commit}"], cwd=ROOT, capture_output=True, text=True).stdout.strip()
    if target != commit:
        problems.append("tag %s points at %s, the build is of %s" % (tag, target or "nothing", commit))
    return problems


def metadata_provider(tag, environ) -> str:
    """What the build says about its metadata provider: Trakt's Client ID supplied or not, never the ID itself.

    A real release (a tag) must carry the production Client ID (REFLUX_TRAKT_CLIENT_ID, a GitHub Actions variable);
    a rehearsal builds without one, and the app then makes no lookups."""
    supplied = bool((environ.get("REFLUX_TRAKT_CLIENT_ID") or "").strip())
    if tag and not supplied:
        sys.exit("a release needs Trakt's production Client ID in REFLUX_TRAKT_CLIENT_ID (docs/RELEASE.md, \"Metadata provider\")")
    return "client id supplied" if supplied else "no client id"


def build(args) -> int:
    provider = metadata_provider(args.tag, os.environ)
    status = run(["git", "status", "--porcelain"])
    if status.strip() and not args.allow_modified:
        sys.exit("the tree has changes: a release is built from a clean commit (commit or use a clean worktree)")
    commit = run(["git", "rev-parse", "HEAD"]).strip()
    env = signing_env(args.signing_env)
    gradle = str(ROOT / ("gradlew.bat" if WINDOWS else "gradlew"))
    started = datetime.datetime.now(datetime.timezone.utc)
    print("building " + commit[:7] + " ...")
    run([gradle, "--no-configuration-cache", "releaseInfo", ":android:app:assembleRelease", ":android:app:bundleRelease",
         ":desktop:app:packageDesktopArchive"], env=env)
    info = json.loads((ROOT / "build/release/release-info.json").read_text(encoding="utf-8"))
    if info["commit"] != commit[:7]:
        sys.exit("release-info names commit %s, HEAD is %s" % (info["commit"], commit[:7]))
    if info["modified"] and not args.allow_modified:
        sys.exit("the build saw a modified tree")
    version = info["version"]
    if not VERSION_RE.fullmatch(version):
        sys.exit("version %r is not a version" % version)
    if info["androidPackage"] != PERMANENT_PACKAGE:
        sys.exit("release-info names package %r, a release is exactly %r" % (info["androidPackage"], PERMANENT_PACKAGE))
    if args.tag:
        bad = check_tag(args.tag, version, commit)
        if bad:
            sys.exit("; ".join(bad))
    out = pathlib.Path(args.out or ROOT / "build/release-out" / version).resolve()
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)

    artifacts = []

    def add(source: pathlib.Path, name: str, platform_: str, arch: str, kind: str, extra=None):
        target = out / name
        shutil.copyfile(source, target)
        entry = {"name": name, "platform": platform_, "arch": arch, "kind": kind, "size": target.stat().st_size, "sha256": sha256(target)}
        entry.update(extra or {})
        entry["distribution"] = distribution_of(entry)
        artifacts.append(entry)

    apk = ROOT / "android/app/build/outputs/apk/release/app-release.apk"
    if not apk.exists():
        sys.exit("no signed APK: the signing env did not reach the build")
    identity = apk_identity(apk)
    expected = {"versionCode": info["versionCode"], "versionName": version}
    bad = check_apk_identity(identity, expected, args.expect_signer_sha256 or os.environ.get("REFLUX_EXPECTED_SIGNER_SHA256"))
    if bad:
        sys.exit("the APK is not a release: " + "; ".join(bad))
    add(apk, "reflux-%s-android.apk" % version, "android", "universal", "apk", {"android": identity})
    add(ROOT / "android/app/build/outputs/bundle/release/app-release.aab", "reflux-%s-android.aab" % version, "android", "universal", "aab",
        {"android": {"package": info["androidPackage"], "versionCode": info["versionCode"], "versionName": version}})
    desktop = "windows" if WINDOWS else "linux"
    archive = ROOT / "desktop/app/build/release" / ("reflux-%s-%s-x64.%s" % (version, desktop, "zip" if WINDOWS else "tar.gz"))
    add(archive, archive.name, desktop, "x64", archive.suffix.lstrip("."), {"signed": False} if WINDOWS else {})
    pin = dict(line.split("=", 1) for line in (ROOT / "desktop/app/packaging/libmpv-windows.properties").read_text().splitlines() if "=" in line and not line.startswith("#"))
    pin = {k.strip(): v.strip() for k, v in pin.items()}
    if WINDOWS:
        # The complete source of the libmpv inside the Windows archive (THIRD-PARTY.md), published beside it.
        bundle = ROOT / "build/libmpv/libmpv-source.tar.xz"
        if not bundle.exists() or sha256(bundle) != pin["sourceSha256"]:
            sys.exit("build/libmpv/libmpv-source.tar.xz is missing or not the pinned source (tools/libmpv/README.md)")
        add(bundle, "reflux-%s-libmpv-source.tar.xz" % version, "source", "any", "source", {"of": "libmpv-2.dll in the Windows archive"})
    for extra in args.add or []:
        path = pathlib.Path(extra)
        m = re.match(r"reflux-(.+)-(linux|windows)-x64\.(tar\.gz|zip)$", path.name)
        if not m or m.group(1) != version:
            sys.exit("--add %s is not a desktop archive of %s" % (path.name, version))
        # An archive built elsewhere (tools/ci/linux-check.sh package) says which commit it is from; it must be this one.
        built = path.with_name(path.name + ".release-info.json")
        if not built.exists():
            sys.exit("--add %s has no %s beside it: its commit is unknown" % (path.name, built.name))
        other = json.loads(built.read_text(encoding="utf-8"))
        if other.get("commit") != commit[:7] or other.get("modified") is not False:
            sys.exit("--add %s was built from %s%s, not %s" % (path.name, other.get("commit"), " (modified)" if other.get("modified") else "", commit[:7]))
        add(path, path.name, m.group(2), "x64", m.group(3), {"signed": False} if m.group(2) == "windows" else {})

    finished = datetime.datetime.now(datetime.timezone.utc)
    manifest = {
        "schema": 2, "product": "Reflux", "publisher": "RSX Develops", "version": version, "versionCode": info["versionCode"],
        "windowsVersion": info["windowsVersion"], "linuxVersion": info["linuxVersion"], "channel": info["channel"],
        "androidPackage": info["androidPackage"], "commit": commit, "repository": "https://github.com/Rifaque/reflux", "tag": "v" + version,
        "builtAt": finished.strftime("%Y-%m-%dT%H:%M:%SZ"), "builder": "local" if not os.environ.get("GITHUB_ACTIONS") else "github-actions",
        "metadataProvider": {"name": "trakt", "clientId": provider}, "artifacts": artifacts,
    }
    # LF everywhere: `sha256sum -c` on Linux and macOS reads a CRLF SHA256SUMS as names ending in \r.
    (out / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8", newline="\n")
    # SHA256SUMS lists exactly the public release's files, so `sha256sum -c SHA256SUMS` passes on the downloaded release.
    (out / "SHA256SUMS").write_text("".join("%s  %s\n" % (a["sha256"], a["name"]) for a in artifacts if distribution_of(a) == "github-release"), encoding="utf-8", newline="\n")
    provenance = {
        "_type": "https://in-toto.io/Statement/v1",
        "subject": [{"name": a["name"], "digest": {"sha256": a["sha256"]}} for a in artifacts],
        "predicateType": "https://slsa.dev/provenance/v1",
        "predicate": {
            "buildDefinition": {
                "buildType": "https://github.com/Rifaque/reflux/blob/%s/tools/release/release.py" % commit,
                "externalParameters": {"version": version, "channel": info["channel"]},
                "resolvedDependencies": [
                    {"uri": "git+https://github.com/Rifaque/reflux@" + commit, "digest": {"gitCommit": commit}},
                    {"uri": "git+https://github.com/Rifaque/reflux@%s#tools/libmpv" % commit, "digest": {"sha256": pin["dllSha256"]},
                     "annotations": {"use": "windows libmpv-2.dll, built by tools/libmpv", "mpv": pin["mpv"], "ffmpeg": pin["ffmpeg"], "sourceSha256": pin["sourceSha256"]}},
                ],
            },
            "runDetails": {
                "builder": {"id": manifest["builder"]},
                "metadata": {"startedOn": started.strftime("%Y-%m-%dT%H:%M:%SZ"), "finishedOn": manifest["builtAt"]},
            },
        },
    }
    (out / "provenance.intoto.json").write_text(json.dumps(provenance, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("wrote " + str(out))
    for a in artifacts:
        print("  %s  %s  %d bytes" % (a["sha256"], a["name"], a["size"]))
    return 0


def verify_problems(folder: pathlib.Path, public: bool = False, signer: str = None):
    """Everything wrong with a release folder, as (manifest, problems).

    A full build folder (public=False) must hold every artifact. A downloaded public release (public=True) must hold
    every github-release artifact and no store artifact; its SHA256SUMS lists exactly the github-release artifacts.
    """
    manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
    sums = {}
    for line in (folder / "SHA256SUMS").read_text(encoding="utf-8").splitlines():
        digest, name = line.split(maxsplit=1)
        sums[name.lstrip("*")] = digest
    problems = []
    statement = folder / "provenance.intoto.json"
    dependencies = json.loads(statement.read_text(encoding="utf-8"))["predicate"]["buildDefinition"]["resolvedDependencies"] if statement.exists() else []
    libmpv_digest = next((d["digest"].get("sha256") for d in dependencies if "libmpv" in d.get("annotations", {}).get("use", "")), None)
    if not re.fullmatch(r"[0-9a-f]{40}", manifest.get("commit", "")):
        problems.append("manifest names no full commit")
    version = manifest.get("version", "")
    if not VERSION_RE.fullmatch(version):
        problems.append("manifest version %r is not a version" % version)
    if manifest.get("tag") not in (None, "v" + version):
        problems.append("manifest tag %s is not v%s" % (manifest.get("tag"), version))
    names = {a["name"] for a in manifest["artifacts"]}
    for artifact in manifest["artifacts"]:
        name = artifact["name"]
        if not name.startswith("reflux-%s-" % version) or "/" in name or "\\" in name:
            problems.append("%s: not an artifact of version %s" % (name, version))
        expected_distribution = "store" if artifact["kind"] in STORE_KINDS else "github-release"
        if distribution_of(artifact) != expected_distribution:
            problems.append("%s: distribution %s, a %s is %s" % (name, distribution_of(artifact), artifact["kind"], expected_distribution))
        path = folder / name
        is_store = distribution_of(artifact) == "store"
        if public and is_store:
            if path.exists():
                problems.append(name + ": a store artifact is on the public release")
            continue
        if not path.exists():
            problems.append(name + ": missing")
            continue
        actual = sha256(path)
        if actual != artifact["sha256"] or (not is_store and sums.get(name) != actual):
            problems.append("%s: SHA-256 %s does not match the published %s" % (name, actual, artifact["sha256"]))
        if artifact["kind"] == "apk":
            identity = apk_identity(path)
            for key in ("package", "versionCode", "versionName"):
                if identity[key] != artifact["android"][key]:
                    problems.append("%s: %s %r, manifest %r" % (name, key, identity[key], artifact["android"][key]))
            if identity["package"] != manifest.get("androidPackage"):
                problems.append("%s: package %s is not the release's %s" % (name, identity["package"], manifest.get("androidPackage")))
            for problem in check_apk_identity(identity, {}, signer):
                problems.append("%s: %s" % (name, problem))
        if artifact["kind"] == "zip":
            with zipfile.ZipFile(path) as z:
                if z.testzip() is not None:
                    problems.append(name + ": corrupt entry")
                # The libmpv inside is the source-built one the provenance names, and its source is published beside it.
                dlls = [n for n in z.namelist() if n.endswith("/libmpv-2.dll")]
                inside = hashlib.sha256(z.read(dlls[0])).hexdigest() if len(dlls) == 1 else None
                if inside is None:
                    problems.append(name + ": not exactly one libmpv-2.dll inside")
                elif inside != libmpv_digest:
                    problems.append("%s: its libmpv-2.dll is %s, not the source-built %s" % (name, inside, libmpv_digest))
                if not any(a["kind"] == "source" for a in manifest["artifacts"]):
                    problems.append(name + ": libmpv's source bundle is not published beside it")
    # SHA256SUMS names exactly the public release's files, so that `sha256sum -c SHA256SUMS` passes on a downloaded release.
    public_names = {a["name"] for a in manifest["artifacts"] if distribution_of(a) == "github-release"}
    for name in sums:
        if name not in names:
            problems.append(name + ": in SHA256SUMS, not in the manifest")
        elif name not in public_names:
            problems.append(name + ": a store artifact is in SHA256SUMS, which lists only the public release's files")
    for name in sorted(public_names - set(sums)):
        problems.append(name + ": a public artifact is missing from SHA256SUMS")
    if public:
        for extra in sorted(p.name for p in folder.iterdir() if p.is_file() and p.name not in names and p.name not in RECORDS):
            problems.append(extra + ": not part of the release")
    # The provenance statement names exactly these artifacts, by these digests, from this commit.
    if not statement.exists():
        problems.append("provenance.intoto.json: missing")
    else:
        provenance = json.loads(statement.read_text(encoding="utf-8"))
        subjects = {s["name"]: s["digest"]["sha256"] for s in provenance.get("subject", [])}
        if subjects != {a["name"]: a["sha256"] for a in manifest["artifacts"]}:
            problems.append("provenance.intoto.json: its subjects are not the manifest's artifacts")
        commits = [d.get("digest", {}).get("gitCommit") for d in provenance["predicate"]["buildDefinition"]["resolvedDependencies"]]
        if manifest.get("commit") not in commits:
            problems.append("provenance.intoto.json: does not name the manifest's commit")
    return manifest, problems


def report(problems: list) -> int:
    print("FAILED")
    for p in problems:
        print("  " + p)
    return 1


def verify(args) -> int:
    public = getattr(args, "public", False)
    manifest, problems = verify_problems(pathlib.Path(args.dir), public, getattr(args, "expect_signer_sha256", None) or os.environ.get("REFLUX_EXPECTED_SIGNER_SHA256"))
    if problems:
        return report(problems)
    present = sum(1 for a in manifest["artifacts"] if not (public and distribution_of(a) == "store"))
    print("verified %d artifacts of Reflux %s (%s)%s" % (present, manifest["version"], manifest["commit"][:7], ", public release" if public else ""))
    return 0


def stage_public(args) -> int:
    """Copies the public release's files out of a full build folder, with a SHA256SUMS of exactly those, and verifies them."""
    folder, out = pathlib.Path(args.dir), pathlib.Path(args.out)
    manifest, problems = verify_problems(folder)
    if problems:
        return report(problems)
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    for a in manifest["artifacts"]:
        if distribution_of(a) == "github-release":
            shutil.copyfile(folder / a["name"], out / a["name"])
    for record in RECORDS:
        shutil.copyfile(folder / record, out / record)
    return verify(argparse.Namespace(dir=str(out), public=True))


def changelog_section(text: str, version: str, allow_unreleased: bool = False):
    """The (heading, body) of `## <version> — <date>` in CHANGELOG.md; with allow_unreleased, `## Unreleased` stands in for an unreleased version."""
    lines = text.splitlines()
    heads = [i for i, line in enumerate(lines) if line.startswith("## ")]

    def find(pattern):
        for n, i in enumerate(heads):
            if re.fullmatch(pattern, lines[i][3:].strip()):
                end = heads[n + 1] if n + 1 < len(heads) else len(lines)
                return lines[i][3:].strip(), "\n".join(lines[i + 1:end]).strip()
        return None

    found = find(re.escape(version) + r"(?:\s+[—-].*)?")
    if found is None and allow_unreleased:
        found = find("Unreleased")
    if found is None:
        sys.exit("CHANGELOG.md has no section for %s (a heading '## %s ...' with the release date)" % (version, version))
    if not found[1]:
        sys.exit("CHANGELOG.md's section %r is empty" % found[0])
    return found


def plain_text(markdown: str) -> list:
    """A changelog section as plain one-line items: no links, code marks, or emphasis."""
    items, current = [], None
    for line in markdown.splitlines():
        if line.startswith("#"):  # a category heading ("### Playback & Media") is not an item
            current = None
            continue
        if line.startswith("- "):
            current = [line[2:].strip()]
            items.append(current)
        elif line.strip() and current is not None:
            current.append(line.strip())
        elif line.strip():
            current = [line.strip()]
            items.append(current)
        else:
            current = None
    plain = []
    for item in items:
        text = " ".join(item)
        text = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", text)
        text = re.sub(r"[`*_]{1,3}(\S(?:.*?\S)?)[`*_]{1,3}", r"\1", text)
        plain.append(text)
    return plain


def store_text(markdown: str, limit: int = 500) -> str:
    """The store's "What's new": whole items from the changelog section, in order, within the limit (Google Play: 500 characters)."""
    items = plain_text(markdown)
    out = ""
    for item in items:
        line = "• " + item
        candidate = out + ("\n" if out else "") + line
        if len(candidate) > limit:
            if not out:
                out = line[:limit - 1].rstrip() + "…"
            break
        out = candidate
    return out


def release_notes(version: str, body: str, unreleased: bool = False) -> str:
    heading = "## What's new in Reflux %s%s" % (version, " (unreleased)" if unreleased else "")
    return "\n".join([
        heading, "", body, "",
        "## Install", "",
        "- Windows and Linux: download the archive below and follow %s/docs/getting-started (all downloads: %s/download)." % (SITE, SITE),
        "- Android, Google TV, and Fire TV: from Google Play and the Amazon Appstore; this release has no Android file. See %s/download." % SITE,
        "- The Windows archive is not code-signed. `reflux-%s-libmpv-source.tar.xz` is the complete source of the libmpv inside it." % version, "",
        "## Verify what you downloaded", "",
        "```",
        "sha256sum -c SHA256SUMS",
        "gh attestation verify <file> -R %s --signer-workflow %s/.github/workflows/release.yml" % (REPO, REPO),
        "```", "",
        "More: %s/security" % SITE, ""])


def notes(args) -> int:
    version = args.version
    if not VERSION_RE.fullmatch(version):
        sys.exit("%r is not a version" % version)
    heading, body = changelog_section(pathlib.Path(args.changelog).read_text(encoding="utf-8"), version, args.allow_unreleased)
    unreleased = heading == "Unreleased"
    if args.out_notes:
        pathlib.Path(args.out_notes).write_text(release_notes(version, body, unreleased), encoding="utf-8", newline="\n")
    store = store_text(body, args.limit)
    if args.out_store:
        pathlib.Path(args.out_store).write_text(store + "\n", encoding="utf-8", newline="\n")
    print("release notes from %r; store text %d of %d characters" % (heading, len(store), args.limit))
    return 0


def compare_release_assets(folder: pathlib.Path, manifest: dict, assets: list) -> list:
    """Problems between a GitHub release's assets (gh release view --json assets) and the verified manifest and records."""
    expected = {a["name"]: a["sha256"] for a in manifest["artifacts"] if distribution_of(a) == "github-release"}
    expected.update({record: sha256(folder / record) for record in RECORDS})
    problems = []
    seen = set()
    for asset in assets:
        name = asset.get("name")
        seen.add(name)
        if name not in expected:
            problems.append("GitHub release asset %s is not part of the public release" % name)
            continue
        digest = asset.get("digest") or ""
        if digest != "sha256:" + expected[name]:
            problems.append("GitHub release asset %s has digest %r, expected sha256:%s" % (name, digest or None, expected[name]))
    for name in sorted(set(expected) - seen):
        problems.append("GitHub release has no asset %s" % name)
    return problems


def publish_record(args) -> int:
    """release/published/<version>.json: the verified manifest, byte for byte; never overwritten with different content."""
    folder = pathlib.Path(args.dir)
    listed = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))["artifacts"]
    # A folder holding the store artifacts is a full build; anything else is a downloaded public release.
    public = not any((folder / x["name"]).exists() for x in listed if distribution_of(x) == "store")
    manifest, problems = verify_problems(folder, public)
    if args.expect_commit and manifest.get("commit") != args.expect_commit:
        problems.append("manifest commit %s is not the expected %s" % (manifest.get("commit"), args.expect_commit))
    if args.github_release:
        if args.github_release != manifest.get("tag"):
            problems.append("GitHub release %s is not the manifest's tag %s" % (args.github_release, manifest.get("tag")))
        if not problems:
            view = json.loads(run(["gh", "release", "view", args.github_release, "--repo", REPO, "--json", "assets"]))
            problems += compare_release_assets(folder, manifest, view.get("assets", []))
    if problems:
        return report(problems)
    data = (folder / "manifest.json").read_bytes()
    target = pathlib.Path(args.out or ROOT / "release/published") / (manifest["version"] + ".json")
    if target.exists():
        if target.read_bytes() == data:
            print("%s already holds this manifest; nothing to do" % target)
            return 0
        sys.exit("%s exists with different content; a published record is never edited" % target)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    print("wrote %s (Reflux %s, %s)" % (target, manifest["version"], manifest["commit"][:7]))
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    b = sub.add_parser("build")
    b.add_argument("--signing-env", default="~/.reflux-release/dry-run.env")
    b.add_argument("--out")
    b.add_argument("--add", action="append")
    b.add_argument("--allow-modified", action="store_true")
    b.add_argument("--tag")
    b.add_argument("--expect-signer-sha256")
    v = sub.add_parser("verify")
    v.add_argument("dir")
    v.add_argument("--public", action="store_true")
    v.add_argument("--expect-signer-sha256")
    sp = sub.add_parser("stage-public")
    sp.add_argument("dir")
    sp.add_argument("--out", required=True)
    n = sub.add_parser("notes")
    n.add_argument("--version", required=True)
    n.add_argument("--changelog", default=str(ROOT / "CHANGELOG.md"))
    n.add_argument("--allow-unreleased", action="store_true")
    n.add_argument("--out-notes")
    n.add_argument("--out-store")
    n.add_argument("--limit", type=int, default=500)
    pr = sub.add_parser("publish-record")
    pr.add_argument("dir")
    pr.add_argument("--out")
    pr.add_argument("--expect-commit")
    pr.add_argument("--github-release")
    args = parser.parse_args()
    try:
        return {"build": build, "verify": verify, "stage-public": stage_public, "notes": notes, "publish-record": publish_record}[args.command](args)
    except (OSError, ValueError, KeyError) as problem:  # a missing or unreadable record is a refusal, not a traceback
        return report(["%s: %s" % (type(problem).__name__, problem)])


if __name__ == "__main__":
    sys.exit(main())
