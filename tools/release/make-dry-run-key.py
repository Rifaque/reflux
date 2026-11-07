#!/usr/bin/env python3
"""Makes a throwaway Android signing key for internal release dry runs, outside the repository.

    python -I tools/release/make-dry-run-key.py

Writes ~/.reflux-release/dry-run.jks and ~/.reflux-release/dry-run.env (REFLUX_SIGNING_* lines, readable only by
this user where the platform allows). The password is random and passed to keytool through the environment, so it is
never an argument, in a process listing, or in any output.

This key is NOT Reflux's upload key and must never sign anything given to a store or a person: it exists so that the
real release configuration (R8, resource shrinking, signing) can be exercised end to end. The upload key is made and
kept by the owner (docs/RELEASE.md, "Signing").
"""
import os
import pathlib
import secrets
import shutil
import subprocess
import sys

HOME = pathlib.Path.home() / ".reflux-release"
STORE = HOME / "dry-run.jks"
ENV = HOME / "dry-run.env"


def keytool() -> str:
    found = shutil.which("keytool")
    if found:
        return found
    java_home = os.environ.get("JAVA_HOME")
    if java_home and (pathlib.Path(java_home) / "bin").exists():
        return str(pathlib.Path(java_home) / "bin" / "keytool")
    sys.exit("keytool not found: put a JDK's bin on PATH or set JAVA_HOME")


def main() -> int:
    if STORE.exists():
        print("exists: " + str(STORE) + " (delete it to make another)")
        return 0
    HOME.mkdir(mode=0o700, exist_ok=True)
    password = secrets.token_urlsafe(32)
    env = dict(os.environ, REFLUX_DRY_RUN_PASSWORD=password)
    subprocess.run(
        [keytool(), "-genkeypair", "-keystore", str(STORE), "-storetype", "PKCS12", "-alias", "reflux-dry-run",
         "-keyalg", "RSA", "-keysize", "4096", "-validity", "3650",
         "-dname", "CN=Reflux internal dry run (not for distribution), O=RSX Develops",
         "-storepass:env", "REFLUX_DRY_RUN_PASSWORD", "-keypass:env", "REFLUX_DRY_RUN_PASSWORD"],
        env=env, check=True, capture_output=True,
    )
    ENV.write_text(
        "REFLUX_SIGNING_STORE_FILE=%s\nREFLUX_SIGNING_STORE_PASSWORD=%s\nREFLUX_SIGNING_KEY_ALIAS=reflux-dry-run\n"
        "REFLUX_SIGNING_KEY_PASSWORD=%s\n" % (STORE.as_posix(), password, password),
        encoding="utf-8",
    )
    for path in (STORE, ENV):
        os.chmod(path, 0o600)
    print("made " + str(STORE) + " and " + str(ENV))
    return 0


if __name__ == "__main__":
    sys.exit(main())
