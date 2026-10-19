# Reflux Release Readiness

What stands between the source tree and builds that can be handed to testers and the public, and the checklists each stage must pass. The milestones themselves are in [ROADMAP.md](ROADMAP.md) (M2 release engineering, M3 validation, M4–M7 beta to 1.0); version rules are in [VERSIONING.md](VERSIONING.md); the website, beta program, and store tracks are in [LAUNCH.md](LAUNCH.md).

## Beta entry checklist (`0.9.0-beta.1`)

- [ ] Every M1 item in ROADMAP.md is Done, or decided out of V1 in DECISIONS.md.
- [x] Reflux's own terms are in the repository: `LICENSE`, proprietary, all rights reserved (DECISIONS.md, 2026-10-19); third-party notices are done (`legal/`, checked by the build, below).
- [ ] The legal review below is done, or each item decided as not needed for the beta.
- [ ] About page with version and build, Reflux's copyright, third-party licenses, TMDB attribution, privacy and support links (done 2026-10-19 apart from the privacy policy and support links, which wait for the website to have them).
- [ ] Android: signed release build from CI; R8 on, keep rules validated by a release build on a device; upgrade from the last alpha keeps library, watch state, sources, and downloads.
- [ ] Fire TV: the developer's devices inspected (model, Fire OS, API level) and the release build run on them.
- [ ] Desktop: Windows installer and Linux package installing libmpv with provenance; a clean-machine install on each.
- [ ] TMDB credential injected into release builds; live TMDB run recorded in VERIFICATION.md.
- [ ] At least one physical phone, one physical Google TV / Android TV device, one Fire TV device, and one physical controller verified (VERIFICATION.md).
- [ ] CI green on GitHub, including Android.
- [ ] Google Play closed testing track and Amazon Appstore testing set up with the current store requirements read and recorded here.
- [ ] Beta website (W3) live with the demo video, signup, install instructions, privacy policy, changelog, and support.
- [ ] Feedback path in the app (link and diagnostics export) works on every platform.
- [ ] `CHANGELOG.md` has the beta's entry; the `release: 0.9.0-beta.1` commit and tag exist.

## Release checklist (every build given to anyone outside CI)

- [ ] Built by CI from a clean tree at a tagged commit; version follows VERSIONING.md (no hand-written `versionCode`).
- [ ] Full `check` green (with an Android SDK it includes `checkDebugPlatformApi`, the Java-on-old-Android check); Android unit tests and lint green; instrumented tests run on at least one phone and one television image, and on a device at Android's oldest supported level in practice (a Fire OS device, API 28 or 30).
- [ ] Release build smoke-tested on a device per platform: first run, a folder, a server, playback, resume, a download.
- [ ] Upgrade from the previous build tested (data kept, migrations applied).
- [ ] No credential, token, or test account in the build, its resources, or its logs; the TMDB credential comes from the build environment.
- [ ] Changelog entry written in user terms; known issues listed.
- [ ] Store or download artifacts checksummed; desktop installer signed.
- [ ] VERIFICATION.md has a dated section for the build.

## 1.0 checklist (in addition)

- [ ] The V1 acceptance criteria in ROADMAP.md hold on every V1 target.
- [ ] `1.0.0` is the final release candidate's commit with only the version changed.
- [ ] Store listings final on Google Play and the Amazon Appstore; desktop downloads on the website.
- [ ] Website in its 1.0 state (W4).

## Store requirements

Store rules change; this section records what was read in the stores' current documentation and when. Nothing here has been checked against the live consoles yet.

| Store | To confirm when the track is set up |
| --- | --- |
| Google Play | Target API level in force at submission; closed-testing requirements for a new personal developer account (tester count and testing period); data safety form; TV app quality checklist and TV review; Watch Next certification (Google TV shows an application's Watch Next programs only once its integration is certified for quality); content rating; the `READ_MEDIA_VIDEO` declaration (see Credentials, televisions) |
| Amazon Appstore (Fire TV) | Fire OS versions and devices to target (never Vega OS devices); Fire TV app requirements and test criteria; Live App Testing for the beta; listing assets at Amazon's sizes; privacy policy |

## Native runtime: libmpv

Desktop playback and probing use libmpv through its stable client API (decision log, "libmpv integration"). The library is loaded by name at run time and is not part of the repository.

**Development today**

- Nothing compiles or links against libmpv. Building, and every test that does not play real media, needs only the Gradle wrapper.
- Tests that need the engine skip themselves when it cannot be loaded, and those that generate media also need FFmpeg on the path.
- To get the engine: on Linux install the distribution's package (`libmpv2` on Debian and Ubuntu, `mpv-libs` on Fedora); on Windows put `libmpv-2.dll` in `.reflux/native/`, which Git ignores and which the build passes to tests and `run` tasks as the native library path. That folder is a developer convenience only.
- Without the engine the application runs, browses, and says that playback is not set up.

**What a release needs**

- A libmpv build to ship on Windows, with a known provenance and configuration: built LGPL (no GPL-only components), with the decoders and hardware paths Reflux relies on. The development DLL is a third-party build and is not that.
- On Linux, either a dependency on the distribution's libmpv (deb/rpm) or a bundled copy (Flatpak/AppImage). The versions differ between distributions; the binding only uses API that every libmpv 2.x has.
- LGPL obligations for the bundled library and its dependencies (FFmpeg, libass, libplacebo): license texts, source offer, and dynamic linking, which is already how it is loaded. Reflux is proprietary, so the bundled build must be an LGPL one, with no GPL parts in mpv or FFmpeg (DECISIONS.md, 2026-10-19). The About page lists libmpv today as the copy on the user's computer, not included; a bundled build changes its line in `legal/components.txt` (its license as built, the LGPL text, where its source is offered).
- A packaged desktop build that carries its own Java runtime adds that runtime to the notices (OpenJDK is GPL-2.0 with the Classpath Exception); the notice check only sees Maven modules.
- An installer or package per platform, and code signing on Windows. `desktop:app` has no packaging configuration yet.
- On X11 the engine changes process-wide state (decision log, 2026-10-12); a bundled build is a chance to patch that at the source.

## Credentials

On Android the app currently allows plain-HTTP connections to any host, because home media servers are usually `http://`. Before release that should be narrowed where it can be (a network security configuration, or asking when a server is added). Reviewed for the Android client so far: no storage permission is requested (folders are granted one at a time through the system picker, read-only, and the grant is given back when a source is removed); backups of app data are switched off, so the library database with its source credentials does not leave the device that way; problem reports are redacted before they reach the system log or the log file; the exported components are the launcher activity (which Watch Next programs also open, by an explicit intent naming a work) and `ArtworkProvider`, which lends the launcher read-only artwork from the cache by digest names and nothing else (checked on the television emulator: a cache file is served to another application; `..` paths and other files are not found); nothing in the production path is debug-only. Still open: the plain-HTTP policy above, credentials in the database (below), and the media session being available to any media controller on the device, which is the platform's default.

Downloads for offline are files in the application's internal storage: not readable by other applications, not in any shared or public location, not backed up (backups are off), and removed with the application. No permission was added for them. They are fetched with the same credentials the source plays with, which is one more reason for the credential work below. Not yet done: continuing a download with the application closed (a foreground service and notification, undecided), and anything that limits downloads to unmetered networks.

For televisions: the application declares the TV launcher category and a banner, and requires neither a touchscreen nor the leanback feature, so one package serves handhelds and televisions. It declares `READ_MEDIA_VIDEO` (and `READ_EXTERNAL_STORAGE` up to Android 12), requested only on a television, where there is no system folder picker; a store listing will show that permission for every device, which is worth a line of explanation there. Known gap (found 2026-10-15, investigated the same day): on Android 13 and later, `READ_MEDIA_VIDEO` shows a folder chosen by its path only its videos, so the artwork beside them (`poster.jpg`, `fanart.jpg`) is never seen on a television; works there use provider artwork when identified and online, a network source's own artwork, or the generated treatment. No less-privileged way reaches those files (see DECISIONS.md, 2026-10-15, "One artwork fallback rule"). Reading them would need `READ_MEDIA_IMAGES`, which v1 does not request (decided; DECISIONS.md, 2026-10-15, "Android TV does not request `READ_MEDIA_IMAGES` in v1"): it would expose every image in shared storage for the sake of artwork, and Google Play restricts it as broad access. The manifest must not gain it. Android 12 and earlier are unaffected (`READ_EXTERNAL_STORAGE` covers images). Watch Next (DECISIONS.md, 2026-10-18) adds `WRITE_EPG_DATA`, which inserting programs requires; the TV provider library's `READ_EPG_DATA` is removed. Both are normal permissions with no prompt; a store listing may name the first as access to TV channel data. Not looked at yet for a television release: the store's TV quality checklist beyond the manifest. Televisions that offer picture-in-picture (Fire OS does) do not get it: leaving pauses (DECISIONS.md, 2026-10-18).

Android release builds also need what debug builds do not: a signing configuration, shrinking with keep rules for the engine modules, and a look at what the desktop-only classes left in those modules (the JDBC database opener, the JDK HTTP client) do to a shrunk build.

Servers and shares are now added in the application. What that changed, and what it did not: the password typed for a Jellyfin server is sent once, to sign in, and is not kept anywhere; the form holds a password only while it is open, shows it masked, and clears it after connecting; a failed connection is reported to diagnostics as its kind and the connector's own detail (an HTTP status, an SMB status name), never with the request, the address's credentials, or the password; an address with a name and password written into it is refused, so they cannot end up in a stored or logged address; and the sources page shows an address and a user name, never a password or a token. Plain `http://` addresses are accepted for home servers, as before (the plain-HTTP question above is unchanged).

Source credentials (Jellyfin access tokens, WebDAV and SMB passwords) are kept out of the library database where the platform allows (decision log, 2026-10-14):

| Platform | Where a connection is kept | What the database holds |
| --- | --- | --- |
| Windows | Credential Manager, as a generic credential of the signed-in user (`Reflux/<library>/<source id>`) | A reference, `keychain:v1:<source id>` |
| Linux with a running Secret Service (GNOME Keyring, KWallet, KeePassXC) | The Secret Service, through `secret-tool` | A reference |
| Linux without one (a bare window manager, a container) | The library database, as it is; the diagnostics log says so at start | The connection |
| Android | The library database, encrypted with an AES key held in the Android keystore | Ciphertext |

- A connection stored in the open by an earlier version is moved at start, and keeps working; if the store refuses it, it is left as it was and the problem is logged. A new connection that cannot be kept securely where secure storage exists is not kept at all.
- Removing a source removes its entry from the store. Removing a Jellyfin server also asks the server to revoke that token (best-effort; decision log, 2026-10-19).
- Nothing outside the source adapters reads a connection, and nothing in the Media Core sees one.
- Verified: the Windows store with a real round trip, and the migration and failure paths with a store in memory. The Linux Secret Service path is tested through its command protocol only; no Linux desktop with a running keyring was available, and the Linux container has none, so there the fallback is what ran.
- Still open: Linux desktops without a Secret Service keep connections in the open; on Android the database holds ciphertext rather than a reference.

The diagnostics log, the Android system log, and Reflux's own log file pass everything through `Redaction`: credentials in addresses, query parameters, and headers, a Jellyfin token quoted on its own (`Token="..."`), and the per-file tokens of the SMB stream server's loopback addresses. So does the engine's own verbose log that the developer switch `REFLUX_MPV_LOG` turns on: Reflux takes libmpv's log messages and writes them itself, rather than letting libmpv write a file it cannot redact (libmpv records every option it is given, a Jellyfin stream's authorization among them).

## Metadata provider

- The TMDB credential is an application credential supplied by the build (decision log, "Metadata providers"). Today it is read from the environment; release builds need it injected and not checked in.
- TMDB's terms require attribution in the product: its approved logo and "This product uses the TMDB API but is not endorsed or certified by TMDB.", in an About or Credits section (developer.themoviedb.org FAQ, read 2026-10-19). The About page carries both whenever the provider is in use (DECISIONS.md, 2026-10-19).
- The live service has not been exercised from this repository's tests; the provider is tested against recorded responses.

## Media handling gaps

Known, deliberate, and visible in the library rather than silent:

- **Multi-part files** (`CD1`/`CD2`, `part1`/`part2`) are identified as one work but appear as separate versions instead of playing in sequence. Solving it means a version made of several locations; the version model has one location per version today.
- **Episodes with no show name anywhere in their path** (`S01E01.mkv` at the top of a folder) are grouped under one unnamed show; nothing in the path tells such files apart. Decided for V1 (DECISIONS.md, 2026-10-17): grouped per folder, named after it, and offered to Identify; not built yet.
- **Disc structures** (`BDMV`, `VIDEO_TS`) are recognized and skipped. Playing them means treating a folder as one playable version and handing the engine a disc path.

## Playback over the network

- A server that stops answering without closing its connections (a machine that loses power or its network) plays on from what was buffered; once that runs out, the player shows that it is waiting, and after thirty seconds of waiting it says the source has stopped sending and offers Try again and the way back, on every platform (decision log, 2026-10-16). Where the engine gives up sooner by itself (Media3's read timeout on Android, about a minute after the silence began in the live run, so usually after the thirty seconds), the same message comes then. The desktop behavior is tested against the real engine and a connection held open in a test; it has not yet been rerun by hand against a real server. A server that closes its connections is reported as unreachable.
- On a Jellyfin server, a position Reflux reports near the end of a work is held at 85% of its running time until the playback finishes, so that the server does not count the work watched by its own rule before the viewer has (decision log, 2026-10-17). The server's own clients therefore resume a little earlier there than where the viewer left; Reflux itself resumes at the exact position. A server whose administrator set its maximum resume percentage below 90% can still count a work watched early.

## Platforms and input

- Verified: Windows 11, and Linux on X11 with a compositing manager (Ubuntu 24.04). Wayland has only been reached through XWayland; X11 without a compositor shows the player's controls window opaque.
- Controllers: the readers and everything above them are tested; a session with physical Xbox and PlayStation controllers is still owed (`PhysicalControllerTest` is the script for it).
- Television: verified on the Google TV emulator only. A real television or streaming box is still owed, and with it everything an emulator cannot show: hardware decoding, 4K, HDR and Dolby Vision, an HDMI receiver's audio formats, drives plugged into the device, and a real remote in the hand.
- Media keys are not captured on desktop.

## Third-party notices

The About page lists the third-party software in each build, with its license texts, from `legal/` (DECISIONS.md, 2026-10-19):

- `legal/components.txt`: one line per component (name, license, texts, targets, Maven module patterns, a note), and `legal/licenses/`: the texts, verbatim, taken from the artifacts where they carry them (`META-INF/LICENSE*`), otherwise from the license's canonical source.
- `./gradlew check` runs `checkThirdPartyNotices` for `desktop:app` (its runtime classpath) and `android:app` (its release runtime classpath): it fails, naming the modules, when a module no line covers appears, or when a line's modules have all gone. Native code is not seen by it: Skia and ICU (inside Skiko's runtime), libmpv, and a bundled Java runtime are kept by hand.
- When it fails after a dependency change: read the new module's license from its POM and from the license files in its artifacts, add it to an existing line when it is the same project under the same license, otherwise add a line and, for a license whose text names its copyright holder (MIT, BSD), its own text file. Never infer a license from a project's name.
- Audited 2026-10-19: everything on both runtime classpaths is Apache-2.0 except Bouncy Castle, MBassador, and SLF4J (MIT), SQLite JDBC's original Zentus code (BSD-2-Clause, beside its Apache-2.0), and JNA (Apache-2.0 or LGPL-2.1-or-later); Skiko's desktop runtime adds Skia (BSD-3-Clause) and ICU data (Unicode-3.0). No artifact carries an Apache `NOTICE` file. Media3 uses the platform's decoders, so no FFmpeg is in the Android package.

## Project

- Reflux is proprietary, all rights reserved, and not open source; its public source is for viewing (DECISIONS.md, 2026-10-19). `LICENSE` is the ownership notice, and the About page shows it from there. Third-party components keep their own licenses.
- Reflux is free at launch: no price, subscription, Pro tier, feature gating, or in-app purchase, so store listings declare none (DECISIONS.md, 2026-10-19). Donations are taken on the website once it has a destination for them (LAUNCH.md).

## Legal review

Questions for legal advice before builds go to people outside the project. None is drafted here; each is resolved, or decided as not needed, and recorded in DECISIONS.md:

- End-user terms (an EULA) for the Android, Fire TV, and desktop builds, beyond what Google Play and the Amazon Appstore apply by default.
- The copyright holder's legal name: whether "RSX Develops" is a registered name or the trading name of its owner, and so how the notice names it.
- How the ownership notice reads beside the hosting service's own terms for public repositories (GitHub lets its users view and fork public repositories on GitHub).
- An agreement for outside contributions (ownership, or a license to Reflux), without which none is accepted.
- The bundled libmpv's obligations for a proprietary application: an LGPL build only, with its texts and a source offer (above).
- The CI workflow (`.github/workflows/ci.yml`) runs the full check on Linux and Windows and the window tests on Linux. It has been run in its container locally but never on GitHub, because nothing has been pushed since it was written. Android is not deliberately part of it: the workflow sets up no Android SDK, so whether the Android modules build there depends on the hosted runner image happening to provide `ANDROID_HOME`; no instrumented test, emulator, or release build is run, and the Linux container has no SDK at all.
- The developer CLI is a diagnostic tool and is not distributed.
