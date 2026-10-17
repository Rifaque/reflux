# Reflux Design Direction

## Identity

Reflux should combine:

- restrained, premium consumer-software ergonomics,
- cinematic media presentation,
- selective frosted glass for what floats above the media (DECISIONS.md, 2026-10-14, "Visual reset").

The goal is not to copy Apple, Netflix, Infuse, Plex, or any other product. Those products are references for principles, not visual templates. What Reflux takes from the best of them is restraint: media is the hero, artwork carries the color and the energy, and the interface stays quiet around it.

In one line: let the media be colorful; let the interface be calm. Powerful underneath, calm on the surface. The interface is near-black, charcoal, graphite, soft grey, white, and selective frosted glass, with no color of its own. Artwork is the color on screen, and it may tint the page around it as atmosphere, but never the controls, the navigation, or focus.

## Material system

Glass is a system, not a blur filter, and it is a layer above the media, not Reflux's identity.

Define material roles such as:

- Base
- Elevated
- Floating
- Interactive
- Focused
- Modal

Materials should communicate depth, hierarchy, and interaction.

Avoid:

- excessive translucency,
- unreadable text over imagery,
- blur applied everywhere,
- decorative glass that hurts usability.

## Motion

Motion should communicate state and continuity.

Examples:

- library transitions,
- player controls,
- source/availability changes,
- controller-focus changes,
- input-mode morphing,
- navigation transitions,
- poster-to-details transitions.

The controller morph is a signature interaction and should feel intentional rather than like a separate "TV mode" being switched on.

## Defaults

The default presentation must already feel finished.

Do not expect users to tune:

- card density,
- blur intensity,
- animation duration,
- source priority,
- metadata providers,
- subtitle layout,
- advanced player controls

before the application becomes pleasant.

## Platform adaptation

Phone, tablet, TV, and desktop may use different information densities and navigation structures.

Consistency should come from:

- the same visual language,
- the same semantic model,
- the same interaction principles,
- the same media concepts.

Not from forcing identical layouts onto every screen.

## Implemented foundations (`design`)

- **Palette:** neutral and dark. No chrome token has a hue (a test holds every one of them to near-zero saturation), so nothing the interface draws competes with artwork; only errors are red. Text tokens are tested to meet WCAG AA on every surface, and glass materials raise their tint over bright artwork until text stays at 4.5:1.
- **Emphasis without color:** the one action a screen exists for (Play, Resume, Connect, Use this folder) is bright neutral (`primaryAction`, near-white with near-black text). Progress is white on a translucent track. The section the user is in, a season, or a player list is white on a soft pill (`Tab`). Switched-on toggles are a lit surface; a favorite is a filled heart. The version Play will use is lifted out of the list and marked with a filled neutral badge. Focus is a white ring. If something can be said in white and grey, it is; there is nothing else to say it in.
- **Type carries the hierarchy:** size, weight, and tone, not capitals, boxes, or rules. Featured titles are large and tight (54 on a desktop, 48 at ten feet before the TV step, 40 on a phone); what describes them is the `metadata` style, a size, a weight, and a tone below; supporting text is quieter still. A title needs no label above it; labels are sentence case.
- **Materials:** Base, Elevated, Floating, Interactive, Focused, and Modal, each with tint, backdrop blur, highlight, shadow, and corner radius. Blur applies to what is behind a material, never to its content.
- **Motion:** standard, emphasized, and exit curves; focus changes 140 ms, navigation 320 ms, poster-to-details 420 ms, input morph 360 ms; player controls auto-hide after 3.5 s.
- **Adaptive layout:** form factor (phone, tablet, desktop, TV) comes from the window and device; input modality only changes density, focus scale, target size, and button prompts. That is what makes the controller morph a transition rather than a mode. A television's canvas is 960 by 540 whatever its resolution, so the distance is already in its density: it keeps a 5% overscan-safe margin, shows six titles across, sizes controls to be read rather than hit, and sets type one step (1.1×) above the desk.
- **Artwork atmosphere:** a deterministic, saturation-weighted hue histogram over a sparse sample of the artwork picks its dominant hue, which becomes a deep, muted tone (`ArtworkAtmosphere`: dark enough for primary text at 7:1). Grey artwork has none. A work with a poster but no backdrop takes the poster's hue, both for the page and for the backdrop generated behind it (graphite for a grey poster), so the page never disagrees with the artwork on it; only a work without any artwork takes its title's hue.

## Rendering (`ui`)

- **Glass:** a material is a blurred copy of what is behind it, a tint, a top-lit hairline, and a shadow. Blur is real for materials that float above a screen (navigation bar, panels, notices): the screen is recorded as it draws and the material blurs that recording over an opaque base, so nothing sharp shows through. Materials inside scrolling content, and everything over video, use the tint alone.
- **Floating chrome is solid to the pointer.** A click, hover, or scroll on the navigation bar or a panel never reaches the page beneath it; a click outside an open panel closes it and goes no further.
- **The best real image for the frame, then the other one, then a designed one.** A poster card shows the poster, else the backdrop's middle; a wide frame the backdrop, else the poster where the frame is no wider than 16:9 (a phone's details header held upright, a wide card); a header with room for a poster shows it beside the title. An image that cannot be read now gives way to the next. The same rule (`ArtworkChoice`) on every form factor; only when no real image is left is the work generated. A television reads no images beside local videos (it does not ask for image access), so there a local work is pictured by provider artwork or generated.
- **No artwork is a designed state.** A work without artwork is drawn as its title on a color derived from the title, and Home opens on a generated backdrop, so a library with no metadata yet looks intentional rather than broken. A placeholder already says the title, so only the caption is repeated under it. Empty states (first run, an empty folder, a work that left the library) sit on a quiet ambient glow.
- **Grids use columns of exactly equal width,** so posters in a row are the same height and their captions share a baseline.
- **Artwork tints the page, never the controls.** Home's hero and a details page wash the page behind them with their artwork's atmosphere (a fifth of its strength over the base), down to the bottom of the artwork and fading out over the next 520 dp; the artwork's scrims end in the same tone, so the image dissolves into the page without a seam. The wash belongs to the artwork: it recedes as the artwork scrolls away, and rows, versions, and episodes further down sit on the plain base. Buttons, navigation, badges, and focus look the same on every page.
- **Buttons:** `PRIMARY` (bright neutral) for the one main action of a screen, whether that is playing, connecting, or choosing a folder; `GLASS` (frosted) for secondary actions that sit on artwork; `QUIET` for the rest. A screen has at most one `PRIMARY`.
- **Glass is for what floats.** The navigation bar (once the page has moved under it), the player's bar and lists, panels, sheets, and notices are frosted; content is not put in glass cards, and rows of text sit directly on the page. Over a page that opens on artwork, the navigation bar is just its labels until the page scrolls, so the artwork reaches the top of the window.
- **Edges are hairlines.** Posters, episode thumbnails, version rows, and badges are defined against the black by a hairline of white at 7–14%, never by a frame or a colored border.
- **Active state:** pointer hover and keyboard or controller focus are one "active" state shown by a small lift; a focus ring is added only for input that needs one.
- **Focus from across a room:** with a controller or a remote, what has focus is lifted, ringed in white with a sliver of the page between the ring and the control (so it reads the same around a white button as around a poster), and lit from behind by a soft glow that fades outwards. None of it is large: a focused poster grows by 8%, a button by less.
- **Versions answer "which one should I watch?"** Each copy leads with what it is chosen by (resolution, HDR, edition) in title type; its source follows, then its codecs and size, quietest. The copy Play will use is lifted out of the list (a lit surface, a white play disc, a "Recommended" badge) with Best Version's reason beneath it. A copy the user pinned says "Your choice" and "Play always uses this one", so a preference never looks like Reflux's guess.
- **Television:** cinematic at ten feet, effortless with ten buttons. The page is mostly artwork; glass is the navigation bar, the player's bar and lists, and panels, as elsewhere. Home moves by whole rows and fades what has gone up under the navigation instead of leaving it half showing. The player's bar is a timeline and the names of the lists beneath it; there are no buttons on a screen nobody can point at. A remote's keys say what they do, so no prompts are shown for it; a controller is still prompted in its own terms.
- **Player:** the way back at the top; what is playing (title and episode) just above the timeline, where the eye already is; then the scrubber with chapter marks and the transport, on a frosted bar over a scrim. They fade out after 3.5 s of playback without input and return on any input. On a phone the title stays at the top beside the way back, clear of the transport in the middle of the picture. Chapters, audio, subtitles, and speed are one level deeper, in one menu with its siblings across the top. A skip button appears while the position is inside a named intro, recap, or credits chapter. While the controls show, subtitles move up above the bar instead of being read through it, and go back down when the controls leave. A failure replaces the controls with what happened and the way back.
- **Controller prompts** live in the navigation bar while browsing and in the player's top corner. On a phone, where the bar has no room for them, they float just above it, and pages end clear of both.
- **Media sources:** a page, not a console. One column of quiet cards, each with a name, what kind of thing it is (folder, Jellyfin, network share), and whether it is there; selecting one reveals where it is, who is signed in, when it was updated, and its actions (refresh or try again, sign in again or change, remove). A source that is not connected keeps its card, with a grey dot and a sentence about what to do. Adding media is a choice of three, then a form of three fields with the keyboard's own key going from one to the next; a problem is one sentence under the fields, and the field it is about gets a red edge. The same column serves every form factor; on a phone it is its own page with the way back in the corner. A server's libraries are one more page of the same kind: a row per library, included shown by a filled white mark and left out by an empty ring, with "Left out" in words; no color is needed to read it. While any of these pages is open, no section tab is lit; the media sources button is.
- **Kept on this device:** a small download-and-check mark before a card's caption, nothing on the artwork; on a details page, the action becomes a lit "Downloaded" toggle and one quiet line says "On this device" or how far a download is. The offline sheet shows what would be kept in the words of someone keeping it (resolution, HDR, size, where it comes from, why that one), never a codec. On a phone it rises from the bottom.
- **A source that is away:** "Not connected", in grey, on cards, details, and versions. "Offline" is not used for it, since a copy kept for offline is the opposite.
- **Phone:** navigation along the bottom; the player's play, back, and ahead in the middle of the picture, and one bar beneath with the timeline and the less used controls. With the controls away the picture is left alone, except for what has to be said at that moment (a skip offer, the next episode, a notice). Text follows the system's text size. A details page opens on its title and Play however short the screen (a phone on its side): there the header leaves out the poster, the genres, and the credit, and keeps the overview to two lines.
- **Type:** the platform's system sans-serif. A bundled typeface is a later decision with licensing attached.
