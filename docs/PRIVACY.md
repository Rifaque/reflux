# Reflux Privacy Inventory

What Reflux stores, sends, and to whom, as the code does it (checked 2026-11-08 against the code and against every host the production code names). The website's privacy page (website/src/pages/Legal.jsx) and the stores' data disclosures are written from this; change this first when behavior changes. Legal review of the public wording is still due (RELEASE.md, "Legal review").

## On the device

| Data | Where | Protection | Leaves the device? |
| --- | --- | --- | --- |
| Library: sources added, titles found, descriptions and pictures, watch state, favorites, collections, choices | Library database (Android: app-private storage; Windows `%LOCALAPPDATA%\Reflux`; Linux `~/.local/share/reflux`) | App-private on Android | No. Android backup and device-to-device transfer excluded (`allowBackup="false"`, `dataExtractionRules`) |
| Source connections (Jellyfin/Plex session tokens, SMB and WebDAV passwords) | Android: database, sealed with an AES key in the Android keystore. Windows: Credential Manager. Linux: Secret Service where one runs, otherwise the database as it is (the log says so) | As stated | No |
| Passwords typed for Jellyfin and Plex | Used once to sign in, never stored | — | Sent to that server (Plex: to plex.tv) to sign in |
| Downloads (copies for offline) | App-private storage / data directory | App-private on Android | No |
| Artwork cache | Data directory | — | No |
| Diagnostics log | Data directory, Android system log | Credentials, tokens, and addresses' user info redacted (`Redaction`) | Only if the user sends it |
| Device ID | Data directory, a random UUID | — | Not as it is: each Jellyfin and Plex account signs in as a device identity derived from it, the server, and the account (M8, docs/WATCH_STATE.md, "Profiles"), sent to that server as its clients identify themselves. A Plex token is only ever in it as a one-way digest |
| Profiles: each viewer's name, watch state, favorites, collections, pinned versions, and preferences, kept apart (M8) | Library database | As the library. One profile's history, favorites, and server accounts are never shown to another, and an account signed in to in one profile cannot be signed in from another | No |
| A profile's optional PIN | Never stored: a random salt and an HMAC-SHA256 verifier under a key the platform's secure store holds (Windows Credential Manager, the Secret Service, the Android keystore); wrong entries counted, with growing waits | A lock between people sharing a device, not encryption: the library database is not encrypted, and someone who can read the device's storage and the key can try all ten thousand PINs. On Linux without a Secret Service the key is in the data directory as it is, and the PIN locks the interface only. A forgotten PIN is not recovered: "Forgot PIN" erases what the profile kept on the device, after a confirmation | No. Never logged, never in a support report |

## Over the network

| To | What | When |
| --- | --- | --- |
| The user's Jellyfin, Plex, SMB, WebDAV servers | Sign-in, listing, artwork, streams, playback progress, played state | As the user uses them |
| plex.tv | Plex account sign-in and the list of the account's servers | Only when signing in with a Plex account |
| TMDB (themoviedb.org) | A title and its year, or an ID; the language; the app's credential; the device's IP address, as with any request | Public builds whose build supplied the credential (DECISIONS.md, 2026-11-08, "TMDB: working non-commercial classification"), unless the user turns off "Online metadata & artwork" in Settings. Never the file's path, the user's servers, or anything about the user |
| RSX Develops | Nothing | Never: no account, analytics, crash reporting, advertising, or telemetry |
| The Android TV provider (on the device) | Titles and pictures of the active profile's works in progress, for the launcher's Watch Next row, each naming that profile so it opens there | Android TV and Google TV only; not on Fire OS. Nothing at all for a profile with a PIN: the launcher is outside the lock, so whoever holds the remote would see it |

## Store disclosures (prepared, not submitted)

**Google Play, Data safety:** (with TMDB on, the titles of the user's media are sent to TMDB to identify them; the legal review decides whether Play counts that as "shared" data, and the conservative answer is App activity → Other user-generated content: shared with a third party for app functionality, not collected by the developer, optional because it can be turned off)

- Does the app collect or share any of the required user data types? **No.** Data that never leaves the device is not "collected" in Play's definition; data sent to a server the user chose and operates (their own media server) for the app's function is processed at the user's direction. Reviewed again with the final wording in the legal review.
- Is all of the user data collected by your app encrypted in transit? Not applicable (nothing collected). Note for the review: connections to users' own servers may be plain HTTP by the user's choice.
- Do you provide a way for users to request that their data be deleted? Not applicable: there is no account or server-side data; uninstalling removes it.
- Privacy policy URL: `https://reflux.rsxdevelops.dev/privacy`.

**Amazon Appstore:** no personal information collected or shared; no account; no advertising ID; privacy policy at the same URL.

## The website

Hosted by Vercel, which processes requests (IP addresses) to serve the pages. No cookies, no analytics, no third-party scripts, fonts, or images (the Manrope font and every image are served by the site itself; a `default-src 'self'` content security policy), no forms.
