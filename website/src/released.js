// Whether a version is out, from the publication records (release/published/*.json through release-data.mjs), never
// typed on a page: the site shows a feature as current only once a build that has it can be downloaded
// (the Phase 4 brief: "Nothing is published as a current feature on the website until it is in a downloadable build").
import release from './generated/release.json'
import { releasedIn } from '../scripts/release-lib.mjs'

/** Beta 2's features are named as coming until a Beta 2 (or later) build is published. */
export const BETA2 = '0.9.0-beta.2'
export const beta2Out = releasedIn(release.published, BETA2)
