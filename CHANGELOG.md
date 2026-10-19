# Changelog

What changes for people using Reflux, version by version. The rules for versions and for this file are in [docs/VERSIONING.md](docs/VERSIONING.md). Nothing has been released yet.

## Unreleased

- On Android TV and Google TV, what you are watching and the next episode of your shows are offered on the home screen's Watch Next row, and choosing one opens Reflux and plays it from where you were. It follows your progress in Reflux, and something you remove from that row stays off until you play it again.
- "From the start" no longer loses your place: leaving within the first minute keeps where you were, in Reflux and on your Jellyfin server.
- A film's version can be made the one Play always uses ("Always play this"), and given back to Reflux ("Let Reflux choose"), from its versions on the details page.
- Fire TV and Android 8 to 13: artwork and WebDAV shares no longer fail, which they did on every system before Android 14.
- Fire TV and Android 8 to 11: panels and the navigation bar are solid where the system cannot blur, instead of showing the screen behind them through their glass.
- On a television, pressing Home while something plays pauses it and keeps your place, instead of leaving a frozen picture in a corner of the home screen on Fire TV.
- Searching with a Fire TV remote no longer starts with a stray letter.
- While Reflux is out of sight, it gives back the video decoder and sound to other apps, and picks up where it was when you come back.
- Identify: when Reflux has a movie or show wrong, or isn't sure, tell it what it is from its page. Search, choose from results with their year, poster, and summary, and Reflux remembers it, keeping your progress, favorites, and chosen version. "Let Reflux identify it" takes it back.
- On Android, a Play button pressed while Reflux is in the background (a remote, a headset, a Bluetooth device) no longer starts the sound with nothing on screen; your film waits, paused, for you to come back.
- On Fire TV, Reflux no longer writes what you are watching into the system's TV listings, where Fire TV's home screen never showed it.
- An episode in more than one copy shows "2 versions" beside it on its show's page: see each copy, play the one you want, or make one the copy Play always uses for that episode. If your choice can't be used right now (its source is away, or this device can't play it), Reflux says so and keeps it.
- A version that is a different cut from the recommended one now says so, with its running time.
- Choosing an episode from the TV home screen's Watch Next row opens that episode's page in Reflux, instead of a page saying it was no longer in your library.
- Episodes in a folder with no show name (just `S01E01.mkv`) are a show of their own folder, instead of one show mixing every such folder, and Reflux offers to identify them.
- Keep a single episode for offline from the mark beside it on its show's page, or from the episode's own page, and a whole season with "Download season". A show is only shown as downloaded once every episode is; until then it says how many are ("3 of 30 episodes on this device").
- A Downloads page, from the download button in the navigation, lists everything kept on the device, on its way, or that didn't finish, with pause, continue, try again, cancel, and remove.
- Watch offline on Windows and Linux: films, episodes, and seasons are kept in Reflux's own data folder and play without their source, as on Android.
- Asking again to keep something whose download was paused or didn't finish picks it up again.
- When the version you chose can't be downloaded right now, the offline sheet says so before keeping another one.
- A Windows or NAS share left idle no longer shows as "Not connected" until the next check: Reflux now reconnects first when the connection it kept went stale.
- Web shares (WebDAV: a NAS, Nextcloud, a web server) can now be added on Android phones and tablets, Android TV, Google TV, and Fire TV. Before, connecting one failed with "Nothing answered at that address".
- Screen readers no longer briefly find the page you are leaving alongside the one you are opening.
- On Windows and Linux, the recommended version of a film or episode now follows what your computer actually does: which formats its graphics hardware decodes (found out shortly after Reflux starts, and confirmed by what you play), and the screen Reflux's window is on, including a second monitor. Before, Reflux assumed every computer decoded the same formats in hardware and judged by the main screen.
- Removing a Jellyfin server now also signs this device out of it, so the server stops accepting Reflux's old sign-in. Your other devices and apps stay signed in, and the server is removed from Reflux at once even when it can't be reached.
- Web shares are only ever asked about their own address: a share whose listing names another server or a folder outside the share no longer sends your sign-in there.
