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
- The **watch state** (`WatchState`) belongs to the work. There is exactly one per work, in Reflux's own database. No file, version, or source owns progress.

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
- The first minute of a *different* timeline does not replace where the user was. Glancing at another cut costs nothing.
- Watching any copy to its end completes the work.

## Choosing the version

Best Version (`VersionSelector`) is deterministic and device-specific. The first criterion that tells two versions apart decides:

1. Availability of its source.
2. Whether this device can play it at all: container, codec, resolution, bit depth, Dolby Vision handling.
3. Continuity: when the user is part-way through a work, a copy on the timeline in progress.
4. The version the user pinned for the work.
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

### What a real server does

The exchange is exercised against a real Jellyfin server by `JellyfinLiveTest` (it runs when a server is named, in the environment or in `~/.reflux-live/jellyfin.env`, as CONTRIBUTING.md describes; `tools/ci/jellyfin-test-server.sh` starts a throwaway one in Docker). Observed on Jellyfin Server 12.2.0, and relied on above:

- A stop reported for an item with no playback session started is accepted and sets the item's position and last-played date. This is how a position reaches the server at a scan.
- Marking an item played clears its position and counts a play.
- Marking it not played clears the position, the play count, and the last-played date. An un-marked record is therefore empty and undated, which is exactly the record Reflux does not let erase its own history.
- The server keeps no position in items only a few minutes long.

Verified end to end there: reading existing state, progress made in Reflux reaching the server, nothing being re-sent once the two agree, a stale server value not overwriting newer progress, progress and "played" made on another client being adopted, and watched/unwatched travelling both ways.

The same test and a walk through the desktop engine also ran against a real home server with a full library (Jellyfin Server 12.2.0, a dedicated test account; ROADMAP.md has the details). There, a local copy and the server's copy of one film were one work: connecting the server beside existing local progress sent nothing, local progress reached the server at the next scan, and the server's newer position resumed the local copy at the same moment. Other server versions, the watch state of episodes, and a work with several copies on the server have not been exercised live.

## Not in scope

- A Reflux account, or synchronization between devices. The state is local; sources that keep watch state are the only thing it is reconciled with.
- Matching content by anything other than cut labels and running times.
- Multi-part files (CD1/CD2) as one timeline.

## Where it lives

| Piece | Code |
| --- | --- |
| Canonical state | `core/model/WatchState.kt` |
| Completion, resume threshold, recording, merging two histories | `core/watch/WatchRules.kt` |
| Timelines and cuts | `core/watch/Timeline.kt` |
| Position handoff | `core/watch/Handoff.kt` |
| Reconciliation with a source | `core/watch/Reconciliation.kt` |
| Best Version | `core/versions/VersionSelector.kt` |
| Persistence, the scan-time comparison, delivery of updates | `library` (`watch_state`, `watch_sync`; `Library.kt`, `WatchSync.kt`, `Playback.kt`) |
| Translation of a server's records | the source adapter (`sources/jellyfin`) |
