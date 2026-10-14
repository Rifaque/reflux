# Reflux Release Readiness

What stands between the current source tree and something that can be handed to users. Nothing here blocks development; each item is a decision or a piece of work that a release needs and that must not be made harder in the meantime.

## Native runtime: libmpv

Desktop playback and probing use libmpv through its stable client API (decision log, "libmpv integration"). The library is loaded by name at run time and is not part of the repository.

**Development today**

- Nothing compiles or links against libmpv. Building, and every test that does not play real media, needs only the Gradle wrapper.
- Tests that need the engine skip themselves when it cannot be loaded, and those that generate media also need FFmpeg on the path.
- To get the engine: on Linux install the distribution's package (`libmpv2` on Debian and Ubuntu, `mpv-libs` on Fedora); on Windows put `libmpv-2.dll` in `.reflux/native/`, which Git ignores and which the build passes to tests and `run` tasks as the native library path. That folder is a developer convenience only.
- Without the engine the application runs, browses, and says that playback is not set up.

**What a release needs**

- A libmpv build to ship on Windows, with a known provenance and configuration: built LGPL (no GPL-only components), with the decoders and hardware paths Reflux relies on. The development DLL is a third-party build and is not that.
- On Linux, either a dependency on the distribution's libmpv (deb/rpm) or a bundled copy (Flatpak/AppImage). The versions differ between distributions; the binding only uses API that every libmpv 2.x has.
- LGPL obligations for the bundled library and its dependencies (FFmpeg, libass, libplacebo): license texts, source offer, and dynamic linking, which is already how it is loaded.
- An installer or package per platform, and code signing on Windows. `desktop:app` has no packaging configuration yet.
- On X11 the engine changes process-wide state (decision log, 2026-10-12); a bundled build is a chance to patch that at the source.

## Credentials

On Android the app currently allows plain-HTTP connections to any host, because home media servers are usually `http://`. Before release that should be narrowed where it can be (a network security configuration, or asking when a server is added). Reviewed for the Android client so far: no storage permission is requested (folders are granted one at a time through the system picker, read-only, and the grant is given back when a source is removed); backups of app data are switched off, so the library database with its source credentials does not leave the device that way; problem reports are redacted before they reach the system log or the log file; the only exported component is the launcher activity; nothing in the production path is debug-only. Still open: the plain-HTTP policy above, credentials in the database (below), and the media session being available to any media controller on the device, which is the platform's default.

Downloads for offline are files in the application's internal storage: not readable by other applications, not in any shared or public location, not backed up (backups are off), and removed with the application. No permission was added for them. They are fetched with the same credentials the source plays with, which is one more reason for the credential work below. Not yet done: continuing a download with the application closed (a foreground service and notification, undecided), and anything that limits downloads to unmetered networks.

For televisions: the application declares the TV launcher category and a banner, and requires neither a touchscreen nor the leanback feature, so one package serves handhelds and televisions. It declares `READ_MEDIA_VIDEO` (and `READ_EXTERNAL_STORAGE` up to Android 12), requested only on a television, where there is no system folder picker; a store listing will show that permission for every device, which is worth a line of explanation there. Not looked at yet for a television release: the store's TV quality checklist beyond the manifest, a recommendations or Watch Next channel on the launcher (not built, and not needed to play), and behavior on devices that do offer picture-in-picture on a television.

Android release builds also need what debug builds do not: a signing configuration, shrinking with keep rules for the engine modules, and a look at what the desktop-only classes left in those modules (the JDBC database opener, the JDK HTTP client) do to a shrunk build.

Servers and shares are now added in the application. What that changed, and what it did not: the password typed for a Jellyfin server is sent once, to sign in, and is not kept anywhere; the form holds a password only while it is open, shows it masked, and clears it after connecting; a failed connection is reported to diagnostics as its kind and the connector's own detail (an HTTP status, an SMB status name), never with the request, the address's credentials, or the password; an address with a name and password written into it is refused, so they cannot end up in a stored or logged address; and the sources page shows an address and a user name, never a password or a token. Plain `http://` addresses are accepted for home servers, as before (the plain-HTTP question above is unchanged).

Source credentials (Jellyfin access tokens, WebDAV and SMB passwords) are stored with the source configuration in the library database. On Android that configuration is sealed with an AES key held in the Android keystore (decision log, 2026-10-14), including what earlier versions stored; on the desktop it is still unencrypted. They belong in the platform's secure storage: Credential Manager on Windows, the Secret Service on Linux, the Keystore on Android.

- The database column should end up holding a reference to the secret, not the secret.
- Nothing outside the source adapters reads the configuration, which keeps that change local.
- The diagnostics log redacts credentials in URLs, query parameters, and headers, because logs get attached to bug reports.

## Metadata provider

- The TMDB credential is an application credential supplied by the build (decision log, "Metadata providers"). Today it is read from the environment; release builds need it injected and not checked in.
- TMDB's terms require attribution in the product ("This product uses the TMDB API but is not endorsed or certified by TMDB", with their logo). There is no About screen yet to carry it.
- The live service has not been exercised from this repository's tests; the provider is tested against recorded responses.

## Media handling gaps

Known, deliberate, and visible in the library rather than silent:

- **Multi-part files** (`CD1`/`CD2`, `part1`/`part2`) are identified as one work but appear as separate versions instead of playing in sequence. Solving it means a version made of several locations; the version model has one location per version today.
- **Disc structures** (`BDMV`, `VIDEO_TS`) are recognized and skipped. Playing them means treating a folder as one playable version and handing the engine a disc path.

## Platforms and input

- Verified: Windows 11, and Linux on X11 with a compositing manager (Ubuntu 24.04). Wayland has only been reached through XWayland; X11 without a compositor shows the player's controls window opaque.
- Controllers: the readers and everything above them are tested; a session with physical Xbox and PlayStation controllers is still owed (`PhysicalControllerTest` is the script for it).
- Television: verified on the Google TV emulator only. A real television or streaming box is still owed, and with it everything an emulator cannot show: hardware decoding, 4K, HDR and Dolby Vision, an HDMI receiver's audio formats, drives plugged into the device, and a real remote in the hand.
- Media keys are not captured on desktop.

## Project

- No license has been chosen for Reflux itself (README).
- Continuous integration runs the full check on Linux and Windows and the window tests on Linux; the Windows window tests run locally only, because hosted Windows runners have no libmpv.
