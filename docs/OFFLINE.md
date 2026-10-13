# Smart Offline

Reflux can keep the right copy of something on the device, so that it plays without its source. The user says *that* they want it; which version, from which source, and whether it fits is Reflux's to work out.

This is not a download manager. There is no list of files to administer and no question about codecs. There is one action on a details page, one sheet that proposes what to keep, and the copies that exist.

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

Asked without a selection ("prepare for offline"), Reflux proposes what the user is in the middle of, most recent first, and for a show the episode after the one in progress; at most eight works. Asked for a show, it keeps the episode to watch next and the two after it, not the whole run.

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

The desktop has the engine and none of the interface: nothing is wired there yet, and where a platform offers no `Platform.offline`, nothing about downloads is shown.

## Not there yet

- No way to choose individual episodes or a whole season; a show keeps its next three.
- Downloads do not continue with the application closed.
- A server that only offers a transcoded stream for a file is not a download source.
- A copy made from a file whose identity the user corrected follows it; a merge of two works while the origin is permanently gone does not move the copy.
- The application has no screen for adding a server or share yet, so today the feature is reachable only for sources added another way (see the roadmap).
