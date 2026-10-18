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
