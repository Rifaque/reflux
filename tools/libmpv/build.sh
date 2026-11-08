#!/usr/bin/env bash
# Builds libmpv-2.dll for Windows x64 from the archives in sources.txt, LGPL only (docs/RELEASE.md, "libmpv on
# Windows"). Runs in the image from tools/libmpv/Dockerfile, with the repository at /src (read-only), the output at
# /out, and a download cache at /cache. Every dependency is linked statically into the one DLL, as Reflux ships it.
#
# LGPL: mpv -Dgpl=false; FFmpeg without --enable-gpl, --enable-version3, or --enable-nonfree, and with no external
# library but dav1d and zlib; nothing GPL-only is built (no x264, x265, Rubber Band, libdvdnav, libdvdread). The script
# fails if FFmpeg reports a license other than LGPL 2.1 or later, or mpv's configuration says gpl.
set -euo pipefail

SRC=${SRC:-/src}
OUT=${OUT:-/out}
CACHE=${CACHE:-/cache}
WORK=/work
PREFIX=/opt/w64
HOST=x86_64-w64-mingw32
JOBS=${JOBS:-$(nproc)}
LIST="$SRC/tools/libmpv/sources.txt"
mkdir -p "$OUT" "$CACHE" "$WORK" "$PREFIX/lib/pkgconfig"

# The POSIX-threads variant of the cross compiler (std::thread, which glslang and SPIRV-Tools use).
export CC=$HOST-gcc-posix CXX=$HOST-g++-posix AR=$HOST-ar RANLIB=$HOST-ranlib STRIP=$HOST-strip
export PKG_CONFIG_LIBDIR="$PREFIX/lib/pkgconfig" PKG_CONFIG_PATH=""
export CFLAGS="-O2 -pipe -ffile-prefix-map=$WORK=." CXXFLAGS="-O2 -pipe -ffile-prefix-map=$WORK=." LDFLAGS=""

cat > "$WORK/cross.txt" <<EOF
[binaries]
c = '$CC'
cpp = '$CXX'
ar = '$AR'
strip = '$STRIP'
windres = '$HOST-windres'
dlltool = '$HOST-dlltool'
pkg-config = 'pkg-config'
nasm = 'nasm'
cmake = 'cmake'
[host_machine]
system = 'windows'
cpu_family = 'x86_64'
cpu = 'x86_64'
endian = 'little'
[properties]
needs_exe_wrapper = true
EOF
cat > "$WORK/toolchain.cmake" <<EOF
set(CMAKE_SYSTEM_NAME Windows)
set(CMAKE_SYSTEM_PROCESSOR x86_64)
set(CMAKE_C_COMPILER $CC)
set(CMAKE_CXX_COMPILER $CXX)
set(CMAKE_RC_COMPILER $HOST-windres)
set(CMAKE_FIND_ROOT_PATH $PREFIX /usr/$HOST)
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
EOF

# --- sources: each archive from the cache or its URL, refused unless its SHA-256 is the one listed ----------------
declare -A VERSION ARCHIVE
while read -r name version sha url; do
    [[ -z "$name" || "$name" == \#* ]] && continue
    case "$url" in *.tar.gz) ext=tar.gz ;; *.tar.xz) ext=tar.xz ;; *) echo "unknown archive type: $url" >&2; exit 1 ;; esac
    file="$CACHE/$name-$version.$ext"
    if [[ ! -s "$file" ]]; then curl -fsSL --retry 3 -o "$file.part" "$url" && mv "$file.part" "$file"; fi
    actual=$(sha256sum "$file" | cut -c1-64)
    [[ "$actual" == "$sha" ]] || { echo "$name: SHA-256 $actual, expected $sha" >&2; exit 1; }
    VERSION[$name]=$version
    ARCHIVE[$name]=$file
done < "$LIST"

unpack() { # unpack <name> -> prints the directory
    local dir="$WORK/$1"
    rm -rf "$dir" && mkdir -p "$dir"
    tar -xf "${ARCHIVE[$1]}" -C "$dir" --strip-components=1
    echo "$dir"
}
meson_static() { # meson_static <dir> <options...>
    local dir=$1; shift
    meson setup "$dir/build" "$dir" --cross-file "$WORK/cross.txt" --prefix "$PREFIX" --libdir lib --buildtype release \
        --default-library static --prefer-static --wrap-mode nodownload -Db_ndebug=true "$@"
    ninja -C "$dir/build" -j "$JOBS" install
}
cmake_static() { # cmake_static <dir> <options...>
    local dir=$1; shift
    cmake -S "$dir" -B "$dir/build" -G Ninja -DCMAKE_TOOLCHAIN_FILE="$WORK/toolchain.cmake" -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_INSTALL_PREFIX="$PREFIX" -DBUILD_SHARED_LIBS=OFF "$@"
    ninja -C "$dir/build" -j "$JOBS" install
}

# --- zlib -------------------------------------------------------------------------------------------------------
d=$(unpack zlib)
make -C "$d" -f win32/Makefile.gcc -j "$JOBS" PREFIX=$HOST- CC="$CC" AR="$AR" RC=$HOST-windres libz.a
make -C "$d" -f win32/Makefile.gcc install PREFIX=$HOST- SHARED_MODE=0 \
    BINARY_PATH="$PREFIX/bin" INCLUDE_PATH="$PREFIX/include" LIBRARY_PATH="$PREFIX/lib"
cat > "$PREFIX/lib/pkgconfig/zlib.pc" <<EOF
prefix=$PREFIX
Name: zlib
Description: zlib compression library
Version: ${VERSION[zlib]}
Libs: -L\${prefix}/lib -lz
Cflags: -I\${prefix}/include
EOF

# --- dav1d (AV1) ------------------------------------------------------------------------------------------------
meson_static "$(unpack dav1d)" -Denable_tools=false -Denable_tests=false -Denable_examples=false -Denable_docs=false

# --- FFmpeg, LGPL -----------------------------------------------------------------------------------------------
d=$(unpack ffmpeg)
FFMPEG_OPTIONS=(--prefix="$PREFIX" --enable-cross-compile --target-os=mingw32 --arch=x86_64 --cross-prefix=$HOST-
    --cc="$CC" --cxx="$CXX" --pkg-config=pkg-config --pkg-config-flags=--static
    --enable-static --disable-shared --disable-programs --disable-doc --disable-debug
    --enable-w32threads --enable-schannel --enable-d3d11va --enable-dxva2 --enable-zlib --enable-libdav1d
    --disable-indevs --disable-outdevs --disable-vulkan --disable-iconv --disable-sdl2 --disable-ffnvcodec --disable-nvdec
    --disable-nvenc --disable-cuda-llvm --disable-amf --disable-mediafoundation --disable-xlib --disable-libdrm)
# Autodetection only sees this container's MinGW-w64 headers and /opt/w64 (bcrypt, the Windows hardware decoders);
# what it found is in build-info.json, as FFmpeg's own configuration line.
(cd "$d" && ./configure "${FFMPEG_OPTIONS[@]}" && make -j "$JOBS" && make install)
grep -q 'define FFMPEG_LICENSE "LGPL version 2.1 or later"' "$d/config.h" || { echo "FFmpeg is not LGPL 2.1+ as configured" >&2; exit 1; }
grep -E '^#define CONFIG_(GPL|VERSION3|NONFREE) 1' "$d/config.h" && { echo "FFmpeg has GPL, version 3, or nonfree parts" >&2; exit 1; } || true
FFMPEG_FOUND=$(grep -E '^#define CONFIG_(BCRYPT|SCHANNEL|D3D11VA|DXVA2|LIBDAV1D|ZLIB|W32THREADS|MEDIAFOUNDATION|VULKAN|LIBXML2|ICONV) 1' "$d/config.h" | awk '{printf "%s ", tolower(substr($2,8))}')

# --- text rendering: FreeType, FriBidi, HarfBuzz, libass ----------------------------------------------------------
meson_static "$(unpack freetype)" -Dharfbuzz=disabled -Dpng=disabled -Dbrotli=disabled -Dzlib=system -Dtests=disabled
meson_static "$(unpack fribidi)" -Ddocs=false -Dbin=false -Dtests=false
meson_static "$(unpack harfbuzz)" -Dfreetype=enabled -Dglib=disabled -Dgobject=disabled -Dcairo=disabled -Dchafa=disabled \
    -Dicu=disabled -Dgraphite=disabled -Dpng=disabled -Dzlib=disabled -Dgdi=disabled -Ddirectwrite=disabled -Dtests=disabled \
    -Dintrospection=disabled -Ddocs=disabled -Dutilities=disabled -Dbenchmark=disabled -Dsubset=disabled -Draster=disabled \
    -Dvector=disabled -Dgpu=disabled -Dwasm=disabled
meson_static "$(unpack libass)" -Dfontconfig=disabled -Ddirectwrite=enabled -Dasm=enabled -Dtest=disabled -Dcompare=disabled \
    -Dprofile=disabled -Dfuzz=disabled -Dcheckasm=disabled -Dlibunibreak=disabled

# --- shader compilers for Direct3D 11: SPIRV-Headers, SPIRV-Tools, glslang, shaderc, SPIRV-Cross -------------------
d=$(unpack shaderc)
mkdir -p "$d/third_party"
for dep in spirv-headers spirv-tools glslang; do
    mkdir -p "$d/third_party/$dep" && tar -xf "${ARCHIVE[$dep]}" -C "$d/third_party/$dep" --strip-components=1
done
cmake_static "$d" -DSHADERC_SKIP_TESTS=ON -DSHADERC_SKIP_EXAMPLES=ON -DSHADERC_SKIP_COPYRIGHT_CHECK=ON \
    -DSPIRV_SKIP_EXECUTABLES=ON -DSPIRV_SKIP_TESTS=ON -DENABLE_GLSLANG_BINARIES=OFF -DGLSLANG_TESTS=OFF \
    -DSPIRV_TOOLS_BUILD_STATIC=ON -DSKIP_GLSLANG_INSTALL=OFF
# shaderc_combined holds shaderc with glslang and SPIRV-Tools; link that one, with the C++ runtime.
cat > "$PREFIX/lib/pkgconfig/shaderc.pc" <<EOF
prefix=$PREFIX
Name: shaderc
Description: shaderc (combined, static)
Version: ${VERSION[shaderc]}
Libs: -L\${prefix}/lib -lshaderc_combined -lstdc++
Cflags: -I\${prefix}/include
EOF
d=$(unpack spirv-cross)
cmake_static "$d" -DSPIRV_CROSS_SHARED=OFF -DSPIRV_CROSS_STATIC=ON -DSPIRV_CROSS_CLI=OFF -DSPIRV_CROSS_ENABLE_TESTS=OFF \
    -DSPIRV_CROSS_ENABLE_MSL=OFF -DSPIRV_CROSS_ENABLE_CPP=OFF -DSPIRV_CROSS_ENABLE_REFLECT=OFF -DSPIRV_CROSS_ENABLE_UTIL=OFF
# mpv asks for the C API by its shared name; here it is the static libraries.
cat > "$PREFIX/lib/pkgconfig/spirv-cross-c-shared.pc" <<EOF
prefix=$PREFIX
Name: spirv-cross-c-shared
Description: SPIRV-Cross C API (static)
Version: ${VERSION[spirv-cross]}
Libs: -L\${prefix}/lib -lspirv-cross-c -lspirv-cross-hlsl -lspirv-cross-glsl -lspirv-cross-core -lstdc++
Cflags: -I\${prefix}/include/spirv_cross
EOF

# --- libplacebo, with its submodules from their own pinned archives ---------------------------------------------
d=$(unpack libplacebo)
for dep in glad jinja markupsafe fast_float; do
    rm -rf "$d/3rdparty/$dep" && mkdir -p "$d/3rdparty/$dep" && tar -xf "${ARCHIVE[$dep]}" -C "$d/3rdparty/$dep" --strip-components=1
done
rm -rf "$d/3rdparty/Vulkan-Headers" && mkdir -p "$d/3rdparty/Vulkan-Headers" \
    && tar -xf "${ARCHIVE[vulkan-headers]}" -C "$d/3rdparty/Vulkan-Headers" --strip-components=1
meson_static "$d" -Dvulkan=disabled -Dopengl=enabled -Dd3d11=enabled -Dshaderc=enabled -Dglslang=disabled -Dlcms=disabled \
    -Dlibdovi=disabled -Ddemos=false -Dtests=false -Dbench=false -Dfuzz=false -Dunwind=disabled -Dxxhash=disabled

# --- mpv: libmpv only, LGPL -------------------------------------------------------------------------------------
d=$(unpack mpv)
MPV_OPTIONS=(-Dgpl=false -Dlibmpv=true -Dcplayer=false -Dtests=false -Dfuzzers=false -Dbuild-date=false
    -Dlua=disabled -Djavascript=disabled -Dlibcurl=disabled -Dlibarchive=disabled -Dlibbluray=disabled -Ddvdnav=disabled
    -Dcdda=disabled -Ddvbin=disabled -Duchardet=disabled -Drubberband=disabled -Dvapoursynth=disabled -Dzimg=disabled
    -Djpeg=disabled -Dlcms2=disabled -Diconv=disabled -Dsubrandr=disabled -Dsdl2-audio=disabled -Dsdl2-video=disabled
    -Dsdl2-gamepad=disabled -Dopenal=disabled -Dvulkan=disabled -Dwin32-smtc=disabled -Dzlib=enabled -Dwasapi=enabled
    -Dd3d11=enabled -Dshaderc=enabled -Dspirv-cross=enabled -Dd3d-hwaccel=enabled -Dgl=enabled -Dgl-win32=enabled
    -Degl-angle=disabled -Dhtml-build=disabled -Dmanpage-build=disabled -Dpdf-build=disabled)
meson setup "$d/build" "$d" --cross-file "$WORK/cross.txt" --prefix "$PREFIX" --libdir lib --buildtype release \
    --default-library shared --prefer-static --wrap-mode nodownload -Db_ndebug=true \
    -Dc_link_args="-static-libgcc -static-libstdc++ -Wl,-Bstatic -lwinpthread -Wl,-Bdynamic" "${MPV_OPTIONS[@]}"
ninja -C "$d/build" -j "$JOBS"
grep -q '#define HAVE_GPL 0' "$d/build/config.h" || { echo "mpv's configuration does not say gpl=false" >&2; exit 1; }
dll="$d/build/libmpv-2.dll"
$STRIP --strip-unneeded "$dll"

# It must load on a plain Windows: nothing but system DLLs.
imports=$($HOST-objdump -p "$dll" | sed -n 's/^\s*DLL Name: //p' | sort -u)
echo "$imports" | grep -v -i -E '^(kernel32|user32|gdi32|advapi32|ole32|oleaut32|shell32|shlwapi|shcore|ws2_32|bcrypt|crypt32|secur32|ncrypt|winmm|avrt|mfplat|mf|mfuuid|propsys|uuid|dwmapi|dxgi|d3d11|d3d9|dxva2|d3dcompiler_47|opengl32|imm32|version|comctl32|ntdll|uxtheme|setupapi|cfgmgr32|powrprof|mfreadwrite|strmiids|psapi|api-ms-win-[a-z0-9-]+|msvcrt|ucrtbase|dwrite|usp10|winhttp|iphlpapi|wtsapi32|pdh|hid|xinput1_4|dbghelp|userenv|normaliz)\.dll$' \
    && { echo "libmpv-2.dll imports DLLs Windows does not provide (above)" >&2; exit 1; } || true

# --- what was built, and the sources it was built from ------------------------------------------------------------
cp "$dll" "$OUT/libmpv-2.dll"
compiler=$($CC --version | head -1)
python3 - "$OUT" "$LIST" "$compiler" "$(meson --version)" "$(cmake --version | head -1)" "${FFMPEG_OPTIONS[*]}" "${MPV_OPTIONS[*]}" "$imports" "$FFMPEG_FOUND" <<'EOF'
import hashlib, json, sys, pathlib
out, listing, compiler, meson, cmake, ffmpeg, mpv, imports, found = sys.argv[1:]
sources = []
for line in open(listing):
    parts = line.split()
    if not parts or parts[0].startswith('#'): continue
    sources.append({"name": parts[0], "version": parts[1], "sha256": parts[2], "url": parts[3]})
dll = pathlib.Path(out, "libmpv-2.dll").read_bytes()
info = {
    "artifact": "libmpv-2.dll", "architecture": "x86_64", "sha256": hashlib.sha256(dll).hexdigest(), "size": len(dll),
    "license": "LGPL-2.1-or-later (mpv -Dgpl=false; FFmpeg LGPL, no GPL or nonfree parts)",
    "toolchain": {"compiler": compiler, "meson": meson, "cmake": cmake, "image": "tools/libmpv/Dockerfile (ubuntu:24.04 by digest)"},
    "configuration": {"ffmpeg": ffmpeg, "ffmpegEnabled": found.split(), "mpv": mpv},
    "imports": imports.split(), "patches": [], "sources": sources,
}
pathlib.Path(out, "build-info.json").write_text(json.dumps(info, indent=2) + "\n")
EOF
# The complete corresponding source: every archive, unchanged, and the scripts and image definition that built them.
bundle="$WORK/libmpv-source"
rm -rf "$bundle" && mkdir -p "$bundle/archives" "$bundle/tools/libmpv"
for name in "${!ARCHIVE[@]}"; do cp "${ARCHIVE[$name]}" "$bundle/archives/"; done
cp "$SRC/tools/libmpv/"{Dockerfile,build.sh,sources.txt,README.md} "$bundle/tools/libmpv/"
cp "$OUT/build-info.json" "$bundle/"
# Modes, times, and owners fixed, so the bundle is the same bytes however the cache was mounted.
find "$bundle" -type d -exec chmod 0755 {} + && find "$bundle" -type f -exec chmod 0644 {} +
tar --sort=name --mtime=@315532800 --owner=0 --group=0 --numeric-owner -C "$WORK" -cJf "$OUT/libmpv-source.tar.xz" libmpv-source
sha256sum "$OUT/libmpv-2.dll" "$OUT/libmpv-source.tar.xz" | sed "s#$OUT/##" > "$OUT/SHA256SUMS"
cat "$OUT/SHA256SUMS"
