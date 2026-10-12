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
set -euo pipefail

if [ "$(id -u)" = "0" ]; then
    mkdir -p /work /home/ubuntu/.gradle
    tar -C /src --exclude=build --exclude=.gradle --exclude=.kotlin --exclude=.reflux --exclude=.git -cf - . | tar -C /work -xf -
    chown -R ubuntu:ubuntu /work /home/ubuntu/.gradle
    if [ -d /out ]; then chown ubuntu:ubuntu /out; fi
    exec runuser -u ubuntu -- /work/tools/ci/linux-check.sh "$@"
fi

export HOME=/home/ubuntu
cd /work

results() {
    if [ -d /out ]; then
        mkdir -p "/out/$1"
        find . -path '*/build/test-results/*' -name '*.xml' -exec cp {} "/out/$1/" \;
    fi
}

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
