# Reflux documentation

A reading guide: which document holds what, and which one wins when two seem to disagree. Each fact has one home; other documents link to it rather than repeat it. Agents start with [AGENTS.md](../AGENTS.md) (and Claude Code with [CLAUDE.md](../CLAUDE.md)).

## Start here

1. [README.md](../README.md): what Reflux is, how to build it.
2. [PRODUCT.md](PRODUCT.md): the product promise, pillars and non-goals.
3. [ROADMAP.md](ROADMAP.md): where Reflux is, the V1.0 criteria, and the plan from V1.1 to V2.0.
4. [BETA.md](BETA.md): the beta in progress, its gate, and its known issues.
5. [ARCHITECTURE.md](ARCHITECTURE.md): the layers and their boundaries.

## The documents

| Area | Document | Authoritative for |
| --- | --- | --- |
| Product | [PRODUCT.md](PRODUCT.md) | Positioning, pillars, the default-experience requirement |
| | [ROADMAP.md](ROADMAP.md) | Product status, V1.0 criteria, versions after 1.0, the public roadmap's source |
| Beta | [BETA.md](BETA.md) | The beta programme: builds, Beta 2's scope and gate, known issues, beta operations |
| | [BETA-NOTES.md](BETA-NOTES.md) | What testers read about each beta |
| | [planning/BETA-BUG-TRACKER.md](planning/BETA-BUG-TRACKER.md) | **Every issue's status** and evidence (B-001 onward, F-001 onward) |
| | [planning/PHASE-4.md](planning/PHASE-4.md) | Phase 4's work, readiness assessment and closure |
| | [planning/V1.1-LIBRARY-RESEARCH.md](planning/V1.1-LIBRARY-RESEARCH.md) | The researched V1.1 library work |
| | [planning/CACHYOS-FINAL-AUDIT-LEDGER.md](planning/CACHYOS-FINAL-AUDIT-LEDGER.md) | The CachyOS final audit (2026-11-24 and 25): its issues, evidence, verification and closure |
| | [CACHYOS-FINAL-HANDOVER.md](CACHYOS-FINAL-HANDOVER.md) | The CachyOS laptop's environment and how to carry the Linux work on to Ubuntu |
| | [UBUNTU-VALIDATION-REPORT.md](UBUNTU-VALIDATION-REPORT.md) | The Ubuntu laptop's environment, libmpv compatibility, and the Linux player's validation there (2026-11-26) |
| Technical reference | [ARCHITECTURE.md](ARCHITECTURE.md) | Layers, Media Core, local-first state, sources |
| | [PLAYBACK.md](PLAYBACK.md) | Capabilities, the planner, execution, diagnostics, Trickplay |
| | [INPUT_MODEL.md](INPUT_MODEL.md) | Semantic actions, controller mapping, text entry, haptics |
| | [NAVIGATION.md](NAVIGATION.md) | Destinations, back stack, source libraries, the player's presentations |
| | [METADATA.md](METADATA.md) | Identification, providers, artwork |
| | [WATCH_STATE.md](WATCH_STATE.md) | Watch state, versions, handoff, profiles |
| | [OFFLINE.md](OFFLINE.md) | Smart Offline |
| | [DESIGN.md](DESIGN.md) | The visual system and motion |
| Privacy and legal | [PRIVACY.md](PRIVACY.md) | What is kept and sent; store disclosures |
| | [THIRD-PARTY.md](THIRD-PARTY.md) | What each artifact ships and under which licence |
| | [SECURITY.md](../SECURITY.md) | Reporting vulnerabilities |
| Release | [RELEASE.md](RELEASE.md) | The release process, platform matrix, signing, artifacts, checklists |
| | [VERSIONING.md](VERSIONING.md) | Version numbers, tags, the changelog (mandatory before any version change) |
| | [release/](../release/README.md) | Store runbooks, store listing text, publication records |
| | [CHANGELOG.md](../CHANGELOG.md) | User-visible changes per version: the release notes' source |
| Website | [WEBSITE.md](WEBSITE.md) | How the site is built, checked and deployed |
| Records | [VERIFICATION.md](VERIFICATION.md) | What was run, on what, with what result (Beta 2 onward) |
| | [DECISIONS.md](DECISIONS.md) | Durable product and architecture decisions (append-only) |
| | [history/](history/README.md) | Archived plans, milestone records and older verification (historical, never current) |
| Store assets | [store-assets/](../store-assets/release-0.9.0-beta.2/README.md) | Store screenshots and listing graphics per release, with provenance |

## When documents disagree

The more specific record wins: an issue's status is the tracker's; a run's result is VERIFICATION.md's; a decision is DECISIONS.md's latest entry on it; product status is ROADMAP.md's; anything under `history/` is the past. Fix the other document rather than live with the disagreement.

## Checking the documentation

`python -I tools/docs/check_links.py` checks every relative link and `#anchor` in the repository's Markdown (CI runs it). The website has its own link check (WEBSITE.md, "Checks").
