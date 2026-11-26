# Commit map of the attribution cleanup (2026-11-27)

> **Historical record.** On 2026-11-27 every commit of `main` and `beta-3` was rewritten to remove AI co-author and session trailers from commit messages (the owner's decision; [the morning report in VERIFICATION.md](../../verification/VERIFICATION.md#attribution-cleanup-and-beta-3-testing-handoff-2026-11-27)). Trees, authors, committers and dates are unchanged; only the hashes are new. The documents cite commits by their **old** short hashes: find the new one here. The old commits are kept in the owner's backup bundle, and the tags `v0.9.0-beta.1` and `v0.9.0-beta.2` still point at the original history.

Old (short), new (short), subject. Oldest first.

```
38763a7  38763a7  docs: establish Reflux product foundation
79e0599  79e0599  docs: establish Reflux product foundation
9284ae0  9284ae0  docs: establish Reflux product foundation
2c9f2bd  2c9f2bd  docs: establish Reflux product foundation
fb4cbdb  fb4cbdb  docs: establish Reflux product foundation
465d0c7  465d0c7  docs: establish Reflux product foundation
1ab5550  1ab5550  docs: establish Reflux product foundation
4c305c1  4c305c1  docs: establish Reflux product foundation
20bb695  20bb695  chore: add repository hygiene
9a8af7e  9a8af7e  chore: add repository hygiene
fef665c  fef665c  chore: add repository hygiene
1d6a851  1d6a851  docs: initialize Claude Code agent instructions
92f7cb8  f886c1a  feat(core): Media Core contracts and deterministic identification
0f250d1  591591a  feat(library): local-first library engine with SQLite persistence
3619cb2  404d17c  feat(playback): player contract, default tracks, and libmpv desktop engine
28d5b0a  25804f0  feat(metadata): provider contract, deterministic matching, TMDB, artwork cache
c9ab3dd  6fbaf67  feat(library): background pipeline with change watching and reconnect detection
9f72d9e  90870b2  feat(search): deterministic smart search
31f609b  8390811  feat(library): Home feed, collections, and diagnostics
1194ed9  3144013  feat(sources): catalog sources through the shared identity pipeline
1e20ecf  8a0a9e0  feat(jellyfin): Jellyfin catalog source with two-way watch state
fe04feb  a0d8043  feat(library): unify works that share a provider ID
3a0306a  060b96c  refactor(library): split writes and metadata APIs out of Library
857bb19  8893d5b  feat(cli): developer CLI; fix nested queries; resilient mpv defaults
7385f2b  fe506a6  fix(identify): corpus-driven identification improvements
78cddc6  fed0e75  feat(sources): read-only WebDAV source
183bd17  5d772ae  ci: build and test on Linux (with libmpv) and Windows
23ce8ed  abf3fbe  ci: run on every push
43a70b1  db1ed4a  feat(design): platform-free design system foundations
7d309b9  fe3b0d8  docs: roadmap status, platform blockers, and known gaps
10932b6  298793c  feat(playback): chapters, skip segments, and next-episode autoplay policy
33faffd  0e6f6e8  feat(metadata): match dated daily-show episodes by air date
58772a1  34e6273  feat(sources): read-only SMB source; fix Windows folder-watch noise
fb9ef45  43b16b1  docs: update contributing guide for the implementation stage
741e3d0  91211bc  feat(desktop): first visual slice - experience layer and desktop shell
71d2c39  681471e  feat(desktop): vertical slice 0.2 - verified interaction, controllers, failure states, Linux
8121c8b  37f3006  fix(desktop): hardening before Android - controllers, engine lifecycle, CI
d002ccc  e2929dc  fix(playback): pause works while playback is buffering or seeking
1bb7764  ea99ea0  test(ci): engine and window tests must run where CI installs them; record 0.2 verification
bd8e8ef  1c35305  feat(watch): universal watch state and version handoff
27dc583  7bc6acc  fix(watch): close verification gaps - live Jellyfin exchange, selection order, ordered reports
25b9c7f  3c12632  feat(android): phone and tablet client - first vertical slice
d6e757d  b39b3d6  feat(android): hardening - picture-in-picture, media session, storage states, one warm accent
41efd6f  acfee89  feat(ui): visual refinement - calm on the surface
2b7a21f  5fdc1dc  feat(ui): finish the visual refinement - details page, disabled state, phone search
bed0241  2042a8c  docs(roadmap): the visual refinement's verification, on its final tree
1c2ffd1  02120dd  feat(tv): Android TV / Google TV client - the same experience at ten feet
1a53d38  b4ecb08  feat(offline): Smart Offline - the right copy on the device, as one more version
27c464e  d9b8154  feat(sources): source onboarding and management - servers, shares, and Smart Offline in reach
4bb920e  542fe3f  feat(sources): source parity and library controls - SMB on Android, Jellyfin libraries, sealed connections
a76a41f  5b3d3d0  feat(security): desktop source connections in the system credential store
65f8dcd  54acd35  feat(design): visual reset - a neutral-first cinematic interface; the amber identity retired
9d38ee5  e28e379  fix(ui): real-world validation of the visual reset - subtitles, poster color, scrims, short screens, focus
70c614a  58e25be  feat(ui): one artwork fallback rule for every frame; TV image access left for a decision
9b57883  a54ac6b  docs: Android TV does not request READ_MEDIA_IMAGES in v1
816776b  72628a1  test(live): the Jellyfin live test reads ~/.reflux-live/jellyfin.env
4352958  981653b  fix(playback): subtitle selection after live Jellyfin validation
d0da14b  0f9db7b  fix(playback): a stream cut off mid-playback is not watched to the end
27d7578  99b2b57  docs: record Android live playback validation
4b7f087  ee392f1  docs: decide what playback does when a source goes silent
99a4500  0fbae46  feat(playback): a source that goes silent becomes a stated failure after 30 s of waiting
a023ae1  5e0c111  fix(playback): the check after a stalled playback leaves the server's session alone
d8394a3  56980b3  fix(jellyfin): preserve active playback sessions during scans
d01b5e0  5cc0445  fix(jellyfin): prevent premature played state at 90 percent
4afdffb  5ae5b59  docs(playback): correct the stale session note on checkAvailability
82d259f  4ad2aef  fix(identify): identity keys keep every script
b444ced  cef5f70  fix(scan): a folder a scan could not list keeps what was known below it
7e1b055  6c20867  fix(versions): a source not checked yet is not a source that is away
4b8ce06  e7c2bd8  fix(library): a source removed during its scan leaves no works behind
a9a4692  e9cfc21  fix(mpv): the engine's diagnostic log carries no credentials
a2b3d10  c53f656  fix(ui): long titles are read in full where they identify something
213568b  1f18caa  test(android): a document-tree subfolder that cannot be read keeps its works
a98fc67  1a5663d  test(android): an unreadable document-tree subfolder is reported, not skipped
7ac5ce3  049e24d  docs: record the stress-testing cycle
bd91377  85b653b  test(android): keep a film in the unreadable-subfolder scenario
2a66250  9f7659b  docs: the stress cycle's Android test has run on the emulator
bbe7583  b807ea0  docs: rebaseline the roadmap and establish release governance (M0)
1349af2  9f74b05  feat(watch, versions): "From the start" keeps the place; films' versions can be pinned
9f2d650  b6b7ad4  feat(watch-next): the launcher's Watch Next row follows Continue Watching
ee58837  7080b7b  docs: record Watch Next, its emulator validation, and the Fire TV decision
c8b5820  6631cc4  fix(fire-tv): what the first physical Fire TV found, fixed and guarded
9fe632d  3401845  feat(identify): tell Reflux what a movie or show is, and take it back
fc9656a  71b8b21  fix(platform): stop Fire OS Watch Next publishing and ignore background play
04d9b8f  16d7261  feat(versions): add episode version selection and pinning
a48cd67  e90c037  docs: record the completed episode-version milestone and its verification
f1cacfb  5c8696e  docs: record the Google TV emulator crash triage
250c595  fcbf902  feat(offline): episodes, seasons, a Downloads page, and the desktop
d78390e  5ae342e  fix(smb): reconnect before calling a share away
05a4229  a588c6b  docs: record Smart Offline verification and what it found
847318f  e79fd76  fix(webdav): web shares can be added on Android
e3249db  0bc6cfa  docs: record WebDAV on Android, its decision and verification
d07818d  3032621  feat(jellyfin): removing a server signs its session out
c7cd940  ef58a13  docs: record Jellyfin sign-out on removal, its decision and verification
abce1ac  d73426b  feat(desktop): capabilities are observed, not declared
ff8123e  f1e3a12  docs: record observed desktop capabilities, the HDR decision, and verification
08a160f  250018f  test(desktop): make real-window browsing trustworthy; pages leave the accessibility tree as they go
8bd4bba  9168622  docs: record the real-window browsing causes, fixes, and verification
8690a24  acf31cb  feat: About, legal, and attribution
e41c538  3dff56d  docs: record About, legal, and attribution: decision, notices process, verification
7e6a0e8  528ec13  legal: Reflux is proprietary, all rights reserved; About shows its ownership apart from third-party licenses
bf982c1  0e85e6d  docs: record the ownership notice's verification on Windows, Linux, Google TV, Fire TV, and the Pad 7
4f4a26f  a2db278  release: 0.9.0-alpha.2
6bf6bb4  d3c8500  feat(ui): compose player and chrome controls per device
fc51bd4  ea72245  docs: record the per-device controls decision and its verification
1b6b930  ab56702  feat(ui): an accessibility baseline in shared rules
273e5fe  29ea0f6  fix(ui): a touch does not put the player's controls away for a screen reader
56d3f86  9b53b15  fix(ui): what real TalkBack showed the semantics tests could not
18b9a35  09e46ab  docs: record the accessibility closing pass
936fa42  61c87d3  fix(ui): a remote keeps its place when a source asks about removal
743b407  8814455  docs: record the Fire TV pass
a4435d8  472204c  docs: audit playback compatibility and propose M2P
545f721  d602c65  feat(playback): report what really happened to a playback
f66c5bd  1b19bc7  feat(playback): a shared capability model
1813bf4  9f0b227  feat(playback): the playback planner
1e22dc6  e02189c  fix(playback): judge a version by the track it would play
3ff630b  53b7585  feat(playback): execute the plan and compare it with what happened
b1d4eb7  62643e3  fix(security): keep credentials out of commands, logs, and printed values
05f6559  9358e43  fix(android): place image subtitles on the canvas they were authored for
0234da8  164f136  fix(android): a stream unlike its playlist is not an unreachable file
7cbdf1e  8ef601d  fix(playback): say when a late subtitle never arrives, and keep failed negotiation from re-planning
8f3ceb1  756896a  test(live): drive the installed app from the on-device runner
65ac895  f4bab1a  fix(playback): a track change or re-plan carries on from the exact place
e8e3bac  0a563f5  docs: record the M2P.4 real-application verification
56838cf  010e792  test(desktop): carry a plan out in the real window on a real server
ebfac8b  78248f4  test(mpv): wait for the engine's log before judging it
766149f  b1e99d9  docs: record desktop, Linux, and the server's changing files
0e53b5f  08143aa  docs: the server's changing files are the owner's own remux
224940a  5908128  test(live): explicit tracks on any file with two languages, not only HEVC
249a072  7445c2c  test: a real refused audio output, and how a desktop player goes away
7421dab  c4ea95f  fix(desktop): leaving waits for a player that has only just closed
5e96f53  3eb246d  test: no token in a failure, and films that still have two languages
777afa5  a0bab5f  test(mpv): judge the engine's log by the line that matters
e31b84a  6a52cc2  docs: M2P.4 on a stable library
3529a19  b7db1c6  docs: name what the window tests skip
99ebe63  2e9daa7  docs: say only what is known of the skipped window tests
e652cbc  2107021  test: the physical Linux gate of M2P.4, which refuses anything else
0b78ed0  bf78f0e  docs: close M2P.4 with the physical Linux gate waived
1eb5241  4d147ff  feat(playback): a survey of real media, and its first walk
79fc739  304b2bd  fix(playback): a seek Media3 cannot do no longer loses the place
dbd90e5  589b831  docs: chained SeekHeads on the emulator, with no remux, and the evidence
942130f  f5c16cc  docs: chained SeekHeads validated on the Phone 2a and the Pad 7
a145f59  c1b0d81  test: the real media matrix on Android, and what it found
3c3e07e  eb83007  feat(android): read a server's PGS file, so audio conversion keeps the video
1df2c14  756934a  docs: why Android reads a server's PGS file itself
6f7cf42  8e9cb8d  test: generated fixtures for the formats the library lacks
98fb2a9  b66ba7b  feat(playback): Playback info says why a playback is what it is
de4c869  617a4bd  fix(ui): the Info list's tab fits, and its screen is rendered in a test
7da35c3  326fbcb  docs: M2P complete
92ce305  e71253d  docs: one roadmap, M0 to M10, with M2P complete and M3 next
399809b  1dfa096  docs: the Reflux website is release infrastructure, across M6 to M10
f68c0c8  e01b251  feat(library): source and library inclusion, kept apart from connection and availability
2a9a7be  d230523  feat(sources): Plex, as a catalog source on the same source model
758e52c  7c756b5  feat(ui): six global destinations, placed for every window
bc6bb1f  1d023f8  fix(ui): Left goes to the rail, and Right comes back to where focus was
6261252  b9a5e7b  fix(sources): Plex titles and names as a real server gives them, and a live check
09f8d8b  e6216ad  test(desktop): the real-window tests follow the six destinations
6ebd794  470c0e2  docs: M3 complete
b7a574e  a0abf74  docs: the navigation test count
e5915eb  ed78fc4  feat(ui): Visual System 2.0: semantic tokens, material tiers, and the navigation as functional material
1a40acb  aea7945  docs: Visual System 2.0, and one visual identity with themes deferred to V2
c757809  1b3b7b6  docs: M4 complete
05d2710  8649831  feat(library): canonical descriptions with provenance, source metadata, and a gated provider
d492411  c70b26a  feat(ui): richer movie, show, season, and episode details, and the metadata controls
b8324d8  561b228  feat(sources): Jellyfin pictures at the size Reflux shows them, and a live library-depth check
1ca92e1  03ede4e  fix(core): a Plex token header is redacted like the others
0448ac3  001257b  perf(library): descriptions that cost no scan time and pages that read faster than before
7b267ac  23b8bd5  test: library depth on a real Android runtime, and the season story at tablet and phone sizes
cdbd5f0  4c7874d  docs: library depth, its precedence, cache, requests, privacy, and the provider's terms
b800187  4863a73  docs: M5 complete
1cc707b  e5849b7  feat(release): one release identity, a minified signed Android release, desktop archives, and a release tool
8e5f37c  01289ff  legal: libmpv as Windows builds include it, the bundled Java runtime, and their texts
1717d7e  9a4b5ec  feat(website): the website foundation for reflux.rsxdevelops.dev
fba4590  870f46a  ci: Android, website, and secret-scan jobs, and a manual release workflow with build provenance
23cdd1e  f5e2feb  ci: binutils in the Linux image, which jlink needs to strip the packaged runtime
1fd8b01  12cf1d2  fix(library): a refresh already waiting for a source is joined, not queued again
4da58cc  fe6d3d7  ci: a Windows checkout's line endings do not make a Linux release archive 'modified'
07cfa2f  c1f54c4  chore: gradlew.bat stored as .gitattributes asks, so a fresh checkout is clean
dfb7056  101e9fb  fix(release): SHA256SUMS, the manifest, and provenance are written with LF, so sha256sum -c reads them anywhere
9a95fe8  58bed12  fix(desktop): the Linux archive keeps its launcher and runtime tools executable
6f8c7da  72c51ee  docs: M6 release system, signing, store readiness, privacy and third-party inventories, website, and decisions
3ac4fba  4c67ddb  docs: M6 state in the roadmap and its verification record
2c92c31  3eb1d5d  docs: M6 complete - the release on the Phone 2a, Pad 7, and Fire TV, and release-to-release upgrade
343cb4d  e315a78  docs: M7's authoritative plan - workstreams, order, gates, external dependencies, what moved out, and the M8 entry checklist
0a59f68  dd4d35c  feat(android): the permanent identity dev.rsxdevelops.reflux, declared once and checked by the release tool
5542d88  5aa2c29  feat: the support report - made on request from About, redacted by shape and by name, never sent by Reflux
e5ea67f  528f8ba  test(ui): Refresh details is clicked once Identify's search has landed
8579017  58295e1  fix(android): a subtitle line already showing where a seek lands is drawn there
b16e766  55545a3  build: Reflux's own reproducible LGPL libmpv for Windows, its source beside each release, and the CI and release pipelines
d05001d  794efc6  feat(brand): the Reflux Loop - mark, wordmark, lockup, every platform's icon, and store artwork from one geometry
35b69ad  e52706e  feat(website): Website 2.0 - a prerendered product site in the Reflux brand
f639817  6abcd4b  docs(release): Google Play, Amazon Appstore, and developer-verification readiness for dev.rsxdevelops.reflux
9a81c55  7d17b9a  build(libmpv): the source bundle's file modes are fixed, so it is the same bytes however the cache is mounted
5c7135d  0a90eeb  fix(android): a television saves its support report where a person can reach it
62eb661  b62664e  chore(security): the support report test's fake api_key, reviewed and recorded for gitleaks
c956417  586f2c8  fix(android): lint - the Media3 version and subtitle-decoding calls are marked as the unstable and experimental API they are
f44b999  22867d6  build: the metadata provider's credential reaches release builds from the build environment; Linux CI made honest
0a67eca  c674e8c  docs: TMDB on in public builds under the working non-commercial classification, with its attribution and privacy disclosure
6736299  27ca34e  feat(brand): RSX Develops named as maker with its own wordmark - website footer and terms, the app's About page
0285018  9c0461c  feat(motion): the Reflux mark in motion - the platform launch screen, the loader for genuine waits, and the first-run welcome
4d6538e  870b196  feat(website): the Reflux motion language - the mark drawing itself, the product coming into focus, three copies becoming one film
253f21f  25b8e54  fix(ui): About's maker is read as text in its place, after Reflux, not as an image description leading the stop
557a54b  afd129b  docs: M7 closed - gate-by-gate evidence, the verification record, and the M8 entry checklist with its owner actions
5a55a0a  48cc73d  fix(ui): Home shows the Reflux loader while a large library is first read, not an empty page
11ec1af  a2dd39b  fix(core): the support report keeps a fully HDR-capable display's ranges instead of redacting them as a token
673afff  ec20528  docs: the M7 release walk on the Phone 2a and Pad 7 - launch animation, playback, sources, and the two defects it found
db968e6  c92e512  ci(release): every push is an attested release rehearsal - provenance without a tag, verified against the commit
cfb4865  cb5e806  fix(ui): the phone's Downloads and Sources buttons stand apart instead of touching
43b4725  2237e86  docs: M7's final local evidence - the Phone 2a on the final code, Linux and Windows on cfb4865; M7 closing pending hosted CI
a0ed9e5  fdef5b0  ci: the Windows check runs its Gradle step in bash - PowerShell split -Preflux.android=false and the job failed
38b76e0  50664dc  test(ui): a UI test may take five minutes, as slow CI hardware needs - the toolkit's one-minute default timed out on GitHub
a905f27  09548bb  test(ui): the test-body budget that applies, a notice watched from sign-in, and reports with their causes
17cd272  930f51f  docs: M7 operationally complete - final source a905f27, hosted CI and Release green, attestations verified, final rehearsal and Linux evidence
212150a  6374e82  feat: complete M8 profiles and account isolation
89075d0  a99180e  feat: complete M9 navigation and mini-player
347818a  26be3e4  test(desktop): the mini-player window test says what an error dialog says
e7bc319  951a5b7  fix: a library read that fails after its page is gone is not reported; the website's font no longer re-flows pages
1dbbc11  3bdefcd  docs: record M9's hosted CI result and what the first runs found
43f8f5b  acc29dc  feat: complete M10 player and playback experience
dbb4840  3846a4d  fix: finish M10 qualification fixes
972e3f0  21e7913  docs: plan M11 distribution and release surfaces
4c79794  a82507b  fix: the timeline keeps its width while dragged; Linux CI measures with a phone-like font
4aab2ed  f613d75  feat: M11 distribution and release surfaces
5e5dd8b  f08a175  docs: record M11's rehearsal and evidence; regenerate renders and store screenshots
50f27f0  98a09a1  fix: the television rail shows the whole Reflux mark
8cfb095  3ef2822  test(desktop): read the window region only on Windows in the mini-player test
b92bea2  c466b3e  docs: record M11's console session
1d869cc  f065348  feat: Trakt replaces TMDB as the metadata provider (D5)
e5ecd97  cd915c8  docs(site): the website, legal text, and store material show Reflux as it is
68a99b6  08ab254  fix(ui): a leaving page's late focus offer no longer takes the arriving page's start
bf71793  a9e0020  docs(website): the Vercel project is named reflux
90000ae  5473ca8  fix(ui): on a TV, focus the viewer put on the navigation stays there
897e477  e326c41  docs: CI run 43821371836, the Pad 7's metadata, and the Windows test archive
040edb3  57104cf  fix(ui): a page's top bar stays clear when scrolled beside a side navigation; Settings says when no metadata provider is built in
ee7edef  a31f029  docs: verification of the top-bar scrim, the unavailable-provider sentence, Sources, and Windows keys
7995f45  92487aa  feat(ui): Sources actions on the card beside a side navigation; Reflux's name goes Home; a tablet stays a tablet while typing
0e8b89d  5eb69fa  docs: M11 closure state, Trakt Production, console drafts, device walks, and the start of M12 Phase A
623c73b  cff5fdf  docs: hosted CI on 0e8b89d, and the SQLITE_BUSY intermittent carried to M12
967c1cb  b2a696c  fix(ui): the top bar's scrim holds through the whole bar and fades below it; the mini-player accessibility test is a handheld's
ee54b9a  d1ad5ae  docs: Windows keyboard walk, privacy review against the consoles' definitions, Wave 9 and Wave 0 evidence
3007c3a  ac18a84  test(ui): wait for the Movies page's own heading, not the navigation's label, before reading it
2aa2041  213f272  docs: the owner's decisions to cut 0.9.0-beta.1 for M11's exit, the literal privacy reading, and Android XR opted out
2113fb5  43c3932  release: 0.9.0-beta.1
2755062  c43be8e  fix(release): compare the upload certificate's fingerprint in either form
2ed1437  63a2b34  docs: 0.9.0-beta.1 cut, release-mode run and its verification, the privacy declaration matrix, M11's exit table, and the M12 candidate
23f9721  ea1c428  docs: hosted CI after the 0.9.0-beta.1 cut, with the FirstRunTest intermittent recorded
dca5104  bb83621  docs(site): the privacy page names the device name and identifier sent on sign-in, unencrypted http:// servers, and what removing a source deletes
d27b87a  ce362e5  docs(site): no literal plain-HTTP scheme in the privacy text (the site's own check refuses it)
1666bee  57f5837  docs: M11 closed; 0.9.0-beta.1 sent for Play closed-testing review and Amazon Live App Testing
decbd20  4debe91  release: record the published 0.9.0-beta.1 pre-release; both stores in testing with tester routes
8d09ac3  c981ae5  site: Beta page, beta-aware Download and Home, the road to 1.0, one feedback route
0132021  11275ef  docs: Beta Season freeze, separate 1.0 paths, Amazon V1 runbook, Fire TV scale finding, verification
044c8f0  f6950fb  docs: Beta Season deployment and hosted CI verified
540147c  ee98fa6  gitignore: add brag-output directory to ignore list
c1ca1d1  b58d1c4  Beta 2 Phase 1: profile deletion, Linux libmpv locale, Android surface, Jellyfin addresses
c9c222f  91ad25c  License Reflux under GPL-3.0-or-later
c9014e0  767b728  Website: documentation portal, platform install guides, Download cards, /license
0a0396f  13ee517  Plan Beta 2: the bug and feature register, three phases, and the gate
556887b  7c939b7  docs: add permanent beta bug tracker and phase roadmap
7dbade6  8cd0000  Phase 1 closeout: device and desktop walks, B-001 server source, baselines
77b994c  2c9f010  Beta 2 Phase 2: interaction contract, Reflux keyboard, controller mapping, haptics, Search, cards, profiles
b85cd57  1597f65  docs: Phase 2 plan, interaction contract, tracker, decisions and verification
5312e01  b1fdf26  B-002: size the player's controls window right on a display of another scale
3330ed7  85a3e2f  B-050: stop the joystick API crashing Reflux when a controller reconnects
183eb31  d4c1362  Phase 2 controller walk: Y toggles the sidebar, felt moves and choices, Reflux keyboard cursor
4b9d0e8  a010de8  Phase 2 follow-up: mouse keyboard in Search, closing the mini-player, Details on Play; Linux rumble
b201532  f75ca10  Hint bar: LT hides and shows it, controller-shaped keys, calm motion, the hold ring (B-055, B-052)
d4aa5ee  deee77d  Search redesign (B-057); a remote's held Back only where Android delivers it
61f3f18  629cf1f  Search, second design (B-057): one search line and an index set in type
e5f595e  34d9f96  Device walks: handheld walks skip a television; the TV controller walk finds its card by place
1d28f53  39ec0c0  docs: Phase 2 final verification, follow-ups and Search; F-P4 adaptive keyboard planned for Phase 3
f203b3c  db14027  docs: F-P4 owner decisions (QWERTY only, title completions, system layout, no dictation, physical typing)
b9ddec6  3563bac  docs: record the final forced full check (1,565 tests, 0 failures; lint clean)
9939fab  9dc95fe  B-058: on Linux, hold the embedded player to X11 graphics contexts
3d8dd08  89083f8  B-059: a Search tab clicked with the pointer leaves the typing in the field
07e8b1b  ff3afcd  B-060: panels opened in place take focus, give it back, and close first on Back
eb007b3  3315f94  Device walks: a TV controller walk for any library, a phone Search walk by touch
3786e31  52bfe87  docs: Phase 2 closure: the Linux VM, Google TV emulator and Phone 2a walks; B-056 deferred and DualSense not applicable (owner); B-058 to B-060
9903e86  87d0a12  docs: Phase 2 accepted and closed by the owner
dcef9fb  ed59eaf  Phase 3 M1: last-used profile everywhere, Switch profile and Log out, hover kept apart from focus
b2af2b7  6cce8d1  Phase 3 M2-M4: Trickplay and the right stick, Quick View, featured hero, Atmosphere, adaptive keyboard
c32f50c  bf7834f  Phase 3 closing: device walks, B-062, Quick View on the system's Back, performance and analytics checkpoints
83c8ef6  c346aa9  Website: release notes grouped for people, the next beta's changes marked as not yet downloadable
2dcc967  c2db6b8  Phase 3 final validation fixes: keyboard follows the mouse, profile management, LT hints, mini-player, Linux display scale
9bb69e9  dfc3717  Steam library artwork for Reflux as a non-Steam game (B-070)
da08741  26946bd  docs: Phase 3 final validation and closure; F-003 deferred; Phase 4 handoff
03b4ec2  2c8de73  Right from the sidebar enters a page however soon it is pressed; hidden hints leave no bar; row arrows only for the mouse
3aa5964  d2f40cb  docs: Phase 3 accepted and formally closed by the owner
232ef76  cc91432  docs: Phase 4 documentation consolidation, product roadmap, beta roadmap and notes
684158c  a4a205d  website: Phase 4 overhaul: Features, Roadmap with its easter egg, categorised Releases, new product images
85d6d26  28ce0fc  store-assets: Reflux 0.9.0-beta.2 store screenshots and graphics (LaunchFrame 2.0.0)
1aa0ce8  9e378e7  docs: RELEASE.md unified release process and platform matrix; CLAUDE.md for the new structure; Phase 4 record draft
90a42da  2f0e4d7  release.py notes: a section's preface is not a store item (B-075, found in the Phase 4 rehearsal)
5491309  be2713c  docs: README and CONTRIBUTING point at the current roadmap and beta; Beta 2 readiness assessment
5b9894f  fd725a1  docs: Phase 4 P5 results, tracker summary for B-075, Linux desktop note in the platform matrix
4e9ff8e  fa4d8cc  docs: Phase 4 verification and closure; B-074 second sample
d62e91c  ceddad8  store-assets: Fire TV set from the physical stick; generated media moved to the release archive
95a1990  1c33a87  docs: Beta 2 approved and 0.9.0-beta.2 authorised by the owner; store media moved to the release archive
ecce561  1a5bdde  release: 0.9.0-beta.2
b6ae28f  e57f592  website: Beta 2 text follows the release record (profiles guide, timeline, roadmap summary, report version); tracker B-076
474ca4c  acafe8f  tracker: B-076 diagnosed: deterministic on Linux, bisected to 77b994c, focus pulled back after a successful Right
9604cc5  99b2927  docs: Beta 2 release held on B-076 (hosted Linux CI); the attempt, its runs, and how to resume
dc70958  c76fd33  fix(focus): B-076: an item that became the one to come back to no longer takes focus back when it recomposes
3986850  5396815  test(desktop): B-078: the real-window tests follow Phase 2's Search, controller prompts and Home
a3adb69  b46b029  tracker: B-079: the Linux CI job no longer fits its 60-minute limit (waiting on the owner); CI runs recorded
2f59c37  f57a2f8  build(ui): B-079: the UI tests run in parallel JVMs (half the cores, at most four)
8abd195  5b3693d  build(ui): revert B-079's parallel test JVMs: faster on hosted CI but less reliable
ff98a0d  c030754  ci: B-079: the Linux Check job's limit is 75 minutes (the owner's decision)
6212f86  eb1b3ae  docs: hosted CI green on every job (44994139313 on ff98a0d); Beta 2 gate step 8 holds
b7d5ba2  2a5c043  release: record the published 0.9.0-beta.2 pre-release; Amazon Live App Test submitted; Play upload is the owner's
780ffc6  8a2dc20  docs: website deployed with Beta 2 (main at b7d5ba2, live pages checked); tracker B-081 (a window-test intermittent)
1d65dcf  0420a79  docs: Beta 2 testing active (desktop, Fire TV); Play upload and tester announcement are the owner's; artifacts archived and verified
17aba72  d72862d  CachyOS final audit: Quick View, Linux window identity, sidebar shrink, compact keyboard
2d65eb5  1fa3311  docs: CachyOS audit: hosted CI results; B-090 (Back after the dock's Stop, pre-existing)
0377cab  51762b4  Linux player as one window (B-084); sidebar name toggles (F-P1 correction)
d3d60a7  568bdde  Linux player: the picture drawn in Reflux's own scene through libmpv's render API (B-084, CACHY-011)
1da6aaf  abc7533  Linux player: the docked video clipped to the dock's rounded corners (B-084, CACHY-011)
fec8a4a  fc51d83  docs: CachyOS retired; final state and Windows handover for cachyos-final-audit
4be4c45  8265ea6  Windows acceptance audit: Start closes Quick View again (B-091); two test fixes (B-093, B-097)
5b39a27  3d1f9c1  CI on 4be4c45: tracker anchors, transient link-check failures retried (B-098), B-074 reports its state
3197225  2539a0f  Website link check: gnu.org not asked from CI, by the owner's decision (B-098)
99fff18  df7f80a  Final CachyOS session: B-092 fixed, the integrated player's context replacement (B-099), Reflux keeps its Linux desktop entry (B-102)
406382b  b3ba0f3  CI on 99fff18: the Linux check green (B-092); LinuxDesktopEntryTest compared a raw path, failing on Windows (B-102)
5de9fae  fb5581c  docs: CachyOS closure and Ubuntu handover; beta-3 branch; final CI recorded [skip ci]
814518b  d798538  docs: Windows repository audit before Ubuntu; Ubuntu transition procedure; tracker reconciled [skip ci]
76b526a  958f93c  docs: Ubuntu 26.04.1 validation of the Linux player; B-104
8eaf79c  967bdf7  Merge cachyos-final-audit (76b526a, Ubuntu validation) into beta-3
cbd28ca  569c12b  test(sources): B-105: the folder-watch test waits for the watcher, not 300 ms
54c21a4  20348d3  docs: Beta 3 integration audit; results of the integration's test runs
b823a74  e56a92e  docs: reorganize project documentation
9e5769e  9e7e560  fix(release): restore pinned Windows libmpv sources
156694f  8df06b1  ci: beta-3 writes its own Gradle caches; tests declare the environment they depend on
0043973  56f395d  ci: libmpv sources fetched and verified in the workflow, retried only with their pinned digest
da896c1  9388da2  ci(release): the libmpv cache key covers the build's definition and its pins, not comments
f87b1ae  d2775be  ci: window tests in their own Linux job; one "All checks passed" job stands for every check
fdb68c7  d322555  docs: CI performance audit and its implementation; B-106 registered
0bd84c9  0efc15e  fix(ci): handle blank fontconfig environment input
```
