# Amazon Appstore (Fire TV, Fire OS only): owner runbook

Owner steps in console order. Every step is **setup and rehearsal (M11)** unless it says **M12** (real testers). Nothing here submits for review or publishes. Text and artwork come from `release/store-listing.md` and `design/brand/store/`; do not retype them. Amazon's pages were read 2026-11-15; the console is the authority, so recheck a field's wording when you reach it and record any difference in `docs/VERIFICATION.md`.

Fire TV runs Fire OS. Vega OS devices run no Android apps and are never targeted. Fire TV is never "Android TV" in any listing text.

**State on 2026-11-15** (docs/VERIFICATION.md, "M11 console session"): steps 1 and 2 are done (account review complete; the app entry exists, draft, its list shows "Fire OS / Vega OS" until an APK sets the devices); step 5's text, step 6's Fire TV images and icons, the privacy policy URL, and step 8's content rating (complete) are saved; step 10(a) is done (the Stick's sideloaded copies are removed). Open: steps 3, 4, 9, 10(b to d) (they need the release workflow's APK), step 5's category and support contact, the target audience, step 7 (its facts are ready: D5 is decided, Trakt). **The long description saved on 2026-11-15 still has the TMDB sentence**: replace it with `store-listing.md`'s current text (Trakt, and Trakt's attribution as its last line).

**State on 2026-11-16** (docs/VERIFICATION.md, "M11 closure and M12 Phase A start"): the long description now carries `store-listing.md`'s Amazon text (Trakt, no TMDB, no Watch Next clause; autosaved); the short description matches the source exactly; the support e-mail is entered. Step 7 is saved as a draft, not submitted: collects or transfers user data **Yes**; Files and docs collected **Yes** and transferred to a third party **Yes**, purpose App functionality only (Amazon's type covers "information about their files ... such as file names"; its page does not define "collected" for a direct device-to-API call, so the conservative answer is given); account creation **No**. **Reviewed 2026-11-16 (later; docs/PRIVACY.md, "Review against the consoles' own definitions"):** the saved Files and docs answer stays (it is right for Trakt under either reading); whether to add User Info for Jellyfin and Plex sign-ins depends on the reading you choose, the same for both stores. Still yours: the data-deletion question (RSX Develops holds no data; recommended **No**), the category (recommended **Movies & TV**), the target audience, and everything that needs the release APK (steps 3, 4, 9, 10b to d).

## Order of work

| # | Console | Action | Why | Needs first | Expected result |
| --- | --- | --- | --- | --- | --- |
| 1 | developer.amazon.com | Sign in or create the RSX Develops developer account; complete its profile and verification | Amazon verifies its own accounts; apps can't be created without one | Support e-mail decided | Developer console opens to "Apps & Services" |
| 2 | Console, Add a new app | Create the app for **Fire TV** on **Fire OS** (not Vega). App title `Reflux`. Do not create a second entry for the same package | One entry per package; the same package and `versionCode` as Google Play | 1 | An app entry in draft, no submission started |
| 3 | Release workflow run | Get `reflux-<version>-android.apk` from the release workflow's artifacts (D1: Android is store-only, so not from the public GitHub release). Run `python tools/release/release.py verify <dir>` on it first | The APK is signed with the upload key; Amazon removes that signature and re-signs with its own. Verification re-checks package, `versionCode`, not debuggable, and signer | A release-workflow run on the release commit (an owner-requested build; never change the version by hand, `docs/VERSIONING.md`) | Verified APK: package `dev.rsxdevelops.reflux`, the `versionCode` the Play AAB uses |
| 4 | Console, upload and Supported devices | Upload the APK. Open **Edit supported devices**; keep **Amazon Fire TV** devices that run Fire OS; clear tablets, Amazon Automotive, and anything marked Vega OS; read every "reduced device support" or "target device not available" warning and record it | Manifest filters already require no touchscreen (so the Appstore classifies the app as Fire TV). `minSdk` 26 excludes Fire OS 6 (API 25) and includes Fire OS 7 (28) and 8 (30); targetSdk 36 meets Amazon's floors (Fire OS 16: 36). Selection of Fire TV devices is manual | 3 | A Fire TV-only device list, with the Fire TV Stick 4K Plus (`AFTMA08C15`, Fire OS 8) on it. The Stick's userland is 32-bit: do not clear armeabi-v7a support, and note any warning about 64-bit |
| 5 | Console, listing details | Fill title, short description (first 200 characters are what Fire TV shows), long description, 3 to 5 feature bullets, keywords, category, support e-mail, and privacy policy URL from `store-listing.md`; the metadata sentence names Trakt and the description ends with Trakt's attribution (D5, decided) | One source for both stores | 2 | Listing text complete; length limits (long description 4,000 characters, plain text) accepted |
| 6 | Console, images | Upload from `design/brand/store/`: `amazon-icon-114.png`, `amazon-icon-512.png`, `amazon-firetv-icon-1280x720.png` (no transparency, subject inside 882 x 448), `amazon-firetv-background-1920x1080.png` (required, no transparency), and 3 to 10 TV screenshots at 1920 x 1080 | Fire TV's required set; the four `screenshots/tv-*.jpg` were captured on the Fire TV Stick from the current interface on 2026-11-16 (the console's saved screenshots are the earlier renders: replace them) | Nothing more | Every image accepted. If the console rejects the RGBA PNGs as transparent, export them as RGB |
| 7 | Console, User Data Privacy Questionnaire | Answer from `docs/PRIVACY.md`, "Store disclosures": no account, no advertising ID, nothing collected by RSX Develops; the titles of the user's media (or catalog IDs) sent from the device to Trakt, a third party, over HTTPS, for app functionality, optional ("Online metadata & artwork"); declare any third-party code's collection | Amazon will not publish a new app or update while the questionnaire is incomplete or has unaddressed issues | 2 | Questionnaire complete, no flagged issues; privacy labels preview matches the site's privacy page |
| 8 | Console, content rating | Answer Amazon's 13 fields from `store-listing.md` ("Content rating") | Required to submit and for Live App Testing; changes after publication need a new app file | 2 | Rating result recorded in `docs/RELEASE.md` |
| 9 | Console, Live App Testing (LAT) | Create a test from the uploaded APK; add the owner's own Amazon account as the one tester. Several listing fields (long description, keywords, support contact, privacy) are not part of the LAT flow and can be completed later | LAT is the beta route; the owner's own test rehearses it. **Do not count this as the M12 campaign** | 3 to 8 as far as the flow requires | An invitation e-mail and a test page for the owner's account |
| 10 | Fire TV Stick 4K Plus | Rehearse: (a) **uninstall every sideloaded non-store `dev.rsxdevelops.reflux` (and old `dev.reflux`)** first, because a differently signed copy blocks the Appstore install ("App already installed"); debug builds now install as `dev.rsxdevelops.reflux.debug` and can stay; (b) install Appstore Beta Hub, accept the invitation, install Reflux from it; (c) upload a **second LAT build** with a higher `versionCode`, update from Beta Hub and confirm the library survived; (d) uninstall | Proves install, update, and removal through the real channel, and confirms the launcher shows the listing's icon rather than the generic tile seen for sideloaded builds (`docs/LAUNCH.md`) | 9; the sideload clean-up | Install, update, and removal each succeed; record the Fire OS version, Beta Hub behaviour, and the launcher icon in `docs/VERIFICATION.md` |
| 11 | Presubmission checklist | Walk it (below) and mark each item done or blocked by name | Amazon's own list for submission | 2 to 10 | Every row done or named as owner-pending |
| 12 | **M12** | Invite real testers by e-mail (LAT), send the tester instructions below, collect feedback | Real-device coverage of Fire TV models | M11 rehearsal passed | Not part of M11 |
| 13 | **M13** | Submit for review | Store review is a release action | Release candidate | Not part of M11 |

## Tester instructions (for the website's tester page; M12 operates them)

1. Use a Fire TV on Fire OS 7 or later, signed in to the Amazon account that received the invitation.
2. Install **Appstore Beta Hub** from the Appstore. Open it; invitations appear under "Beta Apps Not Installed". Installing the app accepts the invitation.
3. If an expected app or update does not appear: Settings, **Account & Profile Settings**, **Sync Amazon Content**.
4. If the install says the app is already installed, uninstall the existing Reflux first (a build installed another way is signed differently and blocks it), then install again.
5. Testers who opted out of Amazon's tester e-mails need the opt-in link before they receive invitations.

## Presubmission checklist (Amazon, read 2026-11-15) mapped to Reflux

| Amazon item | Reflux state |
| --- | --- |
| App content meets the content policy | A player for the user's own media; no content shipped |
| App preparation (binary) | Verified APK from the release workflow; the manifest needs no touchscreen and no camera permission |
| User data privacy | Step 7 |
| Images | Step 6 |
| Intellectual property documentation | Third-party notices and attribution on the website; Trakt's attribution in the app, the listing, and the website |
| Testing on a physical device and the test criteria | Fire TV Stick 4K Plus. Criteria to walk: D-pad moves a visible focus in all four directions, no screen traps the user, Back returns to the previous screen, media buttons (rewind, fast-forward, play/pause) act on playback, the app exits on Home within about 2 seconds, install size under 4 GB, no force close, graceful behaviour without optional capabilities |
| Device targeting in the manifest | Touchscreen not required (done); device list in step 4 |
| Installation location | Amazon recommends `preferExternal` on Fire OS; the manifest does not set it. Android build change, owned outside this runbook; decide before the first submission |
| Security profile | Reflux uses no Amazon APIs requiring one |
| Compile errors (method count) | Not applicable at Reflux's size; R8 is on |
| Minimum functionality, test credentials | The app is empty without media: provide a reachable test source (open films) and instructions for the reviewer, and keep it up during review |

## Owner-pending (named)

The category and target audience; the data-deletion answer; the reviewer test source; a Fire TV television (M12 hardware); every APK-dependent step (needs an authorised build). Done 2026-11-16: the support e-mail; Trakt's production Client ID is the `REFLUX_TRAKT_CLIENT_ID` variable.

## Sources (read 2026-11-15)

developer.amazon.com: "Step 3: Add Appstore Details", "Step 2: Target Your App", "Device Filtering and Compatibility on Fire OS", "Presubmission checklist", "Appstore privacy labels", "Test Criteria for Amazon Appstore Apps", Live App Testing "Prepare app testers" and "Appstore Beta Hub".
