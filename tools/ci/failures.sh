#!/usr/bin/env bash
# What failed in a GitHub Actions run, in a few lines (docs/VERIFICATION.md, "How M8+ reads CI"): the failed jobs, the
# failed test classes and methods, and the first assertion or error of each. Reading CI then costs a summary, not a log.
#
#   tools/ci/failures.sh <run-id>        (or a run's URL; needs the GitHub CLI, signed in)
#
# Prints nothing but "all jobs passed" for a green run.
set -euo pipefail
RUN="${1:?usage: failures.sh <run-id>}"
RUN="${RUN##*/runs/}"
RUN="${RUN%%/*}"

failed=$(gh run view "$RUN" --json jobs --jq '.jobs[] | select(.conclusion == "failure") | .name')
if [ -z "$failed" ]; then
    echo "all jobs passed ($(gh run view "$RUN" --json conclusion --jq .conclusion))"
    exit 0
fi
echo "failed jobs:"
printf '  %s\n' "$failed"
echo
# Gradle names a failed test as `Class > method FAILED` and follows it with the assertion; other tools say error/Error.
gh run view "$RUN" --log-failed 2>/dev/null \
    | sed -E 's/^[^\t]*\t[^\t]*\t[0-9TZ:.-]+ //' \
    | grep -E ' FAILED$|AssertionError|Exception:|ComposeTimeoutException|^e: |error:|Error:|##\[error\]' \
    | grep -vE 'at (org|java|kotlin|androidx)\.' \
    | awk '!seen[$0]++' \
    | head -n "${FAILURES_LINES:-60}"
