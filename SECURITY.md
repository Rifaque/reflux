# Security Policy

Reflux is an early-stage project and does not yet have a production security-support process.

For a security-sensitive report, do not publish exploitable details in a public issue. Contact the maintainer through the private security/contact mechanism associated with the project when one is established.

Current handling of credentials:

- Jellyfin passwords are used once to sign in and are never stored. The resulting access token is stored in the source configuration inside the local library database. Moving tokens into platform secure storage (Android Keystore, OS keychains) is planned before release.
- WebDAV share credentials are stored in the source configuration in the local library database, like Jellyfin tokens, until platform secure storage lands. Reflux only sends `PROPFIND` and `GET` to shares, and XML responses are parsed with DTDs disabled.
- SMB credentials are stored like WebDAV credentials. Share files are opened read-only and streamed to the player through a server bound to the loopback interface only, with an unguessable token per file.
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
