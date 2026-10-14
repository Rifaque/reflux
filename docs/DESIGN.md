# Reflux Design Direction

## Identity

Reflux should combine:

- restrained, premium consumer-software ergonomics,
- cinematic media presentation,
- liquid-glass materials.

The goal is not to copy Apple, Netflix, Infuse, Plex, or any other product. Those products are references for principles, not visual templates. What Reflux takes from the best of them is restraint: media is the hero, artwork carries the color and the energy, and the interface stays quiet around it.

In one line: premium, cinematic, restrained, liquid glass, with one warm amber signature. Powerful underneath, calm on the surface. The screen is overwhelmingly near-black, charcoal, white, soft grey, and neutral glass; amber appears only where it means something.

## Material system

Liquid glass is a system, not a blur filter.

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

- **Palette:** a very dark, neutral base (no tint of its own), so that artwork is the color on screen; text tokens are tested to meet WCAG AA on every surface, and glass materials raise their tint over bright artwork until text stays at 4.5:1. The interface brings one color of its own, a warm amber.
- **Where the amber goes:** what is playing or about to (the Play and Resume button, first run's one call to action), progress (on a poster, a details page, the player's timeline), and the rare thing worth pointing out (the recommended version). Nowhere else: the section the user is in is white on a soft pill, switched-on toggles are a lit surface, the main action of a form or a sheet is solid white, menus mark the chosen option in white, technical badges and headings are neutral, and the focus ring stays white. If something can be said in white and grey, it is.
- **Type carries the hierarchy:** size and tone, not capitals, boxes, or rules. A title needs no label above it; labels are sentence case; metadata and supporting text are quieter than what they describe.
- **Materials:** Base, Elevated, Floating, Interactive, Focused, and Modal, each with tint, backdrop blur, highlight, shadow, and corner radius. Blur applies to what is behind a material, never to its content.
- **Motion:** standard, emphasized, and exit curves; focus changes 140 ms, navigation 320 ms, poster-to-details 420 ms, input morph 360 ms; player controls auto-hide after 3.5 s.
- **Adaptive layout:** form factor (phone, tablet, desktop, TV) comes from the window and device; input modality only changes density, focus scale, target size, and button prompts. That is what makes the controller morph a transition rather than a mode. A television's canvas is 960 by 540 whatever its resolution, so the distance is already in its density: it keeps a 5% overscan-safe margin, shows six titles across, sizes controls to be read rather than hit, and sets type one step (1.1×) above the desk.
- **Artwork accent:** a deterministic, saturation-weighted hue histogram picks an accent from artwork; it is adjusted until text on it meets AA, and grey artwork keeps the default accent.

## Rendering (`ui`)

- **Glass:** a material is a blurred copy of what is behind it, a tint, a top-lit hairline, and a shadow. Blur is real for materials that float above a screen (navigation bar, panels, notices): the screen is recorded as it draws and the material blurs that recording over an opaque base, so nothing sharp shows through. Materials inside scrolling content, and everything over video, use the tint alone.
- **Floating chrome is solid to the pointer.** A click, hover, or scroll on the navigation bar or a panel never reaches the page beneath it; a click outside an open panel closes it and goes no further.
- **No artwork is a designed state.** A work without artwork is drawn as its title on a color derived from the title, and Home opens on a generated backdrop, so a library with no metadata yet looks intentional rather than broken. A placeholder already says the title, so only the caption is repeated under it. Empty states (first run, an empty folder, a work that left the library) sit on a quiet ambient glow.
- **Grids use columns of exactly equal width,** so posters in a row are the same height and their captions share a baseline.
- **The accent is the same everywhere.** Play is amber on every page; a page does not recolor its controls after its poster. Artwork colors the page behind them, which is where it belongs, and the one constant, rare color is part of what makes a screen recognizably Reflux.
- **Buttons:** amber (`PRIMARY`) only for playing; solid white (`SOLID`) for the main action of everything else (connect, download, use this folder, the player's "Play now"); glass and quiet for the rest. A screen has at most one of the first two.
- **Glass is for what floats.** Navigation, the player's bar, panels, and notices are glass; content is not put in glass cards, and rows of text sit directly on the page.
- **Active state:** pointer hover and keyboard or controller focus are one "active" state shown by a small lift; a focus ring is added only for input that needs one.
- **Focus from across a room:** with a controller or a remote, what has focus is lifted, ringed in white, and lit from behind by a soft glow that fades outwards. The three together read from a sofa where a hairline alone does not, and none of them is large: a focused poster grows by 8%, a button by less. Selection stays amber and focus stays white, so the two are never confused.
- **Television:** cinematic at ten feet, effortless with ten buttons. The page is mostly artwork; glass is the navigation bar, the player's bar and lists, and panels, as elsewhere. Home moves by whole rows and fades what has gone up under the navigation instead of leaving it half showing. The player's bar is a timeline and the names of the lists beneath it; there are no buttons on a screen nobody can point at. A remote's keys say what they do, so no prompts are shown for it; a controller is still prompted in its own terms.
- **Player:** title, scrubber with chapter marks, and transport controls over scrims; they fade out after 3.5 s of playback without input and return on any input. Chapters, audio, subtitles, and speed are one level deeper, in one menu with its siblings across the top. A skip button appears while the position is inside a named intro, recap, or credits chapter. A failure replaces the controls with what happened and the way back.
- **Controller prompts** live in the navigation bar while browsing and in the player's top corner. On a phone, where the bar has no room for them, they float just above it, and pages end clear of both.
- **Media sources:** a page, not a console. One column of quiet cards, each with a name, what kind of thing it is (folder, Jellyfin, network share), and whether it is there; selecting one reveals where it is, who is signed in, when it was updated, and its actions (refresh or try again, sign in again or change, remove). A source that is not connected keeps its card, with a grey dot and a sentence about what to do. Adding media is a choice of three, then a form of three fields with the keyboard's own key going from one to the next; a problem is one sentence under the fields, and the field it is about gets a red edge. The same column serves every form factor; on a phone it is its own page with the way back in the corner. A server's libraries are one more page of the same kind: a row per library, included shown by a filled white mark and left out by an empty ring, with "Left out" in words; no color is needed to read it. While any of these pages is open, no section tab is lit; the media sources button is.
- **Kept on this device:** a small download-and-check mark before a card's caption, nothing on the artwork; on a details page, the action becomes a lit "Downloaded" toggle and one quiet line says "On this device" or how far a download is. The offline sheet shows what would be kept in the words of someone keeping it (resolution, HDR, size, where it comes from, why that one), never a codec. On a phone it rises from the bottom.
- **A source that is away:** "Not connected", in grey, on cards, details, and versions. "Offline" is not used for it, since a copy kept for offline is the opposite.
- **Phone:** navigation along the bottom; the player's play, back, and ahead in the middle of the picture, and one bar beneath with the timeline and the less used controls. With the controls away the picture is left alone, except for what has to be said at that moment (a skip offer, the next episode, a notice). Text follows the system's text size.
- **Type:** the platform's system sans-serif. A bundled typeface is a later decision with licensing attached.
