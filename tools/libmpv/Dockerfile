# The build environment of Reflux's Windows libmpv (docs/RELEASE.md, "libmpv on Windows"): Ubuntu 24.04, pinned by
# digest, with the distribution's MinGW-w64 cross compiler (GCC, POSIX threads) and build tools. Nothing is fetched
# here but Ubuntu's own packages; every source the DLL is made of is in sources.txt with its SHA-256.
#
#   docker build -t reflux-libmpv tools/libmpv
#   docker run --rm -v "$PWD:/src:ro" -v <out>:/out -v reflux-libmpv-cache:/cache reflux-libmpv
#
# /out receives libmpv-2.dll, build-info.json (versions, toolchain, configuration, digests), and the source bundle.
FROM ubuntu@sha256:fe81560990fae86dc4743202b2cb4d3df98d0bcf949173cecef15ce1127ef852

ENV DEBIAN_FRONTEND=noninteractive LC_ALL=C.UTF-8 TZ=UTC SOURCE_DATE_EPOCH=315532800
RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates curl xz-utils bzip2 gcc g++ libc6-dev make cmake ninja-build meson nasm pkgconf python3 \
        gcc-mingw-w64-x86-64-posix g++-mingw-w64-x86-64-posix binutils-mingw-w64-x86-64 mingw-w64-x86-64-dev \
    && rm -rf /var/lib/apt/lists/*

COPY --chmod=755 build.sh /usr/local/bin/build-libmpv
ENTRYPOINT ["/usr/local/bin/build-libmpv"]
