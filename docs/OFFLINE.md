# Smart Offline

Reflux can keep the right copy of something on the device, so that it plays without its source. The user says *that* they want it; which version, from which source, and whether it fits is Reflux's to work out.

This is not a download manager. There is no list of files to administer and no question about codecs. There is one action where a film, an episode, or a season is, one sheet that proposes what to keep and is confirmed, and a Downloads page with the copies that exist.

## In the application

1. Download is offered where the thing to keep is, never larger than Play:
   - a film's details page and an episode's own page: the action beside watched and favorite;
   - each episode on its show's page: a small mark beside the row (on a television a step to the right of it; on a phone at the row's end), which says whether the episode is kept, on its way (a thin ring that moves only with the download), or did not finish;
   - a season: "Download season" at the end of the season tabs' line, over the episodes' marks (beneath the tabs on a phone), with how much of it is kept beside it ("1 of 4 episodes on this device");
   - a show: the action on its page keeps the episode to watch next and the two after it, a convenience over its episodes.
2. Selecting any of them opens the sheet on that work: its name ("Slow Horses · Season 1"), what Reflux would keep ("Preparing the best offline version for this device."), each version in plain terms (`1080p · HDR10 · 4.3 GB · from Living Room`), why that one when there was a choice ("Chosen because it plays best on this device.", "…looks the same here in less space.", "The best version that fits in the space on this device.", "The version you chose for this."), what it needs and what is available, and the copies of it there already are, with what can be done about each: pause, continue, try again, cancel (at once) or remove (asked first). Within a season each line is an episode; what is left out is said of each episode. A season or show with several copies offers "Remove all from this device", asked once.
3. Download confirms and the sheet closes; Not now (or Back) does nothing. Asking again for something of which a copy was paused or did not finish picks that copy up again.
4. The page then says how far it is in one quiet line, with a thin progress bar. A film's or an episode's action becomes "Downloading", then a lit "Downloaded". A show or a season is counted episode by episode: "3 of 30 episodes on this device" while some are kept, and only with every episode kept is it "Downloaded" ("All 6 episodes on this device", "Season on this device").
5. A card carries a small filled mark before its caption when its work is kept, and an outline mark on a show of which some episodes are; the copy is listed among the work's versions as the closest one.
6. **Downloads** is a page of its own, reached from the download button in the navigation (beside Search; at the top of a phone) and from the media sources. It lists every copy under "Downloading" (waiting, under way, paused: in the order they will be made), "Didn't finish" (with why), and "On this device" (by work, a show's episodes in order), each with its work's artwork and name, the version kept, its size and source, where it stands, and its actions beside it. A line opens the work's page (an episode's own page). Above them: how many copies, how much room they take, and how much is available, and "Keep what you're watching" when Reflux has something to propose, which opens the sheet on what is being watched. It is not a second library: nothing is browsed or played from it. While something downloads, the navigation's button carries a small dot that does not move.

Why a version was chosen comes from the planner itself (`PlannedDownload.choice`), so the sheet explains the decision rather than guessing at it. When the version the user pinned cannot be had right now and another is proposed, the sheet says so ("The version you chose can't be downloaded right now. This is the best one that can be."); the pin stays.

## A download is a version, not a work

```text
Media work (movie, episode)
    |
    +-- Unified watch state
    |
    +-- Version / cut
           +-- Jellyfin representation
           +-- WebDAV / SMB representation
           +-- Local file
           +-- Downloaded copy        <- one more of these
```

A finished download is registered as one more version of the work its origin belongs to, in a source of Reflux's own (`DownloadsSource`, type `downloads`, on the device). Nothing new appears in the library and nothing is identified: the copy says which version it was made from, and it is a version of whatever work that version is. If the origin is later re-identified or merged into another work, the copy follows. If the origin goes away for good (its source is removed), the copy stays with the work it last belonged to, and that work stays in the library for as long as the copy exists.

Because it is an ordinary version:

- **Best Version** treats it like any other. It describes the same file as its origin, so the two are equal in everything the device can show, and the copy wins as the closest one (local-first). With the origin's source away, it is simply the version that can be played.
- **Watch state** is the work's. A position reached on the server resumes on the copy and the other way round, by the existing timeline rules; a copy of one cut never resumes another cut's position blindly. There is no offline history.
- **A pin** on the origin is honored by its copy: the copy is that version. A pin on something else is not overridden by a download existing.

Where downloads are kept is not one of the user's sources and is not listed among them.

## What can be downloaded

A source says so itself: the `DOWNLOAD` capability, and `DownloadableSource.downloadTarget(path)`, which is the file as it is, byte for byte, never a transcode. Jellyfin (its static stream), WebDAV, and SMB are downloadable. A local folder is not, because it is already on the device. Nothing in the Media Core knows how any of them does it.

## What Reflux chooses

`OfflinePlanner` (in `core`, pure and deterministic) answers "what should be kept" for a list of works:

1. The versions are ranked exactly as Best Version ranks them for this device: what it can play at all, the cut the viewer is in, a pinned version, how well it plays (hardware decoding, no tone mapping), and resolution, dynamic range, and audio *as far as this device's display and outputs reach*.
2. If a copy that plays here is already on the device, nothing is downloaded.
3. Among versions the device shows and plays equally well, the **smallest** is kept. Streaming breaks that tie the other way; a shelf of limited room should not.
4. If the first choice does not fit, the best version of the same cut that does fit is kept instead, and the user is told it was made to fit.
5. **A pinned version is downloaded or nothing is.** If it does not fit, the work is left out and the reason given; something else is never put in its place.

So 4K is not "better" on a 1080p screen, a 10-bit encode is not "better" on a device that plays the 8-bit one more cleanly, and a different cut is not a smaller copy of the one being watched.

Asked without a selection ("prepare for offline"), Reflux proposes what the user is in the middle of, most recent first, and for a show the episode after the one in progress; at most eight works. Asked for a show, it keeps the episode to watch next and the two after it, not the whole run. Asked for a season, it keeps every episode of it, watched or not: choosing a season is choosing all of it. Each episode is planned on its own, by the rules above, so a season of episodes in several versions keeps the right one of each, a pinned episode keeps its pin, and an episode that is here or on its way is not planned again.

## Room

Reflux never fills the device. A reserve always stays free: the larger of 500 MB and 5% of the volume. What downloads already accepted will still write is spoken for too. A plan says what it needs, how much may be used, and what was left out and why. The room is checked again when each copy starts, because things change between planning and doing.

A file whose source does not give its size is estimated from its bitrate and running time, and the plan says "about".

## Audio and subtitles

A copy is the whole file, so every audio and subtitle track inside it comes along. Subtitles kept in separate files are fetched with it when they are in a language the viewer reads (the system's languages), or do not say what language they are in; other languages are not. A subtitle that cannot be fetched does not fail the download.

## States

| State | Meaning |
| --- | --- |
| Queued | Waiting its turn; copies are made one at a time |
| Preparing | The source is asked for the file and the room is checked |
| Downloading | With progress, when the source says how large the file is |
| Paused | Stopped by the user, with what it has |
| Completed | The copy is a version of its work |
| Failed | Stopped, kept with what it has, and says why (below) |
| Cancelled | Leaves nothing behind: no record, no file |
| Unavailable | It was completed, but the file is not there now |

Failures: not enough room; the source could not be reached; the connection broke; the place downloads are kept cannot be written to; the source no longer has the file; the source did not allow it. The first three of the network kind (source away, connection broken) are tried again by themselves when Reflux starts and whenever the library is refreshed, from the bytes already there if the source can continue part-way. Nothing stays stuck without a reason shown.

## Removing

Removing a download deletes the copy and the subtitle files that came with it. The work stays in the library while another version of it exists, and what was watched is never removed. Removing the origin's source does not remove copies made from it.

If the storage the copies are on is away, they are kept in the library and marked as away, like any disconnected source. If a single file is missing while the storage is there, that download becomes *unavailable*, the copy is no longer a version, and downloading again restores it.

## Platform boundary

Shared, and the same on every platform: the model and planner (`core/offline`), and in `library` the download records, `Offline` (planning against the library, the queue, the copy itself, registration of copies), `DownloadsSource`, and the two things a platform has to bring, behind interfaces: `DownloadStore` (a private place to write) and `DownloadTransport` (fetching what a source hands out). `FileDownloadStore` and `HttpDownloadTransport` implement them for any JVM.

Android supplies where the copies go and when they are made:

- Copies are kept in the application's **own internal storage**. No other application can read them, no permission is needed or asked for, and they go when Reflux is uninstalled. They are not offered on removable storage.
- Copies are made **while Reflux is running**, in a scope that outlives any one screen. Reflux does not run a foreground service or show a notification to keep downloading with the application closed; a download cut short continues from where it was the next time Reflux starts. This is the same product line as background playback, and can be revisited with it.

Windows and Linux supply the same (DECISIONS.md, 2026-10-17, "Smart Offline on the desktop is in V1"):

- Copies are kept in Reflux's **data directory** (`downloads` inside it: `%LOCALAPPDATA%\Reflux` on Windows, `$XDG_DATA_HOME/reflux` on Linux), never in one of the user's folders.
- Copies are made **while Reflux is running**, in the application's own scope, and continue from where they were at the next start, as on Android.

Where a platform offers no `Platform.offline`, nothing about downloads is shown.

## Not there yet

Later (V1.x):

- Downloads do not continue with the application closed.
- A server that only offers a transcoded stream for a file is not a download source.
- A copy made from a file whose identity the user corrected follows it; a merge of two works while the origin is permanently gone does not move the copy.
