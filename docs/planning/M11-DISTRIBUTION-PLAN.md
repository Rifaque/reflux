# M11 — Distribution & Release Surfaces: plan

**Status: authoritative for M11.** Written 2026-11-13, after M10 (`43f8f5b`, `dbb4840`), as a documentation-only planning pass: no application code, build script, workflow, package ID, or version was changed to write it. It replaces the M11 scope in `M8-M13-REBASELINE.md` (section 8, "M11"; sections 3.6 to 3.8) where the two differ, and says where they differ (section 2). The roadmap entry is `ROADMAP.md`, "M11"; the durable decisions are in `DECISIONS.md`, 2026-11-13.

**The sentence M11 exists for:** a hypothetical `0.9.0-beta.1` can be distributed through every intended channel, from one release identity, without any new distribution infrastructure having to be built. M11 makes what already exists *true and connected*; it does not build another release system.

**Read first (the implementer's list):** `AGENTS.md`; `docs/VERSIONING.md` (the protocol and the rules for AI agents: no version change, no tag, no push unless asked); `docs/RELEASE.md` ("Release system", "Signing", "Store readiness (M7)", "Release preparation (M8)"); `docs/LAUNCH.md`; `docs/WEBSITE.md` ("Release data", "Deployment"); `release/README.md`, `release/stores.json`, `release/store-listing.md`; `tools/release/release.py`; `.github/workflows/release.yml`; `docs/PRIVACY.md` ("Store disclosures"); this document.

---

## 0. Boundaries

| | Owns | Does not own |
| --- | --- | --- |
| **M11** | One release identity; every channel's account, listing, track, and metadata *set up and rehearsed*; verified artifacts; a draft release; the website's release surface; an end-to-end rehearsal with no public release | New packaging formats, new signing, new release tooling beyond closing the gaps in section 3, any tester recruitment or beta operation, any public release |
| **M12** | The qualification campaign on a frozen candidate, then `0.9.0-beta.N` with testers: the 12-tester / 14-day Play period (if the account needs it), Amazon Live App Testing with real testers, desktop downloads from the site, one prompt per beta build cycle | Building distribution |
| **M13** | `0.9.0-rc.N`, store submissions for review, the Windows signing decision's implementation if chosen, `1.0.0` | — |

**Non-goals, stated so they are not rediscovered:** an installer (section 2), Debian/RPM/Flatpak/Snap/AppImage packages, an auto-updater, a Reflux backend, casting, Vega OS, iOS, a Microsoft Store listing, signing Windows builds, publishing anything. Nothing in M11 is public.

**Exit gate (M11):** the rehearsal in section 6 passes on a clean commit, every row of section 7's evidence list is recorded in `VERIFICATION.md`, and the owner-action table (section 8) has no open item that blocks a `0.9.0-beta.1` hand-off. Items that need the owner's console, account, or hardware are *prepared and listed*, not claimed done.

---

## 1. Starting state (what exists; M6/M7/M8 built it)

Preserve it. Everything below was read from the repository on 2026-11-13.

- **One version, one place.** `reflux.version` in `gradle.properties` (`0.9.0-alpha.2`); `./gradlew releaseInfo` derives `release-info.json` (version, Android `versionCode` 90002, the Android package, Windows `0.9.2` and Linux `0.9.0~alpha.2` package versions, channel, commit, whether the tree was modified). Nothing else declares a version (`VERSIONING.md`).
- **One tool.** `tools/release/release.py` has two commands, `build` (refuses anything but a clean commit; builds or accepts the APK, AAB, and the desktop archives; names artifacts `reflux-<version>-<platform>-<arch>.<ext>`; writes `SHA256SUMS`, `manifest.json`, `provenance.intoto.json`) and `verify` (re-hashes, re-checks the APK's package, `versionCode`, not-debuggable, and signer, the zip's integrity, the manifest's commit).
- **One workflow.** `.github/workflows/release.yml` in two modes. *Rehearsal*, on every push: a throwaway key, `release.py verify`, GitHub artifact attestations (`actions/attest-build-provenance`) and `gh attestation verify` for every artifact, no secret, tag, release, or uploaded app binary. *Release*, by hand on a tag: the upload key from GitHub's encrypted secrets, the same attestations, then a job `draft` that creates a **draft** GitHub release (`gh release create --draft --verify-tag`) attaching the `*.apk`, `*.aab`, `*.zip`, `*.tar.gz`, `*.tar.xz`, `SHA256SUMS`, `manifest.json`, and `provenance.intoto.json`, then a job on another OS that downloads the draft and verifies checksums, attestations (`--signer-workflow`), and the manifest. Publishing the draft and uploading to stores are people's decisions.
- **Artifacts:** `…-android.aab` (Play), `…-android.apk` (Amazon, internal installs), `…-windows-x64.zip`, `…-linux-x64.tar.gz`, `…-libmpv-source.tar.xz` (the LGPL source offer for the bundled Windows libmpv), and the three records.
- **Keys:** a Play *upload key* (`reflux-upload`, owner-made, not yet made as of the last record), Play App Signing for the app-signing key, Amazon re-signing, a dry-run key (`reflux-dry-run`, `~/.reflux-release/`) for rehearsals. Windows and Linux: unsigned, by decision (`DECISIONS.md`, 2026-11-08).
- **Records the website reads:** `release/published/<version>.json` (a copy of the manifest, **added by hand** in a follow-up commit once the assets are on the release), `release/stores.json` (both stores `"state": "not listed"`, `url: null`), `gradle.properties`, `CHANGELOG.md`. `website/scripts/release-data.mjs` builds the download and release pages from them and fails the build on a manifest without a commit or checksum or a store record for another package. Android files are never linked from the site (`check-dist.mjs` fails on an `.apk`/`.aab` link).
- **Listing material:** `release/store-listing.md` (identity, short and full description, content-rating answers, data-safety and privacy answers); `design/brand/store/` (Play icon 512, feature graphic 1024 × 500, TV banner 1280 × 720, Amazon icons 114 and 512, Fire TV icon and background, screenshots); `docs/PRIVACY.md`, "Store disclosures (prepared, not submitted)".
- **Android build facts:** `applicationId` `dev.rsxdevelops.reflux` (declared once as `reflux.android.applicationId`), `targetSdk` 36, `minSdk` 26, `compileSdk` 37, R8, not debuggable in release, one native library (Compose's) 16 KB-aligned, no cloud backup, `touchscreen` and `leanback` not required, one app for phones, tablets, Android TV, Google TV, and (as an APK) Fire TV.
- **Website:** live at reflux.rsxdevelops.dev (Vercel, Cloudflare DNS), with install, tester, feedback, changelog, privacy, support, and legal pages (M7), technical SEO and responsive checks (M8).
- **Owner state the repository cannot read:** whether the Play Console account is personal or organization, whether the Play and Amazon apps have been created (O-17 asked for it before M9: `RELEASE.md` records it as an open owner action), whether the upload key exists, Android developer verification status of the account, the support e-mail. M11 begins by *recording* each (section 8).

---

## 2. What changed from the 2026-11-11 plan, and why (the critique)

The rebaseline's M11 had seven parts: a branded Windows installer (Inno Setup), first-party Linux packages (AppImage, `.deb`, `.rpm`, a PKGBUILD) with a Linux libmpv build, the metadata provider gate (TMDB or Trakt), a large-library baseline and stress harness, the website's final content, the demo video, and beta operations. It listed "Inno Setup for Windows" and "first-party Linux formats only" under *Confirmed unchanged*. The owner's brief of 2026-11-13 narrows M11 deliberately: **M11 must not become another release-engineering rebuild.** What the critique found:

1. **Two unproven packaging stacks would have been built before anyone had installed Reflux from the channels that already exist.** An Inno Setup installer and four Linux formats are new infrastructure with their own signing, verification, and upgrade questions, added before a single tester has used the ZIP and tarball the release system already produces and verifies. The 2026-11-08 decision ("archives with a pinned runtime, not installers yet") stands until the beta says otherwise.
2. **The installer's real problem was never packaging; it was signing.** An unsigned installer meets the same SmartScreen and Smart App Control wall as the ZIP (M11.5). Building it first and deciding signing later inverts the dependency. The decision gate in M11.5 puts the question where its answer is needed.
3. **The store channels were the unbuilt half.** Google Play and the Amazon Appstore are two of the five V1 channels; both are still `not listed`, and each has a calendar clock (the closed-testing period, TV review, Amazon review) that no amount of engineering shortens. They are M11's centre of gravity.
4. **The release chain has hand-typed seams** (section 3): the published record is copied by hand, the store record is typed by hand, and the debug installs share the permanent package identity. One real inconsistency (the package identity, section 4.2) would have surfaced only at the first Play upload.

**Disposition of every item the older plan gave M11** (nothing is dropped silently):

| Older M11 item | Disposition |
| --- | --- |
| Windows installer (Inno Setup), signed per O-9 | **Not in M11.** The ZIP stays. The installer and signing are one decision, **gate G-WIN** (M11.5), made before broad 1.0 distribution; if it chooses an installer, it is M13 work with its own plan. The `RELEASE.md` "Desktop artifacts" note ("No installer yet") stays true |
| Linux: bundled libmpv build, AppImage, `.deb`, `.rpm`, PKGBUILD, `.desktop`/AppStream | **V1.x**, unless the beta shows the tarball plus the distribution's libmpv cannot serve Tier 1 users. No existing decision requires them: `DECISIONS.md` (2026-11-08) chose archives, and the 2026-11-11 entry names packages as a *need* without deciding their format or timing. M11 documents the tarball honestly (M11.6) |
| Metadata provider gate (TMDB working classification vs Trakt) | **Carried gate, decided by the owner before M12 entry, not built in M11** (section 3, D5). It decides what the store listings say about metadata; building `metadata:trakt` is outside M11's scope and would need an explicit scope change |
| Large-library baseline and seeded stress harness | **M12**, the campaign's large-library wave (`ROADMAP.md`, M12): it measures a candidate; it does not distribute one |
| Website's final content | **M11.7** for the release surface; product renders regenerated from the final interface stay in M11.7's scope because the interface is now final (M10 closed) |
| Demo video | **M11.7**, *owner-tool-gated content*: the shot list and captures are M11's; production waits for the owner's tool (`LAUNCH.md`, W2). It remains one of the ten website conditions for the beta (`ROADMAP.md`, M12) |
| Beta operations ready | **Split.** The channels, tester routes, and templates are *set up* in M11.2, M11.3, M11.7; *operating* them (recruiting, opt-in, triage) is M12 |
| `release.py verify` covering an installer; clean-machine installer test | Replaced by: clean-machine install, upgrade, and removal of the **ZIP and tarball** (M11.5, M11.6, M11.8) |

---

## 3. Cross-cutting: one release identity, and the decisions it needs

**The chain (M11.1):**

```
commit  →  version  →  tag  →  release-info  →  artifacts  →  SHA256SUMS + manifest + provenance
                                                          →  GitHub release (draft → published)
                                                          →  website (release data) and store listings
```

Each arrow is a tool or a workflow step, never a person typing a value. Today four arrows end in a person: (a) `release/published/<version>.json` is copied in by hand; (b) `release/stores.json` is typed; (c) the store listings' version-facing fields (release notes, "what's new") are typed in consoles; (d) the `release: <version>` commit and tag are made by a person on request (correct by `VERSIONING.md` and kept). M11.1 turns (a) and (b) into checked steps and defines (c) as copy from `CHANGELOG.md` with a diff check.

**Decisions M11 needs, each with a recommendation.** They are *open* until the owner confirms; the ones the owner has already decided are in `DECISIONS.md`, 2026-11-13.

| # | Decision | Recommendation | Needed by |
| --- | --- | --- | --- |
| **D1** | **Android files in the public GitHub release.** The workflow's `draft` job attaches `*.apk` and `*.aab` beside the desktop archives, so publishing the draft publishes them. `RELEASE.md` says Reflux offers no Android file outside the stores (so no Android Developer Console registration is needed): publishing them contradicts that | **Store-only Android.** The public release carries the desktop archives, the libmpv source, and the three records; the AAB and APK stay workflow artifacts (attested, listed with hashes in the manifest) and reach the stores from there. If the owner prefers public APKs, register `dev.rsxdevelops.reflux` in the Android Developer Console with the upload and Play app-signing certificates first (4.2), and rewrite the `RELEASE.md` statement | M11.4, before the first draft is published |
| **D2** | **Debug builds' identity** (section 4.2): they install as the permanent ID today | Give debug builds `applicationIdSuffix ".debug"` (a separate app on the device) and keep side-by-side scratch IDs for tests; audit and clear existing installs before the first store install | M11.2 step 0 |
| **D3** | **GitHub immutable releases** | Adopt **if** the repository supports it cleanly at M11 (verify general availability and plan limits then; it was announced as public preview on 2025-08-26, and its status in 2026 was not verified here). The existing draft-then-verify flow already fits: assets are attached before publishing, and after publication assets and the tag cannot change | M11.4 |
| **D4** | **Windows signing / Microsoft Store gate G-WIN** (M11.5) | ZIP through the beta; decision recorded before broad 1.0 distribution | Owner eligibility check by M12 midpoint; decision by M13 start |
| **D5** | **Metadata provider gate** (TMDB working classification, clarification pending since 2026-11-08; Trakt as an alternative, a verified GitHub account needed before 2027-01-01) | Owner decides before M12 entry: if TMDB confirms non-commercial use, ship TMDB; if it says commercial or stays silent and the owner wants the risk gone, the owner opens a scope change for `metadata:trakt` (not M11). The store disclosures (`PRIVACY.md`) follow the answer | Before M12 entry |
| **D6** | **Linux support statement** for the tarball (M11.6) | State the libmpv requirement and the verified distribution plainly; no packaging | M11.6 |
| **D7** | **Publication record by tool** | Add a checked step to `release.py` (or the workflow) that writes `release/published/<version>.json` verbatim from the verified draft's manifest and refuses a mismatch; no new infrastructure, one more verb | M11.1 |

---

## 4. Workstreams

### M11.1 Distribution identity and release contract

**Goal.** A person can answer "what is the version, commit, and checksum of what was given to whom?" from one chain, and nothing is typed twice.

**Work.**

- Write the chain (above) into `RELEASE.md` as the release contract, and the rule that a published record is *never edited* (it already says so for `release/published`).
- D7: the publication-record step (generated, verified against the draft's manifest and the GitHub release's asset digests).
- `release/stores.json`: keep it hand-maintained (a store's state is a fact only a person reads from a console) but make `release-data.mjs`'s existing check (package equality) also check `state` against the schema and that a non-`not listed` state has a `url`; add the Play and Amazon *testing* routes' public descriptions here when they exist.
- Store "what's new" text is generated from the `CHANGELOG.md` section of the version and diffed in the rehearsal.
- Keep the rules of `VERSIONING.md`: one `release: <version>` commit changing only the version and changelog heading; annotated tag `v<version>`; never rebuild a released version with different contents.

**Gate.** `release.py verify` on a rehearsal's artifacts; the record step refuses a tampered manifest; the website build fails on a record that disagrees with the draft.

### M11.2 Google Play

M11 is **setup and rehearsal**. The 12-tester / 14-day campaign is M12's.

**Step 0: the package-identity audit (section 4.2, before anything else).**

**Setup list (owner console actions; the repository prepares everything it can):**

| Item | State / what is needed |
| --- | --- |
| Play Console account: type (personal or organization) and **Android developer verification** of the account complete | Unknown to the repository; **record it first**. It decides whether the closed-test requirement applies (an organization account is exempt) |
| App creation as `dev.rsxdevelops.reflux` | Owner action (O-17). Per `RELEASE.md` (read 2026-11-08), apps created in Play Console since 2026-03-30 are registered to the account at creation, which is *the* registration for developer verification. Confirm on the console's verification page after creation; do not assume |
| **Play App Signing** | Default for new bundles: Google holds the app-signing key; Reflux holds only the upload key. Make `reflux-upload` (owner), keep one offline copy, put the four `REFLUX_UPLOAD_*` secrets in GitHub. A lost upload key is reset through Play Console support without affecting installed copies |
| **AAB upload path** | The release workflow's AAB, signed with the upload key; first upload to the **internal testing** track as the rehearsal |
| **Target API** | Play requires new apps and updates to target API 36 (in force since 2026-08-31); Reflux targets 36. Android TV's own floor is 34 (Google's published exemption); one bundle serves both, so the handheld rule governs |
| TV and 64-bit/16 KB rules | Google's TV quality page states that, from 2026-08-01, TV apps must support 64-bit and meet the 16 KB page-size requirement; Reflux's one native library is 16 KB-aligned on every ABI. **Verify at M11 that the AAB contains an arm64 variant** (the stick's 32-bit userland needs armeabi-v7a too) |
| **Store listing** | `release/store-listing.md` and `design/brand/store/` (icon 512, feature graphic 1024 × 500, TV banner 1280 × 720, screenshots). Confirm current screenshot dimensions in the console before exporting (sources disagreed: 1280 × 720 vs 1920 × 1080 minimums; the console shows the live requirement) |
| **App content declarations** | Privacy policy URL (`https://reflux.rsxdevelops.dev/privacy`), ads (none), app access (below), content rating (IARC questionnaire; answers drafted), target audience, **Data safety** (answers drafted in `PRIVACY.md`: nothing collected; the TMDB identification question is the conservative-answer item the legal review settles and D5 affects), news/health/financial/government declarations (not applicable), the `READ_MEDIA_VIDEO` declaration (TVs only; explained in the listing), `WRITE_EPG_DATA` (Watch Next) |
| **Reviewer access information** | Reflux has no account and is empty without a source. A reviewer (and Play's TV review) needs media: provide, in App access, instructions plus a reachable test source (a Jellyfin or WebDAV server serving open films such as the Blender open movies, kept up for the review window). The owner provides and maintains it. This is a content-and-hosting task, not a feature: Reflux does not gain a demo mode |
| **Android TV / Google TV** | The TV form factor on the same listing: Leanback launcher intent, a 320 × 180 banner naming the app, a 160 × 160 icon, five-way D-pad operation, touchscreen not required, D-pad centre pauses and resumes, at least one TV screenshot, the 1280 × 720 TV banner asset; the TV quality checklist run once on the Google TV emulator and (owner hardware) a physical device; Watch Next certification requested at M13 |
| **Closed testing configuration** | Create the track, the tester list mechanism (an e-mail list or Google Group), and the opt-in link; rehearse with the owner's own accounts. **Do not count the owner's rehearsal as the campaign.** If the account is personal and created after 2023-11-13, production access requires at least 12 testers opted in continuously for 14 days (Google's published rule); the clock is an M12 resource, so M11 decides *when to start it* (the owner may start it early to save calendar time; that is a scheduling choice, not M11 scope) |

**Gate (M11).** An AAB signed with the upload key accepted by the internal testing track and installable on the Phone 2a and the Google TV emulator from the Play track; Play Console's pre-launch report read and recorded; every App content form either completed (owner) or marked with its blocker; the verification page shows `dev.rsxdevelops.reflux` registered to the account (owner reads and records it).

### M11.3 Amazon Appstore (Fire OS only)

**Fire OS and Vega OS stay separate.** Reflux V1 targets Fire OS (Fire TV Stick 4K Plus, Fire OS 8, API 30, 32-bit userland, verified). Vega OS devices run no Android applications and are never targeted; Vega is a future compatibility track (`ROADMAP.md`, Platforms). Fire OS is outside Google's developer-verification scheme; Amazon verifies its own developer accounts.

| Item | Plan |
| --- | --- |
| App entry | Owner action (O-17): Amazon developer console → Add a new app, Fire TV on Fire OS; package `dev.rsxdevelops.reflux`, the same `versionCode` as the Play bundle |
| Package and signing | The APK from the same build as the AAB, signed with the upload key; Amazon removes that signature and re-signs with a key Amazon holds for the account. No other channel delivers this package to Fire TV, so the stores' signatures never meet on one device |
| Device filtering | Fire OS 7 and later qualify (`minSdk` 26); target Fire TV device families explicitly; never Vega. The manifest must not require a touchscreen (done) and a Fire TV app must not request the camera permission (Reflux does not) |
| Metadata and assets | `release/store-listing.md`; Amazon icons 114 and 512; the 1280 × 720 Fire TV icon; Fire TV background 1920 × 1080; screenshots 1920 × 1080 (3 to 10) |
| **Privacy questionnaire** | The User Data Privacy Questionnaire generates the privacy labels, and Amazon's stated rule is that a new app or an update cannot be published while it is incomplete or has unaddressed issues. Answers drafted in `PRIVACY.md` (nothing collected or shared; the TMDB question follows D5); declare third-party code's data collection as the questionnaire asks |
| Content rating | Amazon's own 13-field questionnaire (separate from IARC); drafted answers in `store-listing.md` |
| **Live App Testing (LAT)** | LAT is the beta path (M12): testers are invited by e-mail and install from the Appstore; several fields are unavailable in the LAT flow (long description, keywords, support contact, privacy among them) and can be completed later. M11 sets up one LAT test with the owner's Fire TV and rehearses an install and an update |
| **Appstore Beta Hub** | The tester-side app on Fire OS 7+ (and Vega) that redeems LAT invitations and installs beta apps; it syncs with the Amazon account of the device. Tester guidance for the site: install Beta Hub, accept the invitation, sync (Settings → Account & Profile Settings → Sync Amazon Content) if an expected update does not appear, and uninstall a differently signed build first (the "App already installed" error). A sideloaded debug build on the same device blocks the Appstore install: the same package-identity hazard as 4.2 |
| Submission and review checklist | Amazon's presubmission checklist (app preparation, user data privacy, a physical-device test, install location); the content policy; Fire TV D-pad and remote checks (menu, rewind/fast-forward keys); the test criteria; the launcher icon taken from the listing (the first Appstore build confirms the generic-tile observation, `LAUNCH.md`) |

**Gate (M11).** One LAT build installed from the Appstore on the Fire TV Stick, upgraded by a second LAT build, and removed; the privacy and content questionnaires completed or marked with their blocker.

### M11.4 GitHub distribution

GitHub Releases are the **canonical desktop release surface.** The existing workflow and tool stay; M11 closes the seams (D1, D3, D7).

- **Release shape.** Tag `v<version>` on the `release: <version>` commit (annotated, by a person) → run the release workflow by hand on the tag → the verified **draft** → publication by a person.
- **Assets of the public release (per D1's recommendation):** `reflux-<version>-windows-x64.zip`, `reflux-<version>-linux-x64.tar.gz`, `reflux-<version>-libmpv-source.tar.xz` (the LGPL source offer for the Windows libmpv, published beside the Windows ZIP before the first public download), `SHA256SUMS`, `manifest.json`, `provenance.intoto.json`. The AAB and APK are built and attested by the same run and reach the stores from the workflow's artifacts.
- **Release notes.** The version's `CHANGELOG.md` section in user terms, the known limitations, the install pointers (the site's pages), the checksum and attestation verification commands.
- **Artifact verification.** `release.py verify <dir>`; `sha256sum -c SHA256SUMS`; `gh attestation verify <file> -R Rifaque/reflux --signer-workflow Rifaque/reflux/.github/workflows/release.yml` for every artifact (artifact attestations: Microsoft and GitHub document the verify command; the CLI marks the feature beta, so record the CLI version used); with immutable releases enabled (D3), `gh release verify <tag>` and `gh release verify-asset` as well.
- **Immutable releases (D3).** Optional; adopt only if the repository supports it at the time and the plan allows artifact attestations (attestations need a public repository on the free, Pro, and Team plans). Never enable it in a way that blocks the draft-verify-publish order: once published, assets and tag cannot change, which is the property `VERSIONING.md` already demands ("never rebuild a released version").
- **Not in scope:** auto-update. A manual "Check for updates" (O-10) is not decided here; if added later it is disclosed in `PRIVACY.md` first.

**Gate.** A rehearsal run produces a draft from a clean tag-shaped commit whose independent verification job passes; the publication-record step (D7) writes the record and the website build consumes it.

### M11.5 Windows

- **Preserve the ZIP.** `reflux-<version>-windows-x64.zip`: extract anywhere, run `Reflux.exe`; library in `%LOCALAPPDATA%\Reflux`, connections in Credential Manager; removing the folder removes Reflux; a newer zip over it is an update; pinned Temurin 21 runtime; Reflux's own LGPL libmpv with its source offer.
- **Unsigned, by decision** (`DECISIONS.md`, 2026-11-08): SHA-256, provenance, HTTPS from GitHub only, and a download page that says plainly what Windows will show (SmartScreen's "Windows protected your PC"; Smart App Control blocks unsigned unknown apps with no per-app exception). Observed in M6 and unchanged.
- **Gate G-WIN (the deliberate decision).**
  - **M12 beta may continue with the direct GitHub ZIP.** Testers are told, on the download page and in the beta instructions, what Windows will show.
  - **Before broad 1.0 public distribution, the owner decides** between (a) *trusted direct signing* of the ZIP's executables and libraries (and an installer only if the signing makes it worth it), and (b) *Microsoft Store distribution* (a Store-signed package), or (c) knowingly staying unsigned for 1.0 (recorded with the reason).
  - **Facts to bring to the gate (read 2026-11-13, to be rechecked when it opens):** Microsoft's code-signing options page lists Azure Artifact Signing as available to organizations in the US, Canada, the EU, and the UK, with individual developers currently limited to the US and Canada; Microsoft says Artifact Signing does not give instant SmartScreen trust and that per-file reputation starts at zero for each new build; EV certificates no longer bypass SmartScreen; Smart App Control blocks unsigned files without positive reputation and (a Microsoft-linked note from January 2026) mishandles ECC-signed binaries, so an RSA certificate is the safer choice; community reports from April 2026 describe a reputation regression after Artifact Signing's intermediate CAs changed on 2026-03-26 (unconfirmed by Microsoft here). Microsoft Store account requirements, fees, packaging (MSIX), and certification were **not researched in this pass**; the gate reads them when it opens.
  - **Timing.** The owner's eligibility check (entity, country, account) is an M12-midpoint owner action, because certification and identity verification have lead time; the decision is recorded by M13's start, so any work it needs fits M13 before the final candidate.
- **M11 work:** the clean-machine install, upgrade (zip over zip), and removal rehearsal in Windows Sandbox (M11.8); the download page's wording reviewed against the observed warning; nothing about signing implemented.

### M11.6 Linux

- **Preserve the x64 tarball** `reflux-<version>-linux-x64.tar.gz` (`--sort=name --mtime=1980-01-01`, root ownership, launcher and runtime executable bits preserved).
- **Install and run.** Extract anywhere (for example `~/Applications/`), run `bin/Reflux`.
- **libmpv requirement.** Linux uses the **distribution's libmpv**, loaded by name at run time: `libmpv2` on Debian and Ubuntu (from Debian 12 and Ubuntu 24.04), `mpv-libs` on Fedora. **Ubuntu 22.04 does not ship `libmpv2`** (it has `libmpv1`), so the tarball does not support it without a newer libmpv installed by the user; the site says so rather than hiding it. Because Reflux bundles no libmpv on Linux, no libmpv source offer applies there. Verified distribution as of M7: Ubuntu 24.04 on X11 with a compositing manager; Wayland has been reached through XWayland only.
- **Credentials.** Where a Secret Service is running (GNOME Keyring, KWallet, KeePassXC) connections are stored there through `secret-tool` (`libsecret-tools` on Debian/Ubuntu, `libsecret` on Fedora); without one, connections stay in the library database and the log says so. The install page states it.
- **Update and replacement.** Extract the newer tarball over a fresh directory and remove the old one; the library (`~/.local/share/reflux`) is untouched. A library from a newer Reflux is refused by an older one with a plain message (M6).
- **Removal.** Delete the extracted directory; delete `~/.local/share/reflux` to remove the library (the page says which is which); remove the Secret Service entries (`Reflux/…`) if wanted.
- **Verification.** `sha256sum -c SHA256SUMS`; `gh attestation verify` as above.
- **Not in M11:** Debian, RPM, Flatpak, Snap, AppImage, an AUR package (O-11 stays "not by default"), MIME associations (O-12 stays V1.x), a bundled Linux libmpv build (O-13's recommendation stays a V1.x candidate). The earlier plan's Tier 1/2/3 support matrix is **not** published in M11: only what has been verified is stated, and the M12 campaign extends it.

**Gate.** Clean-container install-run-update-remove of the tarball on the verified distribution (the `tools/ci` container covers launch; a VM or machine covers the window), recorded.

### M11.7 Website release surface

The site is the public entry point for releases and consumes **one** release/publication record; it never becomes a second registry (`WEBSITE.md`, "Release data" already enforces this: keep it).

| Section | Source | M11 work |
| --- | --- | --- |
| Platform availability | `ROADMAP.md` Platforms and the verified matrix | One table: Android phone/tablet, Android TV/Google TV, Fire TV, Windows, Linux, each with its channel and its state (`not listed`, `testing`, `available`) from `stores.json` and `published/` |
| Google Play, Amazon Appstore | `release/stores.json` | Store badges and links appear only when a record has a `url`; the beta routes (Play closed-test opt-in, LAT instructions with Beta Hub) are tester pages, never public downloads |
| Windows, Linux downloads | `release/published/<version>.json` | Buttons built from the record (name, size, SHA-256, commit, release link); the Windows page states the SmartScreen and Smart App Control behaviour; the Linux page states the libmpv and Secret Service requirements |
| Release notes | `CHANGELOG.md` | The version's section; known limitations |
| Install instructions | the M6 desktop notes | Windows (extract, run, update, remove), Linux (6), Android and TV (store route), Fire TV (Beta Hub) |
| Checksums and provenance | the record and the release | The `SHA256SUMS` link, the verification commands, what an attestation proves |
| Support, privacy, legal, attribution | existing pages | Re-read against the final build: `PRIVACY.md` store disclosures, third-party notices, TMDB attribution (per D5), libmpv source offer link |
| Product renders and demo video | the final interface (M10 closed) | Regenerate product renders from the final builds; capture the demo's shot list (`LAUNCH.md`, W2); production waits for the owner's tool |

**Checks.** The existing site build checks (no `.apk`/`.aab` link, no manifest without commit and checksum, no store record for another package, titles, responsive checks only on pages whose content changed against the M8 baselines); a **stale-record check**: the version shown equals the record's, and the record equals the draft's manifest (D7).

### M11.8 Release rehearsal

See section 6.

---

## 4.2 The Android package-identity issue (found in this planning pass; resolution intended for M11.2, step 0)

**What the documents say.** `DECISIONS.md` (2026-11-08, "The permanent Android identity") and `RELEASE.md` ("Android developer verification (M7)") say `dev.rsxdevelops.reflux` is the permanent identity and **`dev.reflux` is the development identity, never registered or listed**. `ROADMAP.md` (M6's limits, "Owner actions before M8") still carries the pre-rename owner action "`dev.reflux` registered for developer verification before any APK is offered outside the stores", which contradicts `DECISIONS.md` (the development identity is never registered).

**What the build does.** The Android build reads one `applicationId` for every variant (`reflux.android.applicationId=dev.rsxdevelops.reflux` in `gradle.properties`; `android/app/build.gradle.kts`: `applicationId = rootProject.extra["reflux.applicationId"]`; there is **no `applicationIdSuffix`** and no per-variant ID). The development identity `dev.reflux` no longer exists in the build; only an explicit override (`-Preflux.android.applicationId=…`, used for side-by-side scratch builds) changes it. Debug builds are signed with the machine's debug keystore (`~/.android/debug.keystore`); release rehearsals with the dry-run key; real releases with the upload key and then Play's app-signing key.

**What is installed (read 2026-11-13).** The Nothing Phone 2a (`0005534AN000190`) holds `dev.rsxdevelops.reflux` 0.9.0-alpha.2, `versionCode` 90002, flagged `DEBUGGABLE`: a debug-signed build installed under the permanent identity. M7's release walks installed dry-run-signed release APKs under the same ID on the two handhelds and the Fire TV Stick. So the permanent package name already has installs under at least two throwaway certificates, on devices that will later receive store builds.

**Why it matters (and what is and is not known).**

- *Update rejection.* An installed copy signed by a different key than the incoming one cannot be updated: Play's build (signed by Google's app-signing key) and Amazon's (re-signed by Amazon) cannot replace a debug- or dry-run-signed install. The owner's devices need an uninstall first (no data worth carrying; `DECISIONS.md` records that nobody outside the project has data).
- *Developer verification.* Google's registration rules, as read 2026-11-13: for a package name Google has never seen, the developer supplies the public certificate of the signing key; for an existing package name the developer must demonstrate control of a known private signing key; several keys can be registered for one package; when several developers claim a name, priority goes to the developer whose signing key accounts for more than half of the package's known installs. Apps created in Play Console are registered to the developer's account at creation (per `RELEASE.md`, read 2026-11-08) provided the account's own verification is complete. Enforcement started 2026-09-30 in Brazil, Indonesia, Singapore, and Thailand; Google says it expands globally in 2027.
- **Not documented by Google, as far as the sources read:** whether sideloaded debug-signed installs count among a package's "known installs" for that priority rule, and whether a debug certificate can be registered. The plan therefore treats debug and dry-run installs under the permanent ID as a **risk to remove**, not to test.
- *The assumption not to make:* that creating the app in Play Console will make everything automatic. It registers the package for Play's own distribution; it says nothing about files signed by other keys installed elsewhere, and it does not clean the owner's devices.

**Intended M11 resolution (documentation now; implementation in M11.2 step 0):**

1. **Audit.** List every installed `dev.rsxdevelops.reflux` (and `.test`) on the owner's devices and emulators with its signer (`pm list packages`, `dumpsys package`), and record it in `VERIFICATION.md`. Include the Pad 7 (which held no Reflux when read on 2026-11-13, after scratch builds were removed) and the Fire TV Stick (not connected when this was written).
2. **Separate the identities (D2).** Give the debug build type `applicationIdSuffix ".debug"` (so development builds install beside, not over, store builds, and never as the permanent name); keep `-Preflux.android.applicationId` for side-by-side scratch builds; make `release.py` additionally refuse a debuggable or non-upload-key-signed APK carrying the permanent ID in a *release* build (it already refuses debuggable ones). Update `DECISIONS.md`'s wording: the development identity is the debug suffix, not `dev.reflux`.
3. **Clean the devices** before the first store-delivered install: uninstall every non-store `dev.rsxdevelops.reflux`.
4. **Register in the right order.** Create the Play app; open Play Console's Android developer verification page and record that the package appears registered to the account; only then upload.
5. **Outside the stores.** If D1 chooses public Android files, register the package in the Android Developer Console with both certificates (the upload key's and the Play app-signing key's) before publishing; otherwise no Console registration is needed and the `RELEASE.md` statement stays.
6. **Correct the stale owner action** in `ROADMAP.md` (`dev.reflux` registration) and `RELEASE.md` when step 2 lands.

---

## 5. Cross-checks (what must agree across the surfaces)

| Fact | Where it must be identical |
| --- | --- |
| Version and commit | `gradle.properties`, `release-info.json`, `manifest.json`, the tag, the GitHub release, `release/published/<version>.json`, the website, the About page's `version (commit)` |
| Android package | `gradle.properties`, the APK and AAB (`aapt2`), `release-info.json`, `stores.json`, both consoles |
| `versionCode` | the AAB, the APK, both consoles (never reused; monotonic per package) |
| Checksums | `SHA256SUMS`, `manifest.json`, the website's buttons, the release's asset digests |
| Privacy answers | `PRIVACY.md`, Play Data safety, Amazon's questionnaire, the site's privacy page |
| Attribution | the About page, the site, the store descriptions (TMDB; Trakt if D5 chooses it) |
| What Windows will show | the download page, the beta instructions, `RELEASE.md` |

---

## 6. The rehearsal (M11.8)

No public release. A throwaway key or the real upload key is used as noted; nothing is tagged `v0.9.0-beta.1` and the version does not change (the rehearsal uses a rehearsal version only if the owner asks for a build to be cut; `VERSIONING.md` forbids otherwise).

1. **Clean commit.** A clean tree at a commit; `./gradlew releaseInfo`.
2. **Release workflow.** The rehearsal mode on the push; then, if the owner authorises it, the release mode on a throwaway tag in a scratch repository or branch, never `main`'s real tag.
3. **Verified artifacts.** `release.py verify`; checksums; `gh attestation verify` for every artifact; the independent download-and-verify job passes.
4. **Draft release.** The draft exists with the intended assets only (D1); not published.
5. **Website release surface.** The publication record (D7) is written from the draft; the website builds from it in a preview deployment; the stale-record and link checks pass; the pages read correctly against the record.
6. **Store metadata validation.** Play: the AAB on the internal track, the listing and App content forms filled as far as the owner's console allows, the pre-launch report read. Amazon: one LAT build. The `CHANGELOG`-derived "what's new" text matches in both. The drafted privacy and content answers agree with the site and `PRIVACY.md`.
7. **Upgrade, install, remove.** Android: Play track install on the Phone 2a (after the 4.2 audit), then an update to a second internal build keeps the library, then removal; Google TV emulator likewise. Fire TV: LAT install, update, removal. Windows: the ZIP in Windows Sandbox (install, run, replace with the next zip, remove). Linux: the tarball in the verified environment (extract, run, replace, remove).
8. **Provenance verification** by a second person or machine than the builder: a machine with only the GitHub CLI and the release assets verifies every artifact.
9. **Release handoff.** A one-page record in `VERIFICATION.md`: what was verified, on what, what the owner still has to do in consoles (the "beta hand-off checklist"), and what M12 inherits.

**Pass.** Every step recorded with what was run, on what, with what result; no step marked passed that was not run; anything blocked on an owner action says so by name.

---

## 7. Evidence to record (VERIFICATION.md, "M11")

The identity audit (4.2, step 1); the verified artifact set and its attestation checks (with the `gh` CLI version); the publication-record step's refusal test; the Play internal install and update on a phone and the Google TV emulator; the Amazon LAT install and update on the Fire TV Stick; Windows Sandbox and Linux install/replace/remove records; the website preview build and its checks; each console form's state; the date and source of every store or platform requirement read. Separate **implemented**, **tested locally**, **verified on a device or console**, **owner-pending**, **unavailable**, and **deferred**, as M10 did. Do not convert *not tested* into *pass*.

---

## 8. Owner actions and external dependencies

| Action | Needed for | Clock / note |
| --- | --- | --- |
| Read and record the Play account type and its developer-verification status | M11.2; whether 12 testers / 14 days applies | First; an organization account is exempt from the closed-test rule (a D-U-N-S number can take time) |
| Create the Play app as `dev.rsxdevelops.reflux`; make the upload key (offline copy + the four GitHub secrets); set the support e-mail | M11.2 | O-17 asked for the app before M9; the repository cannot tell if it was done |
| Create the Amazon app (Fire TV, Fire OS only) | M11.3 | O-17 |
| Provide a reachable test source with open films for reviewers | M11.2 reviewer access, Amazon review | Must stay up during reviews |
| Decide D1, D2, D3 | M11.4, M11.2 | Before the first draft is published |
| Decide D5 (TMDB answer; Trakt registration if chosen) | store disclosures; M12 entry | TMDB follow-up pending since 2026-11-08; Trakt needs a GitHub-verified account before 2027-01-01 and use at least monthly (registrations unused 30 days are deleted) |
| Windows signing eligibility check (entity, country; Artifact Signing or an OV certificate; Microsoft Store account requirements) | G-WIN | By M12 midpoint; decision by M13 start |
| Demo video tool and approval | M11.7 production | `LAUNCH.md` W2 |
| Physical Google TV device; a Fire TV television; controllers | M12 waves | Owner hardware |
| A second Android version for the picture-in-picture rule; a physical remote's held seek | M12 (M10's recorded limits) | Unavailable now |

---

## 9. Risks

| Risk | Mitigation |
| --- | --- |
| Debug- or dry-run-signed installs under the permanent ID block store installs or confuse registration | 4.2 |
| A published GitHub release exposes APK/AAB by accident | D1; the website check; the draft-only rule |
| Calendar clocks (closed-test period, TV review, Amazon review) dominate | Set up in M11, start deliberately, track in `VERIFICATION.md` |
| A reviewer cannot use an empty app | Reviewer access source (8) |
| Privacy answers disagree across the stores and the site | Section 5's cross-check, one source (`PRIVACY.md`) |
| Windows SmartScreen / Smart App Control wall at 1.0 | G-WIN, with eligibility checked early |
| Store rules changed since reading | Every requirement in this plan carries its read date; recheck in the console when each step runs |
| The tool/workflow gaps grow into a rebuild | Section 0's non-goals; a change beyond D7, the stores-record check, and the D1 asset split needs an owner decision |

---

## 10. Research record (read 2026-11-13; confirm in the consoles when each step runs)

Confirmed from Google, Amazon, Microsoft, or GitHub documentation or their published summaries: Google Play's target API 36 rule for new apps and updates from 2026-08-31 (Play Console Help, "target API level"; developer.android.com target-SDK page), with Android TV's lower floor; the personal-account closed-testing rule (Play Console Help, 12 testers opted in for 14 days; organization accounts exempt); Play App Signing and the upload key (Play Console Help, 9842756); the content-rating and Data safety forms (Play Console, App content); the Android TV app quality criteria (developer.android.com, TV app quality: banner and icon sizes, Leanback launcher, D-pad, AAB mandatory for TV, 64-bit and 16 KB from 2026-08-01); Android developer verification: timeline, the Android Developer Console and its API, package-name registration rules (developer.android.com, "Android developer verification" guides; the Android Developers blog; Help Net Security, 2026-06-19); Amazon's presubmission checklist, privacy-labels questionnaire, Live App Testing, and Appstore Beta Hub (developer.amazon.com); Microsoft's code-signing options, SmartScreen reputation, and Smart App Control (learn.microsoft.com); GitHub's immutable releases (changelog, 2025-08-26, public preview), `gh release verify`, `gh release verify-asset`, and artifact attestations (docs.github.com).

**Unverified or third-party only, and so not relied on:** that a drop below 12 testers resets Google's 14-day count; the exact Play screenshot dimensions for TV (check the console); any detail of Appstore Beta Hub beyond Amazon's own page; whether immutable releases are generally available in 2026; the April 2026 Artifact Signing reputation reports; Microsoft Store account, fee, and packaging requirements (not researched).

---

## 11. Hand-offs

**To M12:** a verified candidate pipeline and the beta channels ready to switch on: the Play closed-test track and opt-in mechanism, the LAT test and tester guidance, the download pages with checksums and provenance, the feedback path, the tester templates; the beta entry checklist (`RELEASE.md`) with its distribution items true; the owner-pending list; the D5 answer. **To M13:** the G-WIN decision and its implementation if chosen; store submissions (Play TV review, Watch Next certification request, Amazon review); the 1.0 site state. **Never in M11:** a public release, a tag, a version change, a push.
