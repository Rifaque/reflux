# Reflux Third-Party Inventory

What each release artifact distributes, under which license, with which obligations, and where the notices travel (reviewed 2026-11-08 on the dry-run artifacts of `9a95fe8`, VERIFICATION.md, M6). The per-component list the About page shows is `legal/components.txt`, with the texts in `legal/licenses/`; `./gradlew check` (`checkThirdPartyNotices`) fails when a JVM module on the Android release or desktop runtime classpath is covered by no line. Native code and the bundled runtime are kept by hand, here.

## Android (`.apk`, `.aab`)

- JVM libraries: every line of `legal/components.txt` with target `android`: Kotlin and kotlinx (Apache-2.0), Compose and AndroidX (Apache-2.0), Media3 (Apache-2.0), SQLDelight (Apache-2.0), smbj and ASN.1 (Apache-2.0), Bouncy Castle (MIT), MBassador (MIT), SLF4J (MIT), JSpecify (Apache-2.0), Guava (Apache-2.0), OkHttp and Okio (Apache-2.0). The release runtime classpath is the one checked.
- Native code: none of Reflux's own; Media3 uses the platform's decoders, so no FFmpeg is in the package.
- Modifications: none. R8 shrinks and renames, which the licenses permit; the notices are shown in-app (About → Third-party software) with full texts.
- Obligations: Apache-2.0 notice and license text (no component ships a `NOTICE` file); MIT copyright and permission notice. Both met by the About page.

## Windows (`.zip`)

| Component | Version | License | Linked | Modified | Notices |
| --- | --- | --- | --- | --- | --- |
| JVM libraries (desktop lines of `legal/components.txt`) | as in the Gradle build | Apache-2.0, MIT, BSD-2/3-Clause, Unicode-3.0, JNA's Apache-2.0 or LGPL-2.1+ | jars in `app/` | No | About page, `third-party/` |
| Eclipse Temurin (OpenJDK) runtime | 21.0.12.1, jlinked to the modules Reflux uses | GPL-2.0 with the Classpath Exception | In-process runtime | No (jlink selects modules and strips debug information) | `runtime/legal/` (the JDK's own), `third-party/licenses/openjdk.txt`. Its source is OpenJDK's, published by Eclipse Adoptium |
| libmpv-2.dll: mpv 0.42.0 with FFmpeg 9.0.2, libplacebo 7.387.0, libass 0.17.5, FreeType 2.15.0, FriBidi 1.0.17, HarfBuzz 14.6.0, dav1d 1.5.4, zlib 1.3.1, shaderc 2026.4 (with glslang and SPIRV-Tools), SPIRV-Cross 1.4.357.0, SPIRV-Headers, fast_float, glad, and the MinGW-w64 and GCC runtimes, linked statically | **Built by RSX Develops** from `tools/libmpv/sources.txt` (each archive by SHA-256), reproducibly: SHA-256 `e52f3f45…90a4` (M7) | LGPL-2.1-or-later as built; its parts LGPL-2.1+, ISC, FTL, MIT, BSD-2/3-Clause, Zlib, Apache-2.0, ZPL-2.1, and GPL-3.0+ with the GCC Runtime Library Exception (glslang's Bison parser: GPL-3.0+ with the Bison exception) | Loaded at run time by name (JNA); replaceable | No (unmodified upstream releases) | `third-party/libmpv-SOURCE-OFFER.txt`, `third-party/licenses/` (one text per part, `legal/components.txt`); the source bundle `reflux-<version>-libmpv-source.tar.xz` beside every Windows release |
| Skia and ICU (inside Skiko's native library) | Skiko's | BSD-3-Clause, Unicode-3.0 | In-process | No | About page |

### libmpv and the LGPL

- **How it is linked:** not at all at build time. Reflux's JVM code calls the libmpv client API through JNA, loading `libmpv-2.dll` by name from the app's resources folder at run time (`LibMpv.loadOrNull`). Replacing the DLL with another libmpv 2.x build works, which is what LGPL-2.1 section 6 asks of a work that uses the library.
- **What it is built from (M7):** Reflux's own build, `tools/libmpv` (README there): the unmodified release archives in `sources.txt`, each checked by SHA-256, built in a Docker image pinned by digest (Ubuntu 24.04, MinGW-w64 GCC 13 with POSIX threads), every library static, linked into one DLL that imports only Windows' own DLLs (`build-info.json` lists them). Two independent builds gave identical bytes (VERIFICATION.md, M7), so the pin in `desktop/app/packaging/libmpv-windows.properties` is the DLL's digest, and packaging and `release.py verify` refuse any other.
- **LGPL as built, checked by the build itself:** mpv `-Dgpl=false` (its `config.h` must say so); FFmpeg without `--enable-gpl`, `--enable-version3`, or `--enable-nonfree`, its `config.h` must report "LGPL version 2.1 or later" and no `CONFIG_GPL`, `CONFIG_VERSION3`, or `CONFIG_NONFREE`; FFmpeg's only external libraries are dav1d and zlib (plus Windows' Schannel, D3D11VA, DXVA2, BCrypt); nothing GPL-only is in the source list (no x264, x265, Rubber Band, libdvdnav, libdvdread). glslang's preprocessor grammar is Bison output, GPL-3.0+ with the Bison exception, which lets it be distributed under glslang's own terms; the GCC runtime is GPL-3.0+ with the GCC Runtime Library Exception, which permits it in a program compiled with GCC.
- **What accompanies it:** the LGPL text and every part's license (`third-party/licenses/`), the notice (`libmpv-SOURCE-OFFER.txt`), and the complete corresponding source: `reflux-<version>-libmpv-source.tar.xz` on the same GitHub release, holding every source archive unchanged and the scripts and image definition that built them; and a written offer of the same source for three years.
- **Build-time only, not in the DLL:** Jinja and MarkupSafe (libplacebo's code generators) and the Vulkan headers (Vulkan is off); they are in the source bundle because the build reads them.
- **Gate for public Windows downloads (M6):** met by the above (M7, gate 7). The M6 third-party build (zhongfly/mpv-winbuild) is no longer used.

## Linux (`.tar.gz`)

As Windows, without libmpv: Reflux uses the distribution's libmpv (`libmpv2`, `mpv-libs`), installed and licensed by the distribution. The runtime is Temurin 21 for Linux.

## Website

React 19.3.1, React DOM 19.3.1, React Router 7.18.5 (with `cookie` 1.1.1 and `set-cookie-parser` 2.7.3), scheduler 0.28.0: all MIT, bundled into the site's script. Listed on `/legal/third-party` from the lockfile at build time, with their license texts served verbatim at `/third-party-licenses.txt`. Fonts: the visitor's system fonts, none shipped. Icon: Reflux's own mark.

## Fonts, icons, assets in the apps

The apps' icons and the desktop icons (`desktop/app/packaging/`, drawn by `tools/release/make-desktop-icons.py`) are Reflux's own. Compose's default typography uses the platform's fonts; no font file is redistributed. The TMDB logo is shown on the About page only where TMDB is in use (development builds), under TMDB's attribution rules.
