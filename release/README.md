# Release records

What has been published, kept in the repository so that everything else reads it rather than repeating it
(docs/RELEASE.md, "Release system"):

- `published/<version>.json`: the manifest of a version given to anyone outside CI, exactly as
  `tools/release/release.py build` wrote it (version, channel, commit, every artifact with its SHA-256). Added in the
  `release: <version>` commit's follow-up once the artifacts are on the GitHub release `v<version>`. Never edited.
- `stores.json`: where each store listing stands and its address, once it has one.

The website builds its download and release pages from these and from `gradle.properties` and `CHANGELOG.md`
(website/scripts/release-data.mjs). Nothing here is published yet: no version has been given outside the project.
