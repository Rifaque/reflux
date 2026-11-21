# Amazon Appstore (Fire TV, Fire OS only): owner runbook

Owner steps in console order. Every step is **setup and rehearsal (M11)** unless it says **M12** (real testers). Nothing here submits for review or publishes. Text and artwork come from `release/store-listing.md` and `design/brand/store/`; do not retype them. Amazon's pages were read 2026-11-15; the console is the authority, so recheck a field's wording when you reach it and record any difference in `docs/VERIFICATION.md`.

Fire TV runs Fire OS. Vega OS devices run no Android apps and are never targeted. Fire TV is never "Android TV" in any listing text.

**State on 2026-11-15** (docs/VERIFICATION.md, "M11 console session"): steps 1 and 2 are done (account review complete; the app entry exists, draft, its list shows "Fire OS / Vega OS" until an APK sets the devices); step 5's text, step 6's Fire TV images and icons, the privacy policy URL, and step 8's content rating (complete) are saved; step 10(a) is done (the Stick's sideloaded copies are removed). Open: steps 3, 4, 9, 10(b to d) (they need the release workflow's APK), step 5's category and support contact, the target audience, step 7 (its facts are ready: D5 is decided, Trakt). **The long description saved on 2026-11-15 still has the TMDB sentence**: replace it with `store-listing.md`'s current text (Trakt, and Trakt's attribution as its last line).

**State on 2026-11-16** (docs/VERIFICATION.md, "M11 closure and M12 Phase A start"): the long description now carries `store-listing.md`'s Amazon text (Trakt, no TMDB, no Watch Next clause; autosaved); the short description matches the source exactly; the support e-mail is entered. Step 7 is saved as a draft, not submitted: collects or transfers user data **Yes**; Files and docs collected **Yes** and transferred to a third party **Yes**, purpose App functionality only (Amazon's type covers "information about their files ... such as file names"; its page does not define "collected" for a direct device-to-API call, so the conservative answer is given); account creation **No**. **Reviewed 2026-11-16 (later; docs/PRIVACY.md, "Review against the consoles' own definitions"):** the saved Files and docs answer stays (it is right for Trakt under either reading); whether to add User Info for Jellyfin and Plex sign-ins depends on the reading you choose, the same for both stores. Still yours: the data-deletion question (RSX Develops holds no data; recommended **No**), the category (recommended **Movies & TV**), the target audience, and everything that needs the release APK (steps 3, 4, 9, 10b to d).

**State on 2026-11-17** (docs/VERIFICATION.md, "M11 closure and the M12 beta launch"): steps 3, 4, 7, 9 are done and the remaining App Setup answers are saved: the `0.9.0-beta.1` APK (90031) with **Fire TV-only** targeting (82 Fire TV devices; no tablets, Automotive, or Vega OS); target audience 13+; account creation and data deletion **No**; category Photo & Video; export compliance (the owner). **Live App Test 1 submitted** to the tester group "Reflux" (status "Test submitted"; a first submission can take up to 24 hours; Amazon e-mails the invitations when it is live). Open: step 10's Beta Hub install on the Stick once the test is live, step 11, and M12's real testers (step 12).

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
| 10 | Fire TV Stick 4K Plus | Rehearse: (a) **uninstall every sideloaded non-store `dev.rsxdevelops.reflux` (and old `dev.reflux`)** first, because a differently signed copy blocks the Appstore install ("App already installed"); debug builds now install as `dev.rsxdevelops.reflux.debug` and can stay; (b) install Appstore Beta Hub, accept the invitation, install Reflux from it; (c) upload a **second LAT build** with a higher `versionCode`, update from Beta Hub and confirm the library survived; (d) uninstall | Proves install, update, and removal through the real channel, and confirms the launcher shows the listing's icon rather than the generic tile seen for sideloaded builds (`docs/history/milestones/LAUNCH.md`) | 9; the sideload clean-up | Install, update, and removal each succeed; record the Fire OS version, Beta Hub behaviour, and the launcher icon in `docs/VERIFICATION.md` |
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

## V1 production runbook (prepared 2026-11-17; nothing submitted)

The Live App Test proves the beta channel (the owner installed `0.9.0-beta.1` through Beta Hub on the Fire TV Stick 4K Plus), not a public listing. Amazon production is published together with 1.0 (ROADMAP.md, "Beta Season"), never earlier, and the Live App Test is left as it is until then. Amazon's pages read 2026-11-17: Live App Testing FAQ and "Manage your test", "Add release details", "Staged rollouts".

1. **The build.** 1.0 is `1.0.0` from the release workflow (VERSIONING.md), so its `versionCode` is higher than the beta's 90031 and than anything live; Amazon requires each uploaded binary's version code to be unique and higher than the live version. The beta binary is never promoted to production.
2. **Test it first (recommended).** Upload the `1.0.0` APK to the Live App Test as a new build, let testers update through Beta Hub, and walk the test criteria above on the Stick.
3. **Promote.** On the Live App Testing dashboard, the test's menu, "Promote to upcoming". This copies the tested binary and its metadata into the upcoming version (it overwrites an existing upcoming version) without a re-upload. One binary type only: the test holds an APK and no VPKG (no Vega build exists or is planned for V1).
4. **Complete the listing fields Live App Testing does not need:** long description and keywords (`store-listing.md`), Fire TV screenshots (1920x1080; the device captures of 2026-11-16 in `design/brand/store/screenshots/tv-*.jpg` show the current interface; recapture only if 1.0 changes the TV layout, for example after the scale finding), icons, the privacy policy URL (https://reflux.rsxdevelops.dev/privacy), the support contact, the content rating questionnaire, the reviewer's test source and instructions (the presubmission table above), and the `preferExternal` decision.
5. **Release date.** Set "Release date" to the coordinated 1.0 day so that approval does not publish early (left blank, Amazon publishes as soon as the app passes testing; the date can be changed after submission while it is in the future). Then "Submit for review".
6. **Review.** Submitted, In review, Approved, Live on Appstore; Amazon publishes a few hours after the chosen time. It states no fixed review time and says to contact it after six days, so submit at least a week before the target day.
7. **No staged rollout for 1.0.** Amazon's percentage rollout (1% to 100%, raised by hand) exists only for updates to an app already live with at least 10,000 unique downloads in three years. 1.0 is a full release; staged rollouts may apply to later updates.

Fire OS only: Beta Hub also runs on Vega OS, but Reflux has no Vega build or qualification, and its targeting excludes Vega devices.

## Owner-pending (named)

A Fire TV television (M12 hardware); step 10 (Beta Hub install, a second LAT build, removal) once the test is live. Done 2026-11-17: the category, target audience, the data-deletion answer, the APK upload and targeting, and the Live App Test submission. Done 2026-11-16: the support e-mail; Trakt's production Client ID is the `REFLUX_TRAKT_CLIENT_ID` variable.

## Sources (read 2026-11-15)

developer.amazon.com: "Step 3: Add Appstore Details", "Step 2: Target Your App", "Device Filtering and Compatibility on Fire OS", "Presubmission checklist", "Appstore privacy labels", "Test Criteria for Amazon Appstore Apps", Live App Testing "Prepare app testers" and "Appstore Beta Hub".
