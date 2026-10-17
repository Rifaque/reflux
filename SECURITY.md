# Security Policy

Reflux is a pre-release project and does not yet have a production security-support process. A private reporting address will be published on the website (reflux.rsxdevelops.dev) before the public beta.

For a security-sensitive report, do not publish exploitable details in a public issue. Contact the maintainer through the private security/contact mechanism associated with the project when one is established.

Current handling of credentials:

- Jellyfin passwords are used once to sign in and are never stored. The resulting access token, and WebDAV and SMB passwords, are a source's connection.
- Where a connection is kept: on Windows in Credential Manager and on Linux in the Secret Service where one runs, with only a reference in the library database; on Android in the library database, encrypted with a key held in the Android keystore; on a Linux desktop without a Secret Service, in the library database as it is (the diagnostics log says so). Details: docs/RELEASE.md, Credentials.
- Removing a Jellyfin source forgets its token but does not yet end its session on the server; ending it is decided for V1 (docs/DECISIONS.md, 2026-10-17).
- Reflux only sends `PROPFIND` and `GET` to WebDAV shares, and XML responses are parsed with DTDs disabled. SMB share files are opened read-only and streamed to the player through a server bound to the loopback interface only, with an unguessable token per file.
- Logs (the diagnostics log, the Android system log, libmpv's verbose log when enabled) pass through redaction of credentials in addresses, parameters, headers, and quoted tokens.
- The TMDB credential is an application credential supplied at build time; it is not a user secret.

Security-sensitive areas expected to receive particular attention include:

- local filesystem access,
- media-source credentials,
- Jellyfin and other server authentication,
- network-source credentials,
- downloaded media,
- cached metadata,
- external subtitle/media retrieval,
- casting and device control,
- future optional synchronization services.
