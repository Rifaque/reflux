# Reflux Versioning Protocol

The authoritative rules for Reflux's version numbers, build numbers, and release promotion. Every person and every AI agent changing a version, a build number, a tag, a changelog release heading, or store/package metadata follows this document. If something here conflicts with another document, this one wins for versioning; fix the other document.

## One version, one place

- Reflux has **one product version** for every platform in a release: the Android package (phones, tablets, Android TV, Fire TV), the Windows and Linux desktop packages, and the developer CLI all report the same version.
- It is declared **once**, as `reflux.version` in the root `gradle.properties`. Every build reads it from there; nothing else declares a version. Derived numbers (Android `versionCode`, desktop package versions) are computed from it by the build, never typed by hand.
- The build fails if `reflux.version` does not match the grammar below.

## Grammar

Versions follow [Semantic Versioning 2.0.0](https://semver.org/):

```text
MAJOR.MINOR.PATCH[-STAGE.N][+BUILD]
STAGE = alpha | beta | rc        N = 1..29
```

- `alpha`, `beta`, and `rc` are the only pre-release stages, and each always carries a number (`-beta.1`, never `-beta`). They sort correctly as SemVer requires: `alpha < beta < rc < (release)`.
- `+BUILD` is build metadata (below). It never takes part in ordering or comparisons and never appears in `reflux.version`.

## Stages

| Stage | Version | Who gets it | Meaning |
| --- | --- | --- | --- |
| Development (alpha) | `0.9.0-alpha.N` | The developer's own devices, internal test installs | Pre-beta work towards the beta line. N increases whenever a build is installed anywhere other than an emulator or CI, and at the completion of each roadmap milestone |
| Beta | `0.9.0-beta.N` | Testers through Google Play closed testing, Amazon's testing, and the website's desktop downloads | Feature-complete for V1 per the roadmap; N increases for every build given to testers |
| Release candidate | `0.9.0-rc.N` | Testers and store review | Feature freeze. N increases only for fixes |
| 1.0 | `1.0.0` | Everyone | Built from the final `rc.N` commit with **only** `reflux.version` changed. Any code change after the final release candidate means a new `rc.N+1` |
| After 1.0 | `1.MINOR.PATCH` | Everyone | See below |

The pre-1.0 line is deliberately one line (`0.9.0`) so that every build from now to 1.0 orders correctly in both stores and in package managers.

## SemVer rules after 1.0

- **PATCH** (`1.0.1`): bug fixes, security fixes, compatibility fixes, translations, store-metadata corrections. No new user-visible feature, no change to stored data that an older version cannot read.
- **MINOR** (`1.1.0`): new features, new sources or platforms, schema migrations (forward-only, as today), new settings.
- **MAJOR** (`2.0.0`): a deliberate break the user notices: a data migration that cannot be downgraded *and* changes behavior people rely on, a dropped platform, or a dropped source. Requires a decision in DECISIONS.md.
- A MINOR or MAJOR release may have its own pre-releases (`1.1.0-beta.1`, `1.1.0-rc.1`), with the same stage rules as above.

## Hotfixes

- A hotfix is a PATCH release made from the tag of the release it fixes, on a branch named `hotfix/<version>` (`hotfix/1.0.1`).
- Only the fix goes on it; the fix is also brought to the main line (cherry-pick or merge) in the same session.
- A hotfix to a beta or release candidate is not a patch: it is the next `beta.N` or `rc.N`.
- A version, once given to anyone outside CI, is never rebuilt with different contents. Fix it with the next number.

## Android `versionCode`

Computed by the build from `reflux.version`:

```text
versionCode = MAJOR * 1_000_000 + MINOR * 10_000 + PATCH * 100 + S

S = N          for alpha.N   (1..29)
S = 30 + N     for beta.N    (31..59)
S = 60 + N     for rc.N      (61..89)
S = 99         for a release
```

| Version | `versionCode` |
| --- | --- |
| `0.9.0-alpha.1` | 90001 |
| `0.9.0-beta.1` | 90031 |
| `0.9.0-rc.2` | 90062 |
| `1.0.0` | 1000099 |
| `1.0.1` | 1000199 |
| `1.1.0-beta.1` | 1010031 |

- It increases with every version, which Google Play and the Amazon Appstore both require, and both stores receive the same `versionCode` for the same version.
- N above 29 is not allowed; a stage that needs more builds than that is a sign to move to the next stage or the next PATCH.
- MINOR and PATCH stay below 100.
- `versionName` is `reflux.version` exactly.

## Desktop package versions

Windows installers need a numeric `MAJOR.MINOR.BUILD` that increases for upgrades to work; Linux packages can express pre-releases directly.

- Windows: `MAJOR.MINOR.(PATCH * 100 + S)`, with S as for Android. `0.9.0-beta.1` is `0.9.31`; `1.0.0` is `1.0.99`; `1.0.1` is `1.0.199`. The application itself still shows the SemVer version.
- Linux (deb/rpm): the SemVer version with `~` before the stage (`0.9.0~beta.1`), which sorts before `0.9.0`. Flatpak and AppImage use the SemVer version as it is.

## Build metadata

- A build records the Git commit it was built from (short hash) and whether the tree was clean. It is shown, with the version, on the About page and at the top of the diagnostics log, as `0.9.0-beta.1 (abc1234)`, or `0.9.0-beta.1 (abc1234, modified)` for a tree with uncommitted changes.
- Release and beta builds are made from a clean tree in CI. A `modified` build is never given to testers or a store.
- Build metadata is not part of `reflux.version`, `versionName`, `versionCode`, or any store field.

## Tags, changelog, and promotion

- Each version given to anyone outside CI has an annotated Git tag `v<version>` (`v0.9.0-beta.1`) on the commit it was built from.
- `CHANGELOG.md` keeps an `Unreleased` section. User-visible changes are added there as they are made, in plain words (what a person notices, not commit messages). Cutting a version moves `Unreleased` under a heading `## <version> — <date>`.
- Promotion is a single commit, `release: <version>`, that changes `reflux.version` and the changelog heading and nothing else.
- Promotion order is always alpha → beta → rc → release. Skipping a stage (alpha straight to rc, beta straight to release) needs a decision recorded in DECISIONS.md.
- Entering beta requires the beta checklist in RELEASE.md; entering rc requires the beta exit criteria in ROADMAP.md (M8); releasing requires the release checklist in RELEASE.md and the V1 acceptance criteria.

## Rules for AI agents

1. Read this document before changing `reflux.version`, `CHANGELOG.md` headings, Git tags, store listings, package metadata, or anything that computes a version.
2. Do not change `reflux.version` unless the user explicitly asks for a build or release to be cut. Ordinary development never changes it, including when a milestone is completed; say in the report that a new `alpha.N` would be due instead.
3. Never type a `versionCode` or a desktop package version by hand; change `reflux.version` and let the build compute them.
4. Never reuse a version or a `versionCode`, and never rebuild a released version with different contents.
5. Do add user-visible changes to `CHANGELOG.md` under `Unreleased` as part of the change that makes them.
6. Do not create, move, or push tags, and do not push branches, unless the user explicitly asks.
7. Make a promotion its own `release: <version>` commit containing only the version and changelog changes.
8. If a request conflicts with this protocol (a hand-written `versionCode`, skipping a stage, an untagged tester build), stop and say so rather than working around it.
9. Do not add a second place that declares a version (a constant in code, a property in a module build file). Code that needs the version reads what the build provides.
