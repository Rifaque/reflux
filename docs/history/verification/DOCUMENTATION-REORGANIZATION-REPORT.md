# Documentation reorganisation (2026-11-26)

> **Historical record.** What the reorganisation of 2026-11-26 moved, folded and removed, and why. The current guide to the documents is the repository README's [Documentation](../../../README.md#documentation) section; the decision is [DECISIONS.md](../../decisions/DECISIONS.md), 2026-11-26, "Documentation: two live documents at the root of `docs/`…".

Branch `beta-3`, from `54c21a4`. Documentation only: every file moved with `git mv` (history kept: `git log --follow`); no code, test or build behaviour changed.

## Structure

```text
docs/
├── ROADMAP.md               product status, V1.0 criteria, versions after 1.0
├── BETA-BUG-TRACKER.md      every issue's status and evidence
├── product/                 what Reflux is and how it behaves
├── engineering/             how it is built
├── release/                 the beta, releasing, versions, legal, the website
├── decisions/               the decision log
├── verification/            the verification log; audit and validation reports, with evidence
└── history/                 completed plans, handovers, older records (never current)
```

Each retained document's purpose is the "Authoritative for" column of the README's [Documentation](../../../README.md#documentation) table; it is not repeated here.

## What moved

| From `docs/` | To `docs/` |
| --- | --- |
| `PRODUCT.md`, `DESIGN.md`, `NAVIGATION.md`, `INPUT_MODEL.md`, `METADATA.md`, `PLAYBACK.md`, `OFFLINE.md`, `WATCH_STATE.md` | `product/` |
| `planning/V1.1-LIBRARY-RESEARCH.md` | `product/` |
| `ARCHITECTURE.md` | `engineering/` |
| `BETA.md`, `BETA-NOTES.md`, `RELEASE.md`, `VERSIONING.md`, `THIRD-PARTY.md`, `PRIVACY.md`, `WEBSITE.md` | `release/` |
| `DECISIONS.md` | `decisions/` |
| `VERIFICATION.md`, `UBUNTU-VALIDATION-REPORT.md` | `verification/` |
| `planning/BETA-3-INTEGRATION-AUDIT.md`, `planning/CACHYOS-FINAL-AUDIT-LEDGER.md`, `planning/cachyos-audit/` (24 screenshots) | `verification/` |
| `planning/BETA-BUG-TRACKER.md` | the root of `docs/` |
| `planning/PHASE-4.md` | `history/phases/` (archived) |
| `CACHYOS-FINAL-HANDOVER.md`, `UBUNTU-TRANSITION.md` | `history/linux/` (archived) |

`docs/planning/` no longer exists: its live documents went to their subject's folder, its closed record to history.

## Consolidated and deleted

| Former path | Why | Its content is now |
| --- | --- | --- |
| `docs/README.md` (the reading guide) | The root of `docs/` holds only two files; a guide inside a subfolder would not be found | The repository README's [Documentation](../../../README.md#documentation) section: the same table (rows updated, the three archived documents moved to the [history index](../README.md)), "When documents disagree" and "Checking the documentation" under their old headings, so their anchors still work |

No other document was deleted. Checked and **kept separate**, because each has its own reader or purpose:

- **BETA.md, BETA-NOTES.md, CHANGELOG.md:** the programme and its gates (the project), what testers read (platform notes, known issues, what to test, how to report), and the stores' release notes. Statuses live only in the tracker.
- **ROADMAP.md's "Beta maturation" and BETA.md's "From Beta 3 to 1.0":** a short summary that links to the detail; different content (the backlog vs. what the beta must prove).
- **VERIFICATION.md and the reports:** the log is the one place to find every result and its environment; each report holds evidence the log only summarises (environment measurements, research, screenshots), which could not be rebuilt from it.
- **CACHYOS-FINAL-HANDOVER.md and UBUNTU-TRANSITION.md:** overlapping only in the CI-like virtual display; the first is the only record of the CachyOS machine, the second the procedure that was carried out. Both archived, not merged.

## Historical records

Newly archived, each with a status note at its top: `history/phases/PHASE-4.md`, `history/linux/CACHYOS-FINAL-HANDOVER.md`, `history/linux/UBUNTU-TRANSITION.md`. The [history index](../README.md) lists them. As in Phase 4's archive, **link targets inside history files were updated and their text was not**: 111 lines changed across 14 files, every one only inside a link's target (checked line by line against `54c21a4`).

## References

- **Markdown:** 305 relative links recomputed from each file's old place to its new one; 11 links that pointed at the reading guide now point at the README's `#documentation`; 14 link texts that showed an old `planning/` path show the file's name.
- **Outside Markdown** (the owner's choice: update everything, path only): **175 files, 314 changed lines** in the final staged diff (Kotlin source and tests, Gradle scripts, SQLDelight comments, CI workflows, `tools/`, website sources and scripts, `legal/*.txt`, `store-assets/`, `.gitleaksignore`). Checked mechanically against `54c21a4`: in **310 lines** putting the old path back gives the original line; the other **4 lines** are the three documented exceptions below (1 in `ci.yml`, 2 in `PictureInPicture.kt`, 1 in `.gitleaksignore`). **Nothing in the code, build, CI or website reads a document**; the website's `/docs/...` strings are site routes and were not touched. No linter in the repository limits line length (no ktlint, detekt, Spotless, `.editorconfig` or website linter), so the longer paths cannot fail a check; the 12 edited website scripts pass `node --check`.

  | Change | Files | Lines | What |
  | --- | --- | --- | --- |
  | The automated path rewrite | 173 | 311 | 310 lines with an old path replaced by its new one, plus `ci.yml`'s comment citing the reading guide, `docs/README.md` → `README.md` (the guide moved there; exception 1) |
  | A citation already broken before this work | 1 (`PictureInPicture.kt`) | 2 | `docs/planning/` + `M10-PLAYER-SPEC.md` → `docs/history/` + `milestones/M10-PLAYER-SPEC.md`, across a KDoc line break (exception 2); Phase 4 had archived the file |
  | A citation that never resolved | 1 (`.gitleaksignore`) | 1 | the comment line only (exception 3; decision 3 below) |

  **Why earlier figures differ.** The first count (173 files, 311 lines) was taken straight after the automated rewrite and is what this report first said. The PictureInPicture.kt correction came after it and made **174 and 313**, the figures in the first summary to the owner. The `.gitleaksignore` correction (the owner's decision 3) makes **175 and 314**. The rewrite's own 311 lines are the same throughout.
- **Statements about the past were left as written:** DECISIONS.md's 2026-11-21 entry still says `docs/README.md` was the guide (marked superseded in place), and the Beta 3 integration audit's merge map still names `docs/README.md`.
- **Broken references found and corrected:** none among the Markdown links before the move (the check passed at `54c21a4`); every link the move would have broken was recomputed. Two citations **already broken** were corrected: `PictureInPicture.kt` cited `docs/planning/M10-PLAYER-SPEC.md` (archived to `history/milestones/` in Phase 4), and `.gitleaksignore` cited `docs/SECURITY-AUDIT.md` (never existed). Every `docs/…` path cited outside `history/` now exists, except the two deliberate past-tense mentions of `docs/README.md` and a fake path in `tools/release/test_release.py`'s fixture.
- **Link check** (`python -I tools/docs/check_links.py`, CI's check): 0 broken before; **637 relative links, 0 broken** after (the final staged tree, with the acceptance procedure).

## The owner's decisions (2026-11-26)

1. **The Linux playback acceptance checklist** (section 4 of the archived UBUNTU-TRANSITION.md) is now a current procedure, [verification/LINUX-PLAYBACK-ACCEPTANCE.md](../../verification/LINUX-PLAYBACK-ACCEPTANCE.md): Part 1, the checks for every Linux desktop (the original IDs kept); Part 2, what depends on the distribution, GNOME, tiling desktops and hybrid graphics, with what the Ubuntu validation taught (covered windows throttled, `XDG_CURRENT_DESKTOP` and `FirstRunTest`, B-104's render node). The archived document is unchanged apart from link targets and its status note, which now points to the procedure.
2. **`hyprland-player-owner-look.png` is kept and cited again**, in the ledger's CACHY-002 (B-084) entry. It is the two-window player as the **nested** Hyprland composited it with the owner's opacity, blur, rounding and border values and no window rule: the controls drawn, the whole picture blurred behind the transparent controls window. It is the picture the audit's first report accepted, and shows why the nested session was the wrong acceptance environment.
3. **`.gitleaksignore`'s citation was stale and is corrected.** The file holds six reviewed fingerprints (commit:file:rule:line: fake values in five tests, and the throwaway Docker share's credentials in `tools/ci/share-test-servers.sh`), added on 2026-11-08 (`fba4590`, `62eb661`); the review is recorded in [VERIFICATION-ALPHA-TO-BETA-1.md](VERIFICATION-ALPHA-TO-BETA-1.md), "M6: Release engineering, security, legal" and "M7: Compatibility, release proof, brand, website, beta readiness". `docs/SECURITY-AUDIT.md` never existed in any commit. The citation is on a `#` comment line, and only that line changed. **Proof that scanning is unchanged:** CI's scanner (gitleaks v8.24.3, its release archive checked against the published SHA-256) over the whole history at `54c21a4`, with the repository's own `.gitleaksignore` removed from the scan's working tree so that only the file given counts: no ignore entries, **6 findings** (exactly the six fingerprints); the original file, **0**; the corrected file, **0**, with byte-identical reports. (Without that removal gitleaks also reads the repository's own file, which is why a first control run showed 0.)
4. **The repository's `release/` and `docs/release/` stay separate**: the first holds store runbooks, listing text and publication records, and is read by the website's build; the second holds the process documents.
5. **Hosted CI recorded** in CLAUDE.md, the Beta 3 integration audit, VERIFICATION.md ("Beta 3 integration") and B-105: `54c21a4`, CI 45811843168 and the release rehearsal 45811843105, every applicable job passing on its first attempt; the folder-watcher test passed on Windows; the Windows desktop-window step skipped by design; the Linux desktop window tests passed. The audit's original statements are kept, with dated updates.

## Remaining questions

None from the reorganisation itself. The open product and release decisions are where they were: the tracker's Beta 3 backlog, and the ledger's "Closure".

## Correction after the push (2026-11-26)

*Added after the reorganisation was committed (`b823a74`) and pushed; the sections above are left as they were written.*

**What went wrong.** Four files changed by the path-only update are not only documentation: `tools/libmpv/Dockerfile`, `build.sh`, `sources.txt` and `README.md` are copied into the LGPL corresponding-source bundle of Reflux's Windows libmpv (`tools/libmpv/build.sh`, "The complete corresponding source"), and that bundle's SHA-256 is pinned in `desktop/app/packaging/libmpv-windows.properties` (`sourceSha256`). Their comment edits changed the bundle, so it no longer matched the pin, and the release rehearsal's "Windows libmpv from source" failed on `b823a74` (run [45829860004](https://github.com/Rifaque/reflux/actions/runs/45829860004): the rebuilt DLL `e52f3f45…` matched its pin; the bundle was `70c934e3…` against the pinned `53853ac9…`). The statements above that no release configuration changed were wrong for these four files: nothing *reads* them as documents, but they are release inputs.

**The correction.** A separate commit after `b823a74` restores the four files byte for byte to `54c21a4` (the blobs are identical: `6a5fc070` Dockerfile, `a70bb6e2` build.sh, `3b45b6da` sources.txt, `061950d0` README.md). They are **deliberate exceptions** to the path update: they keep citing the old paths (`docs/RELEASE.md`, `docs/THIRD-PARTY.md`, `docs/VERIFICATION.md`), because changing a word in them changes a pinned, shipped artifact. Neither pin, nor any other release configuration, changed.

**Proof** (WSL2 Ubuntu 24.04, a VM; GNU tar 1.35 and XZ Utils 5.4.5, the build image's distribution): the bundle from the last green release rehearsal ([45811843105](https://github.com/Rifaque/reflux/actions/runs/45811843105), on `54c21a4`) hashes to the pin, `53853ac9…`. Re-packed with `build.sh`'s own flags it gives `53853ac9…` again (the re-pack is byte-faithful); with `b823a74`'s four files it gives exactly the failed run's `70c934e3…` (those four files were the whole cause); with the restored files, `53853ac9…`. The DLL pin, `e52f3f45…`, is untouched by these files.

**After the correction**, measured from `54c21a4`: outside Markdown, **172 files and 311 lines** (from 175 and 314: the three non-Markdown files restored, with their 3 lines); `tools/libmpv/README.md`, a Markdown file, is restored too (3 lines). The three documented exceptions are unchanged.

**Also on `b823a74`**, not caused by the reorganisation:
- The "Windows libmpv" workflow (run [45829859927](https://github.com/Rifaque/reflux/actions/runs/45829859927), which runs whenever `tools/libmpv/` changes) failed earlier, at the download of `zlib-1.3.1.tar.gz`: CI received bytes hashing `5ea058da…` against the pinned `9a93b2b7…`, and `build.sh` refused them. A transient download anomaly: the release rehearsal downloaded the same archive correctly at the same time, and three downloads on 2026-11-26 all matched the pin. It is a different failure from the bundle pin above.
- The CI's Linux check (run [45829860032](https://github.com/Rifaque/reflux/actions/runs/45829860032)) failed on B-077, the known test race.
