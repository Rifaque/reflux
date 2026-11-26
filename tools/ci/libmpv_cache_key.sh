#!/usr/bin/env bash
# The release workflow's cache key for Reflux's Windows libmpv build (docs/engineering/CI-PERFORMANCE-AUDIT.md, L4).
#
#   tools/ci/libmpv_cache_key.sh [repository root]   prints libmpv-v2-<sha256>
#
# It covers what the build is made from (tools/libmpv/Dockerfile, build.sh, sources.txt) and the two pinned digests in
# desktop/app/packaging/libmpv-windows.properties, so a change to any of them, a re-pin included, builds afresh. Left
# out on purpose: that file's comments, and tools/libmpv/README.md. The README is copied into the source bundle, but the
# bundle's every byte is pinned by sourceSha256: a README change that is meant must come with a re-pin (which changes
# this key), and one that is not is drift that the Windows libmpv workflow, which always builds from scratch when
# tools/libmpv changes, fails on. Whatever the cache restores is compared with both pins before it is used.
set -euo pipefail
root=${1:-.}
properties="$root/desktop/app/packaging/libmpv-windows.properties"
pin() {
    local value
    value=$(sed -n "s/^$1=//p" "$properties" | tr -d '\r')
    [[ "$value" =~ ^[0-9a-f]{64}$ ]] || { echo "libmpv_cache_key: $1 missing or not a SHA-256 in $properties" >&2; exit 1; }
    echo "$value"
}
dll=$(pin dllSha256)
source=$(pin sourceSha256)
digest=$(
    {
        for f in Dockerfile build.sh sources.txt; do
            printf '%s %s\n' "$f" "$(sha256sum < "$root/tools/libmpv/$f" | cut -c1-64)"
        done
        printf 'dllSha256 %s\nsourceSha256 %s\n' "$dll" "$source"
    } | sha256sum | cut -c1-64
)
echo "libmpv-v2-$digest"
