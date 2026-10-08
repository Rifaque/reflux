# Reflux Decision Log

## 2026-10-08 — Product identity

Reflux is treated as a universal media player rather than a Jellyfin-specific client.

## 2026-10-08 — Jellyfin architecture

Jellyfin is a source adapter, not the application foundation.

## 2026-10-08 — Local-first

Local media and locally cached library state are first-class. Core functionality must not require a Reflux account.

## 2026-10-08 — Virtual organization

Reflux may organize media virtually, but must not silently rename, move, delete, or otherwise modify user media files.

## 2026-10-08 — Automatic metadata

Automatic identification, metadata, and artwork are core product behavior. Users should not need sidecar files such as poster.jpg or .nfo files for the default experience.

## 2026-10-08 — Default experience

Out-of-the-box quality is a hard requirement. Customization is additive.

## 2026-10-08 — Search foundation

Smart search begins with deterministic, rule-based parsing. Natural-language intelligence can be layered on top later.

## 2026-10-08 — Controller architecture

Controller support is normalized around semantic actions, with first-class PlayStation and Xbox support and adaptive UI transitions.

## 2026-10-08 — Version selection

When otherwise equivalent, local media should be preferred. Best Version logic may also consider resolution, compatibility, HDR, audio, availability, and reliability.

## 2026-10-08 — Platform scope

Initial target scope is Android phone/tablet, Android TV / Google TV, Windows, and Linux. iOS/iPadOS is not a v1 commitment.

## 2026-10-08 — Visual direction

The design language combines restrained premium ergonomics, cinematic media presentation, and liquid glass without copying any single reference product.
