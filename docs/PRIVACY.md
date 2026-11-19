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
| Device name | Built from the device: "Reflux on" and the manufacturer and model (Android), or the computer's host name (desktop) | — | Sent to the user's Jellyfin and Plex servers and to plex.tv when signing in and with requests, as clients name themselves to those servers |
| Device ID | Data directory, a random UUID | — | Not as it is: each Jellyfin and Plex account signs in as a device identity derived from it, the server, and the account (M8, docs/WATCH_STATE.md, "Profiles"), sent to that server as its clients identify themselves. A Plex token is only ever in it as a one-way digest |
| Profiles: each viewer's name, watch state, favorites, collections, pinned versions, and preferences, kept apart (M8) | Library database | As the library. One profile's history, favorites, and server accounts are never shown to another, and an account signed in to in one profile cannot be signed in from another | No |
| A profile's optional PIN | Never stored: a random salt and an HMAC-SHA256 verifier under a key the platform's secure store holds (Windows Credential Manager, the Secret Service, the Android keystore); wrong entries counted, with growing waits | A lock between people sharing a device, not encryption: the library database is not encrypted, and someone who can read the device's storage and the key can try all ten thousand PINs. On Linux without a Secret Service the key is in the data directory as it is, and the PIN locks the interface only. A forgotten PIN is not recovered: "Forgot PIN" erases what the profile kept on the device, after a confirmation. A profile with a PIN is deleted only with that PIN, checked in the same transaction that deletes, with the same count of wrong entries (2026-11-18, B-018) | No. Never logged, never in a support report |

## Over the network

| To | What | When |
| --- | --- | --- |
| The user's Jellyfin, Plex, SMB, WebDAV servers | Sign-in, listing, artwork, streams, playback progress, played state. A Jellyfin server the user signed in to at more than one address (at home, through Tailscale) is reached at whichever of those addresses answers, the home one first (2026-11-18, B-003): the same requests to the same server; the addresses are kept with the sealed connection and as known server addresses on the device | As the user uses them |
| plex.tv | Plex account sign-in and the list of the account's servers | Only when signing in with a Plex account |
| Trakt (api.trakt.tv, and its image host for the pictures it names), a third-party metadata service, not a Reflux backend | Sent directly from the device over HTTPS: a work's title, as read from its file name or given by the user's server; or a catalog ID already known (Trakt, IMDb, TMDB, or TVDB, from a name, a server, or an earlier entry); the viewer's language, only when it is not English (a translation request); Reflux's Trakt Client ID in the `trakt-api-key` header (an application identifier, the same in every copy, not a secret and not the user's); and the device's IP address, as with any request. The year is not sent (it is weighed on the device). Then the pictures Trakt named are downloaded into the artwork cache | Builds that were given a Client ID (DECISIONS.md, 2026-11-15, "Trakt is the metadata provider"), while "Online metadata & artwork" in Settings is on (the default). Turned off: no new request to Trakt and no new picture from it; what was fetched before stays on the device. Never sent: a file's path or name as a path, a source's address, Jellyfin or Plex credentials or tokens, SMB or WebDAV credentials, profile names, profile PINs, the device ID, watch state, or anything else about the library or the user. No Trakt account is used and none is needed |
| RSX Develops | Nothing | Never: no account, analytics, crash reporting, advertising, or telemetry. An opt-in statistics foundation exists in the code (2026-11-18; DECISIONS.md, "Beta 2, Phase 1"), off by default, with no transport and no Settings switch in any build: it cannot send anything. This row, the website's privacy page, and the store declarations change only when a provider is approved and the actual flow exists |
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

### Declaration matrix: the literal reading, decided by the owner (2026-11-16; prepared, not submitted)

The owner chose the conservative, literal reading (DECISIONS.md, 2026-11-16): data the app sends off the device is declared as collected, whoever runs the server that receives it. Read on 2026-11-16: Google Play Console Help, "Provide information for Google Play's Data safety section" (support.google.com/googleplay/android-developer/answer/10787469), and the form's own text; Amazon, "Appstore privacy labels" (developer.amazon.com/docs/app-submission/appstore-privacy-labels.html, last updated 2026-02-17), and its questionnaire. Flows checked against the code: `TraktProvider` (the search carries `query=<title>` only; verified live), `JellyfinApi` and `JellyfinSource` (`Users/AuthenticateByName`, the `MediaBrowser ... Device=..., DeviceId=...` header), `PlexApi` (`X-Plex-Client-Identifier`, `X-Plex-Device-Name`, plex.tv sign-in), `AndroidEnvironment` and `DesktopEnvironment` (the device name).

**The definitions used.** Play: "'Collect' means transmitting data from your app off a user's device"; "'Sharing' refers to transferring user data collected from your app to a third party"; a third party is any organisation other than the developer or its service providers. Collection exemptions: data processed only on the device; end-to-end encryption that no intermediary, the developer included, can read. Sharing exemptions: service providers; legal purposes; "a specific user-initiated action, where the user reasonably expects the data to be shared"; fully anonymous data. Play's **Files and docs**: "A user's files or documents, or information about their files or documents such as file names." **User IDs**: identifiers that relate to an identifiable person, such as an account name. **Device or other IDs**: "Identifiers that relate to an individual device, browser or app." **Other actions**: any other in-app activity not listed. Amazon: the same data categories by name (Files and docs: "information about their files or documents such as file names"); transfers to a third party exclude service providers, legal purposes, and "a specific user-initiated action"; "collected" is not defined on its own, and the literal reading is applied to it as to Play.

**Each flow:**

| Flow | What is sent | Leaves the device, to whom | User-initiated | Optional | Purpose | Encrypted in transit | Stored by Reflux | Store type |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Trakt lookup | A work's title (from a file name or the user's server) or a catalog ID; the language if not English; Reflux's Client ID; the IP address | Yes, Trakt (a third party, not a service provider: it serves its own API under its own terms) | No (automatic after a scan) | Yes ("Online metadata & artwork") | App functionality | Yes (HTTPS only) | The result, on the device | **Files and docs**: collected, **shared** |
| Trakt images | A request for an image Trakt named; no user data beyond the IP address | Yes, Trakt's image host | No | Yes (same switch) | App functionality | Yes | WebP files in the artwork cache | None beyond the row above |
| Jellyfin sign-in | User name; password (once, not stored); the device name ("Reflux on" + manufacturer and model on Android, + the computer's host name on desktop); a per-account device identifier; the app version | Yes, the user's own Jellyfin server | Yes | Yes (a server is never required) | App functionality, account management | **Not always**: the user may give an `http://` address | The session token, sealed (Android keystore, Windows Credential Manager, Linux Secret Service) | **User IDs**, **Device or other IDs**: collected, not shared (user-initiated) |
| Jellyfin use | The token; requests for the library; playback progress and played state | Yes, the user's own server | Yes (choosing to play from it) | Yes | App functionality | Not always | Watch state, on the device | **Other actions**: collected, not shared |
| Plex sign-in | Account e-mail or user name and password (once), or a token; the device name; the client identifier; the app version | Yes, **plex.tv (Plex, a third party)**, then the user's Plex servers | Yes | Yes | App functionality, account management | plex.tv: yes; a local Plex server: not always | The token, sealed; a source identity as a one-way digest of it | **Email address**, **User IDs**, **Device or other IDs**: collected, not shared (user-initiated) |
| Plex use | As Jellyfin use | The user's Plex servers | Yes | Yes | App functionality | Not always | As above | **Other actions**: collected, not shared |
| SMB and WebDAV | User name and password for the share | Yes, the user's own share | Yes | Yes | App functionality | Not always (SMB per the server; WebDAV may be `http://`) | Sealed on the device | **User IDs**: collected, not shared |
| Local folders and scanning | Nothing | No | | | | | The library, on the device | None |
| Watch Next row (Android TV) | Titles and pictures to the launcher's provider, on the device | No | | | | | | None |
| Diagnostics log | Nothing, unless the user exports it and sends it themselves through another app | Not by Reflux | Yes | | | | On the device | None (not transmitted by the app) |

Nothing else leaves the device: no analytics, crash reporting, advertising ID, or Reflux backend (the table "Over the network" above). Media files are never uploaded.

**The forms, field by field (the literal reading):**

| Console field | Answer | Basis |
| --- | --- | --- |
| Play: collects or shares required data types | **Yes** | Rows above |
| Play: all collected data encrypted in transit | **No** | A user's own Jellyfin, Plex, SMB, or WebDAV server may be reached over plain HTTP; only Trakt and plex.tv are always HTTPS |
| Play: account creation methods | **My app does not allow users to create an account** | Reflux has no account; signing in to a service the user already has is not creating one |
| Play: log in with accounts created outside the app | **Yes** | Jellyfin, Plex, and share sign-ins happen inside Reflux |
| Play: users can request deletion (optional) | **No** (or left unanswered) | RSX Develops holds nothing; on the device a source's removal signs out and forgets its credentials, uninstalling removes the rest |
| Play data types | Files and docs (collected, shared, optional, App functionality, not ephemeral); Personal info: Email address, User IDs (collected, not shared, optional, App functionality and Account management); Device or other IDs (collected, not shared, optional, App functionality); App activity: Other actions (collected, not shared, optional, App functionality) | Rows above |
| Amazon: collects or transfers | **Yes** (saved) | |
| Amazon data types | Files and docs (saved: collected, transferred, App functionality); add User Info: User IDs and Email address, Device or other IDs: Device or other ID, App activity: Other actions, each collected, **not** transferred (user-initiated), App functionality | Rows above |
| Amazon: account creation within or outside the app | **No** (saved) | No Reflux account |
| Amazon: data deletion without deleting an account | **No** | Nothing is held by RSX Develops |

**Still for the owner to confirm (ambiguous in the definitions, not guessed):** whether "account management" applies as a Play purpose for sign-ins to the user's own servers (App functionality alone is defensible); whether the desktop host name in the device name should be generalised in a later version (a code change, not for M11; the store forms cover Android only). Not declared under any reading: location, contacts, messages, photos and videos or audio as data, web browsing, installed apps, financial or health data, advertising ID, crash logs.

## Opt-in technical statistics (foundation only; nothing is sent)

Added 2026-11-18 (DECISIONS.md, "Beta 2, Phase 1"); code in `core/src/commonMain/kotlin/dev/reflux/core/telemetry`. **No build sends anything:** the only transport is `TelemetryTransport.Disabled`, which drops every event, and Settings offers no switch while there is no transport. What follows is the contract any future provider gets, enforced by the code and its tests, not a description of something that happens today.

| | |
| --- | --- |
| Default | Off. Nothing recorded, queued, or sent before the person turns it on; turning it off forgets what was waiting and stops it |
| What an event may hold | Its kind (start-up, player failed to start, playback outcome, source error, screen performance, crash); the platform kind, Reflux's version, and its release channel; categories from closed lists (engine, delivery, source kind, failure category); durations and counts in coarse buckets. Nothing else: the schema has no free-text field (`TelemetrySchemaTest` fails if one is added) |
| What it can never hold | Media titles, file names or paths, server addresses, credentials or tokens, profile names, watch history, library contents, exception messages, URLs, HTTP headers or bodies, an advertising identifier, an installation, device, or account identifier, a lasting session identifier |
| Sending | One batch at a time; a batch not accepted, or with no network, is dropped, never retried in a loop |
| Before it may be turned on | The owner approves a provider and its data contract; this inventory, the website's privacy page, Play's Data safety, and Amazon's questionnaire are updated to the actual flow; the Settings switch ships with the transport |

## The website

Hosted by Vercel, which processes requests (IP addresses) to serve the pages. No cookies, no analytics, no third-party scripts, fonts, or images (the Manrope font and every image are served by the site itself; a `default-src 'self'` content security policy), no forms.
