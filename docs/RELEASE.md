# Reflux Release Readiness

What stands between the source tree and builds that can be handed to testers and the public, and the checklists each stage must pass. The milestones themselves are in [ROADMAP.md](ROADMAP.md) (M6 release engineering, security, and legal; M7 compatibility, performance, and release proof; M8 to M11 profiles, navigation, the player, and distribution; M12 the qualification campaign and the beta; M13 the release candidate and 1.0); version rules are in [VERSIONING.md](VERSIONING.md); the website, beta program, and store tracks are in [LAUNCH.md](LAUNCH.md).

## Beta entry checklist (`0.9.0-beta.1`)

- [ ] Every V1 item in ROADMAP.md (the product-completeness record and M3 to M7) is Done, or decided out of V1 in DECISIONS.md.
- [x] Reflux's own terms are in the repository: `LICENSE`, proprietary, all rights reserved (DECISIONS.md, 2026-10-19); third-party notices are done (`legal/`, checked by the build, below).
- [ ] The legal review below is done, or each item decided as not needed for the beta.
- [ ] About page with version and build, Reflux's copyright, third-party licenses, TMDB attribution, privacy and support links (done 2026-10-19 apart from the privacy policy and support links, which wait for the website to have them).
- [ ] Android: signed release build from CI; R8 on, keep rules validated by a release build on a device; upgrade from the last alpha keeps library, watch state, sources, and downloads.
- [ ] Fire TV: the developer's devices inspected (model, Fire OS, API level) and the release build run on them.
- [ ] Desktop: Windows installer and Linux package installing libmpv with provenance; a clean-machine install on each.
- [x] TMDB decided for public builds: on, under the working non-commercial classification, formal clarification pending (DECISIONS.md, 2026-11-08); the credential comes from the release workflow's secret. Reconciled with TMDB's answer when it arrives.
- [ ] At least one physical phone, one physical Google TV / Android TV device, one Fire TV device, and one physical controller verified (VERIFICATION.md).
- [ ] CI green on GitHub, including Android.
- [ ] Google Play closed testing track and Amazon Appstore testing set up with the current store requirements read and recorded here.
- [ ] The website meets the ten conditions in ROADMAP.md, M12, "The website before the beta" (HTTPS on a stable domain, tester routing and instructions, known limitations, feedback, privacy and legal, the current version), with the demo video, signup, install instructions, changelog, and support.
- [ ] Feedback path in the app (link and diagnostics export) works on every platform.
- [ ] `CHANGELOG.md` has the beta's entry; the `release: 0.9.0-beta.1` commit and tag exist.

## Release checklist (every build given to anyone outside CI)

- [ ] Built by CI from a clean tree at a tagged commit; version follows VERSIONING.md (no hand-written `versionCode`).
- [ ] Full `check` green (with an Android SDK it includes `checkDebugPlatformApi`, the Java-on-old-Android check); Android unit tests and lint green; instrumented tests run on at least one phone and one television image, and on a device at Android's oldest supported level in practice (a Fire OS device, API 28 or 30).
- [ ] Release build smoke-tested on a device per platform: first run, a folder, a server, playback, resume, a download.
- [ ] Upgrade from the previous build tested (data kept, migrations applied).
- [ ] No credential, token, or test account in the build, its resources, or its logs; the TMDB credential comes from the build environment.
- [ ] Changelog entry written in user terms; known issues listed.
- [ ] Store or download artifacts checksummed and with provenance (`tools/release/release.py`); Windows unsigned by decision, with the download page's notice.
- [ ] VERIFICATION.md has a dated section for the build.

## 1.0 checklist (in addition)

- [ ] The V1 acceptance criteria in ROADMAP.md hold on every V1 target.
- [ ] `1.0.0` is the final release candidate's commit with only the version changed.
- [ ] Store listings final on Google Play and the Amazon Appstore; desktop downloads on the website.
- [ ] Website in its 1.0 state (W4, M13): stable store and download links, final documentation, release notes, support and legal pages current.

## Release system (M6)

One release identity, one tool, and no version typed anywhere but `reflux.version` (VERSIONING.md).

| Piece | What it owns |
| --- | --- |
| `reflux.version` in `gradle.properties` | The product version, the only declared one |
| `./gradlew releaseInfo` (root build) | `build/release/release-info.json`: version, Android `versionCode`, the Android package (`androidPackage`, from `reflux.android.applicationId` in `gradle.properties`), Windows (`0.9.2`) and Linux (`0.9.0~alpha.2`) package versions, channel (alpha, beta, rc, stable, from the stage), commit, and whether the tree was modified. Everything else reads this |
| `tools/release/release.py build` | From a **clean** commit: the Android APK and AAB (release variant, signed), this machine's desktop archive, any `--add`ed archive built elsewhere (refused unless its `release-info.json` names the same commit and an unmodified tree); checks the APK's package (`reflux.android.applicationId`, `dev.rsxdevelops.reflux`), `versionCode`, `versionName`, that it is not debuggable, and its signer; names every artifact `reflux-<version>-<platform>-<arch>.<ext>`; writes `SHA256SUMS`, `manifest.json`, and `provenance.intoto.json` |
| `tools/release/release.py verify <dir>` | Re-hashes every artifact against `SHA256SUMS` and the manifest, re-checks the APK's identity and signature, the zip's integrity, and the manifest's commit; non-zero on any difference |
| `tools/ci/linux-check.sh package` | The Linux archive from the `tools/ci` container, with its `release-info.json` |
| `.github/workflows/release.yml` | The same on GitHub, in two modes. **Rehearsal**, on every push: the release of the pushed commit, its Android packages signed by a throwaway key made in the runner ("not for distribution"), `release.py verify`, GitHub's signed build provenance (`actions/attest-build-provenance`) for every artifact, and `gh attestation verify` of each against this repository, this workflow, and this commit (`--source-digest`); no secret, tag, release, or uploaded app binary. **Release**, by hand on a tag: the same with the upload key from GitHub's encrypted secrets, a **draft** GitHub release (its own job, the only one that may write), and a job on another OS that downloads and verifies it. Publishing the draft is a person's decision after the release checklist |
| `release/published/<version>.json`, `release/stores.json` | What has been published (a copy of the manifest) and where each store listing stands; the website builds its download and release pages from these (WEBSITE.md) |

Artifacts per release:

| Artifact | Built on | Goes to |
| --- | --- | --- |
| `reflux-<version>-android.aab` | Windows (any machine with the Android SDK) | Google Play (phones, tablets, Android TV, Google TV) |
| `reflux-<version>-android.apk` | the same build | Amazon Appstore (Fire TV), internal device installs |
| `reflux-<version>-windows-x64.zip` | Windows | GitHub release, linked from the website |
| `reflux-<version>-linux-x64.tar.gz` | Linux (the CI container) | GitHub release, linked from the website |
| `SHA256SUMS`, `manifest.json`, `provenance.intoto.json` | the release tool | every GitHub release, beside the artifacts |

The manifest names the full commit, the version and every derived number, the channel, when and by what it was built (`local` or `github-actions`), and per artifact its platform, architecture, size, SHA-256, and for the APK its package identity, target SDK, and signer's certificate digest. Locally the provenance statement (in-toto v1, SLSA provenance v1) is unsigned; on GitHub, `attest-build-provenance` signs and stores one per artifact, verifiable with `gh attestation verify <file> -R Rifaque/reflux`. Nothing is published whose origin is not a commit: the tool refuses a modified tree.

## Signing

| Target | Key | Who holds it | Notes |
| --- | --- | --- | --- |
| Google Play | **Upload key** (`reflux-upload`), made by the owner | The owner: one copy offline (an encrypted backup outside the machine), one in GitHub's encrypted secrets for the release workflow (`REFLUX_UPLOAD_KEYSTORE_BASE64`, `REFLUX_UPLOAD_STORE_PASSWORD`, `REFLUX_UPLOAD_KEY_ALIAS`, `REFLUX_UPLOAD_KEY_PASSWORD`) | **Play App Signing**: Google generates and keeps the app-signing key; Reflux only ever holds the upload key. A lost or leaked upload key is reset through Play Console support (a new upload key is registered; the app-signing key, and so every installed copy's updates, are unaffected) |
| Amazon Appstore | The same upload key | As above | Amazon removes the upload signature and signs with a key Amazon generates for the developer account (the same for every app of the account). Fire TV installs from the Appstore update from the Appstore only; no other channel delivers `dev.rsxdevelops.reflux` to those devices, so the stores' different final signatures never meet on one device |
| Internal dry runs | `reflux-dry-run`, made by `tools/release/make-dry-run-key.py` in `~/.reflux-release/` | The developer machine | Never signs anything given to a store or a person. Its certificate says so |
| Windows | **None: unsigned, by decision** (DECISIONS.md, 2026-11-08) | — | SHA-256 and provenance instead; see "Windows: unsigned distribution" |
| Linux | None (no publisher-signing model to match Windows') | — | SHA-256, provenance, and GitHub's release attestation |

Never committed: keystores, passwords, certificates with private keys, signing tokens. The Android build reads signing values only from `REFLUX_SIGNING_*` environment variables or `reflux.signing.*` properties in the user's own `~/.gradle/gradle.properties`; without them a release builds unsigned and the release tool refuses it. Rotating the upload key: generate a new one, register it with Play (Play Console → App integrity → request upload key reset) and with Amazon (contact the Appstore team), replace the four GitHub secrets, and record the date here. The upload key is made before the first build given to testers (the beta, M12), by the owner.

## Android release build

- `release`: R8 (`minifyEnabled`, `proguard-android-optimize.txt` + `android/app/proguard-rules.pro`) and resource shrinking. Reflux's own code uses no reflection or annotation-driven serialization; the keep rules are for smbj and its MBassador event bus (handlers found by annotation) and Bouncy Castle (algorithms found by name), each commented, plus `-dontwarn` for JDK classes the shared desktop code names and Android lacks. No global `-dontobfuscate` or `-dontshrink`.
- Not debuggable (checked by the release tool from the APK itself), `targetSdk` 36 (Google Play's requirement for new apps and updates since 2026-08-31; Android TV's own floor is 34), `minSdk` 26, `compileSdk` 37.
- The TMDB credential reaches every build whose environment supplies `REFLUX_TMDB_TOKEN` (a GitHub secret in the release workflow; DECISIONS.md, 2026-11-08, "TMDB: working non-commercial classification"); it is kept through resource shrinking (`res/raw/keep.xml`). Without it a build makes no lookups and offers no "Online metadata & artwork" switch.
- No cloud backup and no device-to-device transfer of app data (`allowBackup="false"`, `dataExtractionRules` excluding everything).
- Cleartext HTTP stays permitted (DECISIONS.md, 2026-11-08): home media servers are plain HTTP. User-added certificate authorities are not trusted (the platform default for `targetSdk` 24+).
- Exported: the launcher activity and `ArtworkProvider` (read-only artwork by digest name), both reviewed. No WebView, no debug-only code in the production path, release logging redacted.
- Lint on `release`: 0 errors, 16 warnings, reviewed (VERIFICATION.md, M6).

## Desktop artifacts

- `./gradlew :desktop:app:packageDesktopArchive` on the target OS: Compose's distributable (jpackage app image, not minified) with a **pinned Java runtime**, Eclipse Temurin 21 from the Gradle toolchain (never whichever JDK is installed; the first build picked up an end-of-life JDK 22), the JDK modules the app reaches, `LICENSE.txt`, the license texts (`third-party/`), and on Windows libmpv and its source offer. The zip is written with fixed timestamps and sorted entries; the tar.gz with `--sort=name --mtime=1980-01-01`, root ownership, and the executable bits of the launcher and the runtime's tools.
- **Windows**: `reflux-<version>-windows-x64.zip`. Extract anywhere, run `Reflux.exe`. The library lives in `%LOCALAPPDATA%\Reflux`, connections in Credential Manager; removing the folder removes Reflux, and replacing it with a newer zip is an update. No installer yet: a zip needs no elevation and installs nothing the user cannot see.
- **Linux**: `reflux-<version>-linux-x64.tar.gz`, run `bin/Reflux`. Needs the distribution's libmpv (`libmpv2` on Debian and Ubuntu, `mpv-libs` on Fedora). The library lives in `~/.local/share/reflux`. A deb/rpm or Flatpak is later work.

### libmpv on Windows

Reflux builds its Windows libmpv itself (M7): `tools/libmpv` builds mpv 0.42.0 with FFmpeg 9.0.2 and every library it needs, LGPL only, statically into one `libmpv-2.dll`, from the release archives in `tools/libmpv/sources.txt` (each by SHA-256), in a Docker image pinned by digest. The build is reproducible, so `desktop/app/packaging/libmpv-windows.properties` pins the DLL's SHA-256 and the source bundle's. `prepareLibmpv` (part of packaging on Windows) takes the build from `build/libmpv` and refuses anything else; the release tool publishes the source bundle as `reflux-<version>-libmpv-source.tar.xz` beside the Windows zip, names the DLL in the provenance, and `verify` checks that the DLL inside the zip is the pinned one and that its source is published. The `Windows libmpv` workflow rebuilds it whenever its sources change and fails unless the pins are reproduced. Licensing: THIRD-PARTY.md, "libmpv and the LGPL".

To build locally: `docker build -t reflux-libmpv tools/libmpv`, then `docker run --rm -v "$PWD:/src:ro" -v "$PWD/build/libmpv:/out" reflux-libmpv` (about 15 minutes on 20 cores).

### Windows: unsigned distribution

Reflux for Windows is **unsigned by decision** (DECISIONS.md, 2026-11-08). What stands in for a publisher signature: a stable artifact name, the exact commit, SHA-256 in `SHA256SUMS` and on the download page, GitHub's signed provenance, HTTPS delivery from GitHub releases only, and a download page that says plainly what Windows will show. Observed (VERIFICATION.md, M6): `Get-AuthenticodeSignature` reports NotSigned; Microsoft Defender finds nothing; launched with the download mark, SmartScreen intercepts it ("Windows protected your PC") and it does not start until the user chooses to run it; where Smart App Control is on, Windows blocks unsigned unknown apps outright, with no per-app exception, so this build cannot run there. Users are never told to switch either off. Signing is revisited before or around 1.0, when distribution scale justifies a certificate.

### A library from a newer Reflux

The desktop refuses to open a library whose schema is newer than the build's (`LibraryDatabase.open`, `NewerLibraryException`), before anything is written: a zip of an older Reflux run against a newer library says "This library was created by a newer version of Reflux, and this version can't safely open it. Nothing in it was changed. Install the latest Reflux to use it again.", in a system dialog and on standard error, and ends. Android refuses a downgrade itself (the store will not install a lower `versionCode`, and the platform's database helper throws on one).

## Store readiness (M7)

Read 2026-11-08 from the stores' current documentation (sources below); recheck when each track is set up. Supersedes the M6 table. Nothing is submitted; no listing exists.

| | Google Play | Amazon Appstore (Fire TV) |
| --- | --- | --- |
| Package | `dev.rsxdevelops.reflux`, one bundle for phones, tablets, Android TV, Google TV | `dev.rsxdevelops.reflux`, the APK from the same build and `versionCode` |
| Target API | 36 required for new apps and updates since 2026-08-31; Reflux targets 36, `minSdk` 26, `compileSdk` 37 | Amazon recommends 36 for Fire OS 16 and requires each Fire OS's floor (34 on Fire OS 14); 36 meets both |
| Native code | One library (`libandroidx.graphics.path.so`, from Compose), 16 KB-aligned on every ABI and stored uncompressed: meets Play's 16 KB page-size rule | — |
| Bundle and signing | AAB, Play App Signing; uploads signed with the owner's upload key (made before the first build given to a store; the Play and Amazon app entries are created before M9) | The APK, signed with the same upload key; Amazon re-signs |
| Manifest | Launcher and leanback launcher, banner, touchscreen and leanback not required, `READ_MEDIA_VIDEO` (TVs only, explained in the listing), `WRITE_EPG_DATA` (Watch Next), cleartext allowed for home servers, no backup | `android.hardware.touchscreen` not required, which Amazon needs to list an app for Fire TV |
| Exported components | The launcher activity and the read-only artwork provider (`${applicationId}.artwork`) | Same |
| Release build | R8 and resource shrinking, not debuggable, release logging redacted, no TMDB credential, no endpoint of Reflux's own (it calls only the user's servers and plex.tv) | Same |
| Privacy policy | `https://reflux.rsxdevelops.dev/privacy` (live once the site is deployed) | Same |
| Data safety / privacy questions | PRIVACY.md, "Store disclosures": nothing collected or shared | Same |
| Listing | `release/store-listing.md`: text, category, content-rating answers; artwork in `design/brand/store` (icon, feature graphic, TV banner, screenshots) | The same text; 114 and 512 px icons, the 1280 × 720 Fire TV icon, 1920 × 1080 screenshots (3 to 10) |
| Testing track | Closed testing. A **personal** account created after 2023-11-13 must have ≥12 testers opted in for the last 14 days before production access; an **organization** account is exempt. Which the RSX Develops account is: **not readable from this environment** (owner action, M8 entry checklist) | Live App Testing: testers are invited by e-mail and install from the Appstore on their device; they must allow the Appstore's promotional e-mails |
| Owner actions | Create the app as `dev.rsxdevelops.reflux` in Play Console; read the account type; make the upload key; set the support e-mail | Create the app in the Amazon developer console; set the support e-mail |

### Release preparation (M8)

DECISIONS.md, 2026-11-11 (O-17): the Google Play and Amazon app entries are created before M9, alongside M8, so that Play's testing window (twelve testers for fourteen days, for a personal account) and the TV and Watch Next reviews can start as early as practical. They are made in the stores' consoles with the owner's accounts, so nothing in the repository can make them; M8 prepared everything up to that step.

| Owner action | Where | State (2026-11-11) | What it needs from the repository |
| --- | --- | --- | --- |
| Create the app `dev.rsxdevelops.reflux` in Play Console, and read whether the developer account is **personal** or **organization** | Play Console → Create app | **Owner action, open** | Nothing more: the package name, the listing text (`release/store-listing.md`), and the artwork (`design/brand/store/`) are ready |
| Create the app in the Amazon developer console, for Fire TV on Fire OS only (no Vega OS devices) | Amazon developer console → Add a new app | **Owner action, open** | The same listing and the 1280 × 720 Fire TV icon |
| Make the upload key, keep one copy offline, put it in GitHub's secrets (`REFLUX_UPLOAD_*`) | The owner's machine, GitHub settings | **Owner action**, due before the first build given to a store (the beta, M12) | "Signing", above |
| Verify the website in Google Search Console (DNS TXT at Cloudflare) and submit the sitemap | Search Console, Cloudflare | **Owner action, open** | Nothing: the sitemap is at `https://reflux.rsxdevelops.dev/sitemap.xml` (WEBSITE.md, "Search") |
| Two plex.tv accounts (or their tokens) that can reach a Plex server, for the two-account live test | `~/.reflux-live/plex.env` (`REFLUX_PLEX_URL`, `REFLUX_PLEX_TOKEN_A`, `REFLUX_PLEX_TOKEN_B`) | **Owner action, open**; the test is written and skips until then (VERIFICATION.md, M8) | `PlexTwoAccountsLiveTest` |

When each is done, its row says so, with the date; the Play account type decides whether the beta's closed test needs the twelve-tester period (Store readiness, above).

### Android developer verification (M7)

- Verification is per package name, tied to a developer account and to the signing keys of that package. Enforcement began 2026-09-30 in Brazil, Indonesia, Singapore, and Thailand; global in 2027.
- **Google Play distribution (Reflux's path):** Play registers Play packages; apps created in Play Console since 2026-03-30 are registered to the account when they are created, and every Play package must be registered. So creating `dev.rsxdevelops.reflux` in Play Console registers it; the Play Console's own identity verification of the account must be complete (the owner checks its status on the Android developer verification page).
- **Outside Play:** Reflux offers no Android file outside the stores (the website never links one), so no Android Developer Console registration is needed now. If that changes, `dev.rsxdevelops.reflux` is registered there with the upload-key certificate before any APK is offered.
- **Fire TV** runs Fire OS, which is not Google-certified Android and is outside the scheme; Amazon verifies its own developer accounts.
- `dev.reflux`, the development identity, is never registered.

Sources (read 2026-11-08): Google Play Console Help, "Registering Play package names" and "Registering Android package names"; Android Developers, "Register on Android Developer Console"; Play Console Help on testing requirements for new personal accounts; Amazon developer docs, "Developing for Fire TV devices running Fire OS 16", "Device filtering and compatibility", "Step 3: Add Appstore details", and Live App Testing's "Prepare app testers".

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

- A libmpv build to ship on Windows, with a known provenance and configuration: built LGPL (no GPL-only components), with the decoders and hardware paths Reflux relies on. Done in M7: Reflux's own reproducible build ("libmpv on Windows" above). The development DLL in `.reflux/native/` is any libmpv a developer puts there and is never packaged.
- On Linux, either a dependency on the distribution's libmpv (deb/rpm) or a bundled copy (Flatpak/AppImage). The versions differ between distributions; the binding only uses API that every libmpv 2.x has.
- LGPL obligations for the bundled library and its dependencies (FFmpeg, libass, libplacebo): license texts, source offer, and dynamic linking, which is already how it is loaded. Reflux is proprietary, so the bundled build must be an LGPL one, with no GPL parts in mpv or FFmpeg (DECISIONS.md, 2026-10-19). The About page lists libmpv today as the copy on the user's computer, not included; a bundled build changes its line in `legal/components.txt` (its license as built, the LGPL text, where its source is offered).
- A packaged desktop build that carries its own Java runtime adds that runtime to the notices (OpenJDK is GPL-2.0 with the Classpath Exception); the notice check only sees Maven modules.
- An installer or package per platform, and code signing on Windows. Done as archives in M6 ("Desktop artifacts"); Windows is unsigned by decision.
- On X11 the engine changes process-wide state (decision log, 2026-10-12); a bundled build is a chance to patch that at the source.

## Credentials

On Android the app allows plain-HTTP connections, because home media servers are usually `http://`: decided to stay (DECISIONS.md, 2026-11-08), with user-added certificate authorities not trusted. Reviewed for the Android client so far: no storage permission is requested (folders are granted one at a time through the system picker, read-only, and the grant is given back when a source is removed); backups of app data are switched off, and since M6 device-to-device transfer too (`dataExtractionRules`), so the library database with its source credentials does not leave the device that way; problem reports are redacted before they reach the system log or the log file; the exported components are the launcher activity (which Watch Next programs also open, by an explicit intent naming a work) and `ArtworkProvider`, which lends the launcher read-only artwork from the cache by digest names and nothing else (checked on the television emulator: a cache file is served to another application; `..` paths and other files are not found); nothing in the production path is debug-only. Still open: credentials in the database (below), and the media session being available to any media controller on the device, which is the platform's default.

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

- The TMDB credential is an application credential supplied by the build (decision log, "Metadata providers"): `REFLUX_TMDB_TOKEN` in the build environment, the release workflow's secret of that name. Working classification: non-commercial under TMDB's published definition; formal clarification request pending (DECISIONS.md, 2026-11-08). Owner action: add the secret to the repository before the first beta build.
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
- Media keys on desktop are read on Windows while Reflux has focus (M10); Linux, and the system's own transport controls while Reflux is not the focused window (Windows SMTC, Linux MPRIS), are V1.x.

## Third-party notices

The About page lists the third-party software in each build, with its license texts, from `legal/` (DECISIONS.md, 2026-10-19):

- `legal/components.txt`: one line per component (name, license, texts, targets, Maven module patterns, a note), and `legal/licenses/`: the texts, verbatim, taken from the artifacts where they carry them (`META-INF/LICENSE*`), otherwise from the license's canonical source.
- `legal/credits.txt`: the readable credits About shows under "Credits and technology" (name, role, targets, a note), generated into the app the same way. Names and roles, never logos; a service Reflux only connects to (Jellyfin, Plex) says it is its owner's and that Reflux is independent of it (DECISIONS.md, 2026-11-12). It is not a license notice and is not checked against the classpath.
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
- TMDB (METADATA.md, "Provider and licensing"): its API licence is non-commercial and commercial use needs a written agreement; whether a free application supported by voluntary donations is commercial must be confirmed with TMDB in writing before release. Also: its six-month limit on keeping its data, for a device that stays offline longer; which attribution wording applies (the terms' or the FAQ's); and shipping an application key in a client, which the terms do not address.

**M10 qualification still owed to a release (2026-11-13):** (the predictive back gesture on hardware passed on the Phone 2a, Android 16) picture-in-picture on a second Android version, a held seek on a physical remote, the Windows corners at 100, 150, and 200%, and the hosted `check`.

**Classification of what M10 leaves (2026-11-13).** Physically unqualified: the predictive back gesture (a human on a device), a second Android version for the picture-in-picture rule, a held seek on a physical remote, the Windows corners at 100, 150, and 200%, the 2560 x 1440 fullscreen chrome walk, TalkBack in the final pass, and hosted CI. Known intermittent: `AboutUiTest.aKeyboardGoesInAndComesBackToWhereItWas` (since M8). Deferred to V1.x: Linux media keys and corners.
