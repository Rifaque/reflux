# Reflux Privacy Inventory

What Reflux stores, sends, and to whom, as the code does it (checked 2026-11-08 against the code and against every host the production code names; the metadata provider rechecked 2026-11-15, when Trakt replaced TMDB). The website's privacy page (website/src/pages/Legal.jsx) and the stores' data disclosures are written from this; change this first when behavior changes. Legal review of the public wording is still due (RELEASE.md, "Legal review").

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
| Trakt (api.trakt.tv, and its image host for the pictures it names), a third-party metadata service, not a Reflux backend | Sent directly from the device over HTTPS: a work's title, as read from its file name or given by the user's server; or a catalog ID already known (Trakt, IMDb, TMDB, or TVDB, from a name, a server, or an earlier entry); the viewer's language, only when it is not English (a translation request); Reflux's Trakt Client ID in the `trakt-api-key` header (an application identifier, the same in every copy, not a secret and not the user's); and the device's IP address, as with any request. The year is not sent (it is weighed on the device). Then the pictures Trakt named are downloaded into the artwork cache | Builds that were given a Client ID (DECISIONS.md, 2026-11-15, "Trakt is the metadata provider"), while "Online metadata & artwork" in Settings is on (the default). Turned off: no new request to Trakt and no new picture from it; what was fetched before stays on the device. Never sent: a file's path or name as a path, a source's address, Jellyfin or Plex credentials or tokens, SMB or WebDAV credentials, profile names, profile PINs, the device ID, watch state, or anything else about the library or the user. No Trakt account is used and none is needed |
| RSX Develops | Nothing | Never: no account, analytics, crash reporting, advertising, or telemetry |
| The Android TV provider (on the device) | Titles and pictures of the active profile's works in progress, for the launcher's Watch Next row, each naming that profile so it opens there | Android TV and Google TV only; not on Fire OS. Nothing at all for a profile with a PIN: the launcher is outside the lock, so whoever holds the remote would see it |

## Store disclosures (prepared, not submitted)

No longer true, and not to be used: "no user data leaves the device". With "Online metadata & artwork" on, the titles of the user's media leave the device for Trakt. The facts for each console, to be matched to the console's own wording when the form is filled (the classification itself is not decided here; check the current Play Console and Amazon wording first, and record the answer given in docs/VERIFICATION.md):

| Fact | Answer |
| --- | --- |
| What is transmitted | The title of a film or show in the user's library (as its file is named or its server lists it), or a catalog ID for it; the language when not English. Nothing that identifies the user; no account |
| Applicable data type to consider | Play: the closest listed type for the titles of media the user has (candidates: App activity, "Other user-generated content" or "Other actions"; decide in the console from its definitions). Amazon: the questionnaire's equivalent for content or usage information |
| Recipient | Trakt (trakt.tv), a third party; sent directly from the device. RSX Develops receives nothing and runs no server |
| Purpose | App functionality only: identifying works and fetching their descriptions and artwork. Not analytics, not advertising or marketing, not personalisation, not fraud prevention, not account management |
| Optional | Yes: "Online metadata & artwork" in Settings turns it off, and the app works without it |
| Collected by the developer | No. Shared with a third party: per the console's definition (Play counts data sent off the device to a third party as shared unless an exemption applies; whether the user-initiated or service-provider exemptions apply is the console-time decision) |
| Processed ephemerally | Not claimed: Reflux cannot say how Trakt keeps requests |
| Encrypted in transit | Yes, for Trakt (HTTPS only). Connections to users' own servers may be plain HTTP by the user's choice; they are the user's own servers, not a recipient of collected data |
| Deletion request | Nothing is held by RSX Develops; data on the device is removed by uninstalling or by "Forgot PIN" for a profile |
| Advertising ID, location, contacts, identifiers, financial or health data | None |
| Third-party code that sends data of its own | None of Reflux's libraries sends anything of its own |

**Google Play, Data safety (prepared, not submitted):**

- Does the app collect or share any of the required user data types? Expected **yes, shared** (per the table): titles of the user's media, to Trakt, for app functionality, optional; not collected by the developer. Not final: confirm against the console's current definitions and exemptions when the form is filled, choose the data type there, and record it.
- Is all of the user data collected by your app encrypted in transit? **Yes**: the only data shared, with Trakt, goes over HTTPS.
- Do you provide a way for users to request that their data be deleted? Not applicable: there is no account or server-side data; uninstalling removes it.
- Privacy policy URL: `https://reflux.rsxdevelops.dev/privacy`.

**Amazon Appstore:** the User Data Privacy Questionnaire (which must be complete before a new app or an update can be published) is answered from this section and the table above: no account; no advertising ID; no personal information collected by RSX Develops; the titles of the user's media (or catalog IDs) sent from the device to Trakt, a third party, over HTTPS, for app functionality only, optional through "Online metadata & artwork"; third-party code collects nothing of its own; privacy policy at the same URL. Match each answer to the questionnaire's own categories when it is filled, and record them.

## The website

Hosted by Vercel, which processes requests (IP addresses) to serve the pages. No cookies, no analytics, no third-party scripts, fonts, or images (the Manrope font and every image are served by the site itself; a `default-src 'self'` content security policy), no forms.
