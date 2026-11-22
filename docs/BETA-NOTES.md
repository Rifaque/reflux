# Reflux Beta Notes

What's new in each beta, written for the people testing it. The complete list of changes is [CHANGELOG.md](../CHANGELOG.md) (the stores' "What's new" comes from it); the programme and its gates are [BETA.md](BETA.md); tracker IDs are kept out of these notes on purpose.

## Beta 2 (`0.9.0-beta.2`, 22 November 2026)

**Out for testers.** Windows and Linux: the website's Download page (a GitHub pre-release). Fire TV: Amazon's Live App Test (live since 22 November; update through your invitation). Android, Google TV and Android TV: Google Play's closed test, once the build is uploaded and Google's review passes; until then Play still offers Beta 1. Your version is under Settings → About Reflux.

### Highlights

- **See where you're going.** Move along the timeline, hold a seek key, or push the right stick, and Reflux shows the scene at that moment, wherever your Jellyfin (10.9 or later) or Plex server has made scene previews.
- **One way to use a controller, everywhere.** The same buttons do the same things on every screen, with your controller's own symbols on the hints, and a quick view of any film or show on X.
- **Typing without a keyboard.** Reflux has its own on-screen keyboard for controllers and remotes, and on Windows and Linux it turns into a full keyboard in your own layout when you pick up the mouse.
- **A Home that's yours.** The featured title is what you're watching or one of your best unwatched titles, changing day by day, instead of the first one alphabetically.

### Playback

- Scene previews on the timeline, with the time, from Jellyfin and Plex; where a video has none, the time is shown alone.
- With a controller, the right stick moves through scenes: A jumps there, B stays where you are; without previews it skips ten seconds at a time.
- Android: starting another film straight after one no longer leaves the last one's picture frozen on screen.
- Linux: playback starts whatever your system's number format, and the controls stay above the picture on Wayland.
- Windows: the player's controls stay in place when you move the window to another monitor.
- A version's details no longer show a resolution or HDR guessed from the file name beside the measured one.

### Navigation and controllers

- **Quick view:** X on a controller, a right-click, a long press, or a remote's Menu shows what a film or show is, with Play or Resume, Favorite, Download and Details.
- **Y** goes to the navigation and back to exactly where you were; the bumpers move between sections; **Start** opens Sources; **LT** hides or shows every button hint, and Reflux remembers your choice.
- With the mini-player up, **RT** (or holding B) closes it, and pressing the right stick brings back the full player.
- A film's or show's page opens on Play, so pressing again plays.
- Returning Home, See all, and moving between rows no longer leave focus stuck or skip items.
- A mouse resting over a poster no longer lifts it while you use a controller.
- Rumble is on where your controller supports it (you can turn it off). Vibration for touches on phones and tablets stays off until you turn it on.

### Search and typing

- Search is redesigned: suggestions from your own library, real filters, results in sections, and a clearer empty state.
- As you type, matching titles from your library are offered.
- A real keyboard never presses the on-screen keyboard's keys, and Backspace in a text field never leaves the page.
- Phones and tablets: the system keyboard no longer pushes the navigation out of place.

### Home and discovery

- The featured title is chosen for you, not alphabetically.
- Pull down on Home, Movies or Shows on a phone or tablet to look for new media.
- Search is much faster in large libraries.

### Profiles and privacy

- Reflux opens the profile you used last, on every device.
- With several profiles, Settings has Switch profile and Log out.
- Deleting a profile with a PIN asks for that PIN; Forgot PIN lets you keep the profile or reset it.
- Reflux still sends nothing to the people who make it.

### Interface

- Media Atmosphere now visibly tints the page around the artwork when you turn it on.
- The "finding titles" sign and poster badges no longer cover headings, the search field, or titles.

### Stability and sources

- The same Jellyfin server at two addresses (at home and through a VPN) keeps both; Reflux uses whichever answers.
- Windows: switching a controller off and on no longer closes Reflux.
- Linux without libmpv: Reflux tells you the exact command for your distribution.

### Platform notes

| Platform | Note |
| --- | --- |
| Android phone and tablet, Google TV, Android TV | From Google Play's closed test (an invitation) |
| Fire TV | From Amazon's Live App Test (an invitation), Fire OS only; Vega OS devices can't run Reflux |
| Windows | A zip, unsigned: Windows may warn before it first opens; check the SHA-256 on the Download page |
| Linux | An x64 tarball; it needs your distribution's libmpv (Reflux tells you how) |

### Known issues

- Windows: on a few machines Reflux can crash after the graphics driver fails to start Direct3D (being investigated).
- Linux: on KDE with "Scaled by the system", and with two monitors at different scales, Reflux may look too large.
- A short blank frame can appear when the mini-player closes.
- Windows starts more slowly than Android.
- Scene previews need your server to have made them; local files have none yet.

### What to test

- Scene previews and the right stick on your own Jellyfin or Plex server.
- The controller everywhere: Home, rows, Search with Reflux's keyboard, Quick view, the player.
- Upgrading from Beta 1: your library, profiles, sources and downloads should all still be there.

Report problems to support@rsxdevelops.dev with your device, the build (Settings → About Reflux) and, if you can, the support report from About Reflux.

## Beta 1 (`0.9.0-beta.1`, 16 November 2026)

The first build for testers. Highlights: **profiles** (each viewer's own history, favourites and server accounts, with optional PINs); the navigation's final layout; a lighter **player** with a mini-player on every platform and picture-in-picture on Android; subtitles remembered per profile; chapters on Android; Reflux recognising films online with Trakt (optional, nothing else sent); a support report you can attach to a bug report; Windows and Linux downloads that need no installation. The full list is CHANGELOG.md, "0.9.0-beta.1".
