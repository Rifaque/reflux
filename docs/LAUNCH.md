# Reflux Launch Plan

The tracks that take Reflux from a working tree to a public product beside the engineering milestones: the website, the demo video, the beta program, and the store distribution paths. Milestones and their order are in [ROADMAP.md](ROADMAP.md); the checklists a build must pass are in [RELEASE.md](RELEASE.md).

## Website: reflux.rsxdevelops.dev

The website is release infrastructure, not a task for after 1.0 and not optional. It exists before the beta, carries the beta, and becomes Reflux's permanent public home. When each part is due, and what the site must do before the beta may begin, is in ROADMAP.md ("The Reflux website", M6 to M13); this section keeps its deliverables and its visual direction.

### Stages

| Stage | What it is | Done when (milestone) |
| --- | --- | --- |
| **W0 — Foundation** | Type, color, spacing, and motion rules for the site; the page skeleton; hosting and the domain; an analytics decision, none by default (ROADMAP.md, "The Reflux website", Privacy) | The empty skeleton is deployed at the domain, over HTTPS, and every later page is built from its rules (M6). Built 2026-11-08 (React + Vite, Vercel; WEBSITE.md); redesigned as Website 2.0 in M7; not yet deployed (owner actions, WEBSITE.md) |
| **W1 — Early landing page** | One page: what Reflux is in a sentence, a few real screenshots, supported platforms ("coming"), and a quiet way to hear about the beta | Live on the M6 foundation |
| **W2 — Demo video** | The product demo (below) | Approved by the product owner; embedded on the site before the beta (moved to M8 at M7's closure, and by the 2026-11-11 rebaseline to M11, once the interface is final; the site uses real product renders until then) |
| **W3 — Beta launch** | Beta signup (platform, device, sources, so testers can be chosen for coverage), what the beta is and is not, how to install on each platform, how to report problems, the changelog, privacy policy, support contact | Live before `0.9.0-beta.1` (built in M7: the tester hub, install, feedback, changelog, privacy, support pages; the signup is the store test channels; required for the beta's entry, M12, and operated through it) |
| **W4 — 1.0 site** | The product site: the demo, the core ideas (your media wherever it lives, understood automatically, played well), platforms with store badges and desktop downloads, changelog, privacy, support, legal and attribution | Live with `1.0.0` (M13, presented from its release candidate) |

### Visual direction

The site follows the product's own principle: let the media be colorful; let the interface be calm.

- **Nearly blank.** A near-black or near-white field, generous space, and very few elements per screen. Product artwork, screenshots, and the video provide almost all of the color.
- **Strong typography** carries the hierarchy: size, weight, and tone, not boxes, badges, or rules.
- **Restrained motion**: reveal and continuity only, never decoration; nothing loops for attention; `prefers-reduced-motion` respected.
- **Product first.** Every section shows Reflux doing something real (a library appearing, Play choosing a version, a remote moving focus), not abstract illustrations.
- **Not allowed:** purple or multi-hue gradients, "AI startup" glows and grids, glass everywhere, card mosaics, stock 3D shapes, testimonials before there are users, feature-count walls.
- **RSX Develops** is named as the maker in the footer and on the about/legal pages; the brand on the site is Reflux.
- Fast and accessible: static pages, images sized for the screen, WCAG AA contrast, real text (not text in images), keyboard navigable.

Content the site must carry by W4: supported platforms (Android phone/tablet, Android TV / Google TV, Fire TV, Windows, Linux) and what each needs; the beta call to action until 1.0; the changelog (from `CHANGELOG.md`); privacy policy (what Reflux stores, that it has no account and sends nothing to RSX Develops, what the metadata provider, Trakt, and the user's own servers receive); support; licenses (Reflux's own copyright, proprietary and all rights reserved, apart from the third-party licenses) and Trakt's attribution; and "Support Reflux" (below).

### Support Reflux

Reflux is free, with no paid tier at launch; voluntary donations are how people can support it (DECISIONS.md, 2026-10-19). The site carries a calm "Support Reflux" section: a sentence on why, and a way to give, never a banner, a pop-up, or a nag, and nothing on the site or in the app is held back from those who do not give. The application itself takes no payments and has no donation prompt. The donation destination does not exist yet: choosing a provider and setting it up is a task of this track, done before the section goes live; until then the section is not published and no provider, address, or account is named.

## Demo video

Required for the beta website (W2/W3) and the 1.0 site.

- Shows the benchmark loop on a real-looking library: a folder pointed at → the library appearing with artwork → details → Play picking the right version → resuming on another device or copy → a remote and a controller → keeping something for offline.
- Uses media whose rights allow it (open films such as the Blender open movies, public-domain artwork), never commercial posters or trailers.
- Captured from real builds (the offscreen renderer, the desktop window, device captures), not mock-ups; any generated material is labeled as such in production notes and never shows features that do not exist.
- Produced with the video-generation skill the product owner will name; until then, the shot list and capture plan are the deliverable.
- Short (60–90 s), with captions and no reliance on sound.

**Shot list and capture plan (M11.7, 2026-11-15; production waits for the owner's tool).** Every shot from a real build over the Blender open-films library the site's renders use (`WebsiteShowcaseTest`'s films and artwork):

| # | Shot (about) | Source | Caption |
| --- | --- | --- | --- |
| 1 | First run on the desktop, "Add a folder", the folder chosen (8 s) | The desktop window (window test harness, Linux container so no private screen is caught) | Point Reflux at a folder |
| 2 | The library filling with posters and backdrops (8 s) | The same run | It finds and understands your films |
| 3 | A film's page: description, credits, Resume / From the start / watched / favorite (8 s) | Offscreen renderer, desktop and phone | Everything about it, in one place |
| 4 | Play: the player opening on the right version, the light controls fading (10 s) | Desktop window with libmpv | Press Play |
| 5 | Dragging the timeline, then the mini-player while browsing (8 s) | Desktop window | Keep browsing while you watch |
| 6 | The same film resuming on a phone at the same place (8 s) | Phone 2a capture, `.debug` build, test server | Pick up where you left off |
| 7 | A television by remote: home, the film's page, playback (12 s) | Google TV emulator or the Fire TV Stick capture | Made for the remote |
| 8 | A controller moving through the library (6 s) | Desktop window with a controller | And for a controller |
| 9 | Download for offline, then playing with the network off (8 s) | Phone capture | Keep it for offline |
| 10 | The platforms and where to get Reflux (6 s) | The site's Download page | Android, Fire TV, Windows, Linux |

Captures are made after the M12 candidate is frozen, so the film shows the build testers get; none shows a feature that does not exist, and no commercial title appears.

## Beta program

- **Size:** about 15–20 testers, chosen from the signup for coverage, not the first to arrive: phones and tablets, at least three Google TV / Android TV devices, at least three Fire TV devices on Fire OS, Windows and Linux desktops, local folders, Jellyfin servers, and SMB shares; at least two testers using controllers.
- **Channels:** Google Play closed testing (phones, tablets, Google TV) — an email list or Google Group of the testers; the Amazon Appstore's testing path for Fire TV (Live App Testing); desktop packages from the beta site, checksummed.
- **Versions:** `0.9.0-beta.N` ([VERSIONING.md](VERSIONING.md)); every build has a changelog entry and a tag.
- **Feedback:** an in-app "Send feedback" link (to a form or email) next to an export of the diagnostics log, which is already redacted; a short form per beta (what you played, what failed, what confused you). Reports are triaged into blocker / critical / normal / later.
- **Cadence:** a beta build when there is something worth testing, at most weekly; the changelog says what to look at.
- **Exit:** the M12 exit criteria in ROADMAP.md.

Store testing requirements change; check the current Google Play Console and Amazon Appstore documentation when setting the tracks up, and record what was required in RELEASE.md.

### Beta operations, prepared 2026-11-16 (M12 Phase B; nothing sent, no tester recruited)

**Recruitment text** (for the owner to post; plain text):

> Reflux is looking for about 15 to 20 beta testers. Reflux is a media player for the films and shows you already have: folders, Jellyfin, Plex, and network shares, on Android phones and tablets, Google TV and Android TV, Fire TV, Windows, and Linux. We are choosing testers to cover devices and sources, not first come first served. Tell us: your devices (model and OS), your media sources (folders, Jellyfin, Plex, SMB, WebDAV), whether you use a game controller, and the e-mail address of your Google account (Android) or Amazon account (Fire TV). Android testers must stay opted in to the test for at least 14 days. Reflux sends nothing to its makers; see reflux.rsxdevelops.dev/privacy. Contact: support@rsxdevelops.dev.

**Coverage tracker** (one row per accepted tester; filled from replies only, never invented):

| Tester (initials) | Channel (Play / Amazon LAT / desktop) | Devices | Sources | Controller | Opted in (console date) | Active this build |
| --- | --- | --- | --- | --- | --- | --- |

The coverage targets are the "Size" line above; a gap stays a gap until a real tester fills it.

**The Play clock.** Production access for a personal account needs a closed test with at least 12 testers opted in for 14 continuous days (Play Console dashboard, read 2026-11-16: 0 opted in). The clock starts only when an owner-approved beta build is on the closed track and 12 testers have opted in; recruit 15 or more so a drop-out does not reset it. Opt-ins are counted from the console, never from replies.

**Channels.** Play closed testing: an e-mail list of the testers' Google accounts, the opt-in link published on the website's tester page only when `release/stores.json` records it. Amazon Live App Testing: testers' Amazon account e-mails in the console, installed through Appstore Beta Hub (`release/amazon-appstore.md`, "Tester instructions"). Windows and Linux: the website's Download page, from the verified release record only.

**Triage** (as above): **blocker** (data loss, a crash on launch, playback impossible on a supported target, a privacy breach) → fixed before the next build; **critical** (a main flow broken for a form factor, source, or input) → fixed in the cycle; **normal** → a known issue on the tester page, fixed when it fits; **later** → V1.x. Each report gets one of these, a device and build, and a link to its fix or known-issue entry.

**Release notes per build:** the version's `CHANGELOG.md` section, through `release.py notes` (both stores' "What's new" and the GitHub draft); never retyped.

## Store and distribution tracks

| Target | Channel | Package | Notes |
| --- | --- | --- | --- |
| Android phone, tablet, Android TV / Google TV | Google Play | One app bundle | TV quality review applies to the television listing |
| Fire TV (Fire OS) | Amazon Appstore | APK built from the same source and `versionCode` | Separate listing, review, and device targeting. Devices on Vega OS cannot run it and must not be targeted |
| Windows | GitHub release, linked from the website; signing or the Microsoft Store decided at gate G-WIN before broad 1.0 distribution | Zip with the Java runtime and libmpv | **Unsigned by decision** (DECISIONS.md, 2026-11-08): SHA-256 and provenance; the download page says what Windows will show. The M12 beta uses the direct ZIP (DECISIONS.md, 2026-11-13). libmpv's source published beside each release before the first public download (M7) |
| Linux | GitHub release, linked from the website; distribution packages are V1.x unless the beta shows the tarball cannot serve its users | tar.gz with the Java runtime; the distribution's libmpv (`libmpv2`; Ubuntu 22.04 does not ship it) | SHA-256 and provenance (RELEASE.md) |

### Fire TV / Amazon Appstore plan

Fire TV is a V1 target in its own right (DECISIONS.md, 2026-10-17). It runs the same Android application, but it is not Android TV:

1. **Inspect the developer's hardware first** (a Fire TV Stick 4K and a Fire TV television): model, Fire OS version, and Android API level (`adb shell getprop ro.product.model`, `ro.build.version.sdk`, `ro.build.version.name`). Reflux's `minSdk` is 26 (Android 8): Fire OS 7 (API 28) and Fire OS 8 (API 30) and later qualify; Fire OS 6 (API 25) does not; a Vega OS device does not run Android applications at all. Record the result; do not assume. Done for the stick on 2026-10-18: a Fire TV Stick 4K Plus (`AFTMA08C15`), Fire OS 8.1.8.5, API 30, a 32-bit (`armeabi-v7a`) userland, 1.7 GB of memory (VERIFICATION.md). The television: not yet.
2. **Check what Fire OS lacks or does differently**: no Google Play services (Reflux uses none; keep it that way), no system folder picker (Reflux's own television picker already covers this), storage permissions by API level (on Fire OS 7 `READ_EXTERNAL_STORAGE` also reads sidecar artwork and subtitles), the launcher's banner and icon sizes, the remote's keys (menu, rewind/fast-forward, the microphone button), and Amazon's media-session and audio-focus expectations.
3. **Continue watching on the Fire TV home screen**: evaluated 2026-10-18. Amazon's Watch Activity needs catalog integration, which an application for personal media cannot have, so Fire TV ships without a home-screen continue row (DECISIONS.md, 2026-10-18).
4. **Build and lifecycle validation on the hardware** (M7): install, first run, a folder on a USB drive if supported, Jellyfin, playback including 4K/HDR on the television, leaving and returning with the Home button, sleep and wake. The stick's smoke test (2026-10-18) covered install, first run with a folder, playback including 4K60 HEVC, and Home and Back; USB, Jellyfin from the device, HDR output, and sleep and wake remain.
5. **Appstore preparation** (M6): listing, screenshots at Amazon's sizes, device support list, content rating, privacy policy link, and Amazon's current submission requirements, read from Amazon's documentation at the time. A build installed from outside the Appstore got a generic launcher tile on Fire OS 8, whatever its banner (seen 2026-10-18); community reports say the launcher takes an Appstore application's icon from its listing, which the first Appstore test build should confirm.
6. **Beta through Amazon's testing path** (M12) with Fire TV testers.

Vega OS is tracked separately as a future compatibility track (ROADMAP.md, Platforms).

## Distribution (M11)

M11 prepares every channel above and rehearses them end to end without a public release; the beta that uses them is M12 (planning/M11-DISTRIBUTION-PLAN.md). What changes in this plan: the website is the public entry point for releases and reads one release record (M11.7); the demo video's shot list and captures belong to M11.7 with production waiting for the owner's tool (W2); beta *operations* (the tester routes, templates, opt-in links) are set up in M11 and run in M12; the Google Play closed test (twelve testers, fourteen days, if the account is personal) and Amazon Live App Testing with Appstore Beta Hub are M12's campaign, not M11's rehearsal.

**Done in M11 (2026-11-15):** the Download page is built only from `release/stores.json` and `release/published/<version>.json`: one availability table (Android and Android TV / Google TV → Google Play; Fire TV → Amazon Appstore; Windows and Linux → the GitHub release), the desktop archives with size, SHA-256, commit, and release link, the libmpv source offer beside the Windows zip, the SmartScreen / Smart App Control and Linux libmpv / Secret Service notes, install / update / removal steps, and the checksum and `gh attestation verify` commands; with no published record it says no public build exists yet. No Android file is ever linked. The tester page shows a store's test link only when `stores.json` records one. Amazon's presubmission and test criteria were re-read and are mapped in `release/amazon-appstore.md`; Beta Hub (Fire OS 7+) redeems invitations, and an update that does not appear is fetched with Settings → Account & Profile Settings → Sync Amazon Content. **Open:** the support e-mail (stores require a support contact), the Privacy page's "pre-release" banner at release, Trakt attribution if D5 chooses it. **Store tracks (2026-11-15):** both listings are saved as drafts in the consoles; the Play internal and closed tracks and Amazon Live App Testing wait for the first release-workflow build signed with the owner's upload key (VERIFICATION.md, "M11 console session").
