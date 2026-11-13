# Playback compatibility

What Reflux can really play, how it decides, and how it explains that. This is the specification for milestone M2P (ROADMAP.md). It began as the playback audit of 2026-11-01. Every section is built or decided; M2P closed on 2026-11-02 (section 17). Section 15 is the current real-media evidence: sections 2 and 3a describe the library before the owner's remux.

The rule: **decode locally when possible, preserve original streams when possible, transcode only when genuinely necessary.** Success is that Reflux knows what the client can actually play, preserves the original whenever possible, degrades the smallest necessary part when it cannot, and can explain why. "Every codec works everywhere" is not the goal.

Direct Play is not hardware decoding. Delivery (how bytes reach the device) and decoding (what turns them into pictures) are separate questions, and original media decoded in software is still original media.

## 1. How playback works today

| Question | Answer in the code |
| --- | --- |
| What is modeled | `DeviceCapabilities`: video decoders (codec, frame size, bit depth, hardware true/false/unknown), audio decoder set, passthrough set, output channels, display (size, ranges, tone mapping), containers, subtitle formats, features. No profile, level, frame rate, or sample rate. No Dolby Vision decoder, and no difference between E-AC-3 and E-AC-3 JOC |
| Where choices are made | Once: `Library.planPlayback` (`library/.../Playback.kt`) takes Best Version and asks the source for a target. `PlaybackAssessor` is the only compatibility logic. There is no delivery choice to make, because there is one delivery |
| How Jellyfin playback is requested | It is not negotiated. `JellyfinSource.playbackTarget` always returns `Videos/{id}/stream?static=true`. Since the truth layer (section 3a) `PlaybackInfo` is called to observe only: no transcode URL is ever played. A file the client cannot play fails, or plays silently, and silence is now said |
| How the server session sees it | Reported as `PlayMethod: DirectPlay` with a `PlaySessionId` since the truth layer; it was `DirectStream` with none. The server shows what the client says, so that word is a report and not evidence |
| Android decoders | `MediaCodecList(REGULAR_CODECS)` read once per process (`AndroidEnvironment.capabilities` is `by lazy`). Width and height are each codec's independent maximum. Ten-bit comes from declared profiles. Media3 uses `DefaultRenderersFactory` as it comes: no extensions, no decoder fallback, no tunneling |
| mpv decoding | `hwdec=auto-safe`; `MpvDecodeProbe` at start, then `DecodeEvidence.with` from each real first frame. This is sound and stays |
| Audio output | Android: channels and encodings of HDMI, ARC, and eARC outputs, read once. Desktop: passthrough is an empty set, `audio-spdif` is never set, and `maxOutputChannels` is the constant 2 |
| Subtitles | Media3 cues drawn by the Android shell, bitmaps included; libass and mpv's bitmap renderer on desktop. External files attached at load. `subtitleFormats` is filled in and **read by nothing** |
| Shared vs platform | Shared: stream model, assessor, Best Version, track selection, session. Platform: capability reading, probing, engines |
| Tests | Assessor, selector, tracks, and session are well covered by fakes. No test covers delivery, because there is none to choose |

### Defects and untrue claims the audit found

| # | Where | What is wrong | Consequence |
| --- | --- | --- | --- |
| 1 | `JellyfinSource.kt:227` | **Fixed.** `PlayMethod` was always `DirectStream`; the stream is `static` | The server's dashboard was told something untrue |
| 2 | `AndroidMediaProber.kt:54` | **Fixed.** Ten-bit was detected for HEVC and VP9 only, and `profile != null -> 8` then called SDR 10-bit AV1 and H.264 High 10 **8-bit** | Best Version is told a false bit depth. VP9 Profile 3 is missed too |
| 3 | `AndroidCapabilities.kt:50` | **Answered as unknown.** Media3 has a VobSub parser, unverified on a device; three real files have it. `SubtitleRenderer` says embedded unknown, external unsupported | Stays unknown until seen |
| 4 | `AndroidCapabilities.kt:129` | **Fixed.** Android documents an empty `encodings` list as arbitrary encodings; it was read as none. It now leaves passthrough unknown | Passthrough may be understated on such an output |
| 5 | `AndroidCapabilities.kt:82` | **Fixed for output.** E-AC-3 and E-AC-3 JOC merged into one codec. `atmosPassthrough` is now its own answer | Atmos passthrough cannot be told from plain E-AC-3 |
| 6 | `AndroidCapabilities.kt:140` | **Fixed in the model.** Independent maximum width and height, with no frame rate. Profiles, levels, and frame rates are now read per decoder. Width and height themselves are still each one's own maximum, and only 1080p and 2160p are asked about | 4K60 is claimed where only 4K30 fits, and the reverse cannot be expressed |
| 7 | `AndroidCapabilities.kt:52` | **Fixed.** `video/dolby-vision` was unmapped, and a display's DV flag stood in for DV decoding | DV assessment ignores the decoder |
| 8 | `AndroidEnvironment.kt:191` | **Fixed.** Capabilities were read once per process; outputs and displays are now read again after a change | An AVR or television connected later is not seen |
| 9 | `DesktopCapabilities.kt:36` | **Fixed.** `maxOutputChannels` was always 2. It is now learned from real playbacks, with 2 as the floor | 5.1 on a 5.1 desktop counts as a downmix and is capped in Best Version's audio score |
| 10 | `PlaybackAssessment.kt:7`, `:96` | **Fixed.** Any playable track satisfied the check, not the one that will be chosen. `NO_PLAYABLE_AUDIO` stays degraded | A version could be recommended whose chosen track is silent |
| 11 | `PlaybackAssessment.kt:45` | Subtitles are never assessed | A wanted track the engine cannot show is invisible to planning |
| 12 | `MpvPlayer.kt:56`, `MpvProbe.kt:27` | **Confirmed and fixed.** `http-header-fields` is a comma-separated list, and Jellyfin's `Authorization` value contains commas, unescaped: a server received `MediaBrowser Client="Reflux"` and the token never left the engine | Masked because Jellyfin 12.2.0 serves static streams with no authentication at all (section 3) |
| 13 | `SubtitleStream` | **Fixed.** No hearing-impaired flag; no server stream index on audio or subtitle tracks | A plan cannot address a track on the server |
| 14 | `JellyfinSource.kt:317` | **Fixed.** `substringBefore(',')` of `Container`: a real `mov,mp4,...` became QuickTime, and `mpegts` or `matroska` became unknown | Harmless today (unknown is no evidence); wrong once containers are planned |

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

## 3a. The truth layer

Built after the audit, before any capability or planning work. It reports; it decides nothing, and playback is still the original file.

**What Reflux now knows.** `core/playback/PlaybackEvidence.kt` holds the delivery terms (`DIRECT_PLAY`, `REMUX`, `AUDIO_CONVERSION`, `VIDEO_TRANSCODE`, `UNKNOWN`), what became of video and audio (`PRESERVED`, `CONVERTED`, `UNKNOWN`), and each subtitle's handling. `JellyfinDelivery` is the only code that turns Jellyfin's answers into them.

| Question | Where the answer comes from |
| --- | --- |
| What did Reflux request? | `PlaybackEvidence.requested`, and `JellyfinPlaybackAsk`: the original, no subtitle in the stream, no subtitle format claimed, no conversion allowed |
| What did Jellyfin return? | `JellyfinPlaybackAnswer`, whose flags are reported and never classified from |
| Video and audio preserved or converted? | `TranscodingInfo`: `IsVideoDirect`, `IsAudioDirect` |
| Is it Direct Play? | Only if the original was requested, the server lists this device playing that item, and no `TranscodingInfo` is listed. Anything less is `UNKNOWN` |
| Did a subtitle cause a conversion? | The video is being converted, and either `SubtitleStreamIndex` with `SubtitleMethod=Encode` is in the URL or the server gave `SubtitleCodecNotSupported`. `SubtitleMethod=Encode` alone means nothing: servers write it with no subtitle selected |
| Did authentication work? | `PlaybackInfo` needs a token: answered is true, 401 or 403 is false, unreachable is not tested |

**When it runs.** `PlaybackInfo` goes with a playback's first report, limited to 3 seconds, so it cannot delay the start and a failure changes nothing but the evidence. Five seconds into playing, `PlaybackSession` asks the source once and writes `PlaybackEvidence.summary()` to the diagnostics log; the same value is `PlaybackSession.evidence` for tests. No address or key is in either. `JellyfinTruthLiveTest` drives each mode on a real server.

**Silent audio.** `PlayerState.audioUnavailable` is true when the media has sound and no track is in use while the engine plays; `unsupportedAudioCodecs` names what Media3 refused, since those tracks are not in the menus. `PlaybackSession` needs it to hold for 3 seconds, then sets `silent`, tells the viewer once, logs the codecs, and leaves the picture playing. No conversion is attempted.

**What it deliberately does not know yet.**

- Whether this client can draw a subtitle. Reflux claims no format, so a PGS track in a file reads `CAPABILITY_UNKNOWN`, even under Direct Play. Whether a chosen track was really drawn is not observed.
- `DIRECT_PLAY` is the server's view of delivery. It says nothing of decoding: hardware or software, and whether audio is decoded or passed through.
- The evidence is about the first moments of a playback, asked once.
- A preferred track that cannot be decoded while another can: Media3 plays the other, with no notice, possibly in another language. Only total silence is reported.
- A decoder that is declared and then fails: that is an engine failure, as before, not silence.

**Remaining server limitations.** Static streams need no authentication on Jellyfin 12.2.0, so a server's answer to them proves nothing about a client's token; `Items/{id}/Download` is what checks. `TranscodingInfo` exists only while a conversion runs. `PlayMethod` is an echo. The Iron Man file still answers 404 and 500.

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

Columns: Desktop is libmpv 0.41-dev on Windows (Linux headless only, as noted). Pad 7 is SM7675, API 36, read 2026-11-01. Fire TV is `AFTMA08C15`, API 30, 32-bit only (`armeabi-v7a`), read 2026-11-01, plus what VERIFICATION.md recorded. The Nothing Phone 2a was not attached and the Google TV emulator was not started: they are `?` until the validation in section 12, and an emulator says nothing of hardware decoding.

### Video

| Format | Desktop | Pad 7 | Fire TV |
| --- | --- | --- | --- |
| H.264 8-bit | Played; hardware | Listed; hardware | Played |
| H.264 High 10 | Software (Direct3D path); hardware through NVDEC is unusable by this output | **No decoder** (no High 10 declared) | **No decoder** (no High 10 declared) |
| HEVC 8-bit | Probed hardware | Listed; hardware | Played (720p/1080p test media) |
| HEVC Main 10 | **Played** to 1080p; probed hardware to 4K | Listed; hardware | **Played** 4K60, `OMX.MTK.VIDEO.DECODER.HEVC` |
| VP8 | Software | Software only | Listed; hardware |
| VP9 8/10-bit | Probed hardware | Listed; hardware | Listed; hardware |
| AV1 8/10-bit | Probed hardware; libdav1d in software | Software only (`dav1d`), declared to 2048 wide and 1080p30 | Listed; hardware to level 13 (5.1), 2160p66 |
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

Four independent axes, plus the reason. Plans and evidence share `DeliveryMode` (`DIRECT_PLAY` for `ORIGINAL`, and so on, with `UNKNOWN`). The other axes are named in section 7. For a source without a transcoder only the first value of delivery exists.

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

## 6. Shared capability model

Built in M2P.2. It answers questions and decides nothing. There is no planner yet.

### Four things kept apart

| Term | Means | Lives in | Changes when |
| --- | --- | --- | --- |
| **Media facts** | What a file contains | `StreamInfo` and its streams, on `MediaVersion` | The file does |
| **Capabilities** | What this client can handle, as far as anything shows | `DeviceCapabilities`, asked through `Capabilities` | The device, engine, or output does |
| **Evidence** | What happened to this playback | `PlaybackEvidence`, `PlayerState` | Each playback |
| **Planning** | What Reflux should request or do | Nothing yet (section 7) | |

None holds another. `Capabilities` takes facts and a device and returns an answer; `PlaybackEvidence` knows neither. One crossing is deliberate: on the desktop, what real playbacks showed feeds what the machine is known to do (`DecodeEvidence`, `ObservedAudioOutput`). That is past playbacks becoming a capability, for this launch. Despite its name `DecodeEvidence` is a capability input; the current playback's account is `PlaybackEvidence`.

### Supported, unsupported, unknown

`Support` has three values, and `UNKNOWN` is an answer.

| Value | Means | Comes from |
| --- | --- | --- |
| `SUPPORTED` | Every limit that applies is known and met | Facts and declared limits that agree |
| `UNSUPPORTED` | A limit is known and gone past, or the thing does not exist | A declared limit, a missing decoder, what the engine is not set up to do |
| `UNKNOWN` | Neither can be said | A fact the file does not state, a limit nobody declares, something not asked or not seen |

- Unknown is never read as supported and never as unsupported. What to do about it is the planner's decision.
- A limit gone past decides even with other things unknown.
- Several ways of doing one thing combine by `Support.best`: any that works is enough.
- Not done is `UNSUPPORTED`, not unknown: the desktop does not pass audio through because Reflux does not ask libmpv to, whatever the receiver could take.
- `frameAndDepthOnly` tells a decoder with no such limits from one that declares nothing. FFmpeg's software decoders have only those limits, and so does a hardware decoder seen decoding that codec and depth on this machine. For it a missing profile or level is not unknown. This is a judgement about what was observed: hardware frame rate on the desktop was not tested.
- `PlaybackAssessor` keeps its existing rule: unknown is no evidence of a problem. It still refuses only on codec, frame size, and bit depth. A declared level or frame rate is what a decoder promises, and Media3 tries past it, so those limits are answers for a planner and not refusals.

### The model

Everything is an addition to `DeviceCapabilities`, with unknown as its default. Best Version's existing fields keep their meaning.

| Question | Facts | Capability | Answer |
| --- | --- | --- | --- |
| **Video** | Codec, profile, level, bit depth, size, frame rate | `VideoDecoderSupport`: frame, depth, `hardware` (true, false, null), `profiles` with highest levels, `frameRates`, `frameAndDepthOnly` | `Capabilities.video`: `decode`, `hardware`, `software`, and `limits` saying which was passed |
| **Audio** | Codec, channels, sample rate, Atmos | `audioDecoders`, `audioDecoderLimits`, `audioPassthrough` and `audioPassthroughKnown`, `atmosPassthrough`, `maxOutputChannels` and `maxOutputChannelsKnown` | `Capabilities.audio`: `decode`, `passthrough`, `plays`, `channels` (unsupported is a downmix, which plays), `atmos` |
| **Subtitles** | Format (text or bitmap by format), forced, hearing-impaired, embedded or external | `SubtitleCapabilities`: per format `embedded`, `external`, `fidelity`; `forcedTracks`, `hearingImpairedTracks` | `Capabilities.subtitle`: `render`, `fidelity`, `bitmap` |
| **Container** | Container | `containers`, `containerSeek` | `Capabilities.container`: `demux`, `seek` |
| **Display** | Dynamic range, Dolby Vision profile | `DisplayCapabilities` with `dynamicRangesKnown`, `canToneMap`; `dolbyVisionProfiles` | `Capabilities.display`: `show`, `toneMap` |

`Capabilities.of` gives one stream's answers together. Levels are the bitstream's own numbers (HEVC 4.0 is 120, H.264 and VP9 4.0 are 40, AV1 4.0 is 8), as servers report them.

**Left out on purpose:** audio offload (Media3's is for long audio playback, and video has no use for it); bit rate (nothing limits it on a local network, and a server's limit arrives as a reason); chroma and interlacing (no real case decided by them yet). A per-output model of several displays or audio routes is not built: there is one current reading.

### Where each platform's answers come from

No answer comes from a device name, brand, or system version.

| Answer | Android | Desktop |
| --- | --- | --- |
| Video decoders | `MediaCodecList`: per decoder, `profileLevels`, sizes, `getSupportedFrameRatesFor` at 1080p and 2160p, `isHardwareAccelerated` | `decoder-list` for software; `MpvDecodeProbe` and real first frames for hardware. A GPU being there proves nothing |
| Dolby Vision | Decoders under `video/dolby-vision` | Not decoded: tone-mapped |
| Audio decoders | `MediaCodecList`, with declared channels and sample rates | `decoder-list`, so a build without a decoder does not claim it |
| Passthrough, Atmos | Media3's own `AudioCapabilities.isPassthroughPlaybackSupported`, the code that will do it | None: `audio-spdif` is not set |
| Output channels | HDMI, ARC, and eARC outputs; stereo otherwise | Real playbacks: `audio-params` against `audio-out-params`. Unknown above what was used until one downmixes |
| Subtitles | Written from Media3's parsers and `Media3Player`, unknown where nobody has seen it work | Written from libass and mpv's renderers |
| Containers | Written from Media3's extractors, which ship with Reflux; FLV's lack of seeking read from its class | `Container.entries`: FFmpeg |
| Display | `Display` modes and HDR types | The window's monitor; HDR not claimed (`ENGINE_OUTPUTS_HDR`) |
| Freshness | Decoders once; outputs and display again after a change (`AndroidCapabilitySource`) | `ObservedCapabilities`, this launch only |

### What the devices say

Read 2026-11-01 through `CapabilityReportOnDeviceTest`. Not rules: nothing reads this table.

| The reference file's class, and two hard cases | Pad 7 | Fire TV (this HDMI output) |
| --- | --- | --- |
| HEVC Main 10 level 120, 1080p24 | Hardware | Hardware |
| E-AC-3 5.1 Atmos | Decoded; stereo output, so a downmix; no passthrough | Decoded; passthrough and Atmos **as Media3 reports them**, not heard |
| H.264 High 10 (one real file) | Unsupported: bit depth, profile | Unsupported: bit depth, profile |
| DTS | Does not play | Does not play: no decoder, and Media3 reports no passthrough |
| Dolby Vision profiles | 5, 8 | 4, 5, 8, 9, 10 |

## 7. Playback planner

Built in M2P.3 and executed since M2P.4 (section 8). `PlaybackPlanner.plan` is pure, deterministic, and platform-neutral. Its result is `PlannedPlayback`. (`PlaybackPlan` already names the library's "what to start now", and these are different things.)

### Inputs

`PlaybackRequirements`, with `DeviceCapabilities` beside it:

| Input | Is |
| --- | --- |
| `version` | Media facts: containers, tracks, sidecar subtitles, and how they were learned |
| `preferences` | `TrackPreferences`, the same as track selection reads |
| `audio` | `Automatic`, or `Explicit` for a track the viewer chose |
| `subtitle` | `Off`, `Automatic`, or `Explicit` |
| `source` | The source's own `SourceCapability` set. A source with no `SERVER_` capability converts nothing, so there is no generic transcoder interface and no source type is ever tested for |

HDR and output requirements are read from the client's capabilities. There are no new settings.

### The plan

| Field | Values |
| --- | --- |
| `outcome` | `PLAYS`, `PLAYS_WITHOUT_AUDIO`, `CANNOT_PLAY` |
| `delivery` | `DeliveryMode`, the same words as evidence so the two can be compared: `DIRECT_PLAY`, `REMUX`, `AUDIO_CONVERSION`, `VIDEO_TRANSCODE`. `UNKNOWN` only with `CANNOT_PLAY` |
| `video` | `ORIGINAL_HARDWARE_DECODE`, `ORIGINAL_SOFTWARE_DECODE`, `ORIGINAL_DECODER_UNDECIDED`, `TRANSCODE`, `NONE` |
| `audio` | `ORIGINAL_LOCAL_DECODE`, `ORIGINAL_PASSTHROUGH`, `CONVERT`, `NONE` |
| `subtitles` | `LOCAL_RENDER`, `EXTERNAL`, `CONVERT`, `BURN_IN`, `NONE` |
| `range` | `PRESERVED`, `BASE_LAYER`, `TONE_MAPPED`, `NOT_ADAPTED`, `CONVERTED` |
| `audioIndex`, `subtitleIndex`, `subtitleExternal` | Which tracks |
| `reasons` | `PlanReason`: why, in stable names |
| `unverified` | `PlanPart`: what the plan assumes rather than knows |

### Decision order

1. **Can it play at all?** `PlaybackAssessor` answers, and nothing else does.
2. **Video:** hardware, then software: both are the original, and a software decoder that can take it avoids a transcode. Only video the assessor refuses is transcoded, and only by a source declaring `SERVER_TRANSCODE`; otherwise `CANNOT_PLAY`.
3. **Container:** a demuxer refuses it, so remux, or `CANNOT_PLAY` without `SERVER_REMUX`. A transcode covers it too.
4. **Audio:** the policy below. Audio never touches the video.
5. **Delivery** is the least that covers 2 to 4.
6. **Subtitles:** the policy below. Only a burn-in can raise delivery further.
7. **Range** is handled here: base layer, then tone mapping, then said. It is never a reason to convert, because a server would not make it better. A transcode says the range is at risk.

A level or frame rate past what a decoder declares is `VIDEO_PAST_DECLARED_LIMIT`: tried, because engines try past it, and named as unverified.

### Audio tracks

Passthrough comes first where it is known to work, since it loses nothing; then a local decode. Downmixing and losing Atmos are reported and convert nothing.

**A track the viewer chose** is that track or none, because another language is never put in its place:

1. It plays or may play: use it.
2. It cannot, and the source converts audio: convert that track.
3. Otherwise `PLAYS_WITHOUT_AUDIO`, with `AUDIO_UNAVAILABLE`, naming the track.

**Automatic selection** starts from what `TrackSelector` would choose, so plans and engines agree:

1. That track certainly plays: use it.
2. Another track of its language certainly plays: use that (`AUDIO_OTHER_TRACK_SAME_LANGUAGE`).
3. That track may play: try it.
4. The source converts audio: convert it. Language outranks avoiding a conversion.
5. Another language plays: use it, and say so (`AUDIO_OTHER_LANGUAGE`). Sound in another language beats none, but only where nothing can keep the viewer's own.
6. Otherwise `PLAYS_WITHOUT_AUDIO`.

### Subtitles

In order, the first that applies:

| | Case | Plan |
| --- | --- | --- |
| 1 | A file beside the video, drawable or unknown | `EXTERNAL` |
| 2 | In the original stream and known drawable | `LOCAL_RENDER` |
| 3 | The source extracts it, and the client is known to draw that file | `EXTERNAL` |
| 4 | A text format the source converts, for a client known to draw text files | `CONVERT` |
| 5 | In the stream, renderer unknown | `LOCAL_RENDER`, unverified |
| 6 | The source extracts it, renderer unknown | `EXTERNAL`, unverified |
| 7 | Known undrawable, the viewer chose it, and the source burns in | `BURN_IN` |
| 8 | Otherwise | `NONE`, with `SUBTITLE_UNAVAILABLE` |

- A remuxed or converted stream carries no embedded track, so only the original can be case 2: this is why a PGS track can stay an audio-only problem by travelling beside the stream.
- Bitmaps are never converted to text.
- Burn-in needs a viewer's choice and known impossibility. Unknown never burns in, and an automatic track is not worth a transcode.
- A subtitle never stops a film: the outcome stays `PLAYS`, and the plan says it is unavailable.

### Unknown

| Unknown | Plan |
| --- | --- |
| Decoder, container, display, audio | Try the original, and name it in `unverified` |
| A subtitle's renderer | Try it. Never a burn-in |
| Choosing between tracks | A track known to play beats one that may |

Unknown never causes a conversion and never blocks playback: both would be acting on something not known. A failure is how an unknown is resolved (section 8, failures). It would be wrong to convert first, since it costs quality and server time for what may play.

### 16. Format fixtures

What the real library cannot answer, made by FFmpeg from fixed arguments: `playback/mpv/src/test/.../FormatFixtures.kt` on the desktop, the same list in `android/app/build.gradle.kts` (`GenerateTestMedia`) on Android. Test pictures and tones only, 80 KB to 0.9 MB, generated at build or test time and never committed. `FormatFixtureTest` (desktop) and `FormatFixtureOnDeviceTest` (Android) read each, assess it, plan it as a local file, play it, and compare.

| Fixture | Question |
| --- | --- |
| `av1-1080p-8bit.mkv`, `av1-1080p-10bit.mkv` | AV1 Main at 1080p, 8 and 10-bit |
| `av1-2160p-10bit.mkv` | AV1 Main 10 at 4K |
| `hevc-2160p-hdr10.mkv` | HEVC Main 10 at 4K, PQ, BT.2020, mastering metadata |
| `hevc-2160p-hlg.mkv` | HEVC Main 10 at 4K, HLG |
| `truehd.mkv` | TrueHD |
| `vp9-1080p.webm` | VP9 Profile 0 with Opus in WebM |

A local file has no server, so these test reading, assessing, planning, and the engine. Conversion of the same formats follows the paths the real library proved (DTS for audio, HEVC Main 10 for video). Dolby Vision has no fixture: FFmpeg cannot make one, and no licensed sample is held.

## 17. What M2P proves

**Verified** is seen on a real engine and device. **Unsupported** is known not to work there. **Unknown** is not seen either way. **Unavailable** could not be tested with what was here.

| Capability | Windows | Fire TV | Pad 7 | Phone 2a | Google TV emulator | Linux |
| --- | --- | --- | --- | --- | --- | --- |
| H.264 8-bit, HEVC Main and Main 10 to 1080p | Verified | Verified | Verified | Verified | H.264 and HEVC 8-bit verified; Main 10 unsupported (no decoder): transcoded | Container only |
| H.264 High 10 | Verified (hardware) | Unsupported: transcoded | Unsupported: transcoded | Verified (hardware) | Unsupported: transcoded | Unknown |
| AV1 1080p, 8 and 10-bit | Verified (fixture) | Verified (fixture, hardware) | Verified (fixture, software) | Unavailable | Verified (fixture, software) | Unknown |
| AV1 4K | Verified (fixture) | Verified (fixture, hardware) | Unsupported by declared limit (fixture played anyway, at 5 fps) | Unavailable | The same as the Pad | Unknown |
| HEVC Main 10 4K | Verified (fixture) | Verified (fixture) | Verified (fixture) | Unavailable | Unsupported | Unknown |
| VP9 Profile 0 | Verified (fixture) | Verified (fixture; depth unread on Fire OS 8) | Verified (fixture) | Unavailable | Verified (fixture) | Unknown |
| HDR10 and HLG signalling read | Verified (fixture) | Verified (fixture) | Verified (fixture) | Unavailable | Verified (fixture) | Unknown |
| HDR shown as HDR | Not claimed: tone-mapped to SDR | Unknown (display lists HDR10; HLG not shown, said) | Unknown | Unknown | Unavailable | Unknown |
| Dolby Vision | Unknown | Unknown | Unknown | Unknown | Unknown | Unknown |
| AAC to 7.1, AC-3, E-AC-3, Atmos bed | Verified | Verified | Verified | Unsupported for AC-3, E-AC-3: audio converted, video kept | Unsupported for AC-3, E-AC-3: converted | Container only |
| DTS | Verified | Unsupported: converted | Unsupported: converted | Unsupported: converted | Unsupported: converted | Unknown |
| TrueHD | Verified (fixture) | Unsupported (fixture: no decoder) | The same | Unavailable | The same | Unknown |
| Opus | Verified (fixture) | Verified (fixture) | Verified (fixture) | Unavailable | Verified (fixture) | Unknown |
| FLAC, ALAC, PCM in video | Unknown | Unknown | Unknown | Unknown | Unknown | Unknown |
| Passthrough heard | Unavailable: not done | Planned; unavailable: no receiver | Not applicable | Not applicable | Not applicable | Unavailable |
| SRT, ASS in the file | Verified | Verified | Verified (cues) | Verified (cues) | Verified, on screen | Container only |
| PGS in the file | Verified | Verified | Verified (cues) | Verified, on screen | Verified (cues) | Container only |
| PGS beside a converted stream | Verified | Unknown (never needed: decodes the audio) | Unknown (the same) | Verified (generated); real film unavailable | Verified, on screen | Unknown |
| VobSub in the file | Verified | Verified (cues) | Verified (cues) | Verified (cues) | Verified, on screen | Unknown |
| VobSub beside a converted stream | Verified | Unsupported: burned in | The same | The same | The same | Unknown |
| Extracted text subtitles over a conversion | Verified | Verified | Verified | Verified | Verified | Unknown |
| Chained SeekHead | Verified (seeks) | Verified (remux on seek) | Verified | Verified | Verified | Unknown |

**Proves:** the capability model, assessor, planner, negotiation, execution, and evidence agree on every real kind of media and every fixture, with no mismatch; conversions happen only for what a device cannot do, and audio never converts the picture; subtitles burn in only for VobSub chosen where the audio converts; a seek or resume an engine refuses re-plans once; a viewer's language is kept; Info says why.

**Does not prove:** HDR or Dolby Vision on a screen; passthrough heard; Linux on physical hardware (waived, VERIFICATION.md); the Phone 2a on fixtures (not attached); Jellyfin's conversion of the fixture formats (they are not on the server); Media3's real-time 4K AV1 in software.

**Known platform limits:** Android's own extractor drops a TrueHD track from a local file's probe, so its silence is found at playback, not planned, and said then; Fire OS 8 reports no VP9 profile, so depth stays unknown; Media3 does not follow a chained SeekHead.

## 18. One session, several presentations (M9, M10)

A playback is one `PlaybackSession`: one plan, one engine, one video surface, one watch reporter, one profile. How it is shown is a property of the session, never another playback (DECISIONS.md, 2026-11-12 and 2026-11-13).

```text
PlaybackSession (plan, engine, reporting, profile = the owner, held once)
       │
       ├── FULL    the player has the screen; everything behind it is inert
       ├── MINI    the picture and a few controls in the navigation's dock; the page is the viewer's
       └── SYSTEM  the system's own window (Android's picture-in-picture): the picture, and what the viewer must not miss
```

| Piece | Where | What it is |
| --- | --- | --- |
| `PlayerPresentation` | `ui/state` | `FULL`, `MINI`, or `SYSTEM`, on the session (`collapse()`, `expand()`; `enterSystem()` and `leaveSystem(expanded)`, called only by a shell from the platform's confirmed callbacks). Moving between them plans nothing, loads nothing, and makes no engine |
| `BackRoute` | `ui/state` | What Back does now, derived from the state (`AppState.backRoute`): `SYSTEM` (the platform's own Back runs), `APP`, `COLLAPSE` (the full player steps into the mini-player), `SYSTEM_PRESENTATION` (what plays in the dock goes into the system's window). `dispatch(BACK)` returns false exactly when the route is `SYSTEM` |
| `ChromeMode`, `chrome()` | `ui/player` | What the full player draws, as one derived value (below) |
| `SeekHold` | `core/playback` | A held seek key: accelerates by time held, shows its target, seeks once |
| `AppState.watching`, `AppState.docked` | `ui/state` | The session that has the screen, or the one beside the page. Input, inertness, and notices follow `watching`; `playback` is the session either way |
| `VideoFrame` | `ui/state` | Where the shell's surface goes: `Full`, `Docked(bounds)`, or `Hidden` (something covers the page; the sound goes on). The experience layer computes it from its own layout |
| `Platform.docksVideo` | `ui/state` | Whether the shell can place its surface. False means no mini-player there, and nothing offers one |
| `VideoStage`, `rememberVideoPlacement` | `ui/player` | Places any surface by the frame; a surface the toolkit composes may travel, a native one moves once |
| `MiniPlayerDock` | `ui/player`, drawn by the shell's chrome | The dock: the picture's box, what is playing, play or pause, stop |
| `MiniPlayerSurface` | `ui/player` | What a shell layers over a native surface in the dock: a click goes back to the player |
| `LayoutProfile.dock` | `design` | The dock's geometry per window; none on a television |

What does not change with the presentation: the plan and its comparison with what happened, track choices, the silent-source rules, watch reporting and whose it is (the profile that started it, held by the session as `owner` and used for every report and every remembered choice), and the one-engine rule. `PresentationTest` and `PlayerM10Test` hold each of these.

**Desktop.** libmpv renders into the same native child window throughout; the shell moves that window to the dock's picture box and back, and the controls window follows it (section "Desktop video" of DECISIONS.md, 2026-10-09, still stands). **Television** has none by decision.

**Android (M10).** One `SurfaceView` for the whole of a playback, composed once inside `VideoStage` and put where `VideoFrame` says by laying it out: its parent and its place in the composition never change, so the decoder is never restarted and no surface is destroyed or made again (`SurfaceContinuityOnDeviceTest`; probe P1 in VERIFICATION.md). Its buffer is the picture's own size (`holder.setFixedSize`), so moving it is the compositor scaling it. Unlike the desktop's native window it travels between the whole window and the dock. The dock's picture shows no subtitle lines (too small to read); they are drawn in the full player and in the system's window.

**Back.** Reflux enables a back handler only where it has something to do with Back, and none at the root: Android's own Back (back-to-home with its predictive animation) runs there. A handler is `PredictiveBackHandler` for `COLLAPSE` (a gesture's progress shrinks the picture in place by 10% and fades the controls; the session changes only when the gesture completes, so a cancel restores everything because nothing else was changed; with reduced motion there is no preview), `BackHandler` for `APP` and `SYSTEM_PRESENTATION`, and on Android 11 and earlier one that moves the task back. Precedence, first that applies: asking who is watching; the system's window (no Back there); an open list; a failure (Back closes it); the full player (Back steps into the mini-player where there is one, else closes); a text field, the folder picker, the offline sheet; the navigator; then at the root, a mini-player that is playing or buffering with nothing to answer goes into the system's window, and anything else is the system's own Back. Back never ends a session that is in the dock.

**System window.** `Platform.systemPresentation` is true on a handheld whose system has picture-in-picture. The shell asks for it on leaving while something is playing (`setAutoEnterEnabled`, and `onUserLeaveHint` before Android 12) and by Back at the root; the window is the session's `SYSTEM` presentation from the platform's `onPictureInPictureModeChanged`. Its shape is the picture's (clamped to the 1:2.39 to 2.39:1 the system accepts), its `sourceRectHint` is the picture's box, seamless resize is on, and on Android 15 and later `onPictureInPictureUiStateChanged(isTransitioningToPip)` takes the controls away before the animation. The system's controls (play or pause, next, previous) are the activity's own media session; there is no `MediaSessionService` (DECISIONS.md, 2026-11-13). When the window goes: with the activity still started it was expanded (the session is `FULL`); with the activity already stopped it was closed (the session ends and its place is recorded). Observed on a Phone 2a (probe P2): expanding, and launching the app while in the window, arrive with the activity `STARTED`; the close button and swiping the window away arrive with it `CREATED`. Failures and questions in the window are said there (a glyph and a line over the picture); expanding shows the player with them.

**Chrome.** The full player's controls are the value `chrome(facts, hold, now)` and nothing else: `NONE` (not the full player, or the system's window is on its way), `FAILURE`, `ENDING` (the next episode's prompt), `LOADING` (opening; or buffering past a 600 ms grace, unless the viewer is dragging the timeline or holding a seek), `SHOWN`, `HIDDEN`, in that order of precedence. Each mode has the layers it may draw (`ChromeMode.layers()`), so loading draws the Reflux loader and the way out and none of the transport, timeline, lists, or skip offer. What keeps `SHOWN` up is the viewer's last doing for their patience (scaled by the system's accessibility timeout) and pins: paused, a list open, a screen reader running (never hidden), focus inside the controls with a keyboard, controller or remote, a pointer over them, dragging the timeline, a held seek. A touch on the picture puts away what only softly pins. The player waits for one deadline (`nextChange`) and for nothing else.

**Held seek.** A press is the ten-second seek it always was, at once; held, a key only moves a target (10, 30, then 60 media seconds per second held, by time and not by the platform's repeat rate), shown on the timeline, and one seek is made when the key is let go, or 400 ms after the last repeat for a platform that tells nothing of a release. Android's repeat count and AWT's repeats (with X11's release-and-press pairs folded into repeats) say which presses are repeats; quick separate presses (a double tap, an arrow tapped quickly) are separate seeks, and a controller's presses are single presses.

**Chapters on Android** are what Media3 reads out of the file: `Chapter` metadata on the tracks' formats, from Matroska and WebM, and from MP4's `chpl` and QuickTime chapter tracks (`ChaptersOnDeviceTest` plays generated files of each). Streams Media3 describes none of (a server's converted HLS among them) have none; nothing in Reflux parses a file for them.

**A viewer's subtitle choice** is kept per profile and per work (the show, for an episode) in `profile_setting`, as a description of the track (off, or language, forced, hearing impaired), never as a position. The planner matches it against each version's tracks (`SubtitleChoice.Preferred`; embedded first, then files; language, then forced, then hearing impaired) and plans a version it matches nothing in as the automatic choice would, leaving the memory as it was. Only a choice the viewer makes is kept.

**A failure stays where the viewer is.** In the full player it is read and answered there (Back closes it). In the dock it is the same dock: the picture is taken out of sight, it says "Playback stopped", offers Try again where there is one, and Close, and is announced; it does not take the screen from someone browsing. A next episode that cannot be played is such a failure, and no longer a silent end.

## Sources

| Source | Plan |
| --- | --- |
| Local, SMB, WebDAV | Declare no `SERVER_` capability. Plans describe client handling alone; what the client cannot do is `CANNOT_PLAY`, `PLAYS_WITHOUT_AUDIO`, or a subtitle unavailable |
| Jellyfin | Declares all six. Plans say what the server would need to do. Negotiating it is section 8 |

A server's per-user limits are not known at planning time: a refusal comes from negotiation, and the plan falls back to what the client can do alone.

## 8. Execution

Built in M2P.4. The planner decides, executors carry it out, and evidence records what happened. No executor chooses a codec or a track.

```text
Library.planPlayback
  Best Version → PlaybackPlanner.plan → NegotiatingSource.negotiate (where there is one)
        ↓
PlaybackRequest: target, attachments, TrackDirective
        ↓
PlaybackController → Media3Player / MpvPlayer
        ↓
PlaybackSession: PlaybackEvidence + engine state → PlaybackComparison
```

### Sources

A source's real `descriptor.capabilities` reach the planner, and a source that cannot negotiate has any `SERVER_` capability removed, so a conversion is only planned where something can ask for it. Local, SMB, and WebDAV open the file. What they cannot do is a stated outcome, never a worse track. There is no remux for them: nothing provides one.

### Tracks

`PlayerTrack.position` is a track's place among the media's tracks of its type, counted before an engine leaves any out, so a plan's track means the same on every engine. `TrackDirective` carries the plan to `PlaybackController`.

| Plan | Directive | When the engine lacks it |
| --- | --- | --- |
| Automatic track | `Track(position)` | Default selection, for media the facts do not match |
| A viewer's track | `Track(position, explicit)` | No audio: `selectNoAudio`, and `audioUnavailable` says so |
| No track can play | `None` | |
| A converted stream | `EngineChoice`: it carries the one track asked for | |

Hardware and software decoding are not forced on either engine. Each takes what it finds, and the comparison checks it against the plan.

**On a converted stream** the menus list the file's tracks, not the stream's. Choosing one plans again from the same place, with that track explicit.

**A subtitle a server extracts arrives late.** The first request for a PGS track in a 5.6 GB file took 48.9 s, and 0.1 s once cached. `sub-files` would hold the picture for that long, past the 30-second patience, so such an attachment is `arrivesLate`: mpv adds it with `sub-add` after the file opens, and the controller selects it when it appears, unless the viewer chose something meanwhile. Media3 loads attachments without holding the picture.

### Jellyfin negotiation

`JellyfinSource.negotiate` runs before playback starts, within 3 seconds.

| Sent | From |
| --- | --- |
| `DirectPlayProfiles` | `DeviceCapabilities`' containers, decoders, and audio |
| `CodecProfiles` | Bit depth and frame size only. Level and frame rate are left out because Reflux tries past them, and a server told of them would transcode what the plan says to try |
| `SubtitleProfiles` | Formats whose renderer is known to work. Unknown ones are not claimed |
| `TranscodingProfiles` | HLS in TS, only when the plan converts, with what is kept listed first so that it can be copied |
| `MaxAudioChannels` | The larger of the output and the kept track: a smaller number makes a server convert audio this client downmixes itself |
| `AudioStreamIndex`, `SubtitleStreamIndex` | The plan's tracks. A subtitle only for a planned burn-in, `-1` otherwise |
| `AllowVideoStreamCopy`, `AllowAudioStreamCopy`, `EnableDirectPlay` | The plan |
| `MaxStreamingBitrate` | Unlimited, except a video transcode: twice the original, at least 10 Mbit/s |

- A plan for the original opens the static stream whatever the server's flags say. If the server disagrees, the comparison shows it.
- A conversion the server will not agree to, or a server that does not answer, falls back to a plan for the client alone, with `NEGOTIATION_FAILED`.
- The session reports the true `PlayMethod` and `PlaySessionId`, and a conversion is stopped at `Sessions/Playing/Stopped` and when the player closes.
- `SupportsDirectStream` is reported, and decides nothing.

**Android:** `media3-exoplayer-hls` is the one new dependency, at the same version. `DefaultMediaSourceFactory` finds it by itself for a `.m3u8` address. No engine code handles HLS.

### Failures

| Failure | What happens |
| --- | --- |
| `UNSUPPORTED` from an engine, on the original, source has `SERVER_TRANSCODE` | Planned again once, with `originalFailed = VIDEO`, from the same place. Reason `ORIGINAL_FAILED` |
| Picture with no sound, on the original, source converts audio | Planned again once, with `originalFailed = AUDIO`, keeping the track that was playing |
| Either, and the source cannot convert | The stated failure, or the "no sound" notice, as before |
| Media3 says it cannot move in the original, and a seek or a saved place needs it (`PlayerState.seekable` false), source remuxes | Planned again once, with `originalFailed = CONTAINER`, from the place wanted. Video and audio are kept. A viewer who never seeks stays on Direct Play |
| The same, and the source cannot remux, or has already | The seek is not sent, as Media3 would go to the beginning. The viewer is told, and the film goes on |
| A second failure of a work | Hard: the stated failure |
| `UNREACHABLE`, `OUTPUT`, `UNKNOWN` | Hard: a conversion would not help. `UNREACHABLE` keeps Try again |
| A failure on a stream already converted | Hard |
| A stream unlike its playlist (Media3's `SampleQueueMappingException`, raised as an I/O error) | `UNKNOWN`, hard: everything was reached, so it is not `UNREACHABLE` |
| A subtitle the server was to make never arrives (`PlayerState.subtitlesNotLoaded`, from mpv's `sub-add` reply) | The picture goes on and the viewer is told once. Not a reason to plan again |

A re-plan for a track change or a failure carries on from the player's exact position. The return-visit rules in `Handoff` are not for it: they restart a place under a minute, and doubt one on a stream that runs longer than its file (seen restarting a film at 1:38).

**A server's stored tracks can be wrong.** When a file is rewritten and keeps its modification date, a library scan does not notice: the server goes on listing a track the file no longer has, and every stream index after it is off by one, selecting the wrong track or none. Seen on 34 live films. Reflux cannot see this ahead of time. It shows as a mismatch or the failure above; a refresh of that item on the server fixes it.

There is no retry, backoff, or lower-quality ladder. `PlanOutcome.CANNOT_PLAY` stops before anything starts. A re-plan never changes the audio language.

### Planned against actual

`PlaybackComparison` holds the plan, `PlaybackEvidence`, and the engine's decoder. Five seconds into playing, `PlaybackSession.comparison` is set, and a mismatch goes to the diagnostics log.

- `mismatches`: shown to differ. `DELIVERY`, `VIDEO`, `AUDIO`, `SUBTITLES` (a burn-in not planned, or a planned one that did not happen), `AUDIO_TRACK`, `DECODER`.
- `unconfirmed`: could not be shown either way.

A source with no account of itself still has the engine's side compared. A planned original the server transcodes is a mismatch, and tests hold that. Live, this found a real defect: a planned remux whose audio the server converted.

## 9. Diagnostics

Built as `PlaybackReport` (core): the player's **Info** list and the diagnostics log, in a viewer's words. The table below was the proposal; section 17 says what it covers.


One `PlaybackReport`, for development first: written to the diagnostics log through `Redaction`, and printed by the developer CLI.

| Part | Planned | Observed from |
| --- | --- | --- |
| Delivery | `PlannedPlayback.delivery` | `TranscodingInfo` (Jellyfin, built: section 3a); fixed `ORIGINAL` elsewhere |
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

- **Release builds:** the dev build has libdav1d and native decoders for every audio format. The LGPL build (M6) must keep `libdav1d`, because FFmpeg's own `av1` needs hardware. Worth a test against the released library.
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
| HEVC Main 10 + E-AC-3 Atmos + MKV + PGS | Yes: Iron Man, served since the remux (section 15) |
| HEVC Main 10 + E-AC-3 + MKV + PGS | Yes |
| HEVC Main 10 + E-AC-3 **7.1** | Yes: 3 tracks (section 15) |
| HEVC + DTS | DTS exists on H.264; HEVC pairing to be checked |
| HEVC + TrueHD | **No: needs a licensed sample** |
| H.264 + AAC + MP4 | Yes |
| AV1 + common audio | **No: generated or licensed** |
| PGS, ASS, forced, SDH, multi-audio | Yes |
| HDR | **No: licensed sample** (Blender's open films have HDR masters) |
| Seek, resume, audio and subtitle switching | Yes |

## 12. Validation plan

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

- It reads `DeviceCapabilities` and `PlaybackAssessor`, and the assessor reads its limits from the same code as `Capabilities`, so no capability logic is duplicated. Its criteria and their order are unchanged.
- **Inputs found wrong and corrected:** a display listing Dolby Vision stood in for decoding it, so a profile with no decoder was counted as DV; it now counts as its base layer, and profile 5 as unsupported. Atmos scored on any passthrough; it now needs Atmos not known to be lost. Desktop output was a fixed 2 channels; it now rises to what playback showed. Desktop audio decoders were every codec; they are the engine's list. Android called 10-bit AV1 and H.264 8-bit. Android read outputs once.
- **Inputs left as they were:** hardware decoding on the desktop was already observed (2026-10-19). HDR on the desktop was already not claimed. `canToneMap` on Android stays false. Frame rate, level, and profile are not assessed, for the reason in section 6.
- `PlaybackAssessor` and the planner must not disagree. The assessor stays "how well does this device play it locally"; the planner answers the delivery question using the assessor's checks, sharing one function.
- **One answer to "can this play".** `PlaybackAssessor` is the only implementation, reading `Capabilities`. `RankedVersion.playable` reads it, and `PlaybackPlanner` plans from it. A test holds that a client-only plan is `CANNOT_PLAY` exactly when the assessor says unsupported, over 720 combinations. Its audio check moved to `Capabilities`, which corrected two things: audio that may pass through is no longer unplayable, and a declared decoder limit now counts.
- **A source's abilities count.** A version this device cannot play is playable where its source's real capabilities make a plan possible (`RankedVersion.throughSource`). It stays a compromise, so anything the device plays itself ranks first. The criteria and their order are unchanged. Smart Offline passes no capabilities: a copy must play here as it is.
- **The chosen track is assessed** (defect 10, fixed): with the viewer's languages, a version is judged by the track the planner would choose. Falling back to another language is the compromise `AUDIO_OTHER_LANGUAGE`.
- **No executor has compatibility logic.** Engines report; `PlaybackAssessor` and `Capabilities` remain the only answers.
- **Where playback is started from (M3, NAVIGATION.md, sections 4 and 8).** Best Version ranks the versions in the scope the work was opened in: the included versions for the unified library, or one source's (or library's) for a source's own page. Nothing else about ranking or planning changes, and both scopes use the same planner and engines. Next-episode autoplay and the player's episode list keep the scope.
- **One open question, to settle with evidence:** today a version needing conversion and one playing unchanged are ranked with no knowledge of delivery. On a phone with no DTS decoder, a DTS-only version is `NO_PLAYABLE_AUDIO` although Jellyfin could convert it. Whether "server can fix it" affects ranking is a policy change: it is left out until a real case shows a wrong choice.

## 14. Order of work

1. **Truth first (small, no new behavior):** done. The truth layer (section 3a), and defects 1 and 12.
2. **Capability model and stream facts:** done (section 6). Defects 2 to 9, 13, and 14.
3. **Planner:** done (section 7).
4. **Execution and Jellyfin negotiation:** done (section 8).
5. **Diagnostics:** the report, planned against observed, in the log and the CLI.
6. **Audio outputs on desktop, then the Android decoder decision** with measurements.
7. **Corpus and validation:** sections 11 and 12, and the exit criteria in ROADMAP.md.

Risk to watch: every playback now making a network call before it starts. Start-up time is measured before and after step 4.

## 15. Compatibility expansion (M2P.5)

Evidence first: what a real library contains, what the real planner says for each real device, and what the real engine then does. This adds no decision. `PlaybackSurvey` groups versions into cases of the same media and plan, and `MediaMatrixLiveTest` and `MediaMatrixOnDeviceTest` play one file of each, on request. Nothing here feeds logic.

### The library after the remux

Read 2026-11-02: 1,003 versions, Matroska 992, QuickTime 11; all SDR; 275 at 1080p, the rest smaller.

| Kind | Found |
| --- | --- |
| Video | H.264 8-bit 830, HEVC Main 10 141, HEVC Main 31, **H.264 High 10: 1** |
| Level | H.264 3.1 to 5.0, HEVC 93 and 120 |
| Audio tracks | AAC 1116 (HE-AAC 837; 5.1 493, 7.1 4), AC-3 64, E-AC-3 41 (Atmos 34, 7.1 3), DTS 2 |
| Subtitle tracks | SRT 4715, ASS 738, PGS 168, VobSub 3. None external |
| Not present | **TrueHD, DTS-HD, AV1, VP9, VC-1, HDR, Dolby Vision, 4K, WebM, MPEG-TS** |

### State by target

| Target | Real tracks | Windows | Fire TV | Phone 2a, Pad 7, Google TV emulator | Linux |
| --- | --- | --- | --- | --- | --- |
| DTS | 2 | Verified, local decode | Verified, audio conversion, video kept | Phone and Pad verified, conversion (VERIFICATION.md); emulator unknown | Waived on hardware |
| AC-3, E-AC-3, 5.1 and 7.1 | 105 | Verified, local decode | Verified, plays; passthrough planned | Phone converts, Pad decodes; 7.1 unknown | Waived on hardware |
| Atmos | 34 | Bed only, verified | Plays; objects unverified | Unknown | Waived on hardware |
| HE-AAC 5.1 and 7.1 | 497 | Verified | Verified | Unknown | Waived on hardware |
| PGS | 168 | Selected, in the file | Selected, in the file | Phone and Pad seen (VERIFICATION.md) | Container only |
| VobSub | 3 | Selected, in the file | Selected in the file; burned in when the stream is converted | Phone burn-in seen | Unknown |
| ASS | 738 | Selected | Selected on one film; fidelity unknown | Unknown | Unknown |
| H.264 High 10 | 1 | Verified, NVDEC | Verified, transcoded, audio kept | Unknown | Waived on hardware |
| Passthrough heard | None | Unavailable: not done, no receiver | Unavailable: no receiver | Unavailable | Unavailable |
| TrueHD, DTS-HD | 0 | Unknown | Unknown | Unknown | Unknown |
| AV1, VP9, 4K | 0 | Unknown | Unknown | Unknown | Unknown |
| HDR, Dolby Vision | 0 | Unknown | Unknown | Unknown | Unknown |

"Selected" means the engine took the track. No one looked at this walk's picture. Unknown stays unknown: it is not unsupported.

**Findings.** The architecture held: 49 cases on Windows and 48 on the Fire TV needed no new concept. No level changed a plan. Conversions on the Fire TV were the 5 cases that need one, and audio never touched the video. VobSub in a converted stream burns in, as section 7 says: the same track is drawn from the file under Direct Play. The evidence still does not justify an FFmpeg extension: 2 DTS tracks and no TrueHD, all converted on Jellyfin. The local, SMB, and WebDAV gap in section 10 stands.

**A real gap, found and fixed: Media3 does not follow a chained SeekHead.** A Matroska file may have a first SeekHead that names only a second SeekHead, at the end of the file, which names the Cues. That is valid, and 3 of 992 real files are like this: Moon Knight E01, E03, and E04. Each has a whole index (E01: 1,319 points). `MatroskaExtractor` takes a SeekHead entry only if it names Cues (read in the 1.11.1 bytecode), so at the first cluster it knows of none and declares the file unseekable.

| On the Fire TV, plain ExoPlayer, the same calls as `Media3Player` | E01, E03, E04 | E02, E05, E06, Doctor Strange | E01 by server remux |
| --- | --- | --- | --- |
| `isCurrentMediaItemSeekable`, `window.isSeekable`, `COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM` | **false** | true | true |
| Duration | Known | Known | Known |
| Seek to 120 s | Accepted, 120,000 shown, then adjusted to **0** | Lands | Lands |
| Start at 120 s | Starts at **0** | Lands | Lands |

E01 as a local file on the device behaved as it did over Jellyfin, so delivery of the file is not the cause. Windows, on FFmpeg, seeks all three by Direct Play. This was first recorded here as a missing cue index, which was wrong: the index is there.

Media3 is the cause and could not be changed from here. What was Reflux's was sending a seek the engine had declared it could not do, and not noticing a place it had lost. The real application, unfixed: a saved place of 300 s opened at 2.8 s, fast-forward went to 0, and after a minute and a half the saved place was overwritten by 81.6 s, with the server's cleared. Fixed as section 8 says. `PlayerState.seekable` is Media3's own answer, null while its window is a stand-in and always null on mpv. Nothing guesses at this from a file, and Best Version and plans for a first attempt do not change.

**Left:** the evidence Reflux reads after this re-plan is a true `UNKNOWN`, shown as unconfirmed and not a mismatch: a remux that only copies can finish in seconds, and `TranscodingInfo` goes with the job (VERIFICATION.md, "Chained SeekHeads across devices"). Jellyfin's own logs show the remux with video and audio copied. A remux that a server refuses restarts the film, with a word: seen in a test, not live. A subtitle chosen in the player does not carry over this re-plan, the same as the other re-plans. On a source with no remux, these files cannot be moved in on Media3: a known limit, and stated.

**Android matrix (VERIFICATION.md, "M2P.5 real media matrix on Android").** The Google TV emulator, the Pad 7, and the Phone 2a walked as planned, case by case. VobSub in the file delivered bitmaps on all three, so its `UNKNOWN` renderer on Android now has device evidence; it is unchanged until a picture is seen. External PGS on Android is unsupported by Reflux's own attachment path, so PGS chosen beside converted audio is burned in.

### Next

1. Run the walks on the Phone 2a, the Pad 7, and the Google TV emulator (none walked yet).
2. Generated fixtures, played locally on each engine, for what the library lacks: AV1, VP9, 4K, HDR signalling, TrueHD (section 11). Dolby Vision needs a licensed sample.

## Sources

- Live server, libmpv, and Pad 7 readings: section 3, VERIFICATION.md (2026-11-01).
- [org.jellyfin.media3:media3-ffmpeg-decoder on Maven Central](https://central.sonatype.com/artifact/org.jellyfin.media3/media3-ffmpeg-decoder/): GPL-3.0 in its POM.
- [ExoPlayer FFmpeg module README](https://build.khadas.com/khadas/test/edge2/external/exoplayer/tree_15dc86382f17a24a3e881e52e31a810c1ea44b49/extensions/ffmpeg/README.md): legacy; built from source, not on Google's Maven.
- [NextLib](https://github.com/anilbeesetti/nextlib): a community FFmpeg extension for Media3.
- [mpv issue 13899](https://github.com/mpv-player/mpv/issues/13899): `target-colorspace-hint` behavior differing by graphics API, for the HDR session.

**Final qualification (2026-11-13).** The desktop picture's corners in the dock are a window region on the native surface plus a rim (DECISIONS.md, 2026-11-13, final pass); the session, the surface, and the engine are the same ones. A recreated Android activity ends its playback cleanly (confirmed on the Phone 2a and the Pad 7).
