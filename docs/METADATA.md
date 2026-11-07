# Reflux Metadata and Identification

## Principle

Reflux should understand ordinary media filenames without requiring users to prepare their libraries specifically for the application.

## Input example

```text
Movies/
  Interstellar (2014).mkv
  Dune (2021).mkv
  Oppenheimer (2023).mkv
```

Messier filenames should also be normalized:

```text
The.Dark.Knight.2008.1080p.BluRay.x264-GROUP.mkv
```

## Pipeline

```text
Filesystem
  -> filename/folder parser
  -> normalized title candidate
  -> year / season / episode hints
  -> provider lookup
  -> candidate ranking
  -> confidence
  -> persisted association
  -> metadata + artwork cache
```

## Matching rules

Initial identification should be deterministic and explainable.

The parser should strip common release and technical tokens such as:

- resolution,
- source tags,
- codec tags,
- bit depth,
- audio tags,
- release-group suffixes.

The matching layer should use title, year, folder context, season/episode information, and other deterministic signals.

## Implemented rules (`core/identify`)

Parsing is deterministic and ordered from most to least specific:

1. **Skipped content.** Extras folders (`Extras`, `Featurettes`, `Trailers`, ...) and `-trailer`-style suffixes, `sample` files, and disc structures (`BDMV`, `VIDEO_TS`) are classified, not added as works.
2. **Episode markers.** `S01E02`, `S01E02-E03`, `1x02`, `Season 1 Episode 2`, air dates (`2024.03.14`). Bare `E02` / `Episode 2` and leading numbers (`01 - Pilot`, `103`) count only inside a season folder, so "Star Wars Episode 2" stays a movie. Fansub-style `[Group] Show - 12` names are absolute-numbered episodes.
3. **Title end.** A title ends at the first unambiguous release token (resolution, codec, source, audio, HDR, release flags) or edition phrase. Ambiguous words (`Web`, `DV`, `Max`) never end a title; they are read as hints only after the title.
4. **Year.** The last plausible year before the title end, never at the start (`2001 A Space Odyssey (1968)`, `Blade Runner 2049`, `1917`).
5. **Folder context.** Show titles come from the nearest folder that is not a season, release, or category folder (`TV`, `Movies`, `Downloads`, ...); without a season folder in between, the folder must agree with the file's title. Movie folders supply missing years, curated punctuation, and titles for generic file names (`movie.mkv`).
6. **Hints.** Resolution, codec, HDR/Dolby Vision, bit depth, and audio codec/channels are read from release tokens into a partial `StreamInfo` until a probe provides real data.
7. **Embedded IDs.** `{tmdb-123}`, `[imdbid-tt123]`, and Plex `{edition-...}` tags are honored.

Every result carries a confidence score with its signals (`YEAR_PRESENT`, `SHOW_FOLDER`, `GENERIC_FILE_NAME`, ...). Low confidence surfaces in diagnostics and the Identify flow.

Files are grouped into works by identity keys built from normalized titles (case, accents, punctuation, and dotted acronyms ignored). A file without a year joins its title's only known year; with several known years (`Dune` 1984 and 2021) it stays separate rather than being guessed into the wrong work. Editions and qualities of the same work become versions of one item.

Sidecars are honored when present: `Movie.en.forced.srt`, `Subs/English.srt`, `poster.jpg`, `fanart.jpg`, `<video>-thumb.jpg`, and `season01-poster.jpg`.

## Provider matching (`core/metadata`, `library`)

Providers describe works; they never define identity. For each movie or show without metadata, Reflux resolves a provider entry in this order: a choice the user made in the Identify flow, an ID embedded in a file or folder name (`{tmdb-…}`, then IMDb/TVDB IDs through the provider's lookup), then a title search. Search results are scored by title similarity (normalized, article- and qualifier-insensitive, original titles included) and year agreement (±1 year tolerated). A candidate is accepted only when it is strong and clearly ahead of the runner-up; when the year is unknown, popularity decides only exact-title ties where it is decisive. Everything else is left for the Identify flow and retried after a week.

Shows fetch the seasons present in the library; episodes match by season and episode number, absolute numbers map across provider seasons, and episodes added later are filled in without re-matching the show. Dated (daily-show) episodes match by air date in the season named after the year or the season implied by the show's first air year.

Artwork choice is deterministic: one image per kind; posters and logos prefer the user's language then text-free images; backdrops and stills prefer text-free images. A source's own artwork wins over provider artwork ("Artwork precedence", below). Provider failures (offline, rate limits, rejected credentials) stop the refresh without recording failures, so it resumes next time.

TMDB is the first provider. Its credential is an application credential supplied by the build, never something users configure.

## Unification

When two works of the same kind share a provider identifier (a matched provider entry, or an ID embedded in a name or reported by a server), they are the same work under two names. The oldest survives; the other's identity key is recorded as an alias, its files are re-identified into the survivor (episode by episode for shows), and its watch history, favorites, and collection membership move along. Aliases are permanent, so rescans keep one work.

## Manual correction (Identify)

**Details page → Identify → search → choose → remembered.** Every movie and show page offers "Identify" (a quiet button; where Reflux is unsure, the page says so and offers it plainly). An episode is identified through its show. The page searches the work's own name once as it opens, then whatever the user types, when they ask (a trailing year narrows it: "Dune 1984"); results show a small poster, the year, the original title where it differs, and what each is about, and mark the current match. Choosing writes an override for every file of the work and pins the chosen provider entry (`identifyAs`); the work's watch history, favorite, collections, and pinned version follow it, and the corrected work's page opens in place of the old one. "Let Reflux identify it" takes the correction back (`undoIdentification`). Searching, nothing found, and the catalog out of reach are each said; without a provider in the build, Identify is not offered.

Episodes whose path names no show (`S01E01.mkv` at a source's top, `TV/Season 1/S01E02.mkv`) are a show of their folder, titled after it or the source, keyed by source and folder so they never merge with another folder's, and weak until identified (DECISIONS.md, 2026-10-17 and 2026-10-18).

Corrections persist as Reflux state and never modify the underlying media file.

## Library depth (M5)

The rule: **sources provide media truth; metadata providers provide descriptive truth; Reflux owns canonical identity, provenance, the cache, and presentation.** Nothing here reaches playback: streams, files, availability, Best Version, and the playback planner read the source as before.

### Canonical identity

A movie, show, season, or episode is a work in the library (`item`), keyed by its identity, never by a file or a source. Each source's copies are versions of it; the same work on two sources is one work with two versions (above, "Unification"). A source's own description of a work and a provider's are kept apart and resolved when read, so neither duplicates the work nor overwrites the other.

### What each witness says

| Witness | Where it is kept | Refreshed by |
| --- | --- | --- |
| A source's own description (a media server's overview, tagline, genres, rating, content rating, runtime, dates, studios or networks; Plex's cast and directors; its seasons' names, overviews, and posters; its episodes' stills) | `source_metadata`, one row per work and source; artwork in `artwork` with origin `source:<id>` | Each scan of that source, replacing that source's rows whole. What each version's work was described as is kept beside it (`catalog_description`, a show's and season's with one of their episodes), so a correction re-identifies offline without losing it. Kept while the source is away; removed with the source |
| A provider's entry (TMDB) | `metadata`, `credit`, provider artwork | The metadata pass, a refresh, or the staleness pass (below) |
| The file name | `item` | Each scan |

Jellyfin's cast is not read: a server takes about twenty milliseconds an item to list people, which made a real library's scan (187 films, 812 episodes) take 25 seconds instead of 3; cast comes from the provider. Plex lists its cast in the request Reflux makes anyway.

Sources that list files (local folders, SMB, WebDAV) have no description of their own beyond sidecar artwork; `.nfo` files are not read and never required. Artwork embedded in files is not read yet (its place in the order is reserved).

### Provenance and precedence

Every resolved value carries its provenance (`core` `MetadataResolver`, `Provenance`): who is answerable for it (user, source, local, provider, inherited, fallback) and which witness gave it. The precedence is per field, deterministic, and tested (`ProvenanceTest`, `LibraryDepthTest`):

| Field | Order |
| --- | --- |
| Title, original title | The provider entry the user chose; a provider entry matched automatically; a source; the file name. A provider's name is the same whichever sources are connected and is in the viewer's language, so the unified title does not change as servers come and go |
| Overview, tagline, genres, runtime, dates, rating, content rating, studios, credits | The provider entry the user chose; the sources; a provider entry matched automatically, filling what the sources leave out |
| Stream facts, file names, availability, versions | The source and the probe, always; no metadata witness is consulted |

- The user's choice outranks every source because a source's description may be of what the user corrected. The seasons and episodes of a show the user identified are as certain as the show.
- Sources are weighed in the order they were added (oldest first), never the order they were scanned, so the unified description does not oscillate with scan order (`twoServersResolveTheSameWhicheverIsScannedFirst`).
- A list (genres, studios, credits) is one witness's, whole; lists are never mixed.
- A blank, zero, or missing value is not a value: it never hides another witness's.

### Matching evidence

How a provider entry was found is recorded (`MatchEvidence`, column `metadata.match_evidence`), strongest first: the user's choice, the provider's own ID (in a name or reported by a server), another catalog's ID resolved through the provider, a title and year search, a title search. Entries matched before this was recorded have no evidence (treated as the weakest); entries the user chose were marked as theirs by migration 6.

- A refresh reads the entry already matched again; it never searches, so a refresh cannot turn a known identity into a guess.
- A provider ID that a server reports replaces only a weaker match (a title search); it never replaces the user's choice or another ID match.
- An entry the provider no longer has keeps what was known of it.
- Identify drops the provider IDs the file name or server claimed for the files it corrects, so the corrected work is never unified back with the work it was mistaken for; a choice follows its work through unification.

### Artwork precedence

`core` `ArtworkPrecedence`: one picture of each kind, chosen by authority, then the source order:

1. The user's choice (reserved: Reflux has no artwork picker yet).
2. The sources' own: a server's images, a `poster.jpg` beside the media.
3. Artwork embedded in files (reserved; not read yet).
4. The provider's.
5. The parent's, where a picture is drawn: an episode without a still shows its show's backdrop; a season without a poster, its show's.
6. The generated title treatment (`ArtworkChoice`).

What each surface uses: a poster for grids; the backdrop for a work's header; a season's poster beside its name; an episode's still in its row, its page, and Continue Watching. The cache is how a chosen picture is delivered, never a reason to choose one.

### Artwork cache

`ArtworkCache` (`library`, JVM) keeps downloaded images and copies of in-source images on disk in Reflux's data directory, named by a digest of where they come from: one file per picture, a picture whose address changes is a new one. It never writes anywhere else, and never beside media.

- **Bounded:** least recently used files are evicted beyond 2 GB; `usage()` reports files and bytes.
- **One download at a time per picture:** a picture asked for while it is on its way waits for that download.
- **Failures are not retried at once:** a provider picture that failed is not asked for again for 10 minutes, a source's own for 1 minute (the source coming back is the usual reason).
- **Sized:** Jellyfin pictures are asked for at most 780 pixels wide (a backdrop 1,920, a logo 800), not the server's original (a real library: 6.3 MB for 16 pictures became 2.25 MB). TMDB's are fetched at fixed sizes (posters and stills `w780`, backdrops `w1280`, people `w185`).
- **Fetched as needed:** after a pass, the posters, backdrops, and logos of movies and shows are prefetched; of episodes' stills, only those in Continue Watching. The rest are fetched as they come into view, and shells keep decoded images in memory (`DesktopArtworkLoader`, `AndroidArtworkLoader`).
- **Offline:** what is kept is shown with the source or provider away.

### Requests

`ProviderGate` stands between Reflux and the provider: the same question asked while it is being answered is asked once; an answer is remembered for 10 minutes (at most 256); requests go one at a time at least 50 ms apart; after the provider cannot be reached it is left to rest (1 minute, doubling to 30), and callers are told at once. A request the viewer makes (Identify, Refresh details) ends the rest. The metadata pass looks up only works that have no metadata and no recent attempt (unmatched works are retried after a week), 50 at a time.

### Refresh

Refreshes are scoped and only on request: Identify's "Refresh details" reads a movie's or show's matched entry again (with a show, every present season), or one season (`Library.refreshDetails`). Nothing is removed until what replaces it has arrived; a failure keeps everything and says so; watch state, sources, and files are untouched. A source's own description is refreshed by scanning it.

Provider data older than 90 days is read again, ten works per pass, oldest first (`refreshStaleMetadata`), within the six months TMDB's terms allow a copy to be kept (below).

### Online lookups

"Online metadata & artwork", under Sources, where the build has a provider (`metadata.online`, on by default). Off, nothing is sent to a metadata provider and no new provider picture is fetched; what sources say, what was fetched before, and file-name identification are what the library shows, and nothing known is forgotten. Identify is not offered while it is off. Turned on again, what was waiting is looked up.

### Language

The provider is asked in the device's language (`Locale` tag, `en-US` when there is none); posters and logos prefer that language, then text-free images; backdrops and stills prefer text-free images. The original title is kept beside the localized one and shown where it differs. A source's description is in whatever language its server keeps; it is not translated.

### Privacy

A lookup sends the provider a work's title and year (or an ID found in a name or reported by a server), the language, and the application's credential: never a source's address, a file path, a server's token or credentials, or the user's identity. Pictures from the user's own servers are fetched from those servers with their own credentials, as playback is. The provider credential is never logged (`Redaction`).

### Provider and licensing

The provider contract (`MetadataProvider`: search, external IDs, details with credits, images, and external IDs, seasons with episodes) is provider-neutral; TMDB is the one adapter (`metadata:tmdb`), isolated and replaceable. TMDB's API terms (last updated 2023-10-20; read 2026-11-07) grant a **non-commercial** licence: commercial use needs a separate written agreement, and TMDB decides what is commercial. Reflux is free and supported by voluntary donations; the terms do not mention donations, so whether that is commercial use is **unresolved and needs TMDB's written confirmation before release** (RELEASE.md, legal review). The terms also limit keeping any TMDB information to six months (the staleness pass keeps refreshed data well inside that while online; a device offline for longer keeps what it had, which the legal review must weigh), require attribution (the About page; the terms' wording and the FAQ's differ, and the review settles which), and say nothing on shipping a key in a client application. None of this is approved for production by M5.

## Future intelligence

Higher-level AI or recommendation features may consume the deterministic metadata model, but AI must not become a prerequisite for basic identification, search, or playback.
