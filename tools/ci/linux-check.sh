#!/usr/bin/env bash
# The Linux CI job, runnable on any machine with Docker (see tools/ci/Dockerfile):
#
#   docker build -t reflux-linux-check tools/ci
#   docker run --rm -v "$PWD:/src:ro" -v reflux-gradle:/home/ubuntu/.gradle reflux-linux-check
#
# The source tree is mounted read-only and copied, so nothing is written into the checkout and build
# output from another operating system is never reused. Mount a directory at /out to receive the test
# result files. Append `window` to run only the window tests, and a number to repeat them that often:
# they involve a window manager, a compositor, and a native engine, and repetition is how to trust them.
# `package` builds the Linux release archive instead (docs/RELEASE.md, "Desktop artifacts"), from a copy that keeps
# .git so the archive knows its commit, and writes it with its release-info.json to /out.
set -euo pipefail

if [ "$(id -u)" = "0" ]; then
    mkdir -p /work /home/ubuntu/.gradle
    git_excluded=--exclude=.git
    if [ "${1:-}" = "package" ]; then git_excluded=; fi
    tar -C /src --exclude=build --exclude=.gradle --exclude=.kotlin --exclude=.reflux $git_excluded -cf - . | tar -C /work -xf -
    chown -R ubuntu:ubuntu /work /home/ubuntu/.gradle
    if [ -d /out ]; then chown ubuntu:ubuntu /out; fi
    exec runuser -u ubuntu -- /work/tools/ci/linux-check.sh "$@"
fi

export HOME=/home/ubuntu
# As in CI: with the engine and a display installed, tests that need them must run, not skip themselves.
export REFLUX_REQUIRE_ENGINE_TESTS=true
cd /work

results() {
    if [ -d /out ]; then
        mkdir -p "/out/$1"
        find . -path '*/build/test-results/*' -name '*.xml' -exec cp {} "/out/$1/" \;
    fi
}

if [ "${1:-}" = "package" ]; then
    git config --global --add safe.directory /work
    ./gradlew --no-configuration-cache releaseInfo :desktop:app:packageDesktopArchive --stacktrace
    for archive in desktop/app/build/release/*.tar.gz; do
        cp "$archive" /out/
        cp build/release/release-info.json "/out/$(basename "$archive").release-info.json"
    done
    exit 0
fi

status=0
# As in CI: everything without a display first, then the window tests on a virtual one.
if [ "${1:-}" != "window" ]; then
    ./gradlew check :tools:cli:installDist --continue --stacktrace || status=$?
    results check
fi
for round in $(seq 1 "${2:-1}"); do
    tools/ci/desktop-window-tests.sh --rerun-tasks --stacktrace || status=$?
    results "window-$round"
done
exit "$status"
