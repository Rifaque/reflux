#!/usr/bin/env bash
# The physical Linux gate of M2P.4 (docs/VERIFICATION.md), from a checkout on a real Linux machine, in a graphical
# session at that machine:
#
#     tools/live/physical-linux-gate.sh [evidence directory]
#
# Needs: libmpv and ffmpeg installed; a real X11 or Wayland desktop; membership of the video, render, and audio groups;
# a working audio output; ~/.reflux-live/jellyfin.env naming the reflux-test account (mode 600).
#
# Exits non-zero on anything else. A container, WSL, a virtual machine, a virtual display, software rendering, or a
# synthetic audio device is not evidence, and this refuses them rather than skipping. Nothing here prints a credential:
# it is searched for, and only the number of matches is shown.
set -uo pipefail
cd "$(dirname "$0")/../.."
out="${1:-$HOME/reflux-physical-gate-$(date +%Y%m%d-%H%M%S)}"

problems=()
[ "$(uname -s)" = Linux ] || problems+=("not Linux")
{ [ -e /.dockerenv ] || [ -e /run/.containerenv ]; } && problems+=("a container")
grep -qi microsoft /proc/version 2>/dev/null && problems+=("WSL")
virt="$(systemd-detect-virt 2>/dev/null || true)"
[ -n "$virt" ] && [ "$virt" != none ] && problems+=("virtualized: $virt")
pgrep -x Xvfb >/dev/null 2>&1 && problems+=("a virtual display (Xvfb)")
[ -n "${DISPLAY:-}${WAYLAND_DISPLAY:-}" ] || problems+=("no graphical session here")
[ -r /dev/dri/renderD128 ] || problems+=("cannot read the render node (render group?)")
grep -q '^ *[0-9]' /proc/asound/cards 2>/dev/null || problems+=("no sound card")
ldconfig -p 2>/dev/null | grep -q 'libmpv\.so' || problems+=("libmpv is not installed")
command -v ffmpeg >/dev/null || problems+=("ffmpeg is not installed")
[ -f "$HOME/.reflux-live/jellyfin.env" ] || problems+=("no ~/.reflux-live/jellyfin.env")
if [ "${#problems[@]}" -gt 0 ]; then
    echo "REFUSED: this cannot provide physical Linux evidence:"
    printf '  - %s\n' "${problems[@]}"
    exit 2
fi

mkdir -p "$out/engine" "$out/frames"
export REFLUX_MPV_LOG="$out/engine"
fail=0

echo "machine: $(cat /sys/class/dmi/id/sys_vendor /sys/class/dmi/id/product_name 2>/dev/null | tr '\n' ' ')" | tee "$out/summary.txt"
echo "session: ${XDG_SESSION_TYPE:-unknown}, $(uname -r)" | tee -a "$out/summary.txt"
grep -m1 -i 'vga\|3d' <(lspci 2>/dev/null) | tee -a "$out/summary.txt"
head -1 /proc/asound/cards | tee -a "$out/summary.txt"
dpkg-query -W libmpv2 2>/dev/null | tee -a "$out/summary.txt"

REFLUX_PHYSICAL_GATE=1 ./gradlew --console=plain :desktop:app:test --rerun --tests '*PhysicalLinuxGateTest*' \
    -Preflux.windowTests=true "-Preflux.screenshots=$out/frames" > "$out/gate.log" 2>&1 || fail=1
report=desktop/app/build/test-results/test/TEST-dev.reflux.desktop.PhysicalLinuxGateTest.xml
if [ -f "$report" ]; then cp "$report" "$out/"; grep -oE 'GATE [0-9]+ .*' "$report" | cut -c1-200 | tee -a "$out/summary.txt"; else fail=1; fi

for mode in hls-quick file-quick; do
    for round in 1 2 3 4 5 6; do
        REFLUX_TEARDOWN=$mode ./gradlew --console=plain :desktop:app:test --rerun --tests '*PlannedPlaybackWindowTest.aPlayerGoesAwayCleanly*' \
            -Preflux.windowTests=true > "$out/teardown.log" 2>&1
        code=$?
        aborts=$(grep -c 'Assertion\|exit value 134' "$out/teardown.log")
        echo "teardown $mode $round: gradle=$code aborts=$aborts" | tee -a "$out/summary.txt"
        [ "$code" = 0 ] && [ "$aborts" = 0 ] || fail=1
    done
done

# The engine's own account: a real audio output and a hardware decode.
ao=$(grep -rhoE 'AO: \[[a-z]+\]' "$out/engine" 2>/dev/null | sort | uniq -c | tr '\n' ' ')
hw=$(grep -rhoE 'Using hardware decoding \([a-z0-9-]+\)' "$out/engine" 2>/dev/null | sort | uniq -c | tr '\n' ' ')
echo "audio output: ${ao:-none}" | tee -a "$out/summary.txt"
echo "hardware decode: ${hw:-none}" | tee -a "$out/summary.txt"
{ [ -n "$ao" ] && ! grep -rqE 'AO: \[null\]' "$out/engine"; } || { echo "FAIL: no real audio output" | tee -a "$out/summary.txt"; fail=1; }
[ -n "$hw" ] || { echo "FAIL: no hardware decode" | tee -a "$out/summary.txt"; fail=1; }

# Counts only. The value is compared inside Python, and never reaches an argument or the output.
python3 -I - "$out" <<'PY' | tee -a "$out/summary.txt" || fail=1
import os, pathlib, re, sys
values = {}
for line in open(os.path.expanduser("~/.reflux-live/jellyfin.env"), encoding="utf-8"):
    if "=" in line and not line.lstrip().startswith("#"):
        k, v = line.strip().split("=", 1)
        values[k.strip()] = v.strip().strip("\"'")
password = values.get("REFLUX_JELLYFIN_TEST_PASSWORD", "").encode()
token = re.compile(rb'Token="?[0-9a-fA-F]{32}|api_key=[0-9a-fA-F]{32}', re.I)
hits = files = 0
for p in pathlib.Path(sys.argv[1]).rglob("*"):
    if p.is_file() and p.suffix != ".png":
        files += 1
        data = p.read_bytes()
        hits += (bool(password) and password in data) + bool(token.search(data))
print("credential scan: %d files, %d matches" % (files, hits))
sys.exit(1 if hits else 0)
PY

if [ "$fail" = 0 ]; then
    echo "GATE PASSED. Now look at $out/frames for the subtitle, and listen: a person confirms sound from the speakers." | tee -a "$out/summary.txt"
else
    echo "GATE FAILED. See $out." | tee -a "$out/summary.txt"
fi
exit "$fail"
