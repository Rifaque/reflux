# Playback compatibility

What Reflux can really play, how it decides, and how it explains that. This is the specification for milestone M2P (ROADMAP.md). It began as the M2.0 audit of 2026-11-01; sections 6 to 10 are a proposal until DECISIONS.md and the code say otherwise.

The rule: **decode locally when possible, preserve original streams when possible, transcode only when genuinely necessary.** Success is that Reflux knows what the client can actually play, preserves the original whenever possible, degrades the smallest necessary part when it cannot, and can explain why. "Every codec works everywhere" is not the goal.

Direct Play is not hardware decoding. Delivery (how bytes reach the device) and decoding (what turns them into pictures) are separate questions, and original media decoded in software is still original media.

## 1. How playback works today

| Question | Answer in the code |
| --- | --- |
| What is modeled | `DeviceCapabilities`: video decoders (codec, frame size, bit depth, hardware true/false/unknown), audio decoder set, passthrough set, output channels, display (size, ranges, tone mapping), containers, subtitle formats, features. No profile, level, frame rate, or sample rate. No Dolby Vision decoder, and no difference between E-AC-3 and E-AC-3 JOC |
| Where choices are made | Once: `Library.planPlayback` (`library/.../Playback.kt`) takes Best Version and asks the source for a target. `PlaybackAssessor` is the only compatibility logic. There is no delivery choice to make, because there is one delivery |
| How Jellyfin playback is requested | It is not negotiated. `JellyfinSource.playbackTarget` always returns `Videos/{id}/stream?static=true`. `PlaybackInfo` is never called, no device profile is ever sent, and no transcode URL can be followed. A file the client cannot play fails, or plays silently |
| How the server session sees it | `playbackBody` reports `PlayMethod: DirectStream` for what is really Direct Play, without a `PlaySessionId`. The server shows what the client says, so that word is untrue today |
| Android decoders | `MediaCodecList(REGULAR_CODECS)` read once per process (`AndroidEnvironment.capabilities` is `by lazy`). Width and height are each codec's independent maximum. Ten-bit comes from declared profiles. Media3 uses `DefaultRenderersFactory` as it comes: no extensions, no decoder fallback, no tunneling |
| mpv decoding | `hwdec=auto-safe`; `MpvDecodeProbe` at start, then `DecodeEvidence.with` from each real first frame. This is sound and stays |
| Audio output | Android: channels and encodings of HDMI, ARC, and eARC outputs, read once. Desktop: passthrough is an empty set, `audio-spdif` is never set, and `maxOutputChannels` is the constant 2 |
| Subtitles | Media3 cues drawn by the Android shell, bitmaps included; libass and mpv's bitmap renderer on desktop. External files attached at load. `subtitleFormats` is filled in and **read by nothing** |
| Shared vs platform | Shared: stream model, assessor, Best Version, track selection, session. Platform: capability reading, probing, engines |
| Tests | Assessor, selector, tracks, and session are well covered by fakes. No test covers delivery, because there is none to choose |

### Defects and untrue claims the audit found

| # | Where | What is wrong | Consequence |
| --- | --- | --- | --- |
| 1 | `JellyfinSource.kt:227` | `PlayMethod` is always `DirectStream`; the stream is `static` | The server's dashboard is told something untrue |
| 2 | `AndroidMediaProber.kt:54` | Ten-bit is detected for HEVC and VP9 only, and `profile != null -> 8` then calls SDR 10-bit AV1 and H.264 High 10 **8-bit** | Best Version is told a false bit depth. VP9 Profile 3 is missed too |
| 3 | `AndroidCapabilities.kt:50` | VobSub is not claimed although Media3 has a parser. Unverified on a device; three real files have it | Understates; nothing reads the set yet |
| 4 | `AndroidCapabilities.kt:129` | Android documents an empty `encodings` list as arbitrary encodings; here it means none | Passthrough may be understated on such an output |
| 5 | `AndroidCapabilities.kt:82` | E-AC-3 and E-AC-3 JOC merge into one codec | Atmos passthrough cannot be told from plain E-AC-3 |
| 6 | `AndroidCapabilities.kt:140` | Independent maximum width and height, with no frame rate. `areSizeAndRateSupported` and profile-levels are not used | 4K60 is claimed where only 4K30 fits, and the reverse cannot be expressed |
| 7 | `AndroidCapabilities.kt:52` | `video/dolby-vision` is unmapped, and a display's DV flag stands in for DV decoding. The Pad 7 has `c2.dolby.decoder.hevc`, which Reflux cannot see | DV assessment ignores the decoder |
| 8 | `AndroidEnvironment.kt:191` | Capabilities are read once per process | An AVR or television connected later is not seen |
| 9 | `DesktopCapabilities.kt:36` | `maxOutputChannels` is always 2 | 5.1 on a 5.1 desktop counts as a downmix and is capped in Best Version's audio score |
| 10 | `PlaybackAssessment.kt:7`, `:96` | `NO_PLAYABLE_AUDIO` is only degraded, and any playable track satisfies the check, not the one that will be chosen | A version can be recommended whose chosen track is silent |
| 11 | `PlaybackAssessment.kt:45` | Subtitles are never assessed | A wanted track the engine cannot show is invisible to planning |
| 12 | `MpvPlayer.kt:56`, `MpvProbe.kt:27` | `http-header-fields` is a comma-separated list, and Jellyfin's `Authorization` value contains commas, unescaped | Masked: Jellyfin 12.2.0 serves static streams with no authentication at all (section 3). May break on any endpoint that does check. Needs a real test before it is called a bug |
| 13 | `SubtitleStream` | No hearing-impaired flag; no server stream index on audio or subtitle tracks | A plan cannot address a track on the server |
| 14 | `JellyfinSource.kt:317` | `substringBefore(',')` of `Container`: a real `mov,mp4,...` becomes QuickTime, and `mpegts` or `matroska` becomes unknown | Harmless today (unknown is no evidence); wrong once containers are planned |

The last two rows of the matrix's "today" behavior matter most: **Reflux has no answer when a file's audio is undecodable.** On Android the engine disables the track and plays the picture in silence.

## 2. What is in the real library

967 versions on the developer's Jellyfin 12.2.0 server (RS-X1), read on 2026-11-01. This decides what real media can validate, and what needs fixtures.

| Kind | Found |
| --- | --- |
| Container | Matroska 956, MP4/MOV 11 |
| Video | H.264 8-bit 831, HEVC Main 10 105, HEVC Main 30, **H.264 High 10: 1** |
| Dynamic range | SDR only, all 967 |
| Audio tracks | AAC 1183 (of which HE-AAC 5.1 and 7.1: 506), AC-3 79, E-AC-3 16 (one with Atmos), DTS 4, MP3 1 |
| Subtitle tracks | SRT 4678 (forced 24, SDH 8), ASS 726, PGS 85 (forced 4), VobSub 3 |
| Not present at all | **HDR, Dolby Vision, AV1, VP9, TrueHD, DTS-HD, WebM, MPEG-TS, external subtitles** |

### The reference file

`Iron Man` (2008), Matroska, 5.6 GB, 5.9 Mbit/s: HEVC Main 10, level 120 (4.0), 1920×1080, 23.976 fps, 10-bit **SDR** (BT.709); E-AC-3 5.1 Atmos, 48 kHz; PGS ×3 (Hindi, English, English forced); SRT ×2 (English forced, English SDH).

**The server currently cannot serve this file.** `Videos/{id}/stream?static=true` answers 404 and HLS segments answer 500, while `PlaybackInfo` still describes it. Every other file sampled answers 206. The cause is on the server and the audit's account is not an administrator, so the server's log has not been read. Its siblings of the same class were used below (HEVC Main 10, E-AC-3, Matroska, one also with PGS). The exit criterion for this file stays open until the file is reachable.

## 3. What the server really does

Run live on 2026-11-01. "Delivered" is `TranscodingInfo` from `GET /Sessions` and `ffprobe` of a real first segment, not inference. Nothing was reported as played, and every encode was stopped afterwards.

| Client profile | Server's decision | Delivered | Reasons |
| --- | --- | --- | --- |
| Browser-like (approximates Chrome: no HEVC, E-AC-3, or MKV) | HLS | Not started; URL asks H.264 + AAC, `SubtitleMethod=Encode` | Container, video, audio |
| Native desktop (all containers, all subtitles `Embed`) | `SupportsDirectPlay: true`, no URL | Direct Play, static stream | None |
| No E-AC-3 decoder | HLS, `SupportsDirectStream: false` | `IsVideoDirect: true`, `IsAudioDirect: false`. Segment: HEVC Main 10 + AAC 5.1 | `AudioCodecNotSupported` |
| MKV refused, codecs fine | HLS | `IsVideoDirect: true`, `IsAudioDirect: true`. Segment: HEVC Main 10 + E-AC-3 | `ContainerNotSupported` |
| No 10-bit HEVC | HLS | `IsVideoDirect: false`, `IsAudioDirect: true`. Segment: H.264 + E-AC-3 | `VideoCodecNotSupported` |

What this settles:

- **Jellyfin already does the least destructive thing when asked accurately.** The Iron Man question is answered by a device profile, and the Chrome/native difference is reproduced by nothing else.
- **`SupportsDirectStream: false` does not mean the video is being converted.** In all three HLS rows above it is false. Only `IsVideoDirect` and `IsAudioDirect` say. The four server words alone cannot be trusted as a description of what happened.
- **`PlayMethod` is not evidence either:** the server repeats what the client reports. `TranscodingInfo` is what the server did.
- **Reasons are not in the JSON field** (`TranscodeReasons` was absent from each media source): they are in `TranscodingUrl`'s query and in `TranscodingInfo.TranscodeReasons`.
- **Subtitles are the trap.** With no external method declared, the server marks PGS as `Encode`, and selecting one adds `SubtitleStreamIndex` plus `SubtitleMethod=Encode`: a burn-in, and so a full video transcode, for an audio-only problem. Declaring `pgssub` and `dvdsub` with `Method: External` turns every track to `External`, with a `DeliveryUrl` per track, and no `Encode`. **Reflux must declare External for what it can draw and keep subtitle selection out of server URLs.** The contents of a `.pgssub` delivery and its use by each engine are still unverified.
- **`TranscodingUrl` carries an `ApiKey`.** `Redaction` already covers `apikey` case-insensitively; a test should hold that.
- **Static streams needed no authentication:** a request with no `Authorization` got 206. That is the server's behavior, not something Reflux chose, and is worth knowing about. It is why defect 12 cannot show itself here.
- The server's OpenAPI document does not list the HLS paths or `Videos/ActiveEncodings`, though both work. Their contract is observed, not documented.
- The server encodes in software (`HardwareAccelerationType: none`): a video transcode is real CPU on RS-X1, which raises the price of an unnecessary one.

The same class on the desktop, through libmpv headless over the static stream: hardware decoded (`d3d11va-copy`, `p010`), software decoded (`yuv420p10`) on request, E-AC-3 decoded locally to 6 channels at 48 kHz, an exact seek 30 minutes in landing within 0.25 s. This is "Reflux Desktop: server Direct Play" as evidence, on a sibling of the reference file.

## 4. Compatibility matrix

This is a record of evidence, **never an input to logic**. Capabilities come from the engine and device at run time.

| Mark | Meaning |
| --- | --- |
| **Played** | Played by Reflux's engine on that device, recorded in VERIFICATION.md or section 3 |
| **Listed** | The device or engine lists it; not played yet |
| **Device** | Depends on the device; read at run time |
| **Gap** | Possible, and Reflux does not do it yet |
| **None** | Not available there |
| ? | Unverified |

Columns: Desktop is libmpv 0.41-dev on Windows (Linux headless only, as noted). Pad 7 is SM7675, API 36, read 2026-11-01. Fire TV is `AFTMA08C15`, API 30, 32-bit only (`armeabi-v7a`), read 2026-11-01, plus what VERIFICATION.md recorded. The Nothing Phone 2a was not attached and the Google TV emulator was not started: they are `?` until M2.9, and an emulator says nothing of hardware decoding.

### Video

| Format | Desktop | Pad 7 | Fire TV |
| --- | --- | --- | --- |
| H.264 8-bit | Played; hardware | Listed; hardware | Played |
| H.264 High 10 | Software (Direct3D path); hardware through NVDEC is unusable by this output | ? Profile to be read at M2.6 | ? Profile to be read at M2.6 |
| HEVC 8-bit | Probed hardware | Listed; hardware | Played (720p/1080p test media) |
| HEVC Main 10 | **Played** to 1080p; probed hardware to 4K | Listed; hardware | **Played** 4K60, `OMX.MTK.VIDEO.DECODER.HEVC` |
| VP8 | Software | Software only | Listed; hardware |
| VP9 8/10-bit | Probed hardware | Listed; hardware | Listed; hardware |
| AV1 8/10-bit | Probed hardware; libdav1d in software | Software only (`dav1d`) | Listed; hardware |
| MPEG-2 | Software; hardware not probed | **None listed** | Listed; hardware |
| VC-1 | Software | None listed | None listed |
| MPEG-4 Part 2 | Software | Software | Listed; hardware |

Combinations that decide playback, none of them modeled today:

| Combination | Why it matters | State |
| --- | --- | --- |
| Video + bit depth | Hi10P has no hardware decoder almost anywhere | Modeled; Android prober wrong (defect 2) |
| Video + resolution + frame rate | A 4K30 decoder is not a 4K60 decoder | **Gap** on Android (defect 6) |
| Video + profile + level | HEVC level 120 and 93 both occur | **Gap**: not in the model |
| Container + video | Media3 plays what its extractors map; HEVC in MKV Played | Device-independent per engine; `?` for AV1 in MP4, VC-1 in MKV |
| 4:2:2 / 4:4:4 | Hardware usually refuses it | **Gap**; desktop excludes it from evidence on purpose |

### Audio

| Format | Desktop, decoded | Desktop, passthrough | Pad 7, decoded | Fire TV |
| --- | --- | --- | --- | --- |
| AAC (LC, HE, 5.1, 7.1) | Played | n/a | Listed; HE-AAC 5.1 ? (506 real tracks) | Played (stereo test media); software decoder |
| MP3, FLAC, Vorbis, Opus, ALAC, PCM | Listed in engine | n/a | Listed | Listed, **no ALAC** |
| AC-3 | Listed | **Gap** | Listed, `c2.dolby` | Listed, `c2.dolby` |
| E-AC-3 | **Played** 5.1 | **Gap** | Listed, `c2.dolby` | Listed, `c2.dolby` |
| E-AC-3 + Atmos (JOC) | Bed only, decoded locally | **Gap** | Listed, `eac3-joc` | No JOC decoder listed: bed through `eac3`, or passthrough over HDMI ? |
| DTS (core) | Listed | **Gap** | **None** | **No decoder**; passthrough ? |
| DTS-HD | Core decoded locally; HRA not told apart | **Gap** | **None** | **No decoder**; passthrough ? |
| TrueHD (+ Atmos) | Listed | **Gap** | **None** | **No decoder**; passthrough ? |

Atmos is metadata on a bed, so a local decode keeps 5.1 or 7.1 and loses the objects; only passthrough to a capable receiver, or a device's own JOC decoder, can keep them. No receiver was available: **all passthrough is unverified**, everywhere. Passthrough on a stereo Pad 7 speaker does not apply.

### Containers

| Format | Desktop | Android (Media3) | Seek and stream notes |
| --- | --- | --- | --- |
| Matroska | **Played** | **Played** | Needs a cue index; byte ranges over HTTP Played |
| MP4 / MOV | Played | Played | Moov at the end costs a seek over HTTP |
| WebM | Listed | Listed | |
| MPEG-TS, M2TS | Listed | Listed | No index: seek is approximate on Media3 |
| AVI, FLV, Ogg | Listed | Listed | Weak seeking on Media3 |
| MPEG-PS | Listed | Listed | |
| WMV / ASF | Listed | **Not claimed** | |

`AndroidCapabilities` declares this list from the engine rather than reading it, which is acceptable: extractors ship with Reflux and do not vary by device. A real test should hold each claim.

### Subtitles

| Format | Desktop | Android (Media3) | With a server stream (HLS) |
| --- | --- | --- | --- |
| SRT | Played (libass) | Played | `External` |
| ASS/SSA | **Full** (libass) | **Plain**: text, position, some style; no animation or fonts | `External`; 726 real tracks, so fidelity is a real question |
| PGS | Listed, not played | Drawn from `Cue.bitmap`; ? on a real film | `External` `.pgssub`: unverified end to end |
| VobSub | Listed | Not claimed (defect 3) | Same as PGS |
| WebVTT, TTML | Listed | Listed | |
| DVB | Listed | Claimed | |
| Forced, SDH | Flags read on desktop; Android prober drops forced | Flags at play time | Flags from the server |
| Embedded | Yes | Yes | **Absent from a remuxed or converted stream**: must arrive `External` |
| External | Yes | Yes, except sidecar files on a television (DECISIONS.md, 2026-10-17) | Yes |

### HDR

| Format | Desktop | Android |
| --- | --- | --- |
| SDR | Played | Played |
| HDR10, HLG | Tone-mapped to SDR always (`ENGINE_OUTPUTS_HDR = false`) | Display's own list; output unverified on any device |
| HDR10+ | Not detected by `MpvProbe` | Display's list: the Pad 7 lists all four HDR types; the Fire TV's current display lists HDR10 only |
| Dolby Vision | Tone-mapped; never output | The Pad 7 and the Fire TV both have DV decoders Reflux ignores (defect 7) |
| DV profile 5 (no base layer) | Needs tone mapping | Needs a DV decoder |

There is no HDR in the real library, so everything here rests on fixtures. HDR display output stays unclaimed until seen on a real display.

### The reference case today, and after M2P

| Client | Today | Required |
| --- | --- | --- |
| Desktop | Original; hardware or software decode; local audio. Reports `DirectStream` | Same media path, reported as Direct Play, with the plan and evidence |
| Android with Dolby decoders (Pad 7, Fire TV) | Original, if the engine accepts it | Same, confirmed by a run |
| Android without an E-AC-3 decoder (either device with a DTS or TrueHD track is the same case) | **Picture, no sound, no explanation** | Original video, server-converted audio (`IsVideoDirect: true`) |
| Any client, with a PGS track wanted | Local rendering where supported | Local rendering; never `Encode` |

## 5. Vocabulary

Four independent axes, plus the reason. For a source without a transcoder only the first value of delivery exists.

| Axis | Values |
| --- | --- |
| **Delivery** | `ORIGINAL`, `REMUXED`, `AUDIO_CONVERTED`, `VIDEO_TRANSCODED` |
| **Video** | `ORIGINAL_HARDWARE`, `ORIGINAL_SOFTWARE`, `ORIGINAL_UNKNOWN` (decoder not known yet), `SERVER_TRANSCODED` |
| **Audio** | `ORIGINAL_LOCAL_DECODE`, `ORIGINAL_PASSTHROUGH`, `SERVER_CONVERTED`, `NONE` |
| **Subtitles** | `NONE`, `LOCAL_RENDER`, `EXTRACTED` (delivered beside the stream, drawn locally), `CONVERTED`, `BURNED_IN` |

Jellyfin's words are a mapping in the adapter and never leave it:

| Jellyfin | Reflux delivery | Tell-tale |
| --- | --- | --- |
| Direct Play | `ORIGINAL` | Static stream |
| Remux | `REMUXED` | `IsVideoDirect` and `IsAudioDirect` both true |
| Direct Stream | `AUDIO_CONVERTED` | `IsVideoDirect` true, `IsAudioDirect` false |
| Transcode | `VIDEO_TRANSCODED` | `IsVideoDirect` false, even with audio kept |

## 6. Shared capability model (proposal)

Extend `DeviceCapabilities`; do not replace it. Best Version and the UI keep working, and each addition has a real source.

```kotlin
data class VideoDecoderSupport(
    val codec: VideoCodec, val maxWidth: Int, val maxHeight: Int, val maxBitDepth: Int = 8,
    val hardware: Boolean?,
    val maxFrameRateAtMax: Double? = null,       // null: not known, nothing capped
    val profiles: Set<VideoProfile>? = null,     // null: not known, nothing refused
    val maxLevel: Int? = null,
    val dolbyVision: Set<Int>? = null,           // DV profiles this decoder takes
    val evidence: Evidence = Evidence.REPORTED,
)
enum class Evidence { OBSERVED, REPORTED, ENGINE_DECLARED }

data class AudioOutput(
    val maxPcmChannels: Int?,                    // null: not known, no downmix claimed
    val passthrough: Set<PassthroughFormat>,     // AC3, EAC3, EAC3_JOC, DTS, DTS_HD, TRUEHD
)
data class SubtitleSupport(val format: SubtitleFormat, val fidelity: Fidelity) // FULL, PLAIN
enum class Fidelity { FULL, PLAIN }
```

`StreamInfo` gains what planning needs: video `profile`, `level`, `interlaced`, chroma; audio `sampleRate`, `profile`, and `sourceIndex`; subtitle `hearingImpaired`, `external`, and `sourceIndex`. That is a database migration on `track` and `version`; a rescan of a Jellyfin source refills them.

Rules kept from the existing model: unknown is a value, neither for nor against; unknown never blocks playback; hardware is about quality, never about whether something is "direct"; there is no table keyed by brand, model, or Android version. Non-server sources use exactly the same model.

**Network:** `maxStreamingBitrate` is null by default, so nothing is capped on a local network. A real limit may come from the server's own per-user remote limit, which then arrives as a reason (`ContainerBitrateExceedsLimit`) rather than as a guess of Reflux's.

## 7. Playback planner (proposal)

`PlaybackPlanner` in `core`: pure, deterministic, no I/O. It answers "for this version, these chosen tracks, this device, and what this source can do, what is the least destructive viable path?"

```kotlin
data class SourceDelivery(val canRemux: Boolean, val canConvertAudio: Boolean, val canTranscodeVideo: Boolean, val canExtractSubtitles: Boolean)
// NONE for every local folder, SMB, and WebDAV source

data class PlannedPlayback(
    val delivery: Delivery, val video: VideoProcessing, val audio: AudioProcessing, val subtitles: SubtitleProcessing,
    val audioTrack: AudioStream?, val subtitleTrack: SubtitleStream?,
    val reasons: List<ConversionReason>,   // what, why, and whether client-driven or server-required
    val outcome: Outcome,                  // PLAYS, PLAYS_WITHOUT(audio|subtitles), CANNOT_PLAY
)
```

Decision order. Each step changes only what it must, and the first that works wins:

1. **Choose tracks first.** The audio and subtitle tracks `TrackSelector` will choose, not "any track". Shared logic over stream facts, so the plan and the engine agree. Language is never traded for convenience: a French viewer's DTS track is converted, not swapped for an English AC-3.
2. **Video:** can any local decoder take this codec, profile, level, depth, size, and rate? Hardware or software both count. `SOFTWARE_VIDEO_DECODE` is a quality note and never a reason to transcode, unless real playback shows it cannot keep up (step 7).
3. **Audio:** passthrough to an output that takes it, else local decode, else convert only the audio.
4. **Container:** can this engine demux it? If not, remux.
5. **Subtitles:** render locally, else extract beside the stream, else convert to text. A bitmap format with no renderer is reported as unavailable; the viewer chooses. Burn-in only when the viewer asked for that track, nothing less destructive exists, and the source offers it. Never as a side effect.
6. **Result:** all original gives `ORIGINAL`; otherwise the lowest delivery covering what steps 2 to 5 require. Nothing unavoidable and no source help gives `PLAYS_WITHOUT` or `CANNOT_PLAY`, stated rather than silent.
7. **Escalation:** the engine is the final authority ("Unknown technical information never blocks playback", 2026-10-08). When original delivery fails as `UNSUPPORTED`, the plan is made again with that path excluded, once per step, keeping position. Sources without conversion state the failure as today.

Hard rules, each a test: no video transcode because of audio or subtitles; no burn-in from an unselected track; HDR is not silently downgraded where the client can preserve it; original audio is not replaced when decode or passthrough works; hardware decoding is never a condition of `ORIGINAL`.

`Library.planPlayback` stays the one entry point: Best Version, then the planner, then the source. `DownloadableSource.downloadTarget` is untouched and stays the file byte for byte (OFFLINE.md), so it must stop being an alias of whatever `playbackTarget` becomes.

## 8. Jellyfin negotiation (proposal)

A new optional `NegotiatedSource : MediaSource`, declared by a capability, implemented only by Jellyfin. Everything Jellyfin stays in `sources/jellyfin`.

- **Profile, built from `DeviceCapabilities`:** `DirectPlayProfiles` from containers and decoders (mpv may send wildcards, as the desktop row of section 3 did); `CodecProfiles` for level, depth, profile, and range conditions only where the device has limits; `SubtitleProfiles` `External` for every format Reflux can draw; `TranscodingProfiles` HLS, listing the source's video codec first so copy stays possible, and only codecs the client decodes.
- **Request:** `POST Items/{id}/PlaybackInfo` with `SubtitleStreamIndex: -1` and the chosen `AudioStreamIndex`. Keep `PlaySessionId`.
- **Reading the answer:** `SupportsDirectPlay` true gives the static stream as today. Otherwise follow `TranscodingUrl`; classify from `TranscodeReasons` and then from `TranscodingInfo`. If what the server did differs from the plan, the diagnostics say so.
- **Failure:** if `PlaybackInfo` fails or times out (a short budget, under start-up patience), fall back to the static stream, today's behavior. Negotiation must never make a file that plays today stop playing, and a fast path may skip the call only if it is measured to matter.
- **Reporting:** `PlaySessionId`, the true `PlayMethod`, and `DELETE Videos/ActiveEncodings` on stop, so no encode outlives a viewer. A failed report never touches local watch state (WATCH_STATE.md).
- **Track changes under conversion:** a converted stream carries one audio track and no subtitles. Changing audio means a new `PlaybackInfo` at the same position. The menu's data must come from `StreamInfo`, not the engine, in that case. Original delivery is unchanged. Subtitles stay out of band, so changing them needs no new stream.
- **Android dependency:** `media3-exoplayer-hls` (Apache-2.0, same version catalog). Required: Media3 cannot open `master.m3u8` without it. E-AC-3 and HEVC in HLS-TS on Media3 are unverified.

## 9. Diagnostics (proposal)

One `PlaybackReport`, for development first: written to the diagnostics log through `Redaction`, and printed by the developer CLI.

| Part | Planned | Observed from |
| --- | --- | --- |
| Delivery | `PlannedPlayback.delivery` | `TranscodingInfo` (Jellyfin); fixed `ORIGINAL` elsewhere |
| Video | Original codec, profile, depth, size, rate, range; output codec if changed | `hwdec-current` (mpv), `onVideoDecoderInitialized` name (Media3), `TranscodingInfo` |
| Audio | Original codec, output codec, channels | `audio-codec-name`, `audio-params`, or the renderer's format and whether it is bypassing the decoder |
| Subtitles | Format, processing | Selected track |
| Reason | Each `ConversionReason`: what, why, client-driven or server-required | Server's `TranscodeReasons` beside it |

Each field says planned or observed; a mismatch is the most useful line in it. User-facing surfaces are a later decision.

## 10. Investigation findings

### Android

- **Version:** Reflux is on Media3 **1.11.1**, already past 1.11.0. No upgrade needed.
- **Hardware:** `isHardwareAccelerated` on API 29+, a name rule below that; adequate. Size, rate, level, and DV are the gaps (defects 6, 7).
- **Do not add an FFmpeg extension yet.** The only proven gap is DTS and TrueHD, which neither the Pad 7 nor the Fire TV can decode. On Jellyfin, audio conversion solves it with no new code in the APK. It remains for **local, SMB, and WebDAV** files on devices without those decoders: 4 of about 1,280 audio tracks in the real library, and the track is silent rather than unplayable.
- **If evidence later justifies it:** `org.jellyfin.media3:media3-ffmpeg-decoder` declares GPL-3.0 and so **cannot be used in proprietary Reflux** (LICENSE). The older ExoPlayer documentation says the official module is built from source, with FFmpeg and the NDK; that needs confirming for 1.11. The likely shape is an audio-only LGPL FFmpeg (`dca`, `truehd`, `mlp`), dynamically linked, per ABI (`arm64-v8a`, `armeabi-v7a` for the Fire TV), with a source offer. NextLib exists; its license was not established. Size, battery, and patent position are unmeasured, and legal review (RELEASE.md) comes first.
- **Software video:** the platform already ships `c2.android.*`; Media3's fallback (`setEnableDecoderFallback`) is off and worth a test. No video extension is justified: the only software-only real case is one Hi10P file, which server transcoding covers.
- **Fire TV:** API 30, 32-bit (`armeabi-v7a` alone), 1.7 GB. Any native library multiplies by ABI. It decodes 4K60 Main 10 in hardware, so a transcode there would be a defect. It has separate OMX decoders per Dolby Vision profile, so `dolbyVision` in section 6 can be read rather than assumed.

### Desktop

libmpv needs no redesign. `DecodeEvidence` is the right model and the live run confirmed it.

- **Release builds:** the dev build has libdav1d and native decoders for every audio format. The LGPL build (M2) must keep `libdav1d`, because FFmpeg's own `av1` needs hardware. Worth a test against the released library.
- **Passthrough:** mpv has `audio-spdif`; Reflux never sets it. Whether a Windows or Linux output takes it cannot be read reliably without trying, so this needs a receiver and a decision. Until then, not claimed.
- **Channels:** mpv defaults to `audio-channels=auto-safe`; `audio-device-list` and `audio-out-params` can report the real layout (defect 9).
- **HDR:** unchanged from 2026-10-19. Needs a real HDR display session.
- **Linux:** hardware paths are unverified on real hardware; the container has no GPU.

## 11. Test corpus

**Automated, generated, committed:** none of the media itself above a few KB. As `decode-probe/` does, generate with FFmpeg at build or test time. Generated fixtures cover: H.264/AAC/MP4; HEVC Main 10 with AC-3, AAC, and DTS; AV1 with Opus; forced and SDH SRT; ASS; multiple audio languages; seeking, resume, audio and subtitle switching. FFmpeg cannot encode E-AC-3 JOC, PGS, or Dolby Vision and has only an experimental TrueHD encoder, so those need real files.

**Planner tests** need no media: stream facts and capability objects, one test per row of section 4's combinations and each hard rule of section 7.

**Recorded server answers:** `PlaybackInfo` and `Sessions` JSON (ids and URLs removed) as adapter fixtures, so classification has tests with no server.

**Private corpus, local only, never committed:** the RS-X1 library through `~/.reflux-live/jellyfin.env`.

| Required case | Real file available? |
| --- | --- |
| HEVC Main 10 + E-AC-3 Atmos + MKV + PGS | **Iron Man; server answers 404** |
| HEVC Main 10 + E-AC-3 + MKV + PGS | Yes |
| HEVC Main 10 + E-AC-3 **7.1** | **Not in the library** (E-AC-3 tops out at 5.1) |
| HEVC + DTS | DTS exists on H.264; HEVC pairing to be checked |
| HEVC + TrueHD | **No: needs a licensed sample** |
| H.264 + AAC + MP4 | Yes |
| AV1 + common audio | **No: generated or licensed** |
| PGS, ASS, forced, SDH, multi-audio | Yes |
| HDR | **No: licensed sample** (Blender's open films have HDR masters) |
| Seek, resume, audio and subtitle switching | Yes |

## 12. Validation plan (M2.9)

Run after the planner and negotiation exist: the meaningful combinations by what each device reports, not all of them.

| Device | State on 2026-11-01 |
| --- | --- |
| Windows desktop | Available |
| Linux desktop | Container only (no GPU); no real hardware decode |
| Xiaomi Pad 7 | Attached; decoders read only so far |
| Nothing Phone 2a | Used with Reflux before (VERIFICATION.md); not attached on 2026-11-01, decoders not read |
| Google TV emulator | Available; software decoders only |
| Fire TV `AFTMA08C15` | Reachable at its current address; read only so far |

Each run records: started, first-frame latency, seeking, buffering, dropped frames, CPU and GPU where measurable, delivery, audio and video preservation, subtitles, HDR where relevant, and the real server mode. Results go in VERIFICATION.md.

**Cannot be validated with what is here:** passthrough (no receiver), HDR or DV output (no display in HDR), a real Google TV.

## 13. Best Version

The ranking does not need changing, and M2P changes no policy. Its inputs do:

- It keeps reading `DeviceCapabilities`, the same object the planner reads, so no capability logic is duplicated. Fixing defects 2, 6, 7, and 9 changes outcomes by making inputs true, which is the evidence-based change allowed.
- `PlaybackAssessor` and the planner must not disagree. The assessor stays "how well does this device play it locally"; the planner answers the delivery question using the assessor's checks, sharing one function.
- **One open question, to settle with evidence:** today a version needing conversion and one playing unchanged are ranked with no knowledge of delivery. On a phone with no DTS decoder, a DTS-only version is `NO_PLAYABLE_AUDIO` although Jellyfin could convert it. Whether "server can fix it" affects ranking is a policy change: it is left out until a real case shows a wrong choice.

## 14. Order of work

1. **Truth first (small, no new behavior):** defects 1, 2, 3, 5, 9, 12, 13, 14, with tests.
2. **Capability model and stream facts:** section 6, the migration, Android size and rate and level and DV reading, re-reading on output change.
3. **Planner:** pure, tested, wired so it decides nothing yet and only reports.
4. **Jellyfin negotiation:** profile, `PlaybackInfo`, HLS dependency, session reporting, escalation. This is where behavior changes.
5. **Diagnostics:** the report, planned against observed, in the log and the CLI.
6. **Audio outputs on desktop, then the Android decoder decision** with measurements.
7. **Corpus and validation:** sections 11 and 12, and the exit criteria in ROADMAP.md.

Risk to watch: every playback now making a network call before it starts. Start-up time is measured before and after step 4.

## Sources

- Live server, libmpv, and Pad 7 readings: section 3, VERIFICATION.md (2026-11-01).
- [org.jellyfin.media3:media3-ffmpeg-decoder on Maven Central](https://central.sonatype.com/artifact/org.jellyfin.media3/media3-ffmpeg-decoder/): GPL-3.0 in its POM.
- [ExoPlayer FFmpeg module README](https://build.khadas.com/khadas/test/edge2/external/exoplayer/tree_15dc86382f17a24a3e881e52e31a810c1ea44b49/extensions/ffmpeg/README.md): legacy; built from source, not on Google's Maven.
- [NextLib](https://github.com/anilbeesetti/nextlib): a community FFmpeg extension for Media3.
- [mpv issue 13899](https://github.com/mpv-player/mpv/issues/13899): `target-colorspace-hint` behavior differing by graphics API, for the HDR session.
