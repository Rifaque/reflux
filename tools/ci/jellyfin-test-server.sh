#!/usr/bin/env bash
# A throwaway Jellyfin server in Docker for the live watch-state test (JellyfinLiveTest): one generated
# movie, one user, reachable from this machine only. Nothing here is used by the automated checks.
#
#   tools/ci/jellyfin-test-server.sh <scratch-dir>
#   REFLUX_JELLYFIN_TEST=http://127.0.0.1:8096 REFLUX_JELLYFIN_TEST_USER=reflux \
#       REFLUX_JELLYFIN_TEST_PASSWORD=reflux-test ./gradlew :desktop:engine:test --tests "*JellyfinLiveTest*"
#   ./gradlew :android:app:connectedDebugAndroidTest -Preflux.jellyfin=http://10.0.2.2:8096   (on an emulator)
#   docker rm -f reflux-jellyfin-test
#
# Needs Docker, FFmpeg, curl, and Python. The image tag can be set with JELLYFIN_IMAGE to test a
# particular server version.
set -euo pipefail
MEDIA="${1:?usage: jellyfin-test-server.sh <scratch-dir>}"
IMAGE="${JELLYFIN_IMAGE:-jellyfin/jellyfin:latest}"
NAME=reflux-jellyfin-test
URL=http://127.0.0.1:8096
AUTH='MediaBrowser Client="Reflux test setup", Device="setup", DeviceId="reflux-test-setup", Version="1"'
TOKEN=

mkdir -p "$MEDIA/movies/Night Drive (2021)"
MOVIE="$MEDIA/movies/Night Drive (2021)/Night Drive (2021).mkv"
if [ ! -f "$MOVIE" ]; then
    # Forty minutes: a server keeps no resume position in anything only a few minutes long, and the
    # continuity tests stop half an hour in.
    ffmpeg -loglevel error -y -f lavfi -i "testsrc=size=320x180:rate=5" -f lavfi -i "sine=frequency=440" -t 2400 \
        -c:v libx264 -preset ultrafast -crf 40 -pix_fmt yuv420p -c:a aac -b:a 32k "$MOVIE"
fi

# Git Bash on Windows would rewrite the container-side path of the mount.
HOST_MEDIA="$(cygpath -m "$MEDIA" 2>/dev/null || (cd "$MEDIA" && pwd))"
docker rm -f "$NAME" >/dev/null 2>&1 || true
MSYS_NO_PATHCONV=1 docker run -d --name "$NAME" --memory=2g -p 127.0.0.1:8096:8096 -v "$HOST_MEDIA:/media:ro" "$IMAGE" >/dev/null

post() {
    curl -sf -X POST "$URL/$1" -H "Authorization: $AUTH${TOKEN:+, Token=\"$TOKEN\"}" -H 'Content-Type: application/json' -d "$2"
}

for _ in $(seq 1 120); do
    curl -sf "$URL/System/Info/Public" >/dev/null 2>&1 && break
    sleep 2
done

# The first-run wizard, as the server's own web page drives it.
for _ in $(seq 1 30); do
    post Startup/Configuration '{"UICulture":"en-US","MetadataCountryCode":"US","PreferredMetadataLanguage":"en"}' && break
    sleep 2
done
curl -sf "$URL/Startup/User" -H "Authorization: $AUTH" >/dev/null
post Startup/User '{"Name":"reflux","Password":"reflux-test"}'
post Startup/RemoteAccess '{"EnableRemoteAccess":true,"EnableAutomaticPortMapping":false}' || true
post Startup/Complete '{}'

TOKEN=$(post Users/AuthenticateByName '{"Username":"reflux","Pw":"reflux-test"}' | python -c "import sys, json; print(json.load(sys.stdin)['AccessToken'])")
post "Library/VirtualFolders?name=Movies&collectionType=movies&paths=%2Fmedia%2Fmovies&refreshLibrary=true" \
    '{"LibraryOptions":{"EnableInternetProviders":false,"PathInfos":[{"Path":"/media/movies"}]}}'

# The scan that adding a library asks for can be swallowed by the one the wizard just started, so it is
# asked for again until the movie is listed.
COUNT=0
for attempt in $(seq 0 89); do
    [ $((attempt % 10)) -eq 0 ] && post Library/Refresh '{}' >/dev/null
    COUNT=$(curl -sf "$URL/Items?Recursive=true&IncludeItemTypes=Movie" -H "Authorization: $AUTH, Token=\"$TOKEN\"" \
        | python -c "import sys, json; print(sum(1 for item in json.load(sys.stdin)['Items'] if (item.get('RunTimeTicks') or 0) > 0))")
    [ "$COUNT" -ge 1 ] && break
    sleep 2
done
[ "$COUNT" -ge 1 ] || { echo "The server did not list the movie." >&2; exit 1; }
curl -sf "$URL/System/Info" -H "Authorization: $AUTH, Token=\"$TOKEN\"" \
    | python -c "import sys, json; info = json.load(sys.stdin); print(info.get('ProductName'), info.get('Version'), 'at $URL, user reflux / reflux-test')"
