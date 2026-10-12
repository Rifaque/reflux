#!/usr/bin/env bash
# Runs the desktop shell's real-window tests: first run through the folder chooser, browsing with a mouse
# and a controller, and playback with libmpv under controls operated by a mouse.
#
# Without a display (CI, containers) it first brings up a virtual desktop: an X server, a window manager
# (fullscreen and focus need one), and a compositing manager (the player's controls are a translucent
# window above the video). Extra arguments go to Gradle.
set -euo pipefail
cd "$(dirname "$0")/../.."

if [ -z "${DISPLAY:-}" ]; then
    export DISPLAY=:99
    Xvfb "$DISPLAY" -screen 0 1600x1000x24 -nolisten tcp &
    for _ in $(seq 1 50); do
        xdpyinfo >/dev/null 2>&1 && break
        sleep 0.2
    done
    openbox &
    xcompmgr &
    sleep 1
fi

# The first-run test answers the toolkit's folder chooser. Desktop dialog helpers (zenity, kdialog) are
# separate programs it cannot operate, so for this run they report that they cannot open a window, which
# is also the path Reflux takes on a desktop where they fail.
helpers="$(mktemp -d)"
for helper in zenity kdialog; do
    printf '#!/bin/sh\nexit 127\n' > "$helpers/$helper"
    chmod +x "$helpers/$helper"
done
export PATH="$helpers:$PATH"

exec ./gradlew :desktop:app:test -Preflux.windowTests=true "$@"
