# Beta 3 testing handoff (from 2026-11-27)

Development on `beta-3` is **paused for testing**. This page says what is under test, how to get it, what to test, what is already known, and how findings are handled. It does not repeat what other documents own: the beta programme and its released builds are [BETA.md](BETA.md); every issue is in the [tracker](../BETA-BUG-TRACKER.md); the Linux player's acceptance procedure is [LINUX-PLAYBACK-ACCEPTANCE.md](../verification/LINUX-PLAYBACK-ACCEPTANCE.md).

## What is under test

| | |
| --- | --- |
| Code | the tip of `beta-3` on GitHub at the handoff (its SHA is in [VERIFICATION.md](../verification/VERIFICATION.md#attribution-cleanup-and-beta-3-testing-handoff-2026-11-27), "Attribution cleanup and Beta 3 testing handoff") |
| Version string | still **`0.9.0-beta.2`**: no Beta 3 version has been cut (docs/release/VERSIONING.md; only when the owner asks). A build from source says its commit in Settings → About Reflux |
| Released builds | **none of Beta 3.** Testers outside the project have Beta 2 (`0.9.0-beta.2`): the GitHub pre-release (Windows, Linux) and Amazon's Live App Test (Fire TV); Google Play's closed test still serves Beta 1 until the owner uploads Beta 2's bundle ([BETA.md](BETA.md), "Beta 2 testing") |
| Not to be done during the freeze | a release, a version, a tag, a store upload, a merge into `main` |

So there are two kinds of testing: **Beta 2 as released**, by testers, and **`beta-3` from source**, by the owner. A finding says which one it was seen on.

## Getting a `beta-3` build (owner, from source)

| Platform | How | Notes |
| --- | --- | --- |
| Windows, Linux (run) | `./gradlew :desktop:app:run` | needs libmpv: `libmpv2` (Debian/Ubuntu), `mpv-libs` (Fedora), `mpv` (Arch), or Reflux's `libmpv-2.dll` in `.reflux/native/` on Windows (README, "Development") |
| Windows, Linux (packaged, as testers get it) | `./gradlew :desktop:app:packageDesktopArchive` on the target OS | a local test build, never published; on Windows it needs Reflux's libmpv in `build/libmpv` ([RELEASE.md](RELEASE.md), "Desktop artifacts") |
| Android phone, tablet, Google TV, Android TV, Fire TV | the debug build, `dev.rsxdevelops.reflux.debug`, installed **beside** the store build, never over it | the Xiaomi Pad 7 asks for each USB install to be accepted on the device; the Fire TV is on network adb |

## What to test

1. **First run and sources:** a local folder; Jellyfin; Plex; an SMB share; a WebDAV share; Trakt metadata (opt-in, nothing else sent). Adding, removing, a source that is offline and comes back.
2. **Playback and sound:** the formats you have (H.264, HEVC 8/10-bit, 4K, VP9, AV1; AAC, AC-3, E-AC-3, Opus; SRT, ASS, PGS); seek, pause, audio and subtitle menus; a server that converts (Jellyfin) and one that does not; sound on the device's default output, and a change of output while playing.
3. **Continue Watching and resume:** on one device, then **across devices and profiles** on the same Jellyfin server; a work resumed from another copy (Watch state, [WATCH_STATE.md](../product/WATCH_STATE.md)).
4. **Player windows:** fullscreen and back, resizing, the mini-player (its corners, its controls, closing it with RT or B held), picture-in-picture on Android.
5. **Controllers, remotes, keyboard:** the sidebar (Y; Start shrinks it to icons where there is a sidebar), Quick View (X; a remote's Menu), the Reflux keyboard (left stick = Shift, LT = symbols), the PIN pad, hints (LT), the right stick in the player; a physical keyboard never pressing the Reflux keyboard's keys ([INPUT_MODEL.md](../product/INPUT_MODEL.md), "Controller mapping").
6. **Linux:** [LINUX-PLAYBACK-ACCEPTANCE.md](../verification/LINUX-PLAYBACK-ACCEPTANCE.md) is the procedure (renderer, the GPU that decodes, the fallback, the packaged archive's desktop entry).
7. **Profiles and privacy:** switching profiles, PINs, Forgot PIN; statistics stay off unless turned on.

## Already known (do not report again; add evidence to the entry)

- **Testers (Beta 2):** [BETA.md](BETA.md), "Known issues carried to Beta 3", and [BETA-NOTES.md](BETA-NOTES.md), "Known issues".
- **`beta-3` only, open:** [B-104](../BETA-BUG-TRACKER.md#b-104) (on a hybrid laptop whose discrete GPU owns the first render node, Linux's integrated player decodes there: works, costs power); [B-100](../BETA-BUG-TRACKER.md#b-100) (the Linux fallback mini-player's corners are square); [B-101](../BETA-BUG-TRACKER.md#b-101) (once, a closed Reflux stayed running; not reproduced); [B-103](../BETA-BUG-TRACKER.md#b-103) (a second launch opens a second Reflux; the owner's decision); [B-106](../BETA-BUG-TRACKER.md#b-106), [B-077](../BETA-BUG-TRACKER.md#b-077) and [B-107](../BETA-BUG-TRACKER.md#b-107) (test races that can turn CI's Linux check or window tests red; not seen by users).
- **The owner's Beta 2 ledger (B2-001 to B2-014, kept by the owner, not in this repository).** Its topics already in the tracker: Quick View performance ([B-082](../BETA-BUG-TRACKER.md#b-082), fixed on `beta-3`); Linux desktop identity ([B-083](../BETA-BUG-TRACKER.md#b-083), [B-102](../BETA-BUG-TRACKER.md#b-102)); Linux/CachyOS rendering and the mini-player's clipping ([B-084](../BETA-BUG-TRACKER.md#b-084), [B-100](../BETA-BUG-TRACKER.md#b-100)); Android video sizing ([B-088](../BETA-BUG-TRACKER.md#b-088), blocked: needs the film on the phone); the controller sidebar (F-P1, [B-091](../BETA-BUG-TRACKER.md#b-091)); the controller-first keyboard ([B-085](../BETA-BUG-TRACKER.md#b-085)); the PIN keypad ([B-086](../BETA-BUG-TRACKER.md#b-086)). Matched by topic only: re-test each on `beta-3` before calling it fixed. **Not yet in the tracker:** Windows taskbar identity, playback sound in the Ubuntu VM, Continue Watching across devices on Jellyfin, tooltip layering, SQLite snapshot conflicts. Each is entered as a new issue (below) when re-tested; none is assumed open or fixed.

## Reporting a finding

Every finding becomes a tracker entry ([the tracker's rules](../BETA-BUG-TRACKER.md#entering-beta-2-testing-findings)), next free ID **B-108** onward, with:

- the build: `0.9.0-beta.2` as released, or `beta-3` and its commit (Settings → About Reflux);
- the device, operating system and version; on Linux, the desktop, session and GPUs;
- the steps, what was expected, what happened; how often;
- the support report (Settings → About Reflux), `reflux.log`, and a screenshot or recording where it shows something;
- for a `beta-3` finding, whether Beta 2 as released (`1d65dcf`'s code) does it too, before it is called a regression.

Testers write to support@rsxdevelops.dev.

## Triage during the freeze

**Stop testing and fix** only for: a crash; data loss; a security or privacy fault; playback broken on a common format; an installation that fails; a severe compatibility block on a supported platform. The owner decides each.

**Everything else** is entered and waits for the next development cycle, B-106's fix included (it is a test-reliability fault, not one users meet). No feature work, refactoring or CI work during the freeze.
