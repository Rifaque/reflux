# Google Play: owner runbook

Owner steps to get Reflux onto Google Play, in order. Each step says where, what to do, why, what must be true first, and what you should see. Text for the listing is in [store-listing.md](store-listing.md) and the artwork in `design/brand/store/`; they are not repeated here. The facts about Play's rules were read on 2026-11-15 (see "Rules this runbook relies on"); Play Console wording changes, so where a screen differs, trust the screen and tell the agent.

Identity: the package is `dev.rsxdevelops.reflux`, permanent once created (it can never be renamed or reused). Debug builds are `dev.rsxdevelops.reflux.debug` and are never uploaded or registered. Android files are **not** GitHub release assets (DECISIONS D1): the signed AAB reaches Play only from the release workflow's artifacts.

**State on 2026-11-15** (docs/VERIFICATION.md, "M11 console session"): steps 1, 2, 3, and 5 are done (personal account; the app exists; the package is registered; Play App Signing is on, no upload key yet); step 7 is saved as a draft except a second phone screenshot; step 8's privacy policy, ads, advertising ID, government, financial, and health forms are saved; step 5a has Android TV added, its review-policy agreement still yours. Open: steps 4, 6, 8's remaining forms, 9. Step 10 is done for the Fire TV Stick and the Phone 2a. **Never upload a dry-run or debug AAB: the first upload fixes the upload key.**

## 1. Account type and developer verification

- **Where:** Play Console → Settings → Developer account (account details), and the Android developer verification page (Play Console → the page named "Android developer verification").
- **Do:** Write down (a) personal or organization, (b) whether identity verification is complete, (c) the account's creation date.
- **Why:** A *personal* account created after 2023-11-13 must run a closed test with at least 12 testers opted in for 14 continuous days before it can apply for production; an organization account is, per the plan's earlier reading, not subject to it (the help page read on 2026-11-15 does not itself say so; confirm in the console). An organization account needs a D-U-N-S number, which can take up to 28 days.
- **Needs:** nothing.
- **Expect:** you can state the account type and verification status. Tell the agent; it records them (M11.2) and decides the M12 tester plan from them.

## 2. Create the app as `dev.rsxdevelops.reflux`

- **Where:** Play Console → Create app.
- **Do:** Name "Reflux"; default language; App (not game); Free; accept the declarations. When the package name is asked for (it is fixed at the first upload of a bundle, or in the app-creation / setup flow depending on the console's current layout), it must be exactly `dev.rsxdevelops.reflux`.
- **Why:** Creating the app in Play registers the package name to your account; this is the registration for Android developer verification (docs/RELEASE.md).
- **Needs:** step 1 (verified account).
- **Expect:** the app exists in the console. If the name is refused as taken, **stop and tell the agent**; do not pick another name.

## 3. Check the verification page

- **Where:** Play Console → Android developer verification.
- **Do:** Find `dev.rsxdevelops.reflux` and read its registration status. If it says it could not be registered automatically, follow the console's manual steps (the help material is inconsistent about whether an upload is needed; follow the screen).
- **Why:** Play packages are registered automatically in about 99% of cases, but the plan requires the owner to *read* it rather than assume.
- **Needs:** step 2. Registration may only show after a first bundle with Play App Signing exists (step 6); recheck then.
- **Expect:** "Registered" (or its current equivalent). Record the date and the wording for VERIFICATION.md. There is nothing to register for `.debug`: it is never distributed, and `adb` installs of development builds are exempt from verification (read 2026-11-15, FAQ).

## 4. Upload key and the GitHub settings

- **Where:** your machine (keytool), then GitHub → Settings of the repository → Secrets and variables → Actions.
- **Do:** Make the key `reflux-upload` (RSA 2048 or larger, long validity, alias of your choice) in a keystore outside the repository; keep an encrypted offline copy. Add the four secrets `REFLUX_UPLOAD_KEYSTORE_BASE64` (the keystore, base64), `REFLUX_UPLOAD_STORE_PASSWORD`, `REFLUX_UPLOAD_KEY_ALIAS`, `REFLUX_UPLOAD_KEY_PASSWORD`. Also add the repository **variable** (not a secret) `REFLUX_UPLOAD_CERT_SHA256`: the upload certificate's SHA-256 (`keytool -list -v -keystore <file> -alias <alias>`), which the release workflow compares the APK's signer against.
- **Why:** Play accepts only bundles signed by the registered upload key; the workflow refuses a bundle signed by anything else. Never put the keystore or passwords in the repository, a chat, or a log.
- **Needs:** nothing, but before the first build given to a store.
- **Expect:** a rehearsal workflow run is unaffected; a release run signs with this key. Tell the agent the SHA-256 (it is not secret) so it is recorded.

## 5. Play App Signing

- **Where:** Play Console → the app → Test and release → App integrity (App signing).
- **Do:** Accept Play App Signing (the default for new apps) when asked at the first upload; let Google generate the app-signing key. Do not upload your own app-signing key.
- **Why:** Google keeps the signing key; a lost or leaked upload key is reset through Play support without breaking installed copies.
- **Needs:** step 4.
- **Expect:** the App integrity page shows the app-signing certificate and the upload certificate. Check the upload certificate's SHA-256 equals step 4's; record the app-signing certificate's SHA-256 (the agent adds it to docs).

## 5a. Before the first upload: Android TV form factor

- **Where:** Play Console → the app → Release → Setup → Advanced settings → Form factors (Android TV).
- **Do:** Add the Android TV form factor so the same bundle serves TV; satisfy what the console lists for it (a TV banner, TV screenshots, the "TV quality" checklist).
- **Why:** Reflux is one app for phones, tablets, Android TV, and Google TV; TV distribution is opt-in per app.
- **Needs:** step 2; the TV banner (`design/brand/store/play-tv-banner-1280x720.png`) and TV screenshots from `design/brand/store/screenshots/`.
- **Expect:** a TV section in the listing and the TV tracks. If the console lists a requirement Reflux does not meet, tell the agent verbatim.

## 6. First bundle to internal testing

- **Where:** Play Console → Testing → Internal testing → Create new release; GitHub → Actions → the Release workflow run.
- **Do:** Once the agent has a release-workflow run from a clean commit for a version (it will say which), download the `release` workflow artifact, take `reflux-<version>-android.aab` (name per release.py), and upload it as the first internal-testing release. Add release notes. Add yourself and your own devices' Google accounts as internal testers and copy the opt-in link.
- **Why:** Internal testing needs no review and proves the upload key, Play App Signing, and the install from Play. Do **not** build locally and upload that: only the workflow's attested bundle is distributed.
- **Needs:** steps 2, 4, 5; a version that follows docs/VERSIONING.md. The agent never cuts the version unasked.
- **Expect:** the release is accepted; the pre-launch report appears; the console shows Android TV and phone devices as supported. Record the pre-launch report's findings. Re-check step 3.

## 7. Store listing

- **Where:** Play Console → Grow → Store presence → Main store listing, and the Android TV listing.
- **Do:** Copy the text from `release/store-listing.md` and upload artwork from `design/brand/store/` (icon 512, feature graphic 1024x500, TV banner 1280x720, screenshots). Do not retype or rephrase it here.
- **Needs:** step 2.
- **Expect:** a complete listing preview.

## 8. App content (policy forms)

- **Where:** Play Console → Policy and programs → App content.
- **Do (each as the form asks; answers come from `docs/PRIVACY.md`, "Store disclosures", and `release/store-listing.md`):**
  - Privacy policy: `https://reflux.rsxdevelops.dev/privacy`.
  - Ads: none.
  - App access: all functionality is available without an account, but a reviewer needs a source with open films. Provide the reviewer a reachable test source (a Jellyfin or share address with a test login) and note that a local folder also works. It must stay up during reviews. This needs **your** test source; the agent cannot invent one.
  - Content rating: complete the IARC questionnaire with the answers in `release/store-listing.md`.
  - Target audience: not directed to children; the age groups per `release/store-listing.md`.
  - Data safety: from `docs/PRIVACY.md`; do not claim more or less than it says.
  - Permission declarations: `READ_MEDIA_VIDEO` (it is requested only on televisions, to list and read the videos in a folder the user picks) and, if Play asks, `WRITE_EPG_DATA` (the launcher's Watch Next row). Use the wording in `release/store-listing.md` if present; otherwise write the reason in one plain sentence and tell the agent so it is added there.
  - Other forms the console shows (government apps, financial features, news, health): not applicable; answer No.
- **Why:** Play will not allow testing tracks beyond internal, nor production, with incomplete forms.
- **Needs:** step 2.
- **Expect:** the App content dashboard shows all items complete. Send the agent any form you could not answer, with the exact question.

## 9. Closed testing setup (rehearse with owner accounts only)

- **Where:** Play Console → Testing → Closed testing → Create track (name it `beta`).
- **Do:** Create the track, attach the same AAB/version as internal testing (or the next), add a tester list containing **only your own accounts**, set the countries, copy the opt-in link. Install through the link on the Phone 2a and check an update later.
- **Why:** This rehearses the route. The 12-tester, 14-day campaign (when step 1 says it applies) is M12, run with real testers; **do not recruit or announce anything now**.
- **Needs:** steps 6 to 8.
- **Expect:** your accounts can install Reflux from Play. Record it.

## 10. Device cleanup before the first store install

- **Where:** each of your devices (Phone 2a, Fire TV Stick, any emulator you will test on).
- **Do:** Uninstall any non-store copy of `dev.rsxdevelops.reflux` before the first install from Play (Settings → Apps → Reflux → Uninstall, or `adb uninstall dev.rsxdevelops.reflux`). Uninstalling erases that copy's local library and watch state on that device. Debug copies are `dev.rsxdevelops.reflux.debug` and can stay (they are a different app).
- **Why:** An install signed by another certificate (debug or the dry-run key) under the permanent name blocks, or is replaced by, a store-signed one ("App not installed" / "package conflicts"), and a debug-signed copy could confuse registration.
- **Needs:** the agent's current audit of what is installed where (VERIFICATION.md). As of 2026-11-15: the Fire TV Stick holds `dev.reflux` (the old identity) and `dev.rsxdevelops.reflux`, both dry-run or debug era installs signed by one non-store certificate; the Pad has none; the Phone 2a was not connected for the audit (the plan's 2026-11-13 reading says it holds a debug-signed, DEBUGGABLE `dev.rsxdevelops.reflux` 0.9.0-alpha.2).
- **Expect:** `adb shell pm list packages dev.rsxdevelops.reflux` shows nothing before the Play install, then the Play copy afterwards.

## Rules this runbook relies on (read 2026-11-15)

- Developer verification: enforcement starts 2026-09-30 for participating stores in Brazil, Indonesia, Singapore, and Thailand on certified Android 7+ devices and broadens in 2027; apps on Play across every form factor must be registered; Play registers about 99% of Play apps automatically (developer.android.com/developer-verification, the FAQ).
- `adb` installs for development are not subject to verification (FAQ, "Will ADB install work without registration?").
- A closed-test requirement for personal accounts created after 2023-11-13: 12 testers, 14 consecutive days (Play Console Help, answer 14151465).
- Apps targeting Android 15 or higher must support 16 KB page sizes on 64-bit devices; from 2027-02-01 an update that does not cannot be released (developer.android.com/guide/practices/page-sizes). Reflux's one native library (`libandroidx.graphics.path.so`) is 16 KB aligned in the release build (checked 2026-11-15).
