# Reflux M8–M13 Rebaseline

**PROMOTED 2026-11-11.** The owner approved revision 3, and it was folded into `docs/ROADMAP.md`, DECISIONS.md (2026-11-11), and AGENTS.md. ROADMAP.md is the one source of truth for milestones and status. This document remains the research, critique, and specification behind it: section 3.3 is M8's data-model specification, and sections 8 and 18 define each milestone's scope.

(Originally written as a proposal; the revision notes below record how it got here.)

- Written: 2026-11-11, planning session (Opus 5.5). No implementation, no test runs, and no change to production code.
- **Revision 2 (2026-11-11), after the owner's first review.** The sequence M8 Profiles → M9 Navigation → M10 Player → M11 Distribution → M12 Qualification & Beta → M13 RC & 1.0 is kept. Changed by the second review (section 18 lists every finding):
  - The navigation information architecture (the utility area, Settings, where profiles live) moves into M8, so M9 restyles a settled structure instead of relocating things.
  - The desktop mini-player trial moves to the start of M9, before the dock is designed.
  - The website responsive pass moves into M8.
  - Added: per-account Jellyfin device IDs, a profile-visibility property test, a Guest simplification, Plex Home users as a decision, early store-console actions, and test-efficiency rules.
  - The Fable model ID is corrected to 5.2.
- **Promotion (2026-11-11).** Revision 3 folded into ROADMAP.md, DECISIONS.md, and AGENTS.md; marked PROMOTED above. The beta's website conditions ("The website before the beta") moved with the beta to M12.
- **Revision 3 (2026-11-11).** Revision 2 was approved as the planning baseline, and the owner decided O-3, O-16, and O-17. Per-account device identity is extended to Plex as well as Jellyfin. Section 18 ends with the decisions recorded. It was then promoted (below).
- Baseline: `main` = `17cd272` (M7 closure); verified source `a905f27`. Version `0.9.0-alpha.2`, unchanged.
- Read before writing: ROADMAP, ARCHITECTURE, NAVIGATION, DESIGN, WATCH_STATE, METADATA, PLAYBACK (structure), RELEASE, LAUNCH, WEBSITE, PRIVACY, THIRD-PARTY, VERIFICATION (M7), DECISIONS (2026-10-14 to 2026-11-09); the schema (`Library.sq`, migrations `1.sqm`–`6.sqm`), `SourceConnections.kt`, the source adapters' identity rules, `PlayerOverlay.kt`, `MainActivity.kt`, and the website's routes and prerenderer. The live website was also checked.

---

## 1. Current-state summary

### What is done (M0–M7)

The engine is mature and heavily tested. It covers Media Core identity, versions, availability, identification, metadata with provenance (M5), canonical watch state with source reconciliation, Best Version, the M2P playback planner on libmpv and Media3, and Smart Offline. The experience layer runs on desktop, phones, tablets, and televisions with Visual System 2.0 (M4). The release system is proven: a clean-clone rehearsal, hosted CI, the hosted release workflow, signed SLSA attestations, a reproducible Windows LGPL libmpv, and a diagnostics export. Physical devices verified so far: Nothing Phone 2a, Xiaomi Pad 7, and Fire TV Stick 4K Plus, plus Windows and Linux (the Linux container).

### What has changed since the roadmap was written

| Fact | Consequence |
| --- | --- |
| The website is **live** at `https://reflux.rsxdevelops.dev` (checked 2026-11-11: HTTP 200, HSTS, `sitemap.xml` and `robots.txt` served, `/no-such-page` → 404, trailing slash and `http://` → 308) | ROADMAP's "deployment is an owner action" is stale. The SEO work starts from a live site |
| The owner found new requirements in real use: navigation shell quality, player weight, account isolation, profiles, installers, Linux distribution, SEO | M8 can no longer be "Beta": the beta would test a product that is about to change substantially |
| **A privacy and correctness defect** (below) | Must be fixed before anyone outside the project uses Reflux |

### The account-isolation defect, confirmed in code

- A Jellyfin source's identity is `serverId/userId` (`JellyfinSource.kt:85`); Plex's is `machineId/account` (`PlexSource.kt:392`). Signing in as another user therefore makes a **new source** and removes the old one (`SourceConnections.kt:290-294`).
- `watch_state` is **one table keyed by `item_id` only**, with no viewer dimension (`Library.sq:152`). `remove` keeps watch history "with its works" by design (`SourceConnections.kt:172-184`). User A's history therefore stays and is shown under user B.
- The second half of the contamination happens through ordinary use. On the first comparison with B's server, Reflux keeps its state (A's) and tells B nothing (WATCH_STATE.md, the reconciliation table). But resuming one of A's in-progress works then reports progress to **B's server account** (`SyncingWatchReporter`), and every later change in Reflux is sent to B. A's habits leak into B's server record over time.
- `favorite`, `user_collection`, and `version_preference` have the same single-viewer shape.

This is not fixable by clearing rows on sign-out. It needs a viewer dimension in the state model (section 3.3).

### What is stale in the authoritative roadmap

- M8 = Beta, M9 = RC, M10 = 1.0.
- The website's deployment is still listed as pending (it is live).
- The M8 entry checklist's owner actions are still open: the upload key, the Play and Amazon apps, the TMDB secret, a support e-mail, the account type, and the **casting gate**, which was due "at M8's start" and is undecided.
- M7's "Moved out" items still need homes: playback parity, physical Google TV, the Fire TV television, physical controllers, clean-machine installs, the large-library baseline, the stress harness, image-baseline visual regression, live TMDB, the demo video, per-work subtitle memory, and the `onFocusedBoundsChanged` replacement.

---

## 2. New requirements inventory

| ID | Requirement | Size | Touches |
| --- | --- | --- | --- |
| **NAV** | Navigation & Surface System 2.0: compact floating phone bar that hugs its content, animated selected-state indicator, functional glass, tablet rail, desktop sidebar, TV rail, Reflux mark and wordmark, RSX Develops credit, a primary vs. utility hierarchy, adaptive by window size, accessibility, reduced motion, key/controller/remote | L | `ui` shell, `design`, NAVIGATION.md, DESIGN.md |
| **PLY** | Player Experience 2.0: lighter chrome, one coherent loading state, the title fading with the chrome, Back preserving playback, fullscreen ↔ mini-player as one session, responsive mini-player, TV treatment, accessibility | L | `ui/player`, `PlaybackSession`, `AppState`, the Android shell, the desktop shell (native video window) |
| **PRF** | Local viewer profiles; account isolation; shared sources with per-profile accounts; optional PIN; clear local watch history without resurrection; migration | XL | Schema, `library` (state, reconciliation, Continue Watching, Watch Next), `core/watch`, source connections, `ui`, PRIVACY.md |
| **XFR** | Device-to-device profile transfer, with no backend | XL | New transport, crypto, a bundle format |
| **SYN** | Multi-device synchronization, with no backend | XXL | Conflict model, peers |
| **WEB** | Website 2.x: responsive validation, technical SEO, structured data, Search Console, content for search intent, and a title-format correction (`Reflux — …` → `Reflux \| …`) | M | `website/` |
| **WIN** | A modern branded Windows installer: upgrade and uninstall, high DPI, accessibility, provenance kept, credits | M | Packaging, `release.py`, `release.yml` |
| **CRD** | A third-party credits and technology section (Jellyfin, Plex, FFmpeg, mpv, …), driven by `legal/` | S | About, website `/legal/third-party` |
| **LNX** | First-party Linux distribution (no app stores): AppImage, deb, rpm, Arch PKGBUILD, an honest support matrix | M–L | Packaging, Linux libmpv, CI |
| **MDP** | Metadata provider decision framework (TMDB pending; Trakt and others evaluated) | S (decision) / M (adapter) | `metadata:*`, About, PRIVACY.md |
| **QA** | Test strategy: targeted in feature milestones, one comprehensive qualification campaign | process | VERIFICATION.md, CI |
| **GOV** | One master prompt per milestone; the testing-efficiency rule; the model/usage strategy | process | AGENTS.md, ROADMAP.md |

---

## 3. Research findings

### 3.1 Navigation: current platform guidance

| Source | Finding | What Reflux takes from it |
| --- | --- | --- |
| Android adaptive navigation (developer.android.com, "Build adaptive navigation") | Default: a **navigation bar** when the width or height is compact (or in tabletop posture), a **navigation rail** for everything else. `NavigationSuiteScaffold` implements the switch | Reflux's `AdaptiveLayout.navigation` already does this, and per window rather than per device. Keep Reflux's own surfaces; do not adopt `NavigationSuiteScaffold` (it would replace a working, tested family with Material defaults) |
| Material 3 Expressive (2025; Google's own pages not retrieved, secondary sources only) | The original bar is deprecated for a **shorter "flexible" bar (64 dp vs 80 dp)**. The navigation drawer is deprecated in favor of a **collapsed/expanded rail**, and the expanded rail holds secondary destinations | Agrees with the requirement: a compact bar, no drawer, and secondary (utility) destinations in an expanded rail or sidebar section |
| Android 16 orientation and resizability (Android Developers Blog, 2025-01) | For apps targeting API 36, on displays ≥ sw600dp the system **ignores orientation and resizability restrictions**. Opting out is allowed at API 36 and removed at 37. Play requires target 36 since 2026-08 and will require 37 in 2027-08 | Reflux targets 36. Every shell layout must work at any aspect ratio on tablets and foldables, including a phone-like window on a tablet. M9 must test window classes, not devices |
| Apple HIG, Materials and Tab bars (updated for Liquid Glass, 2025) | Liquid Glass is "a distinct functional layer for controls and navigation", to be limited "to the most important functional elements"; standard materials belong in content. The tab bar floats above content and can **minimize on scroll** | Already Reflux's rule (DESIGN.md, "Where glass goes"). What is new and worth taking: a floating bar that **hugs its content**, a **selection indicator that travels** between items, and **minimizing while scrolling content**. What is not taken: Apple's shapes, refraction optics, or iOS-identical geometry |

**Conclusion.** The existing M3/M4 architecture is right: one family, per-window placement, and glass only on the functional layer. NAV is a redesign of the surfaces and the hierarchy, not a new navigation architecture. The structural change is a **utility area** (below), driven by profiles.

### 3.2 Player: platform guidance and the existing implementation

- **Loader and controls are drawn independently.** In `PlayerOverlay.kt`, the loader (`RefluxLoader`, line 226, centred whenever `waiting`) and the centre transport (line 348) have separate visibility logic, which explains "loader beside half-visible controls". The fix is architectural (one chrome state machine), not a restyle.
- **The player is a takeover** (NAVIGATION.md §8, "never a page"), and Back closes it. A mini-player turns this into a **presentation of one session**: `PlaybackSession` stays the session, and `AppState` gains a presentation (`Fullscreen | Mini | System`, where System means picture-in-picture or out of sight). The navigator and the session become independent.
- **Android.** Picture-in-picture for handhelds exists (`MainActivity.kt:309-330`). Media3 recommends a `MediaSessionService` for playback that outlives the activity. Reflux does not need that for an in-app mini-player (the activity stays foreground), and background audio stays V1.x. Predictive back (Android 14+) gives a gesture progress that can drive the fullscreen→mini shrink natively.
- **Desktop is the hard part.** Video is a **native child window** (libmpv) with the controls in a separate window above it (ARCHITECTURE.md). A mini-player means moving and resizing that heavyweight window inside the browsing UI. Compose cannot draw over it (the airspace problem), so no rounded glass edges over video except through the overlay window. The alternative, mpv's render API into a Skia/GL texture, is a rewrite of desktop video output and must not be undertaken for 1.0. **M10 must start with a time-boxed desktop spike.**
- **Television.** The existing decision ("Leaving on a television pauses"; Back closes the player) matches TV conventions. A TV mini-player conflicts with focus-only navigation and the 10-foot layout. Recommendation: **no mini-player on TV in 1.0**; TV gets the lighter chrome and the coherent loading state only.

### 3.3 Profiles and account isolation: design research

**Prior art.**

- Jellyfin identifies a user per session and token. Each user needs their own session and device ID ("you cannot have multiple users signed into your client with a single randomized string"). Quick Connect (10.7+) signs in a device without a password but needs an already signed-in client to approve.
- Plex Home users are switched through plex.tv (`/api/v2/home/users/{uuid}/switch`, a PIN where set). The PMS API has no user identifier besides the token, and a switched managed-user token may need exchanging via `/api/v2/resources` for a server token (community reports, not official documentation).
- Infuse's tvOS user switching had a regression where "both users are logged into the same library". This is the exact failure class to design against: isolation must hold in the data model, not in UI state.

**Recommended model: profile = the viewer identity boundary.**

| Scope | Owns | Tables (today → proposed) |
| --- | --- | --- |
| **Installation (shared)** | Media truth and device facts: items, versions, tracks, artwork, metadata, source metadata, identity overrides (Identify), metadata pins, aliases, provider collections, the artwork cache, **downloads (files)**, device settings (online metadata, Media Atmosphere) | Unchanged |
| **Server endpoint (shared)** | An address entered once: type, URL, server ID, display name | **New** `server_endpoint`. Today the address lives only inside each account source's sealed config |
| **Account source (per account)** | Today's server source: `serverId/userId` identity, sealed token, its libraries, its catalog scan, its `watch_sync` baseline | `source` gains `endpoint_id` and `owner_profile` (null = shared) |
| **Shared source** | Local folders, SMB, WebDAV, and a Plex server with no account | `source.owner_profile` null |
| **Profile** | Watch state, Continue Watching and Next Up (derived), favorites, user collections, pinned versions, viewer preferences (subtitle/audio language when added), PIN verifier, which account sources it may see | **New** `profile`. `watch_state`, `favorite`, `user_collection`, and `version_preference` gain `profile_id` (a table rebuild for the primary keys) |

**Rules.**

1. A profile sees **shared sources and the account sources it owns**. The unified library for a profile is computed as today's inclusion, plus that visibility filter, which slots into `LibraryScope`. Two profiles on the same Jellyfin server are two account sources on one endpoint. The same film is one work with two versions (existing unification), and each profile sees only its own account's version. Per-user library permissions and parental controls on the server are respected automatically, because each account scans its own catalog. The cost: two accounts scan twice. That is acceptable, and the alternative (one scanning account) leaks one person's libraries to another.
2. **Reconciliation is per (profile, account source).** Because an account source has exactly one owner profile, `watch_sync (source_id, path)` is already per account. The reconciler is fed the owner profile's watch state. **No second watch-state engine**: `WatchReconciler` is unchanged in shape and gains a profile parameter at the library boundary.
3. **Shared sources that keep watch state but have no per-viewer account** (a Plex server open to the LAN without sign-in): sync only for the profile that added it; other profiles read the catalog and keep local state only.
4. **Signing in to a different account on the same endpoint inside a profile that used another account there** asks: "This is a different account than this profile used. Use a new profile for it?" (the default is a new profile). This catches the exact owner scenario at the boundary.
5. **Sign-out** removes the account source (as today: session ended, token forgotten) and keeps the profile's history. History is the profile's, and it never moves to another account.
6. **Downloads** stay device-level files (shared storage, one copy). A copy's *visibility* is shared in 1.0. Per-profile visibility is V1.x (a privacy note goes in PRIVACY.md).
7. **Watch Next / launcher rows** reflect the **active profile**, and are republished on switch. A **PIN-protected profile publishes nothing** to the launcher, because the launcher is outside the lock.
8. **Out of the box:** one default profile, invisible. No picker and no avatar ceremony until a second profile exists. "Excellent by default" holds for single-viewer installs.
9. **No special Guest type in 1.0** (revision 2). "Guest → no account" is simply a profile with no account sources bound: it sees shared sources only. A guest that is wiped on leaving is a V1.x option (O-6), not a second kind of profile.
10. **One device identity per account, for Jellyfin and Plex, not one per installation** (revision 2, found in code; extended to Plex by the owner's decision, revision 3). Today one `deviceId` is given to every Jellyfin and Plex connector on the installation (`AndroidEnvironment.kt:175`, `DesktopEnvironment.kt:132`), and Reflux finds its own Jellyfin sessions by that ID (`JellyfinPlayback.kt:123`). With two accounts on one server, the playback truth layer could read the other account's session.
    - **The fix:** derive a stable identity per (installation, account source): Jellyfin's `DeviceId` and Plex's client identifier (`X-Plex-Client-Identifier`). Existing sign-ins get an upgrade path.
    - **Verified live, never assumed:** the M8 two-account live tests against a real Jellyfin and a real Plex Media Server (both in Docker) record what each server actually does. They answer whether signing in account B with the old shared identity affects account A's token or session, and whether per-account identities keep both apart. The observed result is written into VERIFICATION.md and WATCH_STATE.md, whatever it is.
11. **Every read model goes through the visibility filter.** `Library.sq` has 72 queries reading `item` or `version` directly: Home rows, provider franchises, search, collections, Continue Watching, diagnostics, and Watch Next. Any one that skips the filter shows a title that exists only on another profile's account. M8 needs a **property test**: given two profiles with disjoint account sources, no read model, search, or support report returns an item visible only to the other profile. This one test replaces per-screen device walks for isolation.
12. **Every viewer-state table carries `profile_id`.** This covers the four of schema 7 (`watch_state`, `favorite`, `user_collection`, `version_preference`) and every later one; M10's per-work subtitle choice is the first after M8. A schema test lists the viewer-state tables and fails when any of them, existing or new, lacks a `profile_id` column.
13. **Background work for profiles that are not active:** every profile's account sources are scanned and reconciled, whoever is active, so a profile's state is current when it is next used. Only display is per profile.
14. Identify corrections, metadata, and artwork choices stay **installation-wide**: they describe media truth, not a viewer.

**Clear local watch history: the resurrection problem.**

With today's reconciler table:

- If Reflux simply deletes its rows and keeps baselines, the next scan sees "source unchanged, Reflux changed" and **tells the server** (a remote un-mark, which is forbidden).
- If baselines are deleted too, the scan sees "first comparison, no state" and **adopts the server's record** (resurrection).

Proposed semantics, as an extension of the existing reconciler (pure and table-testable):

- Clearing writes a per-profile **clear epoch** (`profile.history_cleared_at`), deletes that profile's `watch_state`, favorites stay (the action is "watch history" only), and marks the profile's account baselines as reconciled at the epoch. Deletion by clearing is **not a user change** and is never propagated.
- New rule: **a source record that is unchanged since the baseline, or dated at or before the clear epoch, is not news.** Undated non-empty records are not adopted after a clear either; this extends the existing "undated cannot prove it is later" principle.
- A source record that changes **after** the epoch (watched elsewhere later) is news and adopted, as today.
- Derived state (Continue Watching, Next Up, Watch Next programs) is recomputed. Launcher programs are deleted.
- Never touched: the servers, media, downloads, metadata, other profiles.
- Test table: every row of WATCH_STATE.md's reconciliation table × {before clear, after clear, source changed after clear, source away during clear, new account bound after clear}.

**Profile PIN.**

- A 4-digit PIN has 10,000 values, so **no hash protects it against offline guessing**. Hashing (even Argon2) only stops plaintext storage. Honest protection comes from: (a) a verifier keyed by a **non-exportable platform key**: an HMAC under an Android Keystore key, Windows DPAPI or Credential Manager, or the Linux Secret Service; and (b) **persistent rate limiting**: 5 tries, then 30 s, doubling to a 15-minute cap, with the counter stored with the verifier so a restart does not reset it.
- On Linux without a Secret Service there is no non-exportable key: state that the PIN is a UI lock only there.
- The PIN gates the UI, not data at rest: the library database is not encrypted, and app sandboxing or the OS account protect it. PRIVACY.md must say so plainly.
- Never logged (`Redaction` gains PIN fields), never in the support report or any export.
- **Recovery:** a forgotten PIN can be removed only by **deleting that profile** from another profile, which destroys its history and so reveals nothing. Owner decision O-7.

**Migration (schema 7).**

- Create the default profile and move every existing row of the four profile tables into it. Existing server sources become owned by the default profile; local, SMB, and WebDAV sources become shared; endpoints are extracted from the sealed configs (this needs the seal open at migration time, so it runs in code at start, not in SQL).
- SQLite 3.18 limits apply (no `RENAME COLUMN`, so table rebuilds are needed).
- Already-contaminated history cannot be untangled. It stays in the default profile, and "Clear local watch history" is the remedy. Only development devices have this data, since nothing was released.
- **A downgrade guard is new:** an older desktop build opening a schema-7 database must refuse with a clear message, not corrupt it. Android forbids a `versionCode` downgrade already.

### 3.4 Transfer and sync (research only; V2)

| Option | Cross-platform? | Verdict |
| --- | --- | --- |
| Nearby Connections | **Android and iOS only** officially. The open-source C++ `google/nearby` builds for Windows and Linux, but the Linux build "has no mediums implemented" and it is "not an officially supported Google product" | Rejected as the common path |
| Wi-Fi Direct / Wi-Fi Aware | Android only | Rejected |
| WebRTC data channels | Cross-platform, NAT traversal. But it needs signaling (QR-carried SDP is large) and a heavy native library on the JVM | Overkill for a LAN transfer. Revisit only for internet sync, which is out of scope |
| **LAN TCP + DNS-SD (Android NSD, JmDNS on desktop) + QR or short-code pairing + an authenticated key exchange (Noise XX with a QR-carried key, or a PAKE such as CPace/SPAKE2 for a typed code)** | Yes: plain sockets everywhere | **Recommended transport.** TVs cannot scan, so the TV shows a QR and the phone scans it; PC↔TV uses a short code with a PAKE. Fails on client-isolated Wi-Fi, guest networks, and VPNs, which need a fallback |
| **Encrypted export file** (passphrase → Argon2id/scrypt → AES-GCM), moved by USB or the user's own storage | Yes, needs no network | **Recommended first**: it works where LAN discovery fails, is easy to test, and defines the bundle format the LAN path reuses |

- **Credentials are not transferred by default.** The bundle carries endpoints and account *names*, and the new device re-authenticates. Jellyfin Quick Connect (approve from the old device) and Plex's PIN link make that painless and are better V1.x sign-in features for TVs anyway.
- **Item mapping across devices** must use identity keys and provider IDs, never row IDs. Identify overrides travel with the bundle.
- **Sync** is a different problem: concurrent edits need hybrid logical clocks and per-field rules. Position: last writer by HLC. Completed and play count: monotone per device. Favorites and collections: observed-remove sets with tombstones. Clear history: an epoch tombstone.
- **The architectural insight:** a peer Reflux device can be modeled as one more **witness** of watch state, a `WatchStateSyncSource` reconciled by the existing `WatchReconciler`. That reuses the M5/watch-state philosophy instead of inventing a sync engine. This is a V2 pillar and a Fable-class design problem.

### 3.5 Website and SEO: verified state (2026-11-11)

| Area | State |
| --- | --- |
| Prerendered HTML per route, canonical, OG/Twitter cards, 404 with `noindex` and status 404, clean URLs with 308s, HTTPS + HSTS, sitemap, robots | **Done** (live) |
| `<lastmod>` in the sitemap | Missing |
| Structured data (JSON-LD) | **None** |
| Titles | Home `Reflux — your media, played well` (em dash); others `Page · Reflux`. The owner wants `\|` |
| Per-page OG image | One shared `og-image.png` |
| Search Console, sitemap submission | Not done (owner: DNS TXT verification at Cloudflare) |
| Non-canonical hosts (`*.vercel.app` production alias) | To verify: canonical tags mitigate duplicates; an `X-Robots-Tag: noindex` on non-canonical hosts, or a redirect, is cleaner |

**Structured data**, per Google's SoftwareApplication documentation (read 2026-11-11): the rich result **requires** `name`, `offers.price` (0 for free), and **one of `aggregateRating` or `review`**. Reflux has no genuine ratings, and fabricated or self-serving ratings violate Google's policies. So: mark up `Organization` (RSX Develops), `WebSite`, `SoftwareApplication` (name, `operatingSystem`, `applicationCategory: MultimediaApplication`, `offers.price: 0`), and `BreadcrumbList` for `/docs/*`, **for entity understanding, with no expectation of a rich result**. Add ratings only once store ratings exist and are mirrored truthfully.

**Search-intent content** (truthful, useful, no competitor disparagement), for example: "Jellyfin and Plex in one player", "Play SMB shares on Android TV", "A Jellyfin client for Fire TV", "Linux media player for Jellyfin and local folders", and "How Reflux chooses a version". Each is a real doc page, not a doorway page.

**No ranking promises.** Indexing takes weeks, so technical SEO should land early (lead time) and content follows the final UI.

### 3.6 Windows installer

| Option | Branding | Fit | Problems |
| --- | --- | --- | --- |
| **Inno Setup 6.6+** | Custom wizard images, `modern dynamic` light/dark following Windows, custom styles, Pascal-scripted pages | Per-user install with no elevation (`PrivilegesRequired=lowest`, matching the zip's "no admin" principle); `AppId` upgrades; an Apps & Features uninstaller; free for commercial use; the IDE has high-contrast and screen-reader improvements | Output is not bit-reproducible by default. Windows-only build (the Windows release job already exists) |
| Qt Installer Framework 4.11 (2026-03) | Qt Widgets theming, a maintenance tool | Online repositories and component updates | Ships a Qt runtime inside the installer, a heavier toolchain, and licensing to verify (GPL with an exception). Its strengths (online component repos) are not needs Reflux has |
| WiX v6/v7 (MSI/Burn) | A branded Burn UI needs a custom bootstrapper (.NET/WPF) | Enterprise MSI semantics, rollback | The Open Source Maintenance Fee EULA (v7 requires acceptance, with a fee above $10k/year revenue; whether donations count needs reading). Hard to brand |
| MSIX | Modern | Store-grade | **Requires signing**, so an unsigned MSIX does not install normally. Blocked by the "unsigned for now" decision |
| Velopack | One-click (no wizard), with a splash | Built-in delta updates | No options UI; its updater is an update architecture of its own, deciding auto-update by the back door |
| jpackage `--type exe/msi` | Minimal | Already in the toolchain | Uses WiX underneath; little branding |

**Recommendation:** Inno Setup, wrapping the existing jpackage app image (the same files as the zip).

- Pages: welcome with the Reflux lockup and an RSX Develops credit; install scope (current user by default, all users optional with elevation); shortcuts; ready; finish with "Launch Reflux".
- Version and commit on the welcome and finish pages. The license and notices page from `legal/`.
- Uninstall offers "Keep my library" (default: keep `%LOCALAPPDATA%\Reflux`).
- Provenance: the installer is one more artifact in `SHA256SUMS`, the manifest, and an attestation. `release.py verify` extracts it (`innoextract`) and checks the payload against the zip's pinned files (the libmpv DLL digest). **The zip stays** as the portable option.

**Signing remains the real UX problem.** An unsigned installer still triggers SmartScreen, and Smart App Control blocks it. Azure Artifact Signing is reported as available to **organizations in the US, Canada, EU, and UK**, with **individual onboarding limited to the US and Canada and reportedly paused** (Microsoft Q&A, 2026; not confirmed on an official page). The alternative is an OV certificate from a commercial CA. This is owner decision O-9.

**Updates.** No auto-updater in 1.0. A manual "Check for updates" (one request to GitHub's releases API, only when pressed) is optional and must be disclosed in PRIVACY.md (owner decision O-10).

### 3.7 Linux distribution (first-party, no stores)

**Formats.**

| Format | For | Maintainable by one developer? |
| --- | --- | --- |
| **AppImage** (x86_64, static type2 runtime, so libfuse2 is no longer needed; on Ubuntu 24.04 the package is `libfuse2t64` for old runtimes) | Every glibc distribution, as a universal fallback | Yes: one build in the CI container |
| **.deb** | Debian 12/13, Ubuntu 22.04/24.04/26.04, Linux Mint 21/22 | Yes, built with `jpackage --type deb` or `nfpm` from the same payload |
| **.rpm** | Fedora (current two releases), openSUSE Leap 16 / Tumbleweed | Yes, from the same payload |
| **PKGBUILD in the repository** (builds from the release tar.gz) | Arch, Manjaro, EndeavourOS | Yes as a file. **Publishing to the AUR is a listing with its own maintenance**: not by default (owner decision O-11) |
| tar.gz | Everyone, manual | Exists |
| Nix | NixOS | Community only; Reflux documents the tarball's needs |
| apt/dnf repository with auto-updates | — | **Not in 1.0** (repository signing keys and hosting). V1.x candidate |

**libmpv on Linux: bundle Reflux's own build.** Today the tar.gz needs the distribution's `libmpv2`, which **does not exist on Ubuntu 22.04** (it ships libmpv1/0.34). Versions also differ by distribution, which makes playback support a matrix of other people's builds. Extending `tools/libmpv` to a Linux target (same pins, LGPL, published source) gives one tested engine everywhere, with VA-API/VDPAU through the system's driver libraries (dlopen'd). The cost is a second reproducible build recipe, which the existing CI pattern already supports.

**Desktop realities to test and document:**

- Compose Desktop runs through **XWayland** on Wayland sessions (no native Wayland toolkit in the shipped runtime), so fractional scaling under XWayland may be blurry.
- The libmpv child window works under XWayland.
- `secret-tool` (Secret Service) is needed for credential storage: declare `libsecret-tools` (Debian) / `libsecret` (Fedora) as a package dependency, or bundle a minimal client. KDE provides the Secret Service through KWallet or ksecretd.
- Add `.desktop` entries, icons, and AppStream metadata (useful even off-store). There are **no MIME associations** today: "Open with Reflux" for a loose file is a feature (playing outside the library model), proposed for V1.x (owner decision O-12).

**Honest support matrix (proposed).**

| Tier | Distributions and sessions | Verified how |
| --- | --- | --- |
| 1: verified each release | Ubuntu 24.04 LTS (GNOME, Wayland and X11), Fedora current (KDE Plasma, Wayland) | VMs in the M12 campaign; real window tests |
| 2: install and launch smoke-tested | Debian 13, Linux Mint 22 (Cinnamon), openSUSE Tumbleweed, Arch (AppImage and PKGBUILD), Ubuntu 22.04 (AppImage and deb) | CI container install + headless launch; one VM pass in M12 |
| 3: expected to work, community-reported | Manjaro, EndeavourOS, XFCE sessions, NixOS (tarball), others with glibc ≥ the AppImage's floor | Documented requirements only |

### 3.8 Metadata providers (decision framework)

| Provider | License for a free, donation-supported proprietary app | Caching | Attribution | Coverage for Reflux | Notes |
| --- | --- | --- | --- | --- | --- |
| **TMDB** (implemented) | **Non-commercial only**; commercial use needs a written agreement, and TMDB decides. Clarification pending | Six-month limit | Logo + "uses the TMDB API but is not endorsed or certified by TMDB" | Best: films, shows, episodes, localized text, artwork | The working non-commercial classification is the owner's accepted risk (DECISIONS.md, 2026-11-08) |
| **Trakt** (read 2026-11-11 from developer.trakt.tv, pages updated Sep–Oct 2026) | **"You can use the Trakt API in free or paid apps, including apps supported by advertising, subscriptions, lifetime purchases, tips…"; "Standard API access is free for commercial and noncommercial use… no per-user fee or revenue share."** | Images **must be cached** (hotlinking blocked); **no fixed maximum cache duration**; stop using cached Trakt data after the integration ends | "[App] uses Trakt but is not endorsed or certified by Trakt" in About/Settings **and the store description**; logo optional | Films, shows, seasons, episodes, people; images (WebP) via `extended=full`; IDs include TMDB, TVDB, IMDb | A verified GitHub account is required for apps, with **all apps needing it by 2027-01-01**. Production access needs a privacy-policy link and review. **Registrations unused for 30 days are deleted.** Standard limits: 500 GET per 5 min (client ID + IP) and 1 write/s. Limits "may change at any time". A Read-only production app suits a metadata use with no sign-in. Premium is $149/month (not needed). Localization is weaker than TMDB's (to verify with fixtures). Upstream sources of Trakt's artwork are not stated, so ask rather than assume |
| TheTVDB v4 | Free under $50k/year revenue **with attribution and a link**; paid tiers above that; an end-user-subscription model also exists | — | Required, with a link to TheTVDB | Strong for shows; weaker for films | A viable show-only secondary; adds a second adapter's maintenance |
| fanart.tv | Project key; terms on redistribution not found | — | — | Artwork only | Not a primary |
| Wikidata | CC0 data | — | Optional | IDs and facts; no reliable artwork or overviews | A deterministic ID backstop only |

**Recommended path.**

1. Keep TMDB as the implemented provider under the working classification. The owner sends a follow-up to TMDB now (it has been pending since 2026-11-08).
2. **Owner action now:** create the Trakt developer app with a GitHub-verified account **before 2027-01-01**, and use it at least monthly, given the 30-day deletion rule.
3. **Decision gate at M11 start:**
   - If TMDB has confirmed non-commercial use, ship TMDB and keep Trakt unbuilt (V1.x as a fallback).
   - If TMDB says commercial, or is still silent and the owner wants the risk removed, M11 builds `metadata:trakt` behind `MetadataProvider`, with fixtures and attribution. It becomes the release-build provider, with TMDB kept for development builds.
4. **Switching cost to plan for:** `metadata.provider_ref` and `metadata_pin.provider_ref` hold provider-scoped IDs. Trakt returns TMDB IDs, so a deterministic re-keying migration exists, and identity keys and unification survive. Test it.
5. Never run two providers merged in 1.0. One provider per build keeps precedence simple (METADATA.md).

### 3.9 Models

Current model IDs (Claude API reference, 2026-11-03): Opus 5.5 `claude-opus-5-5`, Sonnet 5.5 `claude-sonnet-5-5`, Fable 5.2 `claude-fable-5-2`. Fable 5.2 is the current Fable, which corrects revision 1 of this document. Always use the newest Fable the environment reports; the plan does not depend on the minor version.

---

## 4. Dependency graph

```text
                     ┌─────────────────────────────┐
                     │ PRF profile & identity model│  (schema 7, reconciler epoch, account sources)
                     └───┬──────────┬──────────┬───┘
                         │          │          │
          profile switcher│  session │ owner    │ bundle format, item mapping
          utility area    │  owner   │          │
                         ▼          ▼          ▼
               ┌────────────┐  ┌───────────┐  ┌──────────────┐
               │ NAV shell  │─▶│ PLY player│  │ XFR / SYN    │  (V2)
               │ (dock slot)│  │ mini-dock │  └──────────────┘
               └─────┬──────┘  └─────┬─────┘
                     │ final UI       │ final UI
                     ▼                ▼
               ┌──────────────────────────────┐
               │ WEB content refresh, product │  (renders are generated from the real UI)
               │ renders, demo video          │
               └──────────────┬───────────────┘
                              │
WEB technical SEO ───────────►│ (independent; lead time for indexing → do early)
WIN installer ◄── signing decision (O-9), update decision (O-10)
LNX packages  ◄── Linux libmpv build (tools/libmpv), support matrix
MDP adapter   ◄── TMDB answer / owner gate (M11 start); Trakt app registered before 2027-01-01
                              ▼
               ┌──────────────────────────────┐
               │ QA campaign (all of the above│ ◄── physical Google TV, Fire TV TV,
               │ exists and is frozen)        │     Xbox + PS controllers (owner hardware/testers)
               └──────────────┬───────────────┘
                              ▼
                   Beta → RC → 1.0
```

The edges that decide the order:

- **NAV depends on PRF** for the utility area (profile switcher, profile settings). PRF does not depend on NAV: its UI can be minimal on the M4 system.
- **PLY depends on NAV** for the mini-player's place (above the phone bar, in the content area beside the rail or sidebar) and on **PRF** (a profile switch ends the session; whose history a playback writes).
- **The WEB content refresh and the demo video depend on NAV and PLY.** The site's product images are rendered from the real UI (`WebsiteShowcaseTest`); doing them before the redesign means doing them twice.
- **The QA campaign depends on everything existing**, including installers and packages. Qualifying distribution while building it is not qualification.
- **WIN depends on two owner decisions** (signing, updates). LNX is independent of the app UI.

---

## 5. Initial milestone plan (the hypothesis, as given)

| | Milestone | Content |
| --- | --- | --- |
| M8 | Navigation & Surface System 2.0 | Shell, responsive navigation, glass material, branding, motion, responsive website, SEO |
| M9 | Player Experience 2.0 | Player redesign, mini-player, buffering, fullscreen/browse transition, platform UI |
| M10 | Profiles, Identity & Multi-User | Profiles, isolation, shared sources, Jellyfin/Plex binding, PIN, clear history, migrations |
| M11 | Beta & Distribution Qualification | Comprehensive qualification, Linux/Windows distribution, installer, device/controller/network matrix, testers, stores |
| M12 | Release Candidate | Freeze, final validation, submissions, blockers only |
| M13 | 1.0 | Release |
| V2 | — | P2P transfer, local sync, handoff, casting, recommendations, personalization, themes |

## 6. Initial test strategy (as given)

M8 targeted; M9 targeted player; M10 targeted profiles/DB; M11 a comprehensive campaign; CI for broad regression; no repeated full regression.

---

## 7. Critical review: holes found

| # | Category | Finding | Severity |
| --- | --- | --- | --- |
| C1 | **Architecture / dependency** | Profiles last (M10) means the M8 shell is designed without the utility area that profiles need (switcher, profile settings), and the M9 mini-player is built without a session owner. Both get retrofitted. PRF is the root of the dependency graph | High |
| C2 | **Migration risk** | The largest schema change since M3 (four primary-key rebuilds, endpoint extraction from sealed configs) lands last, shortly before qualification, so it gets the least soak time on real devices | High |
| C3 | **Privacy** | The account-isolation defect is a correctness and privacy bug, not a feature. Scheduling it third treats it like polish | High |
| C4 | **Duplicated work** | Website "responsive design + SEO" in M8 *and* new product renders after M8/M9 means the site is reworked twice. Split: technical SEO early (independent, needs indexing lead time); content and renders after the UI is final | Medium |
| C5 | **Duplicated work** | A demo video before the UI is final (it was due "before the beta's public page") would show a superseded shell and player | Medium |
| C6 | **Mis-scoped milestone** | M11 combines *building* new distribution software (installer, packages, Linux libmpv), a *qualification* campaign, and a *calendar-bound* beta with testers. Qualifying an installer while writing it is not qualification. And a beta with weekly builds cannot be "one master prompt" | High |
| C7 | **Platform risk** | The desktop mini-player hits the native-window airspace problem. Unplanned, it could stall M9 or tempt a rewrite of desktop video output (the mpv render API) inside a UX milestone | High |
| C8 | **Feature creep** | "Liquid-Glass-inspired" could become refraction shaders (AGSL on Android 13+ only, a Skia runtime effect on desktop), which is per-platform work for a look the existing material tiers already deliver. Also tempting: a TV mini-player, per-profile source inclusion, kids profiles with rating filters, custom avatars | Medium |
| C9 | **Gaps** | Not placed in the hypothesis: the casting gate (overdue); M7's moved items (playback parity, the per-work subtitle choice, the `onFocusedBoundsChanged` replacement, the large-library baseline, the stress harness, image-baseline visual regression); the metadata provider decision; the owner's M8-entry actions; the demo video; the credits page; Linux libmpv | Medium |
| C10 | **Release risk** | The Trakt deadlines (GitHub verification by 2027-01-01, the 30-day inactivity deletion) are external clocks that pass during M8–M10 if nobody registers now | Medium |
| C11 | **Release risk** | Windows signing is undecided, and an unsigned *installer* is more alarming than an unsigned zip. Building a branded installer without deciding signing polishes the part users see after the SmartScreen warning | Medium |
| C12 | **Platform risk** | Android 16 on target 36: large screens ignore orientation locks, and opting out ends at API 37 (Play: 2027-08). NAV must be verified by window class (including a phone-sized window on a tablet), not by device | Medium |
| C13 | **Unnecessary testing** | Under the hypothesis, M8 (website) and M11 both validate the website responsively, and every feature milestone would re-walk the physical devices. Device walks belong to the campaign plus the minimum the change needs (one device per changed form factor) | Low |
| C14 | **Test gap** | No upgrade-path qualification that crosses the profile migration (alpha.2 database → profiles) on Android and desktop, and no downgrade guard | High |
| C15 | **Governance** | "One master prompt per milestone" needs exceptions stated up front: the beta (one prompt per beta build cycle) and owner-gated waits (TMDB, hardware). Otherwise the rule is broken on day one | Medium |
| C16 | **Linux** | The current tar.gz depends on the distribution's `libmpv2`, which Ubuntu 22.04 lacks. Without a bundled engine, "Linux is first-class" is a matrix of other people's mpv builds | Medium |
| C17 | **Credentials on Linux** | Packages must declare the Secret Service client dependency, or credentials silently fall back to plaintext in the DB, the path RELEASE.md already calls "still open" | Medium |
| C18 | **Profiles and the launcher** | Android TV Watch Next is shared by everyone in the room. A PIN-locked profile must publish nothing there, or the lock is decorative | Medium |
| C19 | **Navigation IA** | Settings live under Sources today (Media Atmosphere, online metadata). Profiles add per-viewer settings. Without a utility area, "Sources" becomes a settings junk drawer | Medium |
| C20 | **Milestone naming** | The old M8/M9/M10 numbers are referenced across DECISIONS, RELEASE, LAUNCH, and VERIFICATION ("moved to M8"). Renumbering needs an "Earlier numbering" table like the 2026-11-02 one, or history becomes ambiguous | Low |

---

## 8. Improved milestone plan

**Principles.** Order by dependency: profiles → shell → player → distribution → qualification → release. Keep each milestone one coherent master prompt. Put independent work with external lead time (SEO, provider registration) early.

| | Milestone | Outcome | Primary model |
| --- | --- | --- | --- |
| **M8** | **Viewer Identity & Profiles** (+ Website technical SEO, independent workstream) | Every piece of viewer state belongs to a profile; accounts are isolated; the site is technically indexable | Opus 5.5 |
| **M9** | **Navigation & Surface System 2.0** | The shell is unmistakably Reflux on every form factor, on the structure M8 settled | Sonnet 5.5 (the Step 0 desktop trial: Opus 5.5 if it gets hard) |
| **M10** | **Player & Playback Experience 2.0** | Playback stays visually dominant; one coherent session across fullscreen and mini-player | Opus 5.5 (session and presentation states are subtle) |
| **M11** | **Distribution & Release Surfaces** | Installable, upgradable, credited, and documented everywhere; the provider decided; the website in its final content | Sonnet 5.5 |
| **M12** | **Qualification Campaign & Beta** | One comprehensive campaign, then `0.9.0-beta.N` with testers until the exit criteria hold | Sonnet 5.5 (Opus for hard failures) |
| **M13** | **Release Candidate & 1.0** | Freeze, RC, store submissions, `1.0.0` | Sonnet 5.5 (Opus for the release review) |

> Difference from the hypothesis: profiles move first (C1–C3); the comprehensive qualification is **M12**, not M11, because M11 must first build what is qualified (C6); RC and 1.0 share one milestone because 1.0 is the RC's commit with only the version changed (VERSIONING.md), which is too small to be a milestone of its own. If the owner prefers the original numbering for qualification, M11 and M12 can swap names, but not order.

### M8: Viewer Identity & Profiles

**In scope**

- Profile model and schema 7 migration (section 3.3), with a downgrade guard on desktop.
- Server endpoints entered once; account sources owned by profiles; shared sources; the visibility filter in `LibraryScope`.
- Reconciliation per (profile, account source); the "different account in this profile" prompt; sign-out semantics.
- Clear local watch history with the clear-epoch rule in `WatchReconciler`; Watch Next republished per profile; nothing published for PIN profiles.
- Optional 4-digit PIN: a platform-keyed verifier, persistent backoff, redaction, never exported.
- Per-account device identity for Jellyfin and Plex (section 3.3, rule 10), with an upgrade path for existing sign-ins.
- A playback writes to the profile it started in (`PlaybackSession` captures its profile at start; R8).
- Watch Next links carry the publishing profile, so a launcher card opens in it without a picker (R9).
- **The navigation information architecture, final in structure (revision 2).** Profiles force the structural change, so it is made once, here, and M9 restyles it without relocating anything:
  - **Primary destinations:** Home, Movies, Shows, Search.
  - **Utility area:** the profile (avatar or initials), Sources & libraries, Downloads, Settings, and About.
  - **A Settings page.** Device settings (online metadata, Media Atmosphere) leave Sources. Profile settings (name, PIN, Clear local watch history) join them there.
  - **Placement per form factor**, at the positions the M9 design will keep: phone, top right; tablet and desktop, the foot of the rail or sidebar; TV, the foot of the rail.
  - Drawn with today's M4 components; M9 changes how they look, not where they are.
  - NAVIGATION.md and DECISIONS are updated: six destinations become four primary plus the utility area.
- Profile UI, on the M4 visual system:
  - a "Who's watching" picker only when there are two or more profiles (O-4);
  - create, rename, and delete a profile;
  - set or remove a PIN;
  - "Clear local watch history" with a confirmation that states exactly what is and is not touched.
- Support report: profile count only, never names.
- Docs: WATCH_STATE (profile scope, clear epoch), ARCHITECTURE, NAVIGATION (profile scope, the utility area), PRIVACY (profiles, PIN honesty, launcher), DECISIONS.

**Workstream W: the website (independent, `website/` only)**

- **Titles:** the `|` separator everywhere, no em dash (O-3). Descriptions reviewed per page for length and intent. `check-dist` fails on an em dash or a missing `|` in any title.
- **Responsive quality** (moved from M11): desktop, laptop, tablet, phone portrait, phone landscape, and large displays.
  - Fix the layout system now, so M11 only re-checks pages whose content changes.
  - Playwright screenshots at fixed widths become committed baselines, compared in CI.
- **Structured data:** JSON-LD for Organization, WebSite, SoftwareApplication (no ratings, offers price 0), and BreadcrumbList on docs. `check-dist` validates it.
- **Sitemap and indexing:**
  - `<lastmod>` from git dates.
  - Canonical URLs verified per route.
  - `robots.txt` kept.
  - `noindex` (or a redirect) on non-canonical hosts such as `*.vercel.app`.
- **Social and links:** Open Graph checked per page (per-page images optional); an internal-linking pass so every docs page is reached from at least two pages and links onward; a heading-hierarchy and alt-text audit.
- **Core Web Vitals:**
  - A Lighthouse lab baseline per page template, with a budget asserted in CI (LCP, CLS, total weight).
  - Field data (CrUX) will not exist for a new, low-traffic site. Lab numbers are the gate.
- **Search Console:** the DNS TXT record (owner), sitemap submission, URL Inspection of the key pages, and the coverage report read at closure.
- **Discovery:** the search-intent documentation pages are listed in M8 and written in M11, when the final UI can be shown.

**Also in M8 (small, decisions only)**

- **Casting gate decided** (recommendation: V1.x, failing the time-box given this scope).
- **Release preparation, in parallel (owner, O-17, decided):** the Google Play app (and the account type read) and the Amazon app are created before M9 starts, so the Play testing window can open as early as practical. Claude's part is limited to recording the outcome in RELEASE.md. The milestone does not wait on it.
- The M8 entry checklist's owner actions re-listed as **M12 entry** items.

**Out of scope:** visual redesign, mini-player, transfer/sync, per-profile downloads visibility, kids/rating filters, avatars beyond initials.

**Gates**

- Migration tests from each schema 1–6 fixture.
- Isolation tests covering the owner's exact scenario: A signs out, B signs in, no A history visible or pushed.
- The profile-visibility **property test** over every read model (section 3.3, rule 11), and the viewer-state schema test (rule 12).
- The resurrection table.
- PIN backoff persistence and redaction.
- Two-account live tests against a real Jellyfin and a real Plex Media Server (both in Docker; Plex with two plex.tv accounts or tokens). They record the observed effect of signing in account B on account A's token and session, under the old shared identity and the new per-account one. No outcome is assumed beforehand.
- One Android device (Pad 7 or Phone 2a) for the migration from the alpha.2 database and the keystore-backed PIN.
- Windows for Credential Manager/DPAPI.

### M9: Navigation & Surface System 2.0

M9 restyles the structure settled in M8; it does not move destinations.

**Step 0, the desktop mini-player trial (moved from M10, revision 2).**

- **Question:** can the libmpv child window be moved and resized smoothly to a dock rectangle, with the browse UI around it, on Windows and X11/XWayland?
- **Discipline:** a throwaway branch, never merged, time-boxed to a day of work.
- **Outcome, recorded in DECISIONS before the dock is designed:**
  - **Pass:** desktop gets a video mini-player.
  - **Fail:** desktop gets a "now playing" bar (title, transport, return to fullscreen) without video, and video in the dock moves to V1.x.
- The mpv render API stays out of 1.0 either way.
- **Why here:** M9 designs the dock's geometry for every form factor. Finding in M10 that desktop cannot host video there would mean designing the dock twice.

**In scope**

- **Phone:** a compact floating bar that hugs its content (four primary destinations) and a travelling selection indicator (reduced motion: instant).
  - Minimizing the bar on scroll is a **stretch**, kept only if it passes the accessibility review: never under a screen reader, and always restored by focus or an upward scroll.
- **Utility area** (structure from M8): the profile avatar or initials, styled. Downloads and Sources already left the top corners in M8.
- **Tablet:** the rail with names; utility items grouped at its foot.
- **Desktop:** the sidebar with the lockup at the top and the utility cluster at the foot. Collapsing the sidebar is **not** in 1.0 (revision 2: a setting nobody needs on day one).
- **TV:** the rail with the mark at the top when open; utility items at its foot; the profile switch reachable by remote.
- A **mini-player dock** defined in the layout contract, shaped by the Step 0 result (empty until M10).
- Glass per the existing tiers, with no refraction shaders.
- **Brand:** the mark and wordmark where they fit; the RSX Develops credit on About and at the foot of the desktop sidebar (small, `contentTertiary`).
- An in-app **Credits & technology** section on About, generated from `legal/` (names and roles; logos only where their trademark guidelines allow and with no endorsement wording).
- Replace `Modifier.onFocusedBoundsChanged`. **Image-baseline visual tests** for the new shell (the strategy M4 deferred), by window class including a phone-sized window on a tablet (Android 16 large-screen rules).

**Gates**

- Semantics and focus tests (keyboard, controller, remote).
- Reduced motion and 200% text.
- Image baselines.
- One walk per form factor on hardware: Phone 2a, Pad 7, Fire TV, a Windows window, and the Linux container.

### M10: Player & Playback Experience 2.0

**Corrected 2026-11-13** (planning session, Opus 5.5, after M9 closed at `1dbbc11`). The implementation-ready specification, and M10's one master prompt, is [`M10-PLAYER-SPEC.md`](M10-PLAYER-SPEC.md). The decisions are in DECISIONS.md, 2026-11-13. What follows is the summary. Where the earlier text of this section differs, the specification wins: it was checked against the post-M9 code and the current Android documentation.

The desktop mini-player question was answered in M9 (viable; one native surface moved once). M10 builds on it.

**In scope**

- **Presentation:** `FULL | MINI | SYSTEM` for one `PlaybackSession`. `SYSTEM` is Android's picture-in-picture, set only from the platform's confirmed callback. The transient "entering PiP" state is a shell input to the chrome, not a session state. There is no second player, no `MediaSessionService` (proven unnecessary: everything happens while an activity is visible, and the activity-owned media session already drives the PiP controls), and no Jetpack PiP library (alpha).
- **Back follows UI state:** one derived `BackRoute` (system, app, collapse, into PiP), shared with `dispatch(BACK)`. The always-enabled root callback is removed, so Android's back-to-home animation runs at Reflux's root. Back in the full player collapses to the mini-player on handhelds and the desktop, previewed by the predictive gesture. The preview is UI state; the session changes only on commit; when collapsing releases a landscape lock, the surface does not travel. At the root, a docked session that is playing goes into PiP; paused, ended, or failed, the system's Back runs. A television's Back keeps closing the player.
- **Handheld mini-player:** the one `SurfaceView`, placed by `VideoFrame` through `VideoStage`, never re-parented or hidden by visibility, with a fixed-size buffer if the Step 0 probe confirms it. Proven continuous: the same engine, decoder, tracks, position, reporter, and owner.
- **PiP lifecycle:** auto-enter only while playing; `sourceRectHint` from the picture's box (from the full player or the dock); seamless resize; hide chrome on `isTransitioningToPip` (API 35+); expanded → FULL; closed → the session ends with its place recorded. Failures, endings, and autoplay inside PiP are defined.
- **One chrome model:** a pure reducer with a fixed precedence (NONE, FAILURE, ENDING, LOADING, SHOWN, HIDDEN), one inactivity deadline, and explicit pins (paused, menu, screen reader, focus, pointer, scrubbing, held seek). Loading is exclusive. Buffering shorter than a 600 ms grace changes nothing, so seeks are never cut off.
- **Failure:** a mini-player failure stays in the dock as a failure dock (the picture out of sight, "Playback stopped", Try again or Close, announced). It is not escalated to the full player, which would take the screen from someone browsing (M9's rule). A failed next episode is a stated failure, not a silent close.
- **Visual:** a lighter transport (glyphs, no discs), restrained ±10, one bottom row on a scrim instead of a glass panel, measured coverage targets, and reduced transparency and high contrast handled.
- **Parity:** Android chapters from Media3's own `Chapter` metadata (Matroska, MP4 `chpl` and QuickTime; not HLS); desktop media keys for a focused window (Windows; Linux only if the probe shows delivery without D-Bus); held seek acceleration as one committed seek; the television sidecar-subtitle row decided on 2026-10-17.
- **Subtitle preference:** per profile, per work (the show for an episode), stored as a track description in `profile_setting`, and resolved by the planner (`SubtitleChoice.Preferred`). No migration.
- **Found in code and fixed in M10:** system bars hidden in the mini-player; an engine left running when the activity is recreated; the session's owner held once rather than re-read on each load.

**Gates**

- Step 0: three time-boxed probes recorded before dependent work (surface continuity on the Phone 2a, PiP close versus expand, desktop media-key delivery).
- Automated: presentation, Back-route invariant, predictive-back cancel, chrome reducer, failure, PiP shell logic, surface continuity (instrumented), ownership, subtitle preference isolation, chapters, media keys, held seek, the television row, accessibility semantics, and insets.
- Physical: Phone 2a, Pad 7 (each with TalkBack), Fire TV (the player's television changes), Windows, and the Linux container.

### M11: Distribution & Release Surfaces

**In scope**

- **Windows:** an Inno Setup installer (section 3.6) plus the zip, with `release.py verify` covering it; signing per O-9.
- **Linux:** a Linux target for `tools/libmpv` (pinned, LGPL, source published); AppImage, deb, and rpm from one payload; a PKGBUILD in the repo; `.desktop` and AppStream; the declared Secret Service dependency; the support matrix published (section 3.7). Install, upgrade, and uninstall smoke tests in the CI container per format.
- **Metadata provider gate** (section 3.8): if Trakt, build `metadata:trakt`, attribution, privacy text, and the provider re-keying migration.
- **Large-library performance baseline and seeded stress harness** (moved from M7).
- **Website content:** product renders regenerated from the final UI; profiles, player, and installer documentation; the search-intent pages listed in M8; the platform pages' Linux matrix and installer instructions. Responsive checks only on pages whose content changed, against the M8 baselines.
- **Demo video** shot list captured from final builds (production with the owner's tool).
- **Beta operations ready:** the tester signup/route, the feedback form or e-mail (owner support address), and the beta announcement template.

**Gates:** every artifact built by the release workflow and verified; one clean Windows install, upgrade, and uninstall (Windows Sandbox); each Linux format installed in its container; provider fixtures; site checks.

**Can be pulled forward (revision 2).** The Linux libmpv build and the packaging scripts depend on nothing in M8–M10. If an earlier milestone stalls on an owner or external gate, start them then rather than idling. They stay M11's scope and are closed and verified there.

### M12: Qualification Campaign & Beta

**Phase A, the campaign (section 12)**

- Run on one frozen candidate commit.
- Everything found is fixed with targeted retests.
- Exit: the beta-entry gate (V1 acceptance criteria as amended in section 13, minus the beta-only items).

**Phase B, the beta**

- `0.9.0-beta.1` (version changed only on the owner's request, per VERSIONING.md).
- Play closed testing, Amazon Live App Testing, and desktop from the site.
- About 15–20 testers chosen for coverage.
- Weekly at most, **one prompt per beta build cycle** (triage → fixes → targeted tests → build → notes).

**Exit:** no open blocker or critical; the last beta a week without a new critical; the V1 acceptance criteria hold.

### M13: Release Candidate & 1.0

- `0.9.0-rc.N`: feature freeze, blocker fixes only.
- Store listings submitted (Play TV review, Watch Next certification request, Amazon review).
- Legal pages final; a final targeted re-qualification of what changed since the campaign (not the full campaign again).
- `1.0.0` from the final RC's commit with only the version changed.
- Website in its 1.0 state.

---

## 9. Improved test strategy

### Tiers

| Tier | What | Who runs it | When |
| --- | --- | --- | --- |
| **T0** | Pure unit tests of changed logic (core rules, reconciler tables, state machines, migrations) | Claude, locally, by test filter (`./gradlew :core:jvmTest --tests '*Reconcil*'`) | During implementation |
| **T1** | The `check` of touched modules only (`:library:check`, `:ui:jvmTest --tests …`) | Claude, locally | Before each commit of a slice |
| **T2** | The full repository `check` (Linux, Windows, Android unit and lint, website, release self-test) | **GitHub CI on push**, not the local machine | Every push. Claude reads the **summary** (`gh run view --json conclusion,jobs`) and opens logs only for a failed job (`--log-failed`) |
| **T3** | Device and real-window checks of changed surfaces only: one device per changed form factor | Claude + the owner's devices | Once per milestone, at its end |
| **T4** | The comprehensive campaign | M12 only | Once, on a frozen candidate |

### Per milestone

| | T0/T1 focus | T3 (minimum) | Explicitly deferred to M12 |
| --- | --- | --- | --- |
| M8 | Migration 1–6 → 7 fixtures; isolation scenarios; the visibility property test over every read model; the reconciler clear-epoch table; PIN backoff and redaction; Watch Next per profile; navigation IA semantics (the utility area by keyboard, controller, remote); a live two-account Jellyfin (Docker) test | One Android device (migration from a real alpha.2 database, PIN with keystore); Windows (DPAPI/Credential Manager) | Fire TV, Linux GUI, Plex live accounts, other Jellyfin versions |
| M8-W | `check-dist` (titles, JSON-LD validity, no em dash), `check-links`, Lighthouse budgets; Playwright screenshots at six widths in Chromium (committed baselines); Rich Results Test / Schema validator on three pages | — | Firefox and WebKit, and the pages changed in M11 (re-checked against the baselines) |
| M9 | Semantics and focus; image baselines by window class; reduced motion; text scale. The Step 0 trial is evidence for a decision, not a test suite | Phone 2a, Pad 7, Fire TV, Windows window, Linux container | Google TV hardware, controllers |
| M10 | Session presentation; chrome state machine; watch reporting across collapse; seek acceleration; subtitle memory | Phone 2a, Pad 7, Fire TV, Windows, Linux container | Format matrix re-walk (unchanged engines), HDR/passthrough |
| M11 | Packaging scripts; `release.py verify` extensions; provider fixtures and re-keying; performance harness | Windows Sandbox install, upgrade, uninstall; Linux containers per format | Real-distro desktops (VMs), clean physical machines |

### Rules

- **Never weaken a test or an assertion to save usage.** If a test is slow, run it unattended and read only its summary.
- **A full local `check` runs at most once per milestone** (at closure, on the final commit) and only if CI cannot run a part of it. CI is the broad regression.
- **Long runs** (device instrumented suites, container window tests) go to the background with output to a file. Claude reads a tail or a grep summary, never the whole log, and does not re-inspect an unchanged log.
- A regression found outside the touched area is fixed and covered by a T0 test, then left to CI.
- Device runs that pop windows on the owner's screen are announced first (memory: local dev environment).

### Efficiency improvements (revision 2)

These save usage without lowering the bar:

1. **A CI failure digest.** A small script (e.g. `tools/ci/failures.sh <run-id>`) prints only the failed jobs, failed test class and method names, and the first assertion message of each. Reading CI then costs a few lines, not a log.
2. **Fixtures made once, reused to M12.** The schema 1–6 sample databases (M8), the website screenshot baselines (M8-W), and the shell image baselines (M9) are committed. M12 re-runs them instead of rebuilding evidence.
3. **One property test instead of many walks.** The profile-visibility property test (section 3.3) covers every read model in a single run. Isolation is never "verified" by tapping through screens on devices.
4. **Offscreen before hardware.** Layouts are checked by offscreen renders by window class (the existing `VisualSystemTest` and `WebsiteShowcaseTest` pattern). A device walk confirms only what a render cannot: input, focus, the system bars, performance.
5. **Test selection by module graph.** T1 runs the touched modules and their direct dependents (e.g. a `core/watch` change runs `:core` and `:library`), not `ui`'s window tests.
6. **One device per changed form factor, once, at milestone end.** Not after each slice.
7. **No re-runs of the unchanged playback matrix.** The engines do not change in M8–M11, so the M2P format matrix runs once, in M12.
8. **Background long runs with a summary line.** A long job's last command prints `PASS n / FAIL m` and the failed names; Claude reads that line only.

---

## 10. Model and usage strategy

| Work | Model | Why |
| --- | --- | --- |
| This rebaseline, milestone specifications, architecture (the profile model, the reconciler epoch, the desktop mini-player spike decision), difficult dependency analysis, the release-architecture review before M13 | **Opus 5.5** | Subtle invariants; mistakes are expensive |
| Implementation after the specification is settled, targeted tests, bug fixing, documentation, straightforward refactoring, packaging, website work, beta build cycles, routine CI debugging | **Sonnet 5.5** | Most of the volume; the specification carries the judgment |
| Hard failures in any milestone (a flaky device-only failure, a migration that corrupts, a CI failure not understood in one pass) | Escalate that one problem to **Opus 5.5** | Pay for depth only where it is needed |
| V2 transfer and sync architecture (secure no-backend P2P, conflict model), or a deeply ambiguous cross-platform compatibility problem | **Fable** (the newest the environment reports; currently Fable 5.2, `claude-fable-5-2`) | Open-ended, research-heavy design. Not by default |

The M8 master implementation prompt itself should be written by Opus 5.5 in a fresh session, from the approved roadmap and this document's section 3.3.

M8 is assigned to Opus because its core is the identity model and reconciliation. If the owner wants to conserve further, M8 can run on Sonnet with this document's section 3.3 as its specification, with escalation to Opus only for the reconciler and migration slices.

---

## 11. M8+ prompt architecture rules

These are to be added to ROADMAP.md (governance) and AGENTS.md once approved.

1. **One master prompt per milestone** takes it from research, through implementation, testing, fixes, documentation, and verification, to closure. **At most one follow-up prompt**, and only for a genuine blocker (an external dependency, hardware, an owner decision) or an essential correction. No routine audit, review, cleanup, closure, or micro-stage prompts.
2. **Stated exceptions:**
   - M12 Phase B: one prompt per beta build cycle, because a beta is calendar-bound.
   - Waiting on an owner or external gate does not consume the follow-up: the master prompt finishes everything else and records the gate.
3. **Each master prompt contains:** the outcome; in and out of scope; the gates and their evidence; the targeted test plan (T0–T3 per section 9); the documents to update; owner decisions already made; "decide yourself" defaults; stop conditions (stop only when genuinely blocked); and the commit cadence (one commit per coherent slice; push for CI at least at each slice end).
4. **Claude makes ordinary technical decisions itself** and records durable ones in DECISIONS.md. It asks only for decisions that are genuinely the owner's (visual identity, legal, money, external accounts).
5. **Closure is part of the milestone:** ROADMAP state, VERIFICATION evidence, CHANGELOG under Unreleased, and DECISIONS. Not a separate prompt.
6. **Version and tag rules are unchanged** (VERSIONING.md): no version change, `versionCode` edit, or tag unless the owner asks for a build.

---

## 12. M12 qualification philosophy

**One campaign, one frozen candidate, risk-weighted depth, evidence once.**

- **Frozen candidate.** A commit is named the campaign candidate. Fixes made during the campaign are retested in the affected cells only, and the final commit's CI must be green. The campaign is not restarted for a fix that touches one cell.
- **Waves**, in order, so cheap broad checks run before expensive narrow ones:

| Wave | What | Where |
| --- | --- | --- |
| 0 | Full repository tests (CI), one local full `check` on Windows and in the Linux container; Android instrumented suite on one phone and one TV image; release workflow rehearsal; attestation verification | CI + local |
| 1 | Artifacts: Windows installer and zip on a clean machine (install, first run, upgrade from the previous build, uninstall keeping and removing data); Linux AppImage, deb, rpm, and PKGBUILD on the Tier 1 VMs (GNOME Wayland/X11, KDE Wayland) and Tier 2 containers; signing and provenance; `SHA256SUMS`; store artifacts (AAB, APK identity) | VMs, Sandbox, containers |
| 2 | **Upgrade paths** across the profile migration: alpha.2 → candidate on the Phone 2a, Pad 7, Fire TV, Windows, and Linux (library, watch state, sources, downloads kept; the default profile created); the downgrade guard | Devices |
| 3 | Form factors and inputs: phone, tablet, **physical Google TV**, Fire TV stick, **a Fire TV television**, Windows, Linux; touch, pointer, keyboard, **Xbox and PlayStation controllers**, both remotes | Devices (owner hardware or testers) |
| 4 | Sources and playback: local folders, Jellyfin (two versions, two accounts, profiles), Plex (two accounts, profiles), SMB (a NAS), WebDAV; the format fixture matrix once per engine; subtitles; downloads and offline resume; real network failure and recovery (pull the server's cable, Wi-Fi drop, sleep and wake) | Devices + home lab |
| 5 | Profiles and privacy: isolation scenarios end to end on real servers; clear history with no resurrection after a real scan; PIN lockout across restarts; launcher rows; the support report contains no names | Devices |
| 6 | Accessibility: TalkBack walk (phone), TV accessibility tree, keyboard-only desktop, reduced motion, 200% text, high contrast | Devices |
| 7 | Large library: the M11 performance baseline re-measured on the candidate | Desktop + one Android device |
| 8 | Website: responsive matrix (desktop, laptop, tablet, phone portrait and landscape, large displays) in Chromium, Firefox, and Safari (WebKit via Playwright); Search Console coverage and URL Inspection; Lighthouse lab; links; CSP | Browser automation |
| 9 | Metadata provider live in a release build (credential from the workflow secret) | Release build |

- **Evidence once:** each cell records device, build, result, and date in VERIFICATION.md as a table row. A summary per wave, not a log dump.
- **Failure policy:** blocker or critical → fix, retest the cell plus T0; normal → beta known issue; later → V1.x.
- **Exit = beta entry:** the amended V1 acceptance criteria hold, except the criteria that only testers can satisfy (e.g. tester-count coverage), which are beta exit criteria.

---

## 13. 1.0 boundary

**In 1.0** (all of today's V1 acceptance criteria, plus):

- **Profiles:** profiles, account isolation, shared server endpoints, the PIN, and Clear local watch history. A new **acceptance criterion 14**: "One viewer's history, favorites, and accounts are never shown to, or sent from, another profile; clearing local history never changes a server."
- Navigation & Surface 2.0; the utility area and Settings; Credits & technology.
- Player 2.0: the chrome state machine and the handheld mini-player. On desktop: the mini-player, or the "playing" bar if the spike fails.
- The Windows installer plus the zip. Linux AppImage, deb, rpm, PKGBUILD, and tar.gz with bundled LGPL libmpv. A new **acceptance criterion 15**: "Installs, upgrades, and uninstalls cleanly from every published desktop format on the Tier 1 matrix."
- Website technical SEO and structured data, the content for the final UI, and the `|` title format.
- One metadata provider per the M11 gate.

**Before beta but not 1.0-specific:** the casting gate decision (M8).

## 14. V1.x and Reflux 2.0 boundaries

**V1.x** (each by its own decision):

- Casting, if the gate fails.
- Per-profile download visibility; per-profile source inclusion.
- Kids profiles with content-rating filters for local media.
- Jellyfin Quick Connect and Plex PIN-link sign-in (TV-friendly).
- A desktop video-in-dock mini-player, if the M9 Step 0 trial fails.
- A TV mini-player, if testers ask.
- A wiped-on-leave guest mode.
- Plex Home managed-user selection (O-16, decided: not in M8 or 1.0; a candidate only if a requirement appears).
- A collapsible desktop sidebar; minimizing the phone bar on scroll, if it does not pass M9's accessibility review.
- "Open with Reflux" and MIME associations; a desktop update check or updater.
- Signed apt/rpm repositories; AUR publication.
- Background audio on Android; downloads continuing with the app closed (already V1.x).
- A second metadata provider as a fallback.
- Collections UI (already V1.x).

**Reflux 2.0 pillars:**

1. **Profile transfer:** first the encrypted export bundle, then LAN transfer (DNS-SD + QR or code + Noise/PAKE), with credentials re-authenticated.
2. **Local multi-device sync:** a peer as a witness through `WatchReconciler`, HLC-ordered, with tombstones.
3. **Handoff** (continue on another device), built on sync.
4. Recommendations and natural-language search above the deterministic engine.
5. Themes and personalization (the roles in DESIGN.md are the extension point).
6. Advanced playback (picture controls, per-title styling).
7. Vega OS and iOS evaluations stay "Future" tracks, not pillars.

---

## 15. Open decisions (technical, decided in-milestone unless the owner objects)

| # | Decision | Default proposed | Decided in |
| --- | --- | --- | --- |
| T-1 | Endpoint extraction from sealed configs at migration time (code at start) vs lazily on first connect | Code at start, with lazy fallback if the seal fails | M8 |
| T-2 | Favorites cleared by "Clear local watch history"? | No: the action is named "watch history" | M8 |
| T-3 | Phone bar minimizes on scroll? | A stretch: only if it passes the accessibility review (never under a screen reader); otherwise V1.x | M9 |
| T-4 | Desktop sidebar collapsible? | Not in 1.0 (revision 2) | — |
| T-8 | Device identity per Jellyfin and Plex account: derived (a hash of the installation ID and the account source) or random and stored with the connection | Derived, so it is stable across sign-in again and needs no new secret | M8 |
| T-9 | Where the Settings page lives in the back stack | A utility page stacked above the current section, like Sources; Back returns to the section | M8 |
| T-5 | Mini-player close on swipe (phone) | Swipe down on the mini dismisses and stops, with an undo notice | M10 |
| T-6 | Linux packages built with jpackage vs nfpm | Whichever reproduces the payload byte-for-byte from the app image; nfpm likely | M11 |
| T-7 | Installer reproducibility | Not required; provenance and payload verification instead | M11 |

## 16. Owner decisions required

| # | Decision | Recommendation | Needed by |
| --- | --- | --- | --- |
| **O-1** | Approve the reorder: profiles first; qualification in M12; RC and 1.0 merged in M13 | **Approved (2026-11-11) and promoted into ROADMAP.md, DECISIONS.md, and AGENTS.md** | Before M8 |
| **O-2** | Casting gate (due "at M8's start") | V1.x: fail the time-box given the new scope | M8 start |
| **O-3** | Title format: home `Reflux \| Your media, played well`; pages `Platforms and compatibility \| Reflux` (page first, better in truncated results) or `Reflux \| Platforms and compatibility` (brand first, as literally requested) | **Decided (2026-11-11):** home `Reflux \| Your media, played well`; sub-pages `Page \| Reflux`; a website build check fails if an em dash appears in any generated page title | M8-W |
| **O-4** | Profile picker at launch when ≥2 profiles, or remember the last profile and switch from the avatar | Picker on TVs (shared screen); remember the last on phones and desktop | M8 |
| **O-5** | Downloads visible to all profiles in 1.0 | Yes (V1.x per-profile) | M8 |
| **O-6** | Guest (revision 2): in 1.0 a guest is a normal profile with no accounts; a wiped-on-leave guest mode is V1.x | Accept | M8 |
| **O-7** | Forgotten PIN: delete the profile from another profile (history lost) | Yes; no recovery backdoor | M8 |
| **O-8** | Desktop mini-player fallback if the M9 Step 0 trial fails ("now playing" bar without video) | Accept | M9 start |
| **O-9** | Windows signing: stay unsigned for beta and 1.0, Azure Artifact Signing (if the owner's entity and country qualify), or an OV certificate | Unsigned for beta; decide for 1.0 by M11 with the eligibility checked by the owner | M11 |
| **O-10** | Desktop updates: none, a manual "Check for updates" (a GitHub request when pressed, disclosed), or an auto-updater | Manual check, disclosed | M11 |
| **O-11** | Publish to the AUR or not | Not by default; PKGBUILD in the repository | M11 |
| **O-12** | "Open with Reflux" / MIME associations in 1.0 | V1.x | M11 |
| **O-13** | Linux libmpv bundled in all formats (vs distribution libmpv for deb/rpm) | Bundled | M11 |
| **O-14** | Metadata provider gate (section 3.8) | Register Trakt now; decide at M11 start by TMDB's answer | M11 start |
| **O-15** | Logos in Credits & technology (Jellyfin, Plex, FFmpeg, mpv) | Names, roles, and licenses always; logos only where the project's trademark guidelines permit, never implying endorsement; the Plex mark is likely to need permission, so text only | M9 |
| **O-16** | Plex Home managed users in 1.0: a profile can pick a Home user (plex.tv `home/users` and switch, PIN where set), or each profile signs in with its own plex.tv account or token only | **Decided (2026-11-11): not in M8 or 1.0.** Each profile signs in with its own plex.tv account or token; Home-user selection is a V1.x candidate only if a requirement appears | M8 start |
| **O-17** | Create the Play Console app (and read the account type) and the Amazon app **now**, not at M12 | **Decided (2026-11-11): yes, before M9**, as release preparation in parallel with M8, so the Play testing window can open as early as practical. A personal Play account needs 12 testers for 14 days before production, and TV review and Watch Next certification take time | Before M9 |

## 17. External dependencies

| Dependency | Whose | Blocks | Clock |
| --- | --- | --- | --- |
| Trakt developer app with a **GitHub-verified account** | Owner | The M11 provider option | **Before 2027-01-01**; registration deleted after 30 days unused |
| TMDB's answer to the clarification | TMDB (owner follows up) | The M11 provider gate | Pending since 2026-11-08 |
| Google Search Console verification (DNS TXT at Cloudflare), sitemap submission | Owner | M8-W closure | Indexing takes weeks, so do it early |
| Play app created and the account type read; Amazon app created | Owner | The M12 and M13 calendar (O-17, decided) | **Before M9**, in parallel with M8 |
| Upload key; support e-mail; `REFLUX_TMDB_TOKEN` secret | Owner | M12 beta entry (were "M8 entry") | Before M12 |
| Physical Google TV device; a Fire TV television; Xbox and PlayStation controllers | Owner hardware or testers | M12 waves 3 and V1 criterion 7/12 | Before M12 |
| Windows signing eligibility (Azure Artifact Signing: organizations in the US/CA/EU/UK, individuals US/CA, reportedly paused) or an OV certificate | Owner | O-9 | By M11 |
| Test accounts: two Jellyfin users and two plex.tv accounts or tokens (the Docker servers can host them; the owner's real servers for M12) | Owner | M8 live tests, M12 wave 5 | M8 |
| Demo video tool | Owner | M11 capture and production | M11 |

---

## 18. Second review (revision 2, 2026-11-11)

This review was made after the owner's first reading, against the code as well as the plan. Each finding says where it was applied.

### Sequencing

| # | Finding | Change |
| --- | --- | --- |
| R1 | The plan had M8 add a "temporary" profile entry point that M9 would replace: the redesign-twice pattern the owner ruled out. Profiles are what force the navigation's structural change (a utility area, a Settings page, settings leaving Sources) | **The navigation IA moves into M8**; M9 restyles a structure that does not move (section 8) |
| R2 | The desktop mini-player trial was at M10's start, after M9 had designed the dock for every form factor | **The trial moves to M9, Step 0**, on a throwaway branch; O-8 is due at M9 start |
| R3 | The website responsive pass was in M11, but the site is live now and the owner wants it in M8. Doing it in M11 also meant fixing layout and content at once | **Responsive quality moves to M8-W** with committed screenshot baselines; M11 re-checks only changed pages |
| R4 | The Play and Amazon apps (and the Play account type) were left as M12-entry items. The personal-account rule (12 testers, 14 days) and TV review and Watch Next certification are calendar time, so finding out at M12 can push 1.0 | **O-17 (decided): both apps created before M9**, as parallel release preparation |
| R5 | Linux libmpv and packaging depend on nothing in M8–M10 | **Marked "can be pulled forward"** when an earlier milestone waits on a gate |

### Missing dependencies and hidden risks (found in code)

| # | Finding | Change |
| --- | --- | --- |
| R6 | **One device identity for every Jellyfin and Plex account on an installation** (`AndroidEnvironment.kt:175`, `DesktopEnvironment.kt:132`). Reflux finds its own Jellyfin session by that ID (`JellyfinPlayback.kt:123`), so two accounts on one server can read each other's session. Whether a second sign-in from one identity disturbs the first account's token or session is **unknown** | Section 3.3 rule 10: per-account identity for both. T-8. M8 scope, plus a gate: two-account live tests on real Jellyfin and Plex servers that record the observed behavior, with no outcome assumed |
| R7 | 72 queries in `Library.sq` read `item` or `version` directly. One that skips the visibility filter leaks another profile's titles through Home, search, franchises, collections, diagnostics, or Watch Next | Section 3.3 rule 11: **a property test over every read model**, an M8 gate |
| R8 | **A playback must write to the profile it started in.** If `PlaybackSession`'s reports resolve "the active profile" at report time, the last progress report after a profile switch writes into the new profile | M8: the session captures its profile at start. M10: a switch stops the session and waits for its stop report before changing profile, with a test |
| R9 | **Watch Next deep links need the profile.** A launcher card opens a work, so it must open in the profile that published it, and a picker must not stand in front of it. PIN profiles publish nothing (rule 7), so no PIN prompt can block a card | M8: the profile ID goes in the Watch Next link; tested with the existing `WatchNextLink` tests |
| R10 | **Every viewer-state table carries `profile_id`:** the four of schema 7 (`watch_state`, `favorite`, `user_collection`, `version_preference`) and every later one (M10's per-work subtitle choice is the first). Otherwise the leak class returns | Section 3.3 rule 12: a schema test that fails when any viewer-state table lacks it |
| R11 | The TV rail's rule "Up and Down move one destination at a time; there are no other stops" (NAVIGATION.md §3) changes when utility items join the rail's foot | M8 updates NAVIGATION.md and the focus tests along with the IA |
| R12 | With a mini-player, Back at Home is undefined (today Back at Home leaves the app on Android) | M10 decides: at the root with the mini playing, Back leaves the app as today, and a handheld then goes to picture-in-picture (the existing "leaving" rule), so Back never silently stops playback |
| R13 | Desktop downgrade: a tester trying an older zip against a schema-7 database | Already in M8 (the downgrade guard); M12 wave 2 tests it |

### Moved between milestones

- To M8: the navigation IA (R1); the website responsive pass (R3); the Watch Next profile link (R9); per-account device identity for Jellyfin and Plex (R6).
- To M9: the desktop mini-player trial (R2).
- Out of M11: the responsive pass, reduced to changed pages only.

### Removed from 1.0

- **A special Guest type.** A profile with no accounts is a guest; wiping on leave is V1.x (O-6).
- **A collapsible desktop sidebar** (T-4): a setting with no day-one need.
- **Minimizing the phone bar on scroll** becomes a stretch that must pass the accessibility review, otherwise V1.x (T-3).
- **Plex Home managed-user selection** (O-16, decided): not in M8 or 1.0.
- Unchanged and confirmed out: the TV mini-player, per-profile downloads, kids filters, the desktop updater, and transfer and sync (Reflux 2.0, with no Reflux backend or cloud account).

### Promoted to before the beta

- Per-account device identity for Jellyfin and Plex (R6), with its live tests, and the visibility property test (R7). These are privacy, not polish.
- The store console apps and the account type (R4).
- The upgrade path from the alpha.2 database through the profile migration, rehearsed on one Android device in M8 (already a gate) and on every platform in M12 wave 2.

### Release and distribution risks added

| Risk | Mitigation |
| --- | --- |
| A bundled Linux libmpv built against a newer glibc than the oldest supported distribution | Build on the oldest Tier 1/2 base (the Ubuntu 22.04 / Debian 12 era); M11 gate: the AppImage launches in the oldest Tier 2 container |
| Unsigned `.deb`/`.rpm` installed from a file: package-manager signature checks for local files vary by distribution and version | Verify per Tier 1/2 distribution in M11 and state it on the download page; never tell users to turn checks off |
| Moving from the portable zip to the installer | Same data folder (`%LOCALAPPDATA%\Reflux`), so the installer adopts an existing library; tested in M11 |
| Search Console verification needs the owner's Cloudflare access | Listed as an owner action for M8-W (section 17) |

### Confirmed unchanged

- The order M8 Profiles → M9 Navigation → M10 Player → M11 Distribution → M12 Qualification & Beta → M13 RC & 1.0.
- Inno Setup for Windows.
- First-party Linux formats only, with no stores.
- Clear local watch history: local only, with the clear epoch preventing resurrection and never changing a server.
- Transfer and sync in Reflux 2.0, with no backend.
- Targeted validation in M8–M10, CI for broad regression, and one comprehensive campaign in M12.

### Owner decisions recorded (2026-11-11, revision 3)

Revision 2 was approved as the planning baseline, and revision 3 was then promoted: ROADMAP.md, DECISIONS.md (2026-11-11), and AGENTS.md now carry it, and ROADMAP.md is authoritative. M8's full name there is "Viewer Identity & Profiles, Navigation Structure, Website SEO/Responsive Work, and Release Preparation Foundations".

| Decision | Recorded in |
| --- | --- |
| **Sequencing approved.** M8 owns the final navigation structure, the utility area, the Settings surface, and the final position of the profile entry. M9 owns its visual refinement. The desktop mini-player trial opens M9 on a throwaway branch. The website responsive pass and the full SEO work stay in M8. Linux packaging may be pulled forward when another milestone waits on external timing | O-1; section 8 (M8, M9, M11); R1–R3, R5 |
| **O-3:** home `Reflux \| Your media, played well`; sub-pages `Page \| Reflux`; a website build check fails if an em dash appears in any generated page title | Section 8, M8-W; section 9 (M8-W row); O-3 |
| **O-16:** no Plex Home managed-user selection in M8 or 1.0 | Section 14 (V1.x candidate only on a new requirement); O-16 |
| **O-17:** the Google Play and Amazon app entries are created before M9, as parallel release preparation, so the Play testing window opens as early as practical | Section 8, M8 ("Release preparation"); section 17; R4; O-17 |
| **Account isolation stays first-class M8 scope:** per-account device identity for Jellyfin **and Plex** (R6, extended to Plex by this decision); a playback writes to the profile that started it (R8); launcher Continue Watching carries profile context (R9); every viewer-state table has a profile ID (R10); direct item and version access covered by the broad isolation property test instead of manual screen checks (R7); two-account live tests that observe, never assume, the effect of account B's sign-in on account A | Section 3.3, rules 10–12; section 8, M8 scope and gates |
| **Test strategy kept**, without weakening coverage: fixtures and screenshot baselines reused, offscreen renders before physical walks, the short CI failure-summary script, the full playback-format matrix deferred to M12 | Section 9, "Efficiency improvements" |

---

## Sources (read 2026-11-11)

- Google Search Central: [Software app (SoftwareApplication) structured data](https://developers.google.com/search/docs/appearance/structured-data/software-app)
- Trakt: [API Use Policy](https://developer.trakt.tv/docs/api-use-policy), [Create an App](https://developer.trakt.tv/docs/create-an-app), [Images](https://developer.trakt.tv/docs/images), [Rate limiting](https://developer.trakt.tv/docs/rate-limiting), [Developer updates discussion #1031](https://github.com/trakt/trakt-api/discussions/1031)
- TMDB: [API Terms of Use](https://www.themoviedb.org/api-terms-of-use), [Developer FAQ](https://developer.themoviedb.org/docs/faq)
- TheTVDB: [API information and pricing](https://thetvdb.com/api-information); fanart.tv: [Personal API keys](https://medium.com/fanart-tv/what-are-fanart-tv-personal-api-keys-472f60222856)
- Android: [Build adaptive navigation](https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation), [Orientation and resizability changes in Android 16](https://android-developers.googleblog.com/2025/01/orientation-and-resizability-changes-in-android-16.html), [Background playback with a MediaSessionService](https://developer.android.com/media/media3/session/background-playback); Material 3 Expressive coverage: [9to5Google](https://9to5google.com/2025/05/14/material-3-expressive-navigation/)
- Apple HIG: [Materials](https://developer.apple.com/design/human-interface-guidelines/materials), [Tab bars](https://developer.apple.com/design/human-interface-guidelines/tab-bars)
- Jellyfin: [Quick Connect](https://jellyfin.org/docs/general/server/quick-connect); Plex Home switching (community): [Plex forum thread](https://forums.plex.tv/t/third-party-app-managed-user-session-attribution-via-public-api/936928), [python-plexapi MyPlex](https://python-plexapi.readthedocs.io/en/latest/modules/myplex.html); multi-account leak report: [Infuse community](https://community.firecore.com/t/multiple-accounts-leaking/58874)
- Nearby: [Nearby Connections overview](https://developers.google.com/nearby/connections/overview), [google/nearby connections README](https://github.com/google/nearby/blob/main/connections/README.md)
- Installers: [Inno Setup 6.6.0 announcement](https://groups.google.com/g/innosetup-announce/c/svrvZCO0M3Q), [Qt IFW 4.11.0 announcement](https://lists.qt-project.org/pipermail/announce/2026-March/000614.html), [WiX Open Source Maintenance Fee](https://docs.firegiant.com/wix/osmf/), [Velopack installer](https://docs.velopack.io/packaging/installer); signing: [Microsoft Q&A on Artifact Signing eligibility](https://learn.microsoft.com/en-us/answers/questions/5915697/can-an-irish-sole-trader-(non-incorporated)-qualif), [devclass on Azure Artifact Signing](https://www.devclass.com/security/2026/01/14/code-signing-windows-apps-may-be-easier-and-more-secure-with-new-azure-artifact-service/4079554)
- Linux: [AppImage FUSE](https://github.com/appimage/appimagekit/wiki/fuse), [type2 runtime](https://awesome.ecosyste.ms/projects/github.com%2Fappimage%2Ftype2-runtime), [Fedora F44 FUSE 2 proposal](https://discussion.fedoraproject.org/t/f44-change-proposal-atomic-desktops-drop-fuse-2-libraries-selfcontained/179410)

Secondary or community sources (the Material 3 Expressive coverage, the Plex switching threads, the Azure eligibility Q&A) are marked as such above. Recheck them against primary documentation when the relevant milestone starts.
