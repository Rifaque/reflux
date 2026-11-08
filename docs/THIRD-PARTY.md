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
| libmpv-2.dll (mpv with FFmpeg, libass, libplacebo, dav1d, libbluray, HarfBuzz, FreeType, FriBidi, zimg, Little CMS, libjxl, uchardet, shaderc/SPIRV, libssh, Lua, and others linked in statically) | mpv `55d3feb45c`, zhongfly/mpv-winbuild `2026-11-07-55d3feb45c`, SHA-256 `17bff1d2…15df` | LGPL-2.1-or-later as built; its parts are LGPL, BSD, MIT, ISC, Apache-2.0, FTL, MPL, zlib | Loaded at run time by name (JNA); replaceable | No | `third-party/libmpv-SOURCE-OFFER.txt`, `third-party/licenses/lgpl-2.1.txt` |
| Skia and ICU (inside Skiko's native library) | Skiko's | BSD-3-Clause, Unicode-3.0 | In-process | No | About page |

### libmpv and the LGPL

- **How it is linked:** not at all at build time. Reflux's JVM code calls the libmpv client API through JNA, loading `libmpv-2.dll` by name from the app's resources folder at run time (`LibMpv.loadOrNull`). Replacing the DLL with another libmpv 2.x build works, which is what LGPL-2.1 section 6 asks of a work that uses the library.
- **What it is built from:** checked from three sides: the upstream workflow's LGPL patch (removes `--enable-gpl` and every GPL-only library: x264, x265, Rubber Band, libdvdnav, libdvdread, davs2, libzvbi, AviSynth; mpv `-Dgpl=false`, `-Ddvdnav=disabled`, `-Drubberband=disabled`), the DLL's own strings (`-Dgpl=false`; no `--enable-gpl`, `--enable-nonfree`, or `--enable-libx264`/`libx265`), and the absence of GPL-only symbols (the remaining matches for "x264" and "Xvid" are FFmpeg's own stream-signature parsers). No `--enable-nonfree`.
- **What must accompany it:** the LGPL text (shipped), a notice of the library's use and its license (`libmpv-SOURCE-OFFER.txt`, shipped), and the complete corresponding source of the library as distributed, or a written offer for it valid three years.
- **Open, a gate before any public Windows download (M7):** the source. The pinned build's dependencies are nightly checkouts; their exact commits are only in that GitHub Actions run's logs, which expire. Reflux will build libmpv in its own release workflow from mpv-winbuild-cmake with the LGPL patch, archive every source it built from as `reflux-<version>-libmpv-source.tar.zst` on the GitHub release, and the offer file already names that asset. Internal builds (the M6 dry run) are not distribution.

## Linux (`.tar.gz`)

As Windows, without libmpv: Reflux uses the distribution's libmpv (`libmpv2`, `mpv-libs`), installed and licensed by the distribution. The runtime is Temurin 21 for Linux.

## Website

React 19.3.1, React DOM 19.3.1, React Router 7.18.5 (with `cookie` 1.1.1 and `set-cookie-parser` 2.7.3), scheduler 0.28.0: all MIT, bundled into the site's script. Listed on `/legal/third-party` from the lockfile at build time, with their license texts served verbatim at `/third-party-licenses.txt`. Fonts: the visitor's system fonts, none shipped. Icon: Reflux's own mark.

## Fonts, icons, assets in the apps

The apps' icons and the desktop icons (`desktop/app/packaging/`, drawn by `tools/release/make-desktop-icons.py`) are Reflux's own. Compose's default typography uses the platform's fonts; no font file is redistributed. The TMDB logo is shown on the About page only where TMDB is in use (development builds), under TMDB's attribution rules.
