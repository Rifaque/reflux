# Security Policy

## Reporting a vulnerability

Report security problems privately through GitHub's private vulnerability reporting for this repository: <https://github.com/Rifaque/reflux/security/advisories/new>. Please do not open a public issue, and include what is affected (version, platform, source type) and how to reproduce it. Reflux is pre-release software; there is no bounty and no committed response time yet, but every report is read.

## Supported versions

None yet: Reflux has not been released. Once it is, the latest release on each platform receives security fixes.

## What the project does

- **Secrets:** GitHub secret scanning and push protection are on for this repository; CI runs gitleaks over the whole history (reviewed false positives, all fake values in tests, are listed in `.gitleaksignore`). Signing keys, store credentials, and the live-test account never enter the repository: signing values come from `REFLUX_SIGNING_*` in the environment or the user's own Gradle properties, and live tests read `~/.reflux-live/` and pass values to devices through adb's standard input, never as arguments.
- **Release artifacts:** built from a clean commit by `tools/release/release.py` or the release workflow, with SHA-256 checksums, a manifest naming the commit, and build provenance (signed by GitHub's attestation in the release workflow). Windows builds are not signed with a publisher certificate (docs/RELEASE.md, "Windows: unsigned distribution"): verify the SHA-256 published with each release.
- **Android:** release builds are minified, not debuggable, exclude app data from backup and device transfer, export only the launcher activity and a read-only artwork provider, and carry no metadata-provider credential. Plain HTTP is allowed for the user's own home servers; user-added certificate authorities are not trusted.

## How Reflux handles credentials

- Jellyfin and Plex passwords are used once to sign in and are never stored. The resulting session token, and WebDAV and SMB passwords, are a source's connection.
- Where a connection is kept: on Windows in Credential Manager and on Linux in the Secret Service where one runs, with only a reference in the library database; on Android in the library database, encrypted with a key held in the Android keystore; on a Linux desktop without a Secret Service, in the library database as it is (the diagnostics log says so). Details: docs/RELEASE.md, Credentials.
- Removing a Jellyfin server forgets its token and asks the server to end that session (`POST /Sessions/Logout` with that token, which revokes it and nothing else). The removal never waits for the server.
- Reflux only sends `PROPFIND` and `GET` to WebDAV shares, and XML responses are parsed with DTDs disabled. SMB share files are opened read-only and streamed to the player through a server bound to the loopback interface only, with an unguessable token per file.
- Logs (the diagnostics log, the Android system log, libmpv's verbose log when enabled) pass through redaction of credentials in addresses, parameters, headers (including Jellyfin's and Plex's token headers), and quoted tokens.
- No analytics, crash reporting, or telemetry: Reflux sends nothing to its makers (docs/PRIVACY.md).

The full M6 audit is in docs/VERIFICATION.md, "M6: Release engineering, security, legal".
