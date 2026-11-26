# Reflux history

**Everything here is historical.** These are the plans, milestone records and verification logs that built Reflux, kept unchanged so that decisions and evidence can be traced. None of them is a current plan: what is current is listed in the repository README's [Documentation](../../README.md#documentation) section.

Archived in Phase 4 (2026-11-21), and in the documentation reorganisation of 2026-11-26 ([its report](verification/DOCUMENTATION-REORGANIZATION-REPORT.md)), with `git mv`, so each file's history is intact (`git log --follow <file>`). Links inside them were updated to their new places; their text was not edited, so dates, statuses and "next steps" are as they were written. Where a document says "ROADMAP.md", "BETA-2.md" or "VERIFICATION.md, '<section>'", the section is found in the archived copy below unless it is a Beta 2 verification section, which stays in the current [VERIFICATION.md](../verification/VERIFICATION.md).

## Roadmaps

| File | What it was | Replaced by |
| --- | --- | --- |
| [roadmaps/ROADMAP-M0-M13.md](roadmaps/ROADMAP-M0-M13.md) | The milestone roadmap (2026-10-17, rebaselined 2026-11-02 and 2026-11-11): M0 to M13, every item and limit, the V1 acceptance criteria, V1.x, "Earlier numbering" | [ROADMAP.md](../ROADMAP.md) (product roadmap) and [BETA.md](../release/BETA.md) (the beta) |

The roadmap before 2026-10-17 (the Phase 0–18 plan) is in Git history only; the M0–M13 roadmap's "Earlier numbering" maps it.

## Milestones

| File | What it was |
| --- | --- |
| [milestones/M8-M13-REBASELINE.md](milestones/M8-M13-REBASELINE.md) | The 2026-11-11 rebaseline's research, critique and milestone plan for M8 to M13, including the 1.0 boundary and V2's transfer and sync research |
| [milestones/M10-PLAYER-SPEC.md](milestones/M10-PLAYER-SPEC.md) | Player & Playback Experience 2.0's implementation specification (Back, the mini-player, picture-in-picture, the chrome model). Code comments cite it as the design of record for those parts |
| [milestones/M11-DISTRIBUTION-PLAN.md](milestones/M11-DISTRIBUTION-PLAN.md) | Distribution & Release Surfaces' plan and research; its living parts are now [RELEASE.md](../release/RELEASE.md) |
| [milestones/LAUNCH.md](milestones/LAUNCH.md) | The launch plan: the website's stages and visual direction, the demo video, the beta programme and its operations texts, store tracks. Living parts moved to [BETA.md](../release/BETA.md) (beta operations), [WEBSITE.md](../release/WEBSITE.md) and [RELEASE.md](../release/RELEASE.md) |

## Beta 2's phases

| File | What it was |
| --- | --- |
| [phases/BETA-2-PHASES.md](phases/BETA-2-PHASES.md) | Beta 2's register, its three phases (work orders, exit criteria), the verification matrix, the gate, and the **Phase 3 closure record** (2026-11-20). Formerly `docs/planning/BETA-2.md` |
| [phases/PHASE-1-PLAN.md](phases/PHASE-1-PLAN.md) | Phase 1: reliability, security, playback, observability (accepted 2026-11-19) |
| [phases/PHASE-2-PLAN.md](phases/PHASE-2-PLAN.md) | Phase 2: the audit, the interaction contract, the plan, the closure (accepted 2026-11-20) |
| [phases/F-P4-ADAPTIVE-KEYBOARD.md](phases/F-P4-ADAPTIVE-KEYBOARD.md) | Phase 3's adaptive keyboard research and plan; the keyboard is built. Code comments cite it |
| [phases/PHASE-4-HANDOFF.md](phases/PHASE-4-HANDOFF.md) | The brief Phase 4 started from; Phase 4's own record is [PHASE-4.md](phases/PHASE-4.md) |
| [phases/PHASE-4.md](phases/PHASE-4.md) | Phase 4's record: consolidation, roadmap, store assets, website, release infrastructure; its readiness assessment and closure (2026-11-21). Formerly `docs/planning/PHASE-4.md`, moved 2026-11-26 |

## The Linux machine's move (2026-11-25 and 26)

| File | What it was | Current record |
| --- | --- | --- |
| [linux/CACHYOS-FINAL-HANDOVER.md](linux/CACHYOS-FINAL-HANDOVER.md) | The CachyOS laptop's environment as read before its wipe, the Linux player as it stood, how the window tests were reproduced. The machine no longer exists; its values are evidence, not setup instructions | The audit: [CACHYOS-FINAL-AUDIT-LEDGER.md](../verification/CACHYOS-FINAL-AUDIT-LEDGER.md) |
| [linux/UBUNTU-TRANSITION.md](linux/UBUNTU-TRANSITION.md) | The procedure for the move to Ubuntu: what to keep, the development setup, the CI-identical display, and the Linux playback acceptance checklist (section 4). Carried out on 2026-11-26 | [UBUNTU-VALIDATION-REPORT.md](../verification/UBUNTU-VALIDATION-REPORT.md); its checklist is now [LINUX-PLAYBACK-ACCEPTANCE.md](../verification/LINUX-PLAYBACK-ACCEPTANCE.md) |

## Verification

| File | What it was |
| --- | --- |
| [verification/VERIFICATION-ALPHA-TO-BETA-1.md](verification/VERIFICATION-ALPHA-TO-BETA-1.md) | Every verification section from the first desktop slice (2026-10-08) to the Beta Season's start (2026-11-17): M0 to M11, the release rehearsals, Beta 1's cut. Test counts are the counts of their day |
| [verification/COMMIT-MAP-2026-11-27.md](verification/COMMIT-MAP-2026-11-27.md) | Old and new hashes of every commit rewritten on 2026-11-27 to remove AI attribution trailers: find a commit the documents cite by its old hash |
| [verification/DOCUMENTATION-REORGANIZATION-REPORT.md](verification/DOCUMENTATION-REORGANIZATION-REPORT.md) | The documentation reorganisation of 2026-11-26: what moved where, what was folded or removed, and why |

## What was not archived, and why

- **DECISIONS.md** stays whole in `docs/decisions/`: it is an append-only log, and older decisions still bind (each superseded one says so in place).
- **The tracker** ([BETA-BUG-TRACKER.md](../BETA-BUG-TRACKER.md)) stays at the root of `docs/`: it is the live record for every issue through 1.0.
- **CHANGELOG.md** stays at the root: it is the release notes' source.
