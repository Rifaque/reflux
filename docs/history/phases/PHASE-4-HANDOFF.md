> **Historical (archived 2026-11-21, Phase 4).** Kept unchanged as a record; not a current plan. Current documents: [docs/README.md](../../README.md); this archive: [history/README.md](../README.md).

# Phase 4 handoff

What Phase 4 is for, as the owner set it at Phase 3's closure (2026-11-20), and what it starts from. A starting brief, not a plan: Phase 4 researches and plans each part itself, and nothing here is a commitment.

## Where Phase 3 left things

- **Phase 3 is accepted and formally closed by the owner (2026-11-20).**
  - Its closure record (commits, results, accepted limitations, deferred and carried items): [BETA-2.md, "Phase 3 closure"](BETA-2-PHASES.md#phase-3-closure-2026-11-20-accepted-and-formally-closed-by-the-owner).
  - Each issue: [BETA-BUG-TRACKER.md](../../planning/BETA-BUG-TRACKER.md).
  - The runs: VERIFICATION.md's three "Phase 3" sections.
- **The latest test result is not wholly green:** 941 tests in the UI and core run, with 2 timing-sensitive `SourcesUiTest` failures (B-074). A full `./gradlew check` belongs at the start of Phase 4's release verification.
- **Beta 2's fix scope is frozen.** Findings go to the tracker's [Beta 3 backlog](../../planning/BETA-BUG-TRACKER.md#beta-3-backlog-from-phase-3s-closure-2026-11-20) unless the owner authorises a critical Beta 2 blocker. Phase 4 does not fix bugs by default.
- **Beta 2 is not cut.** The gate in BETA-2.md still needs:
  - the owner's acceptance of Phase 3;
  - hosted CI;
  - the owner's approval and version authorisation;
  - release notes;
  - the release workflow.
- **Open items carried into Phase 4's planning:**
  - B-061: a Skia crash after a Direct3D failure, upstream in Skiko, Investigating (a Beta 3 candidate);
  - B-074: two timing-sensitive `SourcesUiTest` cases (Beta 3);
  - B-063 and B-070's untested parts: physical Linux and the Surface; Steam itself;
  - B-022: Windows start-up, measured, no optimisation justified yet;
  - B-044: a test-clock intermittent, test infrastructure for M12;
  - B-026 and B-015: Not Reproduced;
  - B-056: Deferred by the owner;
  - the V1.1 candidates B-027, B-038, B-040 ([V1.1-LIBRARY-RESEARCH.md](../../planning/V1.1-LIBRARY-RESEARCH.md));
  - F-003, video haptics: a deferred future feature, below.

## 1. Documentation consolidation

- **Audit:** every document under `docs/` and `docs/planning/`. ROADMAP, DECISIONS, VERIFICATION, the phase plans and the tracker have grown by appending, with the same fact often stated in several places.
- **Consolidate** so that each fact has one home: the source-of-truth rules in AGENTS.md and the "nothing repeated" rule in BETA-2.md, applied across the whole set.
- **Preserve history:** decisions and verification records are archived, not deleted (for example, phase records moved under `docs/history/`, with links kept working).
- **Check the result:** a link and anchor check, and a short reading guide for a new contributor or agent.

## 2. User-facing website overhaul

- **Readability:** a polished, easily readable site for people who use Reflux, not people who build it.
- **Release information:** categorised and consistent. Phase 3 introduced groups in CHANGELOG.md's sections ("### Playback & Media" …), which the Releases page shows. Phase 4 decides the lasting categories and applies them to every release.
- **Features and fixes:** presented the same way on every page. Screenshots are refreshed from a released build only: the site shows what people can download.

## 3. Beta 2

- **Contents:** what Beta 2 contains: Phases 1–3's fixes and features, as the tracker records them, and anything the owner adds or removes.
- **Release notes:** written from CHANGELOG.md's Unreleased section, already grouped for people.
- **The gate's remaining steps,** in order. Nothing is published as a current feature on the website until it is in a downloadable build.

## 4. Future product roadmap

The release buckets are planning candidates, to be evaluated and organised in Phase 4, not commitments:
- **V1:** what Beta 2 and the release candidate close.
- **V1.1:** the researched candidates B-027 (separate libraries), B-038 (sorting and filtering), B-040 (discovery variety), and F-P1 (the collapsible sidebar) and F-005 (controller remapping) as the owner places them.
- **V1.2, V1.3, V1.5, Version 2:** to be filled from the research Phase 4 does.
- **Video-synchronised haptics (F-003):** deferred as a future experimental feature by the owner (2026-11-20). Not part of Phase 3 or Beta 2, and no release is assigned yet; V1.2, V1.3, V1.4 and Version 2 are possible, deliberately undecided.
  - **Before it is placed:** its own research, design, hardware validation, synchronisation investigation, platform analysis, safety controls, and an implementation and testing plan.
  - **Starting points:** the desktop feasibility probe and the design notes in the tracker's F-003 entry.

## 5. Website roadmap easter egg (plan and build in Phase 4; nothing was built in Phase 3)

- **What it is:** an optional, discoverable experience on the website showing Reflux's evolution from V1 towards V1.1, V1.2, V1.3, V1.5 and Version 2. It says what is done, what is being explored, and what is planned.
- **How it should feel:** memorable rather than ordinary. Thoughtful diagrams, scroll-driven storytelling, tasteful animation and smooth transitions, reduced motion respected, and an elegant progression through the releases.
- **How it is found:** like an easter egg, not as a menu item.
- **Constraints:**
  - it follows the roadmap of §4 and claims nothing as done that is not released;
  - it respects the site's content security policy (everything served by the site itself);
  - it is checked by the site's layout checks at every size.
- **Haptics:** placed on it only after the research of §4 and the owner's review.
