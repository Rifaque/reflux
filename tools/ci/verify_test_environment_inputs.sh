#!/usr/bin/env bash
# Checks, on the real build, how test tasks treat FONTCONFIG_FILE and the opt-in exclusion (build.gradle.kts, "What a
# test's result depends on"; docs/engineering/CI-PERFORMANCE-AUDIT.md, L2). Run by hand, not by CI: there is no Gradle
# TestKit in this repository, and this needs a Gradle run per case (about a minute in all once compiled).
#
#   tools/ci/verify_test_environment_inputs.sh            exit 0 when every case behaves as below
#
# Gradle's up-to-date check is the observation: after a run with FONTCONFIG_FILE unset, a run whose inputs are the same
# is UP-TO-DATE. So an empty or whitespace-only value must leave the task UP-TO-DATE (no file input, and no failure such
# as "Cannot convert '' to File", CI 45881492615); a file must make it not up to date, with Gradle naming that input
# (it then runs, or comes from the build cache if a run with identical inputs was stored); the same file again must leave
# it up to date; changed contents must not. An opt-in REFLUX_ variable must make it run every time, never from the cache.
#
# It needs an environment with no other REFLUX_ variable and no ~/.reflux-live folder (either makes every test task run,
# by design, and would hide what is checked here). It changes nothing outside build/ and a temporary directory.
set -euo pipefail
cd "$(dirname "$0")/../.."
if env | grep -q '^REFLUX_' || [ -d "$HOME/.reflux-live" ]; then
    echo "refused: a REFLUX_ variable or ~/.reflux-live is present, so every test task runs and nothing here can be seen" >&2
    exit 2
fi
task=":sources:local:test"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
printf '<?xml version="1.0"?>\n<fontconfig><!-- a --></fontconfig>\n' > "$tmp/fonts.conf"
failures=0

# run <label> <expected: UP-TO-DATE | EXECUTED | INVALIDATED (EXECUTED or FROM-CACHE: not up to date, Gradle names the input)> <reason fragment or ""> [VAR=value ...]
run() {
    local label=$1 expected=$2 reason=$3
    shift 3
    local out outcome
    if ! out=$(env "$@" ./gradlew "$task" -Preflux.android=false --console=plain --info 2>&1); then
        echo "FAIL  $label: the build failed"; echo "$out" | grep -E "What went wrong|Cannot convert|> " | head -5 | sed 's/^/      /'
        failures=$((failures + 1)); return
    fi
    outcome=$(echo "$out" | { grep -E "^> Task $task( |$)" || true; } | sed "s#^> Task $task##; s/^ //")
    outcome=${outcome:-EXECUTED}
    local why
    why=$(echo "$out" | { grep -A4 "Task '$task' is not up-to-date because:" || true; } | tail -n +2 | tr '\n' ' ' | tr -s ' ')
    local matches=no
    if [ "$outcome" = "$expected" ] || { [ "$expected" = INVALIDATED ] && [[ "$outcome" = EXECUTED || "$outcome" = FROM-CACHE ]]; }; then matches=yes; fi
    if [ "$matches" = no ] || { [ -n "$reason" ] && [[ "$why" != *"$reason"* ]]; }; then
        echo "FAIL  $label: $outcome, expected $expected${reason:+ (because: $reason)}; Gradle said: ${why:-nothing}"
        failures=$((failures + 1))
    else
        echo "ok    $label: $outcome${why:+ (because: ${why:0:150})}"
    fi
}

unset FONTCONFIG_FILE
echo "      settling: one run with FONTCONFIG_FILE unset (not checked)"
./gradlew "$task" -Preflux.android=false -q >/dev/null 2>&1 || { echo "FAIL  the settling run failed"; exit 1; }
run "unset again"                            UP-TO-DATE ""
run "empty string"                           UP-TO-DATE "" FONTCONFIG_FILE=
run "whitespace only"                        UP-TO-DATE "" "FONTCONFIG_FILE=   "
run "a valid file"                           INVALIDATED "env.FONTCONFIG_FILE" "FONTCONFIG_FILE=$tmp/fonts.conf"
run "the same file again"                    UP-TO-DATE "" "FONTCONFIG_FILE=$tmp/fonts.conf"
printf '<?xml version="1.0"?>\n<fontconfig><!-- b --></fontconfig>\n' > "$tmp/fonts.conf"
run "the same path, contents changed"        INVALIDATED "env.FONTCONFIG_FILE.contents" "FONTCONFIG_FILE=$tmp/fonts.conf"
run "back to empty"                          INVALIDATED "env.FONTCONFIG_FILE" FONTCONFIG_FILE=
run "an opt-in variable (REFLUX_MATRIX)"     EXECUTED   "" REFLUX_MATRIX=1
run "the opt-in again: never up to date"     EXECUTED   "upToDateWhen" REFLUX_MATRIX=1

if [ "$failures" -ne 0 ]; then echo "$failures case(s) failed"; exit 1; fi
echo "all cases behaved as expected"
