# Changelog

What changes for people using Reflux, version by version. The rules for versions and for this file are in [docs/VERSIONING.md](docs/VERSIONING.md). Nothing has been released yet.

## Unreleased

- On Android, a subtitle line that is already showing where you skip to now appears straight away, instead of only from the next line.
- About Reflux now has "Help and feedback", which opens Reflux's support page (on a TV it shows the address), and "Save a support report": a short text file with your Reflux version, your device, what it can play, your sources' kinds and states, how the last playback went, and recent problems, to attach to a bug report. Passwords, tokens, server addresses, file paths, and titles are taken out of it, and Reflux never sends it anywhere: on a computer it is saved in your Downloads folder, on a phone or tablet you choose where to share it, and on a TV it is saved in Reflux's folder on the device.
- Reflux for Android has its permanent identity for Google Play and the Amazon Appstore. Development builds installed before this one are a separate app and can be uninstalled.

- Reflux for Windows comes as a zip and Reflux for Linux as a tar.gz, each with everything it needs to run (on Linux, apart from your distribution's libmpv). Extract it and run Reflux; nothing is installed. The Windows build is not yet signed by a publisher, so Windows may warn before it first opens; each download has a SHA-256 checksum to check it against.
- Public builds look nothing up online: what Reflux shows about a title comes from your servers and your files. The online catalog and the "Online metadata & artwork" switch described below are in development builds only, until its terms allow more.
- Reflux keeps its library out of Android's cloud backup and device-to-device transfer, since it holds your servers' sign-ins.
- Reflux's website, reflux.rsxdevelops.dev, with downloads, the changelog, privacy, terms, and third-party notices.

- Titles from a Jellyfin or Plex server now come with what the server knows about them: what a film, show, season, or episode is about, its genres, rating, runtime, air dates, and network or studio. Plex's cast and directors too. No internet lookup is needed for any of it.
- A show's page says the years it aired and on what network. A season with a name or a story of its own says so beside its poster, and each episode shows its date beside its length.
- Episodes are pictured by their own stills wherever your server or the online catalog has them, including on an episode's own page; an episode without one shows its show's picture instead of a blank.
- An episode's page shows its air date, rating, and guest cast.
- When two servers describe the same title differently, Reflux shows one consistent description: the server you added first, filled in by the other and by the online catalog. It no longer depends on which was scanned last.
- A title you identified yourself keeps your choice through rescans and refreshes, even when a server has it matched to something else.
- Identify has "Refresh details", to fetch a title's details and artwork again. If the catalog can't be reached, nothing changes.
- "Online metadata & artwork", under Sources: turn it off and Reflux looks nothing up online, using what your servers provide and what it already has.
- Pictures from a Jellyfin server are fetched at the size Reflux shows them, so they take a fraction of the space on your device.
- Show pages open faster.

- A refreshed look across Reflux. The navigation on every device is a calm frosted layer that floats over your media, with where you are shown by a lit pill and a small bar, not by color. On tablets, desktops, and Google TV, Home's featured title and a title's page now run their artwork under the navigation.
- On a TV, the navigation rail stays out of the way until you go to it, then widens into a frosted panel with the names.
- On Android phones and tablets, artwork now reaches the top of the screen, under the status bar, and the navigation sits clear of the gesture area.
- Media Atmosphere, under Sources: let a title's artwork tint the space around it on Home and on its page. It is off unless you turn it on.
- If you've asked your system for high contrast (Android's high-contrast text or contrast setting, a Windows contrast theme, GNOME's high contrast), or turned off transparency effects on Windows, Reflux uses solid surfaces instead of frosted ones, and high contrast also brightens secondary text, edges, and the focus ring.
- Buttons and cards now dim briefly while you press them.

- Reflux has one navigation everywhere: Home, Movies, Shows, Search, Downloads, and Sources. On a phone held upright, Home, Movies, Shows, and Search are icons along the bottom, with Downloads and Sources at the top; on its side, and on tablets, they are in a rail on the left; on Windows and Linux, in a sidebar; on a TV, in a rail you reach with Left on the remote, which shows their names while you are in it.
- On a TV, Back on Home, Movies, Shows, Search, Downloads, or Sources first goes to the navigation, so it never leaves Reflux from the middle of a row.
- On Windows and Linux, Ctrl+1 to Ctrl+6 go straight to each place. With a controller, the bumpers step through them.
- Plex servers can be added, by your Plex account, a Plex token, or nothing at all where the server lets your network in. Their films and shows join your library like any other source's, play directly, can be downloaded, and keep what you watched in step with Plex.
- Every source has its own page under Sources: everything on it, or on one of its libraries, whether or not it is in your library, with what you watched and the same Play, versions, and downloads.
- A source, and each of a server's libraries, can be switched out of your library without disconnecting it. It stays under Sources, where you can still open and play it; what you watched is kept; switching it back on brings it all back at once, with nothing to scan.
- Libraries you left out of a server before are kept out of your library, and can now be opened from its page.
- A server that no longer accepts Reflux's sign-in says so, and asks you to sign in again, instead of only saying it can't be reached.

- The player's new Info list says how something is playing and why: the original file or what a server converts, the decoder, the sound, the subtitles, and whether that is what was planned.
- On Android, choosing image subtitles (PGS) on a film whose sound a server converts no longer re-encodes the picture: the subtitles arrive beside the stream, and the original video plays.
- On Android, a few video files that can't be skipped through no longer go back to the beginning when you try, and no longer lose your place. With a Jellyfin server, Reflux carries on from where you asked. Otherwise it tells you.
- On Windows and Linux, quitting right after leaving the player no longer takes the video window away while the player is still shutting down. On Linux that could crash Reflux.
- Changing audio or subtitles on a converted stream now always carries on from the exact place, including in the first minute.
- If subtitles a server was preparing never arrive, Reflux says so and keeps playing.
- On Android, image subtitles on a cropped film are no longer cut off at the bottom of the picture.
- On Android, a server stream that doesn't match its own description is no longer reported as a moved or deleted file.
- The player no longer says Paused while it is loading.
- With a Jellyfin server, Reflux now asks for what your device needs. When only the audio can't play here, the server converts the audio and the original video is kept. Video is converted only when your device can't decode it.
- Reflux keeps the audio track you chose. If it can't play and can't be converted, you're told, rather than hearing another language.
- Subtitles a Jellyfin server has to prepare no longer hold up the picture: they appear when they're ready.
- Changing audio or subtitles on a converted stream works, from where you were.
- On Android, connecting a receiver or television while Reflux is running is noticed, instead of waiting for a restart.
- Best Version no longer counts Dolby Vision or Atmos on a device that would lose them.
- On Windows and Linux, surround sound counts for what a playback showed your speakers take, rather than always stereo.
- When a video's audio can't be decoded on your device, Reflux now says so instead of playing the picture in silence with no explanation.
- On Windows and Linux, Reflux now sends your Jellyfin sign-in with video streams. Before, it was cut off on the way, which went unnoticed only because the server doesn't ask for it there.
- Your Jellyfin server now shows Reflux as Direct Play, which is what it's doing, rather than Direct Stream.
- Screen readers: while a video plays, or a sheet is open, only what is in front can be reached, not the page behind it. Pages, sheets, the player, and its lists say their name as they open; notices, failures, and how many results a search found are spoken; every control says what it is and whether it is on, open, or selected.
- With a remote, asking to remove a source puts focus on Keep, and Keep returns to Remove, instead of focus falling to the navigation.
- Screen readers: cards and the player's speed button say their title or value once, an open player list comes right after its button, and a question that appears in place is announced.
- With a screen reader on, the player's controls stay on screen instead of fading away, and the timeline and volume can be moved from it. On a TV, play, pause, and the ten-second seeks are offered as actions.
- On a desktop, O opens the player's audio, subtitle, and speed lists, on any keyboard. Tab no longer wanders through the page hidden behind the video.
- When a sheet closes, focus goes straight back to the button that opened it, without waiting for the next key.
- Reflux follows the system's reduce-motion setting (Remove animations on Android, Show animations on Windows, Animations in GNOME), and says "Loading…" in words when it does.
- Reflux follows the system's text size on Windows and Linux as it already did on Android, and buttons, fields, and Home's opening title grow with large text rather than cutting it off.
- On touch screens, tabs, the player's lists, and its timeline are comfortably large enough for a finger.

- The player's controls now suit the device. On tablets the play and skip buttons are in the middle of the picture, where your hands are, and the volume slider is gone (your tablet's volume keys do it). Fullscreen is offered only where it does something: on a desktop window, or on a phone or tablet held upright. Previous only appears when there is an episode before this one.
- The skip buttons show "10", so they read as ten seconds back or ahead rather than as reload.
- On phones the player's bar keeps Audio and Subtitles, and the other lists (chapters, speed, episodes) are one tap away under More.
- On a desktop, resting the mouse on a button says what it does, and in the player which key does the same.
- On a TV, the navigation no longer shows a Back arrow: the remote's Back button is the way back.
- On a phone held sideways, the navigation is a compact bar in the middle instead of stretching across the screen; and a message no longer covers the offline sheet's buttons.

## 0.9.0-alpha.2 — 2026-10-20

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
- About Reflux, at the end of the Media sources page, says which version you have, what Reflux keeps on your device and what it sends where, credits TMDB for titles and artwork, and lists the software Reflux is built with, each with its license.
- With a keyboard, a controller, or a remote, going back from several pages in now returns to what you chose on each page, instead of the top of the page.
- About Reflux says who owns Reflux: it is proprietary software by RSX Develops, all rights reserved, under "Copyright and ownership". The software Reflux is built with keeps its own licenses, listed separately under "Third-party software".
