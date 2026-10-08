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

## Manual correction

Provide a fast flow such as:

**Long press poster -> Identify this movie/show -> candidate search -> choose -> remember**

Corrections should persist as Reflux state and must not modify the underlying media file.

## Caching

Metadata and artwork should be cached locally.

A disconnected source should not cause the library's identified content to disappear.

## Future intelligence

Higher-level AI or recommendation features may consume the deterministic metadata model, but AI must not become a prerequisite for basic identification, search, or playback.
