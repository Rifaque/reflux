#!/usr/bin/env python3
"""Runs instrumented tests that need the live server on one device, without the credentials ever being on a command line.

    python -I tools/live/run-on-device.py <adb serial> [test classes, comma-separated]

The server and account come from ~/.reflux-live/jellyfin.env (LiveJellyfin.kt), which is outside the repository.
They reach the device through adb's standard input into a file only the shell user can read, which the test reads and
this script removes afterwards, whatever happens. They are never arguments, so they are in no process listing, shell
history, traceback, or log, and everything printed here is scrubbed of them first.

Exceptions are reported by type only: a Python traceback prints the command it was running.
"""
import os
import re
import subprocess
import sys

ON_DEVICE = "/data/local/tmp/reflux-live.env"
RUNNER = "dev.reflux.test/androidx.test.runner.AndroidJUnitRunner"
DEFAULT_CLASSES = "dev.reflux.android.JellyfinExecutionOnDeviceTest,dev.reflux.android.CapabilityReportOnDeviceTest"


def read_settings() -> dict:
    values = {}
    with open(os.path.expanduser("~/.reflux-live/jellyfin.env"), encoding="utf-8") as lines:
        for line in lines:
            if "=" in line and not line.lstrip().startswith("#"):
                key, value = line.strip().split("=", 1)
                values[key.strip()] = value.strip().strip("\"'")
    return values


class Device:
    def __init__(self, serial: str, secrets: list):
        self.serial = serial
        self.secrets = [s for s in secrets if s]

    def clean(self, text: str) -> str:
        for secret in self.secrets:
            text = text.replace(secret, "<redacted>")
        text = re.sub(r"https?://\S+", "<address>", text)
        return re.sub(r"\b[0-9a-f]{32}\b", "<id>", text)

    def adb(self, *args, stdin: str = None, timeout: int = 300) -> str:
        try:
            done = subprocess.run(
                ["adb", "-s", self.serial, *args], input=stdin, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout,
            )
        except subprocess.TimeoutExpired:
            return "adb timed out after %d s" % timeout
        return self.clean(done.stdout + done.stderr)


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: run-on-device.py <adb serial> [test classes]")
        return 2
    settings = read_settings()
    device = Device(sys.argv[1], [settings.get("REFLUX_JELLYFIN_TEST"), settings.get("REFLUX_JELLYFIN_TEST_USER"), settings.get("REFLUX_JELLYFIN_TEST_PASSWORD")])
    classes = sys.argv[2] if len(sys.argv) > 2 else DEFAULT_CLASSES
    seconds = int(os.environ.get("RUN_SECONDS", "240"))
    device.adb("logcat", "-c")
    if os.environ.get("RUN_ADDRESS"):
        settings["REFLUX_JELLYFIN_TEST"] = os.environ["RUN_ADDRESS"]  # a local relay in front of the server
    body = "".join("%s=%s\n" % (key, value) for key, value in settings.items())
    try:
        device.adb("shell", "umask 077; cat > " + ON_DEVICE, stdin=body)
        extra = os.environ.get("RUN_ARGS", "")
        print(device.adb("shell", "am instrument -w -e class " + classes + " " + extra + " " + RUNNER, timeout=seconds)[-1800:])
    finally:
        device.adb("shell", "rm -f " + ON_DEVICE)
    for line in device.adb("logcat", "-d", "-s", "RefluxExecution:I", "RefluxCapabilities:I").splitlines():
        if "Reflux" in line and ": " in line:
            print(line.split(": ", 1)[1][:430])
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as failure:  # noqa: BLE001 - the type is all that is safe to show
        print("failed: " + type(failure).__name__)
        sys.exit(1)
