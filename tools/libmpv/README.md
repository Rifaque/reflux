# Reflux's Windows libmpv

Reflux for Windows plays through `libmpv-2.dll`, built here from source (DECISIONS.md, 2026-11-08, "Reflux builds its own Windows libmpv before public distribution"; docs/RELEASE.md, "libmpv on Windows").

| File | What it is |
| --- | --- |
| `sources.txt` | Every archive the DLL is made of: name, version, SHA-256, URL. Nothing else is downloaded |
| `Dockerfile` | The build environment: Ubuntu 24.04 by digest, its MinGW-w64 GCC (POSIX threads), Meson, CMake, Ninja, NASM |
| `build.sh` | Fetches and checks the archives, builds every library statically, links them into one `libmpv-2.dll`, checks the result, and writes `build-info.json` and the source bundle |

## Build

```sh
docker build -t reflux-libmpv tools/libmpv
docker run --rm -v "$PWD:/src:ro" -v "$PWD/build/libmpv:/out" -v reflux-libmpv-cache:/cache reflux-libmpv
```

`build/libmpv/` then holds `libmpv-2.dll`, `build-info.json` (versions, compiler, configuration, the DLL's imports and digest), `libmpv-source.tar.xz`, and `SHA256SUMS`. The release workflow does the same and publishes the source bundle beside each release as `reflux-<version>-libmpv-source.tar.xz`.

## What is in it, and under which license

mpv (libmpv only, `-Dgpl=false`), FFmpeg (LGPL: no `--enable-gpl`, `--enable-version3`, or `--enable-nonfree`; external libraries dav1d and zlib only; Windows' own Schannel, D3D11VA, and DXVA2), libplacebo, libass, FreeType, FriBidi, HarfBuzz, dav1d, zlib, shaderc with glslang and SPIRV-Tools, and SPIRV-Cross, linked statically, with the GCC runtime and winpthreads. The DLL as a whole is LGPL-2.1-or-later; each part's license is in `legal/components.txt` ("libmpv (Windows build)") and docs/THIRD-PARTY.md. No source is patched.

The script fails if FFmpeg's configuration reports anything but "LGPL version 2.1 or later", if mpv's says anything but `gpl=false`, or if the DLL imports anything Windows does not provide.

Left out on purpose: Lua and JavaScript (Reflux uses no mpv scripts), libcurl (FFmpeg's own HTTP with Schannel serves streams), Vulkan, Blu-ray and DVD, uchardet, Little CMS, zimg, and the Windows media-controls integration (Reflux handles the window itself).

## Changing it

A new version of anything is a release decision: change the line in `sources.txt` (with the archive's SHA-256), rebuild, run the engine tests against the new DLL (`.reflux/native/`), and record it in `legal/components.txt`, docs/THIRD-PARTY.md, and docs/VERIFICATION.md.
