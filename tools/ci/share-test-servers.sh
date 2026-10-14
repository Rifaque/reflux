#!/usr/bin/env bash
# Throwaway SMB and WebDAV shares in Docker for the live source tests (SourcesLiveTest) and for trying the
# application's "Network share" flow by hand. One generated film, one user, reachable from this machine.
# Nothing here is used by the automated checks.
#
#   tools/ci/share-test-servers.sh <scratch-dir>
#   REFLUX_SHARES_TEST=127.0.0.1 ./gradlew :desktop:engine:test --tests "*SourcesLiveTest*"
#   docker rm -f reflux-smb-test reflux-webdav-test
#
# SMB:    \\127.0.0.1:1445\Movies   (the emulator: \\10.0.2.2:1445\Movies), user reflux / reflux-test
# WebDAV: http://127.0.0.1:8098/     (the emulator: http://10.0.2.2:8098/),   user reflux / reflux-test
#
# Port 445 belongs to Windows itself, so the share listens on 1445. Needs Docker and FFmpeg.
set -euo pipefail
MEDIA="${1:?usage: share-test-servers.sh <scratch-dir>}"

mkdir -p "$MEDIA/share/Night Drive (2021)"
MOVIE="$MEDIA/share/Night Drive (2021)/Night Drive (2021).mkv"
if [ ! -f "$MOVIE" ]; then
    ffmpeg -loglevel error -y -f lavfi -i "testsrc=size=320x180:rate=5" -f lavfi -i "sine=frequency=440" -t 2400 \
        -c:v libx264 -preset ultrafast -crf 40 -pix_fmt yuv420p -c:a aac -b:a 32k "$MOVIE"
fi

# Git Bash on Windows would rewrite the container-side paths of the mounts.
HOST_MEDIA="$(cygpath -m "$MEDIA/share" 2>/dev/null || (cd "$MEDIA/share" && pwd))"
docker rm -f reflux-smb-test reflux-webdav-test >/dev/null 2>&1 || true
MSYS_NO_PATHCONV=1 docker run -d --name reflux-smb-test --memory=512m -p 1445:445 -v "$HOST_MEDIA:/share:ro" \
    dperson/samba -u "reflux;reflux-test" -s "Movies;/share;yes;yes;no;reflux" -p >/dev/null
# The WebDAV image takes ownership of its data folder at start, so that one mount cannot be read-only. It
# is a scratch copy, and Reflux only ever sends PROPFIND and GET.
MSYS_NO_PATHCONV=1 docker run -d --name reflux-webdav-test --memory=256m -p 8098:80 -v "$HOST_MEDIA:/var/lib/dav/data" \
    -e AUTH_TYPE=Basic -e USERNAME=reflux -e PASSWORD=reflux-test bytemark/webdav >/dev/null

for _ in $(seq 1 60); do
    curl -sf -u reflux:reflux-test -X PROPFIND -H "Depth: 0" http://127.0.0.1:8098/ >/dev/null 2>&1 && break
    sleep 1
done
echo "SMB \\\\127.0.0.1:1445\\Movies and WebDAV http://127.0.0.1:8098/, user reflux / reflux-test"
