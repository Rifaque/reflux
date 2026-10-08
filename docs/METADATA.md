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

Shows fetch the seasons present in the library; episodes match by season and episode number, absolute numbers map across provider seasons, and episodes added later are filled in without re-matching the show. Dated (daily-show) episodes are not matched yet.

Artwork choice is deterministic: one image per kind; posters and logos prefer the user's language then text-free images; backdrops and stills prefer text-free images. User-provided local artwork always wins over provider artwork. Provider failures (offline, rate limits, rejected credentials) stop the refresh without recording failures, so it resumes next time.

TMDB is the first provider. Its credential is an application credential supplied by the build, never something users configure.

## Manual correction

Provide a fast flow such as:

**Long press poster -> Identify this movie/show -> candidate search -> choose -> remember**

Corrections should persist as Reflux state and must not modify the underlying media file.

## Caching

Metadata and artwork should be cached locally. Metadata lives in the library database; `ArtworkCache` keeps downloaded images and copies of in-source images (such as `poster.jpg` on a removable drive) on disk with least-recently-used eviction.

A disconnected source should not cause the library's identified content to disappear.

## Future intelligence

Higher-level AI or recommendation features may consume the deterministic metadata model, but AI must not become a prerequisite for basic identification, search, or playback.
