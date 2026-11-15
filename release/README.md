# Release records

What has been published, kept in the repository so that everything else reads it rather than repeating it
(docs/RELEASE.md, "Release system"):

- `published/<version>.json`: the manifest of a version given to anyone outside CI, byte for byte as
  `tools/release/release.py build` wrote it (schema 2: version, channel, commit, every artifact with its SHA-256 and
  its `distribution`: `github-release` for what the public release carries, `store` for the APK and AAB, which reach the
  stores and are never on GitHub). Written by the tool, never typed: `release.py publish-record <downloaded release dir>
  --github-release v<version>` re-verifies the release, compares GitHub's asset digests, and writes the file; it refuses a
  tampered manifest and never overwrites a record with different content. The release workflow's verify job produces the
  same file as its `published-record` artifact; it is added in the commit after the release is published. Never edited.
- `stores.json`: where each store listing stands and its address, once it has one.

The website builds its download and release pages from these and from `gradle.properties` and `CHANGELOG.md`
(website/scripts/release-data.mjs). Nothing here is published yet: no version has been given outside the project.
