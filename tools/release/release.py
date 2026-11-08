#!/usr/bin/env python3
"""Reflux's release tool (docs/RELEASE.md, "Release system"): one clean commit in, named and checksummed artifacts out.

    python -I tools/release/release.py build  [--signing-env FILE] [--out DIR] [--add FILE ...] [--allow-modified]
    python -I tools/release/release.py verify DIR

build
    Requires a clean tree (a release is never built from uncommitted changes). Reads the release identity from the
    Gradle `releaseInfo` task (the only place a version is derived), builds the Android release APK and app bundle
    (R8, resource shrinking, signed with the key named in --signing-env, a file of REFLUX_SIGNING_* lines that is
    passed to Gradle through its environment and never printed) and this machine's desktop archive, checks each
    artifact's identity, and writes into DIR (default build/release-out/<version>):

      reflux-<version>-android.apk         sideloadable APK (internal testing, Fire TV uploads)
      reflux-<version>-android.aab         Google Play bundle
      reflux-<version>-windows-x64.zip     or reflux-<version>-linux-x64.tar.gz, as this machine builds
      SHA256SUMS                           sha256sum format
      manifest.json                        release identity and every artifact (platform, arch, size, SHA-256)
      provenance.intoto.json               in-toto statement, SLSA v1 provenance predicate (unsigned locally)

    --add includes an artifact built elsewhere (the Linux archive from tools/ci), checked against the same commit.

verify
    Re-hashes every artifact against SHA256SUMS and the manifest, checks the manifest's commit, version, and Android
    package identity, and exits non-zero on any difference: what a downloader or the release workflow runs.
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


def build(args) -> int:
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
        artifacts.append(entry)

    apk = ROOT / "android/app/build/outputs/apk/release/app-release.apk"
    if not apk.exists():
        sys.exit("no signed APK: the signing env did not reach the build")
    identity = apk_identity(apk)
    expected = {"package": info["androidPackage"], "versionCode": info["versionCode"], "versionName": version, "debuggable": False}
    for key, value in expected.items():
        if identity[key] != value:
            sys.exit("APK %s is %r, expected %r" % (key, identity[key], value))
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
        "schema": 1, "product": "Reflux", "publisher": "RSX Develops", "version": version, "versionCode": info["versionCode"],
        "windowsVersion": info["windowsVersion"], "linuxVersion": info["linuxVersion"], "channel": info["channel"],
        "androidPackage": info["androidPackage"], "commit": commit, "repository": "https://github.com/Rifaque/reflux", "tag": "v" + version,
        "builtAt": finished.strftime("%Y-%m-%dT%H:%M:%SZ"), "builder": "local" if not os.environ.get("GITHUB_ACTIONS") else "github-actions",
        "tmdb": "disabled", "artifacts": artifacts,
    }
    # LF everywhere: `sha256sum -c` on Linux and macOS reads a CRLF SHA256SUMS as names ending in \r.
    (out / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8", newline="\n")
    (out / "SHA256SUMS").write_text("".join("%s  %s\n" % (a["sha256"], a["name"]) for a in artifacts), encoding="utf-8", newline="\n")
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


def verify(args) -> int:
    folder = pathlib.Path(args.dir)
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
    for artifact in manifest["artifacts"]:
        path = folder / artifact["name"]
        if not path.exists():
            problems.append(artifact["name"] + ": missing")
            continue
        actual = sha256(path)
        if actual != artifact["sha256"] or sums.get(artifact["name"]) != actual:
            problems.append("%s: SHA-256 %s does not match the published %s" % (artifact["name"], actual, artifact["sha256"]))
        if artifact["kind"] == "apk":
            identity = apk_identity(path)
            for key in ("package", "versionCode", "versionName"):
                if identity[key] != artifact["android"][key]:
                    problems.append("%s: %s %r, manifest %r" % (artifact["name"], key, identity[key], artifact["android"][key]))
            if identity["package"] != manifest.get("androidPackage"):
                problems.append("%s: package %s is not the release's %s" % (artifact["name"], identity["package"], manifest.get("androidPackage")))
            if identity["debuggable"]:
                problems.append(artifact["name"] + ": debuggable")
        if artifact["kind"] == "zip":
            with zipfile.ZipFile(path) as z:
                if z.testzip() is not None:
                    problems.append(artifact["name"] + ": corrupt entry")
                # The libmpv inside is the source-built one the provenance names, and its source is published beside it.
                dlls = [n for n in z.namelist() if n.endswith("/libmpv-2.dll")]
                inside = hashlib.sha256(z.read(dlls[0])).hexdigest() if len(dlls) == 1 else None
                if inside is None:
                    problems.append(artifact["name"] + ": not exactly one libmpv-2.dll inside")
                elif inside != libmpv_digest:
                    problems.append("%s: its libmpv-2.dll is %s, not the source-built %s" % (artifact["name"], inside, libmpv_digest))
                if not any(a["kind"] == "source" for a in manifest["artifacts"]):
                    problems.append(artifact["name"] + ": libmpv's source bundle is not published beside it")
    for name in sums:
        if name not in {a["name"] for a in manifest["artifacts"]}:
            problems.append(name + ": in SHA256SUMS, not in the manifest")
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
    if problems:
        print("FAILED")
        for p in problems:
            print("  " + p)
        return 1
    print("verified %d artifacts of Reflux %s (%s)" % (len(manifest["artifacts"]), manifest["version"], manifest["commit"][:7]))
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    b = sub.add_parser("build")
    b.add_argument("--signing-env", default="~/.reflux-release/dry-run.env")
    b.add_argument("--out")
    b.add_argument("--add", action="append")
    b.add_argument("--allow-modified", action="store_true")
    v = sub.add_parser("verify")
    v.add_argument("dir")
    args = parser.parse_args()
    return build(args) if args.command == "build" else verify(args)


if __name__ == "__main__":
    sys.exit(main())
