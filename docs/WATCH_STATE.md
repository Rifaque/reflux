# Watch State and Version Handoff

> **Playback progress follows the media work, not the physical file or source.**

Someone who watches a film from a local file to 00:30:12 and later presses Play where that file is not available continues at 00:30:12 from whichever copy Reflux can reach. They do not choose a source and they do not seek.

## The model

```text
Media work (movie, episode)
    |
    +-- Unified watch state          one record, Reflux's own
    |
    +-- Version (a cut, an encode)
    |      +-- at Source A
    +-- Version
           +-- at Source B
```

- A **work** is what the media is. An episode is a work; a show and a season are not played and have no watch state of their own.
- A **version** is one playable copy at one location. A work has as many as its sources hold.
- The **watch state** (`WatchState`) belongs to the work, for one viewer. There is exactly one per work and profile, in Reflux's own database ("Profiles", below). No file, version, or source owns progress.

The canonical state holds:

| Field | Meaning |
| --- | --- |
| `positionMs` | Where the user is, as a time on the timeline of the copy that was playing |
| `durationMs`, `cut`, `versionId` | That timeline: how long the copy runs, which cut it is, and which version it was (it may be gone) |
| `completed`, `playCount` | Whether the work has been watched, and how often |
| `lastPlayedAtEpochMs` | When it was last played |
| `updatedAtEpochMs` | When the user last changed the state, by playing or by marking it, wherever that happened |

Nothing in it names a kind of source.

## Timelines and cuts

A position is only meaningful on a timeline. Two copies share a **timeline** when a position in one is the same moment in the other, and that is a property of the cut, not of the file:

- The same cut in another encode, container, resolution, or on another source is the same timeline.
- A different cut (theatrical and extended) is a different timeline.
- A copy whose running time is materially different is a different timeline, whatever it is called.

`Timeline` is a cut plus a running time, and `Timelines.compare` relates two of them:

| Result | When |
| --- | --- |
| `SAME` | The running times agree within the tolerance, and the two are not named as different cuts |
| `DIFFERENT` | Two different named cuts; or running times further apart than the tolerance; or a named cut against the ordinary cut with a running time missing |
| `UNVERIFIED` | Nothing says they differ, but a running time is missing to confirm it |

The tolerance is 1% of the longer running time and at least 15 seconds: encodes of one cut differ by seconds, different edits by minutes.

An edition label is free text. Only labels that say the film was *edited* differently name a cut (`Cuts`): extended, director's cut, unrated, uncut, final cut, special edition, ultimate, redux, rogue cut. Labels about the transfer, the framing, or the packaging (remastered, IMAX, open matte, criterion, anniversary edition) and "theatrical" describe the ordinary cut. A label Reflux does not recognize, such as a server's name for one of its files, makes no claim, and the running times decide.

This is a contract, not forensics. Reflux does not fingerprint content. A finer notion of equivalence can replace `Timelines.compare` without changing anything that depends on it.

## Handoff: where playback starts

`Handoff.resume` carries the work's position to the version about to play (`ResumePoint`):

| Basis | Situation | Playback starts |
| --- | --- | --- |
| `SAME_VERSION` | The version the position was measured on, as long as it still runs as long. A version is a location, and a file replaced in place by another cut is treated as the other cut it is | At the position |
| `SAME_TIMELINE` | Another copy on the same timeline | At the position |
| `UNVERIFIED` | Another copy with nothing known against it | At the position: a copy that could not be inspected is not held against the user |
| `ADJUSTED` | A different timeline, both running times known | At the latest point that cannot be past what the user has seen: the same position in a longer copy, the position minus the difference in running time in a shorter one |
| `NOT_TRANSFERABLE` | A different timeline with nothing to measure the difference by, or an adjusted position too early to be worth resuming | At the beginning; the remembered position is kept |
| `NONE` | Nothing in progress | At the beginning |

The reasoning behind `ADJUSTED`: cuts of a work differ by material that is in one and not in the other. In a longer copy, whatever was added before the user's moment only moves that moment later. In a shorter one, at most the difference in running time is missing before it. Either way the user may see a little again and is never put past something unseen. The player says so when this happens.

Recording follows the same rule in the other direction:

- A report from a playing version stores the position together with that version's timeline.
- The first minute of a playback does not replace a place worth resuming: not on a *different* timeline (glancing at another cut costs nothing), and not on the same one ("From the start", then leaving within a minute, keeps where the user was; DECISIONS.md, 2026-10-17). Past the first minute, the new position is where the user is. A source that keeps watch state is told the place Reflux kept, not the seconds played, so it cannot hand them back at the next scan.
- Watching any copy to its end completes the work.
- A playback that never got going (still opening when it was left, or failed to open) records nothing: the engine has no position of the viewer's to report, and the work's progress stays where it was.
- Trying again after a playback failed (DECISIONS.md, 2026-10-16) records the position reached as progress, never as a stop, so it cannot complete a work wherever it happened. It then plans afresh as *continuing*: the position is where the viewer is even past 90% of the running time, where pressing Play would start the work over (`WatchRules.resumePosition`, `planPlayback(continuing = true)`).

## Choosing the version

Best Version (`VersionSelector`) is deterministic and device-specific. The first criterion that tells two versions apart decides:

1. Availability of its source: one known to be away sets a copy back; one not checked yet (just added, or signed in to again) does not.
2. Whether this device can play it at all: container, codec, resolution, bit depth, Dolby Vision handling.
3. Continuity: when the user is part-way through a work, a copy on the timeline in progress.
4. The version the user pinned for the work ("Always play this" on a film's or an episode's versions; "Let Reflux choose" removes it). Pinning a copy kept on the device pins the version it was made from. Where something above outranks the pin, the versions list says so and the pin is kept.
5. How well this device plays it: fewer compromises such as software decoding, tone mapping, or a Dolby Vision base layer.
6. Resolution, as far as this display can show it.
7. Dynamic range, as far as this display can show it.
8. Audio, as far as this device can decode, pass through, and output it.
9. Locality: on the device, then the local network, then remote.
10. Size, as a stand-in for bitrate.

In short: what can be played here; the cut in progress; what the user asked for; quality as this device delivers it; and then the closest copy.

**Local is preferred among versions that are equivalent by everything above it, and never over something materially better.** It is a tie-breaker placed where the ties are: after what the device can actually show and play, before what it cannot tell apart.

Two placements are deliberate:

- **Continuity outranks a pinned version.** Resuming continues what was being watched; a pin is a standing preference and applies when a work is started. Neither outranks being able to play at all, and a version the user picks by hand for one playback bypasses the ranking altogether.
- **A pinned version outranks "plays better".** How well something plays is a judgment Reflux makes, and a pin is the user disagreeing with it. A pin never makes Reflux play something the device cannot play.

There is no rule that a higher resolution, a newer codec, or a higher bit depth is better. H.264 and HEVC are codecs, x264 and x265 are encoders, 8 and 10 bits are bit depths; none of them is quality in itself, and each matters only through what this device does with it.

| Versions | Device | Chosen | Decided by |
| --- | --- | --- | --- |
| Local 1080p H.264 8-bit; server 1080p H.264 8-bit | Any | Local | Locality |
| Local 4K HEVC 10-bit; server 1080p H.264 | No HEVC decoder | Server | Compatibility |
| Local 4K HEVC 10-bit; server 1080p H.264 | HEVC only in software | Server | Fewer compromises |
| Local 4K HEVC 10-bit; server 1080p H.264 | Hardware HEVC, 4K display | Local | Resolution |
| Local 1080p; server 4K HDR | 4K HDR display, hardware decoding | Server | Resolution |
| Local 1080p H.264 8-bit; server 1080p HEVC 10-bit, both SDR | Plays both | Local | Locality |
| Local theatrical 4K; server extended 1080p; the user is an hour into the extended cut | Plays both | Server | Continuity |
| The same, with the theatrical version pinned | Plays both | Server when resuming, local when starting over | Continuity; the pin |
| 4K HDR pinned; 1080p SDR also there | SDR display, HEVC in software | The pinned 4K HDR | The pin |

## From Play to the first frame

```text
Continue Watching / Play
        |
        v
The work                         Library.planPlayback
        |
        v
Best available version           VersionSelector
        |
        v
The work's watch state           Handoff.resume
        |
        v
Playback at the right position
```

This is the same for a local folder, a media server, an SMB or WebDAV share, and whatever comes later. The experience layer has no logic per source: it asks for a plan and plays it.

## Sources that keep their own watch state

```text
          Canonical Reflux watch state
              /                 \
     Playback in Reflux      Source records
                                   |
                              (e.g. Jellyfin)
```

Some sources record what the user watched. Their adapter translates the native record into `SourceUserState` (played, position, play count, last played, favorite) and accepts updates through `WatchStateSyncSource`. Nothing above the adapter knows what the source is.

A source's record is one more witness of what the user did. It is not Reflux's state, and `WatchReconciler` decides what it changes. For each item Reflux remembers what the source said at the last comparison (`SyncBaseline`) and when the two last had nothing left to settle:

| Source since the last comparison | Reflux since then | Outcome |
| --- | --- | --- |
| Says the same as Reflux | Either | Nothing |
| Unchanged | Unchanged | Reflux's state stays; nothing is sent |
| Unchanged | Changed | Reflux's state stays; the source is behind and is told |
| Changed | Unchanged | The source's record is news and is adopted, unless it is empty and undated (see below) |
| Changed | Changed | The later one is where the user is. An undated source record cannot show that it is later |
| First comparison | No state | The source's record is adopted |
| First comparison | Has state | A dated, later source record is adopted. Otherwise Reflux's state stays, and the source is told nothing |

Consequences:

- **A source value that was already weighed never overwrites anything again.** Only a change is news.
- **A source falling silent erases nothing.** A record that has become empty and carries no date is a work that was un-marked on the server, or a server that was reset or rebuilt, and nothing tells them apart. Reflux keeps its history. The price is that un-marking a work on a server's own client does not un-mark it in Reflux.
- Comparing "changed or not" does not depend on the two clocks agreeing. Clocks are only compared when both sides changed.
- Connecting a server never rewrites what the server knows. Updates flow to a source only for what changed in Reflux after the two were first compared.
- A position is adopted with the timeline of the source's own copy, and is only sent to a source whose copy is on the timeline it was measured on. That the work was watched is sent regardless of the cut.
- An update that cannot be delivered stays pending and is tried at the next scan. One that was accepted and had no effect is not repeated, and the source's old record is still not believed.
- Reflux's state is written first and never waits for a source. A source being away, removed, or rebuilt does not touch watch history.
- Favorites reported by a source are added; a source never removes one.

While Reflux plays a version from such a source, progress is also reported to it directly (`SyncingWatchReporter`), best-effort, one report at a time and in order, so that a stop is never overtaken by the progress report before it.

A scan that finds a source behind brings its position up to date with `updatePosition`, which is not a stop: an adapter with a playback of that item open keeps it open (Jellyfin sends the position as that playback's progress). As a stop it ended the playback's session on a real server, and the playback's next report opened another, which the server counted as a second play (seen 2026-10-17: the library's first refresh read the server just before Play was pressed and delivered the position two seconds into the playback). A scan finds a source behind during a playback when it read the source before the playback's first report, or when reports were lost while the source was away.

**A position is never what makes a source count a work watched.** Whether a work is watched is Reflux's own rule: 90% of the running time at the stop (`WatchRules.COMPLETION_RATIO`). A source may apply a rule of its own to every position it hears: Jellyfin marks an item played, and clears its position, once a progress report or a stop passes its maximum resume percentage (90% unless an administrator changed it). Reported as they were, Reflux's reports near the end made the server's record say "played" while the viewer went back and left at thirty minutes; the next scan took that record, dated after Reflux's own stop, for news and marked the work watched in Reflux (seen 2026-10-17). So:

- A source says where it marks a work played (`WatchStateSyncSource.marksPlayedPast`; Jellyfin: 90%), and every position Reflux tells it, whether progress, a stop, or a scan's update, stays 5% of the running time short of that (`WatchRules.reportedPosition`). The margin covers the server measuring against its own running time of the work, which can differ from the copy's.
- A playback that finished by Reflux's rule is reported as a stop at the end of the source's copy, so the source marks it played by its own rule, as its own clients do, and counts the one play its session already counted.
- Reflux's own position is never changed. The reconciler compares and sends it as the source is told it, so a source holding it just short of its threshold agrees with Reflux instead of overwriting it, also during a playback that went past 90% without finishing.
- A "played" on the source is still news like any other change: another client, or the server itself, marked the work played.

### What a real server does

The exchange is exercised against a real Jellyfin server by `JellyfinLiveTest` (it runs when a server is named, in the environment or in `~/.reflux-live/jellyfin.env`, as CONTRIBUTING.md describes; `tools/ci/jellyfin-test-server.sh` starts a throwaway one in Docker). Observed on Jellyfin Server 12.2.0, and relied on above:

- A stop reported for an item with no playback session started is accepted and sets the item's position and last-played date. This is how a position reaches the server at a scan.
- Marking an item played clears its position and counts a play.
- Marking it not played clears the position, the play count, and the last-played date. An un-marked record is therefore empty and undated, which is exactly the record Reflux does not let erase its own history.
- The server keeps no position in items only a few minutes long.
- Each playback session opened (the first progress report of a playback) counts a play, whether or not the item ends up played. The position sent with that first report is not stored; those of later reports are, at once. A stop ends the session.
- A progress report or a stop past 90% of the item's running time marks it played and clears its position, with no stop needed and no second play counted for that session.

Verified end to end there: reading existing state, progress made in Reflux reaching the server, nothing being re-sent once the two agree, a stale server value not overwriting newer progress, progress and "played" made on another client being adopted, and watched/unwatched travelling both ways.

The same test and a walk through the desktop engine also ran against a real home server with a full library (Jellyfin Server 12.2.0, a dedicated test account; VERIFICATION.md has the details). There, a local copy and the server's copy of one film were one work: connecting the server beside existing local progress sent nothing, local progress reached the server at the next scan, and the server's newer position resumed the local copy at the same moment. Episodes of a show, and a film with two copies of different running times on the server, behaved as described above: Next Up followed the episodes' state, and a position travelled only to the server copy on its own timeline while "watched" reached both. Other server versions have not been exercised live.

## Profiles

Since M8 (DECISIONS.md, 2026-11-11) every viewer of an installation is a **profile**: a local identity, never an account with anyone. A new installation has one, "Main", which is never mentioned until a second exists. Everything above is per profile; nothing else about the model changes.

| Whose | What | Where |
| --- | --- | --- |
| The installation's, shared by every profile | Works, versions, tracks, metadata, artwork, Identify's corrections, provider collections, sources that are nobody's account (folders, SMB and WebDAV shares, a Plex server open to the network), server addresses (`server_endpoint`), copies of shared sources, device settings (online lookups, Media Atmosphere) | `item`, `version`, `metadata`, `source` with no `profile_id`, `server_endpoint`, `download` with no `profile_id`, `setting` |
| One profile's | Watch state, what its servers last said (`watch_sync`), favorites, collections, pinned versions, its own preferences, its server accounts, copies of its accounts' media, its PIN, when it last cleared its history | Every viewer-state table carries `profile_id` (`ProfileMigrationTest` fails when one does not, and when a table is added without deciding whose it is) |

**Accounts.** A Jellyfin user or a Plex account (or token) signed in to is the profile's that signed in: `source.profile_id`. A server's address is entered once (`server_endpoint`) and offered to every profile, each signing in with its own account; two profiles on one server are two account sources, and the same film is one work with each profile seeing only its own account's copy. An account already signed in on the device in one profile cannot be signed in from another (`ConnectResult.InAnotherProfile`): an account's server record is one viewer's. A profile signing in to a server as someone other than it was is told so on the form, and that another person wants a profile of their own. Signing out removes the account source; the profile's history stays the profile's. When a profile is deleted, a shared server whose records were compared with it is compared with nobody (`sync_profile_id` cleared): no other profile inherits the server's record of that viewer.

**One device identity per account.** Jellyfin keeps sessions per `DeviceId`, Plex per client identifier, and Reflux finds its own session by that identity (`JellyfinPlayback`). Each account signs in as a device of its own, derived from the installation's identifier, the server, and the account (`AccountDevices`; a Plex token by a SHA-256 digest of it), so two accounts never share one. A Plex connection made with a token alone has no account name, so its *source* is also told apart by a SHA-256 digest of the token (`PlexSource.idOf`): two tokens on one server are two sources in two profiles, the same token again is the same source, and neither is the source a server open to the network is. The token itself is never in an identifier. A token connection stored before M8 keeps the identity its library rows name until it is signed in to again. Observed on a real Jellyfin 12.4.0 (VERIFICATION.md, M8): with one shared identity a second account's sign-in left the first signed in, but the first account's lookup of "this device's session" listed the second account's session first; with per-account identities each lookup finds only its own. A library from before M8 keeps its single account's identity; where two accounts were on one server, all but the first are asked to sign in again, which gives each its own (`SourceConnections.adoptProfiles`).

**Reconciliation.** A source's own records are compared with the watch state of exactly one profile: its account's owner, or for a shared server that keeps watch state the profile that added it (`source.sync_profile_id`). Every profile's accounts are scanned and reconciled whichever profile is active, so a profile is current when it is next opened; only what is shown is the active profile's. Updates are told to a source only for its own profile's viewing: another profile's playback or marks never reach it (`SyncingWatchReporter`, `setWatchedEverywhere`).

**A playback belongs to the profile that started it.** `SyncingWatchReporter` captures the active profile when it is made and writes every report there, whatever profile is active later. Choosing another profile while something plays stops the playback first, which records its stop for the profile it was playing for.

**What each profile sees.** `version.visible` is recomputed with inclusion (`recomputeVisibility`, `recomputeInclusion`) from the active profile: a version is visible when its library's source is shared or the active profile's own, and a copy kept on the device when it came from a shared source or from the active profile's account. Inclusion requires visibility, so every read of the unified library is bound to it; reads of one source's own library require it as well. `ProfileIsolationTest` holds this two ways: every read model the library offers is swept under each of two profiles with their own accounts on one server (no title only on the other's account, no viewing of the other's), and every query in `Library.sq` that reads versions or viewer state is checked to be bound to the active profile, or to be on a short list of installation-wide queries, each with its reason.

**PIN.** Optional, four digits. Kept as a salt and an HMAC-SHA256 verifier under a key the platform's secure store holds (`SealedPinKey` through the platform's `ConfigSeal`: Windows Credential Manager, the Secret Service on Linux, a key in the Android keystore), never as the PIN; never logged, exported, or in a support report. Five wrong entries are free, then the profile waits 30 seconds, doubling to 15 minutes; the count is kept with the profile, so restarting does not reset it (`PinPolicy`). It is a privacy lock between people sharing a device, not a credential: the library database is not encrypted, and ten thousand values cannot resist someone who can read the device's storage and its key. Where Linux has no Secret Service the key is kept as it is, and the PIN locks the interface only (PRIVACY.md). A forgotten PIN is not recovered. The gate offers **Forgot PIN**: after an explicit confirmation the profile is started over (`Library.resetProfile`), losing its PIN and, because the PIN kept them private, everything it kept: watch history, what its servers last said, favorites, collections, pinned versions, preferences, its downloads, and this device's sign-ins to its server accounts (ended as removing a source ends them). The profile and its name stay, open and usable, and can be given a new PIN. No server's own history, media file, shared source, server address, or other profile changes. This is also the way back when the platform's credential store was reset and the PIN key with it. Another profile can still delete a profile with a PIN without knowing it; either way nothing the PIN protected is shown.

**Who is watching.** While Reflux asks, the page and the navigation behind the question are not composed at all, so nothing there can be reached by a finger, a pointer, a keyboard, or assistive technology (`ProfileBoundaryTest`); and when the viewer changes, the last one's search text, last playback report, and notice go with them. A profile with a PIN opens locked, at start and whenever it is chosen. A television with several profiles asks who is watching at every start; a phone or a computer opens the profile it had. With one profile nothing is asked.

## Clearing local watch history

A profile's "Clear watch history" (Settings) forgets Reflux's own record of what it watched: every position, watched mark, and play count, and with them Continue Watching, Next Up, and the launcher row. It changes nothing on any server, deletes no media, download, favorite, or collection, and touches no other profile (`Library.clearWatchHistory`).

The cleared history must not come back from the servers at the next scan, and must not be sent to them. Two rules, in the reconciler itself (`WatchReconciler.reconcile(clearedAtEpochMs)`), so there is still one watch-state engine:

- What a server last said is kept (`watch_sync`). A server record unchanged since then is not news, so it is not adopted; and Reflux's cleared state is not a change of the viewer's, so nothing is sent.
- The moment of clearing is kept with the profile (`profile.history_cleared_at`). A server record that cannot show it is newer than that, dated at or before it or undated, is not news either, even at a first comparison (a server that was away when the viewer cleared, or newly signed in to).

What happens on a server after the clearing (watched on its own client the next day) is news as usual and is adopted; what the viewer watches in Reflux after it is sent as usual.

## Outside the application

On Android TV and Google TV the launcher's Watch Next row shows what the active profile's Continue Watching shows, derived from the same computation (`Library.continuations`, `WatchNext.programs(library)`) each time Reflux brings it up to date. Each card names its profile (`reflux://watch/<profile>/<work>`), and opens Reflux in that profile and on that work without asking who is watching; a profile that has a PIN now is asked for it first. A profile with a PIN publishes nothing there: the launcher is outside the lock. Switching profiles rewrites the row. A card a viewer removed from the launcher is remembered per profile and work (`WatchNextDismissals`), so it never hides another viewer's card. A link naming a work the profile cannot see opens nothing of it (`Library.seenByActiveProfile`). It is a view of the canonical state, not a record of its own: the launcher's rows are only compared with what should be there, a work removed from the launcher is only kept off it (until it is played again), and nothing on the launcher changes a position or marks a work watched (DECISIONS.md, 2026-10-18).

## Not in scope

- A Reflux account, or synchronization or transfer of profiles between devices (Reflux 2.0, with no backend; ROADMAP.md). The state is local; sources that keep watch state are the only thing it is reconciled with.
- A special guest profile, and Plex Home managed users (DECISIONS.md, 2026-11-11).
- Matching content by anything other than cut labels and running times.
- Multi-part files (CD1/CD2) as one timeline.

## Where it lives

| Piece | Code |
| --- | --- |
| Canonical state | `core/model/WatchState.kt` |
| Profiles, PINs, clearing, endpoints | `library` (`Profiles.kt`, `SealedPinKey`); `core/profile` (`AccountDevices`, `PinPolicy`); schema migration 7 (`7.sqm`) |
| Completion, resume threshold, recording, merging two histories | `core/watch/WatchRules.kt` |
| Timelines and cuts | `core/watch/Timeline.kt` |
| Position handoff | `core/watch/Handoff.kt` |
| Reconciliation with a source | `core/watch/Reconciliation.kt` |
| Best Version | `core/versions/VersionSelector.kt` |
| Persistence, the scan-time comparison, delivery of updates | `library` (`watch_state`, `watch_sync`; `Library.kt`, `WatchSync.kt`, `Playback.kt`) |
| Translation of a server's records | the source adapter (`sources/jellyfin`) |
| The launcher's Watch Next row | `library` (`WatchNext.kt`, `Library.continuations`); the Android shell (`launcher/WatchNextPublisher.kt`) |
