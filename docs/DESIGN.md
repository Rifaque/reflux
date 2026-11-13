# Reflux Design Direction

## Identity

Reflux should combine:

- restrained, premium consumer-software ergonomics,
- cinematic media presentation,
- selective frosted glass for what floats above the media (DECISIONS.md, 2026-10-14, "Visual reset").

The goal is not to copy Apple, Netflix, Infuse, Plex, or any other product. Those products are references for principles, not visual templates. What Reflux takes from the best of them is restraint: media is the hero, artwork carries the color and the energy, and the interface stays quiet around it.

In one line: let the media be colorful; let the interface be calm; let depth come from layers, not decoration. Powerful underneath, calm on the surface. The interface is near-black, charcoal, graphite, soft grey, white, and selective frosted glass, with no color of its own. Artwork is the color on screen. With Media Atmosphere on (it is off by default) it may tint the page around it, but never the controls, the navigation, or focus.

## Visual System 2.0 (M4)

One canonical visual identity, dark and neutral-first. This section is what `design` and `ui/theme` implement; the decisions are in DECISIONS.md, 2026-11-07, "Visual System 2.0" and "One visual identity; themes are V2".

### Layers

Everything on screen belongs to one of four layers, and its layer decides how it may look:

| Layer | What | How it looks |
| --- | --- | --- |
| Media | Artwork, posters, thumbnails, video | Expressive: the only color on screen |
| Structure | The page, rows, grids, headings, text | Calm: the neutral palette, hairline edges, no boxes around text |
| Function | Navigation, bars, sheets, panels, notices, transient controls | Functional material (glass where the tier allows), above the media |
| Transient | What appears for a moment (hover labels, notices, a control being pressed) | Short motion, the strongest elevation |

### Tokens (`design`)

Components name a **role**, never a value. The roles are the extension point for any future appearance; there is exactly one appearance now.

| Kind | Tokens | Where |
| --- | --- | --- |
| Color | `ColorRoles`: background, backgroundElevated, surface, surfaceElevated, contentPrimary, contentSecondary, contentTertiary, contentDisabled, divider, focused, primaryAction and onPrimaryAction, progress, unavailable, error, success, warning, scrim, stateLayer. `ColorRoles.Reflux` is the identity; `ColorRoles.RefluxHighContrast` is the same identity answering the system's high-contrast setting | `Roles.kt`; `RefluxColors` |
| States | `StateLayers`: rest, hover, pressed, selected, on, active on glass, disabled. One table for every component, strengthened at high contrast | `Roles.kt`; `States` |
| Type | `TypeScale`: display (the hero title), pageTitle, sectionTitle, body, bodyStrong, metadata, mediaTitle, caption, utility, navigation, navigationCompact, tvLabel. Only the hero title changes with the window; a television sets the whole scale one step up | `Scale.kt`; `Typography` |
| Spacing | `Spacing`: 2, 4, 8, 12, 16, 24, 32, 48 dp | `Scale.kt` |
| Shape | `Radius`: small 8 (badges), medium 12 (media, navigation items), large 16 (rows, interactive surfaces), xlarge 24 (functional material); otherwise a pill | `Scale.kt`; `Shapes` |
| Depth | `Elevation`: flat, raised, floating, transient. Each step adds a tonal lift and a top-lit hairline before a shadow, so depth reads where a shadow cannot (over video, on black) | `Scale.kt` |
| Material | `MaterialRole` (base, elevated, floating, interactive, focused, modal), drawn per `MaterialTier` | `Material.kt`; `glass()` |
| Motion | `Motion` and `MotionProfile` (below) | `Motion.kt` |

Colors outside the roles are for media only (artwork, generated artwork, the video stand-in).

### Material tiers

Internal, never a setting, never a different identity: the hierarchy, roles, and semantics are the same in all three. `MaterialTiers.select` decides from what the device can do and what the viewer's system asks for.

| Tier | When | Functional material |
| --- | --- | --- |
| BASIC | No backdrop blur (Android before 12, so every Fire OS device), reduced transparency, or high contrast | Opaque neutral surface, a stronger hairline, a restrained shadow. Pages stay beside the navigation |
| ENHANCED | A television that blurs (Google TV on Android 12 and later), or a device the system calls low on memory | Frosted: it blurs what is behind it, its tint raised until secondary text holds 4.5:1 over bright artwork |
| PREMIUM | Phones, tablets, and desktops that blur | As ENHANCED, with a little more of the artwork through and a little more edge light, and its strength measured against the artwork actually beneath it (`MaterialBackdrop`), changing in a short transition |

Legibility is never traded for the look: every tier holds secondary text at 4.5:1 over any artwork (`MaterialTierTest`), and where glass would cost legibility the material is made stronger, up to opaque.

### Where glass goes

Glass is a functional material, not decoration. It is for the phone's bar, the rails, the sidebar, the television's rail while it is open, the player's bars and lists, sheets, panels, notices, hover labels, and glass buttons that sit on artwork. It is not for media cards, rows, posters, text, whole backgrounds, or every dialog. A television's rail is not glass while it is closed: it is its icons over a soft shade, so the page stays content first.

### Navigation surfaces

One family: functional material, one lit mark for where the viewer is, and focus as a ring. M9 refined it without moving anything (DECISIONS.md, 2026-11-12, "Navigation & Surface System 2.0").

- **Where the viewer is** is shown by shape and light together, never by a color or a brightness alone: one lit shape (`StateLayers.SELECTED`) drawn behind the destinations and, in rails and the sidebar, a short bar at its leading edge. It is the navigation's, not each destination's: going somewhere moves it there (`Motion.selectionTravel`), a destination the layout moves takes it along, and where the viewer is in none of them it fades away. Hover and focus are the quieter active state, each destination's own; focus adds the ring for input that needs one.
- **Phone, upright:** a floating capsule of FLOATING material above the gesture area, as wide as its four destinations (each a pill 64 dp wide) and no wider. The utility area is the one profile entry at the top right (M8).
- **Phone on its side, and tablet:** a floating rail of FLOATING material with xlarge corners; icons alone (compact) or with their names beneath (tablet), at the widths M3 set. A tablet's rail carries the mark at its head.
- **Desktop:** the same material as a sidebar: the Reflux lockup at its head, names beside the icons, and the utility area at its foot. The shell is the product's alone: RSX Develops is credited in About, on the website, and in the release and legal surfaces, not in the navigation.
- **Television:** closed, its icons over a shade that fades into the page, the mark quiet at its head; open, it widens to 208 dp over the page as FLOATING material with rounded trailing corners, the wordmark joining the mark and the names in the television label style. It widens in 180 ms; with motion reduced, at once.
- **The utility area** sits at the foot of a rail or sidebar beneath a hairline. Its profile entry is the viewer, by initial and name, once there are several.
- **The mini-player's dock** is this family's too: FLOATING material with xlarge corners, above the phone's bar or in the page's bottom corner, holding the picture, what is playing, and two controls (NAVIGATION.md, section 8). The picture is the platform's video surface, so nothing is drawn over it; its corners are `Radius.LARGE`, one step below the dock's (the dock's 8 dp padding keeps them concentric). On Android the surface itself is clipped to them (its outline, meeting the picture's edges inside the box, with the radius growing as the surface docks); the toolkit cannot clip what a surface puts on screen, so a rounded box drawn around it would show square video over it. On Windows the same radius is cut into the picture's native window as a window region (device pixels, the display's own scale, made again after every reshape) and an antialiased 1.5 dp rim is drawn over its edge by the controls window, because a region's edge is hard; elsewhere on the desktop the native window stays square (DECISIONS.md, 2026-11-13, "Windows rounds the desktop picture"). Menu option rows inside a modal panel are `Radius.LARGE` like other rows.
- **The brand** in the navigation is drawn from the brand geometry (`BrandMark`), in the interface's neutral, at the sizes design/brand/README.md allows; never a logo treatment on a page.

### Hero artwork and the navigation

On ENHANCED and PREMIUM, where the navigation runs down the side, a page that opens on artwork (Home's hero, a work's header) runs that artwork under the navigation: the page is the whole window, its content starts after the navigation (`ChromeInsets.contentStart`), and the navigation is a foreground layer of material over the artwork. The artwork's text scrim is darkest where the text starts and light beneath the navigation, whose own material keeps its labels readable. Every other page starts beside the navigation. On BASIC the hero stays beside it: an opaque rail over artwork would cut the artwork rather than float over it.

### Media Atmosphere

Off by default. The canonical page around artwork is neutral: artwork is the color, and its scrims dissolve into the plain page. Turned on (Settings, "Media Atmosphere"), Home's hero and a work's page wash the page around their artwork with its dominant hue as a deep, muted tone (`ArtworkAtmosphere`; primary text holds 7:1 on it), receding as the artwork scrolls away. It never tints controls, navigation, badges, or focus: functional material picks it up only through what it blurs. It is the only appearance choice Reflux has, and it is not a theme.

### Edge to edge

On Android the application is drawn under the status and navigation bars: the shell hands their sizes to the experience layer (`SystemBars`) rather than padding the application for them. Artwork reaches the top of the screen under a soft protection for the status bar's icons; pages start their content clear of the bars; the phone's bar, the rails, the top buttons, sheets, and notices sit inside them; only the keyboard still pushes the application up. The player keeps its own safe area. Televisions and desktop windows have no bars.

### Motion

| Motion | Duration | For |
| --- | --- | --- |
| `focusChange` | 140 ms | Focus and hover, a page leaving, a control pressed |
| `controlsShow`, `controlsHide` | 180, 240 ms | The player's controls, notices |
| `navigation` | 320 ms | A page arriving (desktop, handhelds) |
| `railExpand` | 220 ms (180 on a television) | A rail widening and narrowing |
| `materialChange` | 200 ms | Material changing strength |
| `artworkReveal` | 240 ms | Artwork arriving over its placeholder |
| `sheetEnter`, `sheetExit` | 260, 200 ms | Sheets and panels |
| `inputMorph` | 360 ms | The controller morph |
| `selectionTravel` | 260 ms | Where the viewer is, moving from one destination to another |
| `playerDock` | 280 ms | The mini-player's dock arriving; a video surface the toolkit composes travelling to it (a native one moves once) |

The question of who is watching arrives as a page does and leaves as one leaves, so the profile entry, the utility pages, and every other page share one movement.

`MotionProfile` applies it per form factor: a television's pages fade in place in 240 ms without travelling, its sheets fade, and its rail widens fastest. Nothing moves on its own: no background motion, no per-card animation, no staggering. With the system's reduce-motion setting every one of these takes no time and the end state is drawn as it would have been, so a state is never left ambiguous.

A pressed control dims briefly under the finger or the click. It never changes size: a control that shrank while pressed made the list that keeps focus in view scroll out from under the press (found by the UI tests).

### Accessibility in the visual system

- **High contrast** (Android's high-contrast text or contrast level, a Windows contrast theme, GNOME's high contrast): `ColorRoles.RefluxHighContrast` (brighter secondary and tertiary content, stronger hairlines and state layers), a 3 dp focus ring, and BASIC material.
- **Reduced transparency** (Windows' "Transparency effects" off): BASIC material, opaque, with pages beside the navigation. Android and GNOME have no such setting.
- **Reduced motion:** above. **Text size:** fixed heights are minimums (M1); the navigation stops growing at 1.5×.
- Nothing important rests on blur, transparency, color, shadow, or animation alone: selection is a pill and a bar, focus a ring, depth a tone and a hairline.

### Performance guardrails

- Blur only on functional material: a handful of surfaces per screen, none per list item. Material inside scrolling content is a tint alone; BASIC has no blur at all.
- A television's closed rail draws no material; the open one exists only while open.
- Artwork's pixels are read once per decoded image, for its hue and its brightness together (`ArtworkSamples`, held by a test), and not at all for the page's tone while Media Atmosphere is off.
- Lazy lists keep their stable keys; scroll position reaches drawing code as a lambda, so scrolling repaints the scrims and the wash without recomposing.
- The blur effect is remembered per radius rather than made on every frame.

### Themes

User-selectable themes are deferred past 1.0 (V2). M4 ships one canonical identity. The roles above are what a theme layer would provide another instance of; nothing in M4 depends on one.


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
- **Materials:** Base, Elevated, Floating, Interactive, Focused, and Modal, each with tint, backdrop blur, highlight, shadow, and corner radius, drawn per material tier (above). Blur applies to what is behind a material, never to its content.
- **Motion:** the vocabulary above; standard, emphasized, and exit curves; player controls auto-hide after 3.5 s.
- **Adaptive layout:** form factor (phone, tablet, desktop, TV) comes from the window and device; input modality only changes density, focus scale, target size, and button prompts. That is what makes the controller morph a transition rather than a mode. A television's canvas is 960 by 540 whatever its resolution, so the distance is already in its density: it keeps a 5% overscan-safe margin, shows six titles across, sizes controls to be read rather than hit, and sets type one step (1.1×) above the desk.
- **Artwork atmosphere** (what Media Atmosphere uses; off by default): a deterministic, saturation-weighted hue histogram over a sparse sample of the artwork picks its dominant hue, which becomes a deep, muted tone (`ArtworkAtmosphere`: dark enough for primary text at 7:1). Grey artwork has none. A work with a poster but no backdrop takes the poster's hue, both for the page and for the backdrop generated behind it (graphite for a grey poster), so the page never disagrees with the artwork on it; only a work without any artwork takes its title's hue.

## Rendering (`ui`)

- **Glass:** a material is a blurred copy of what is behind it, a tint, a top-lit hairline, and a shadow. Blur is real for materials that float above a screen (navigation bar, panels, notices): the screen is recorded as it draws and the material blurs that recording over an opaque base, so nothing sharp shows through. Materials inside scrolling content, and everything over video, use the tint alone.
- **Floating chrome is solid to the pointer.** A click, hover, or scroll on the navigation bar or a panel never reaches the page beneath it; a click outside an open panel closes it and goes no further.
- **The best real image for the frame, then the other one, then a designed one.** A poster card shows the poster, else the backdrop's middle; a wide frame the backdrop, else the poster where the frame is no wider than 16:9 (a phone's details header held upright, a wide card); a header with room for a poster shows it beside the title. An image that cannot be read now gives way to the next. The same rule (`ArtworkChoice`) on every form factor; only when no real image is left is the work generated. A television reads no images beside local videos (it does not ask for image access), so there a local work is pictured by provider artwork or generated.
- **No artwork is a designed state.** A work without artwork is drawn as its title on a color derived from the title, and Home opens on a generated backdrop, so a library with no metadata yet looks intentional rather than broken. A placeholder already says the title, so only the caption is repeated under it. Empty states (first run, an empty folder, a work that left the library) sit on a quiet ambient glow.
- **Grids use columns of exactly equal width,** so posters in a row are the same height and their captions share a baseline.
- **Artwork may tint the page, never the controls** (Media Atmosphere, off by default). With it on, Home's hero and a details page wash the page behind them with their artwork's atmosphere (a fifth of its strength over the base), down to the bottom of the artwork and fading out over the next 520 dp; the artwork's scrims end in the same tone, so the image dissolves into the page without a seam. The wash belongs to the artwork: it recedes as the artwork scrolls away, and rows, versions, and episodes further down sit on the plain base. Off, the scrims end in the plain page. Buttons, navigation, badges, and focus look the same on every page either way.
- **Buttons:** `PRIMARY` (bright neutral) for the one main action of a screen, whether that is playing, connecting, or choosing a folder; `GLASS` (frosted) for secondary actions that sit on artwork; `QUIET` for the rest. A screen has at most one `PRIMARY`.
- **Glass is for what floats.** The navigation, the player's bar and lists, panels, sheets, and notices are functional material (above); content is not put in glass cards, and rows of text sit directly on the page.
- **Edges are hairlines.** Posters, episode thumbnails, version rows, and badges are defined against the black by a hairline of white at 7–14%, never by a frame or a colored border.
- **Active state:** pointer hover and keyboard or controller focus are one "active" state shown by a small lift; a focus ring is added only for input that needs one.
- **Focus from across a room:** with a controller or a remote, what has focus is lifted, ringed in white with a sliver of the page between the ring and the control (so it reads the same around a white button as around a poster), and lit from behind by a soft glow that fades outwards. None of it is large: a focused poster grows by 8%, a button by less.
- **Versions answer "which one should I watch?"** Each copy leads with what it is chosen by (resolution, HDR, edition) in title type; its source follows, then its codecs and size, quietest. The copy Play will use is lifted out of the list (a lit surface, a white play disc, a "Recommended" badge) with Best Version's reason beneath it. A copy the user pinned says "Your choice" and "Play always uses this one", so a preference never looks like Reflux's guess; when it is set aside this time, the line under "Versions" says why. A copy of another cut says "Another cut" and its running time. An episode's versions are on its own page, reached from its row ("2 versions", beside it, beneath it on a phone) only when it has more than one.
- **Television:** cinematic at ten feet, effortless with ten buttons. The page is mostly artwork; glass is the navigation bar, the player's bar and lists, and panels, as elsewhere. Home moves by whole rows and fades what has gone up under the navigation instead of leaving it half showing. The player's bar is a timeline and the names of the lists beneath it; there are no buttons on a screen nobody can point at. A remote's keys say what they do, so no prompts are shown for it; a controller is still prompted in its own terms.
- **Player (M10): the picture is the interface and the controls are temporary.** The controls are one derived mode (docs/PLAYBACK.md, section 18): shown, hidden, loading, ending, failure. Shown, there is the way out at the top (Minimize, and Stop at the far end), what is playing, a light transport (glyphs of 40 to 48 dp in 72 to 84 dp targets, ten-second seeks as quieter glyphs, no discs, a soft shadow behind them for legibility), and one row at the bottom (timeline, times, and the actions) on a scrim of 32% of the height, not in a panel of glass; under 560 dp the actions wrap beneath the timeline. Loading is the Reflux loader and the way out, and nothing else: no stale pause button, no timeline. With reduced transparency or high contrast the row sits on a plate and the scrims are denser. Superseded by this: the next entry's older description of the bar.
- **Player chrome scales with the desktop window (M10).** Same design, responsive density (`design/PlayerChrome.kt`): a handheld's and a television's controls keep the sizes thumbs and distance gave them; a desktop's grow by the window's room in dp, whether it is a window or fullscreen. Compact (under 1200 x 700): today's sizes. Standard (from 1200 x 700): 44 dp buttons, a 48 dp play button, 14 sp clocks, a 32 dp timeline target. Large (from 1680 x 945, a 1080p screen at 100% or 1440p at 100 to 125%): 60 dp buttons, a 68 dp play button with a 36 dp glyph, 17 sp clocks and 40 sp title, a 44 dp timeline target, a wider bar (1760 dp, 64 dp margins). The timeline stays a thin line (4 to 5 dp, 6 to 8 dp when hovered or dragged) with a thumb shown at rest on desktop, a soft dark edge under it, a stronger inactive track (40%), and a target far larger than what is drawn. No plates, discs, or panels are added at any size.
- **Player (before M10):** the way back at the top; what is playing (title and episode) just above the timeline, where the eye already is; then the scrubber with chapter marks and the transport, on a frosted bar over a scrim. They fade out after 3.5 s of playback without input and return on any input. On a phone the title stays at the top beside the way back, clear of the transport in the middle of the picture. Chapters, audio, subtitles, and speed are one level deeper, in one menu with its siblings across the top. Which controls a device shows is decided in one place, `PlayerControls` (DECISIONS.md, 2026-10-20): the transport sits in the middle of the picture on a touch screen (phone and tablet), in the bar for a pointer or keyboard, and nowhere on a television; Previous and Next appear only when there is an episode that way; volume only where the device has no volume keys of its own; fullscreen only where it changes something; a phone keeps Audio and Subtitles as buttons and the other lists behind More. The ten-second seeks carry their number (↺10, ↻10). A skip button appears while the position is inside a named intro, recap, or credits chapter. While the controls show, subtitles move up above the bar instead of being read through it, and go back down when the controls leave. A failure replaces the controls with what happened and the way back.
- **Controller prompts** live in the navigation bar while browsing and in the player's top corner. On a phone, where the bar has no room for them, they float just above it, and pages end clear of both.
- **Media sources:** a page, not a console. One column of quiet cards, each with a name, what kind of thing it is (folder, Jellyfin, network share), and whether it is there; selecting one reveals where it is, who is signed in, when it was updated, and its actions (refresh or try again, sign in again or change, remove). A source that is not connected keeps its card, with a grey dot and a sentence about what to do. Adding media is a choice of three, then a form of three fields with the keyboard's own key going from one to the next; a problem is one sentence under the fields, and the field it is about gets a red edge. The same column serves every form factor; on a phone it is its own page with the way back in the corner. A server's libraries are one more page of the same kind: a row per library, included shown by a filled white mark and left out by an empty ring, with "Left out" in words; no color is needed to read it. While any of these pages is open, no section tab is lit; the media sources button is.
- **Kept on this device:** a small download-and-check mark before a card's caption, nothing on the artwork (an outline mark on a show of which only some episodes are kept); on a details page, the action becomes a lit "Downloaded" toggle and one quiet line says "On this device" or how far a download is. A show or a season is "Downloaded" only when every episode is, and otherwise says how many are ("3 of 30 episodes on this device"). Beside an episode, a round mark: an outline arrow to keep it, a thin ring around it while it downloads (it moves only with the download, never on its own), a lit check once it is kept, red when it did not finish. The offline sheet shows what would be kept in the words of someone keeping it (resolution, HDR, size, where it comes from, why that one), never a codec. On a phone it rises from the bottom.
- **A source that is away:** "Not connected", in grey, on cards, details, and versions. "Offline" is not used for it, since a copy kept for offline is the opposite.
- **Phone:** navigation along the bottom; the player's play, back, and ahead in the middle of the picture, and one bar beneath with the timeline and the less used controls. With the controls away the picture is left alone, except for what has to be said at that moment (a skip offer, the next episode, a notice). Text follows the system's text size. A details page opens on its title and Play however short the screen (a phone on its side): there the header leaves out the poster, the genres, and the credit, and keeps the overview to two lines.
- **Accessibility:** one set of rules in the shared components rather than descriptions scattered over screens (`components/Accessibility.kt`; DECISIONS.md, 2026-10-31). What is covered is out of the accessibility tree (`inert`); what opens says its name (`pane`); what happens by itself is said from a node that was already there (`announces`); a control says what it is, and only what is on says it is on. A card, a person in the cast, what is playing, and a field with its label are one stop each. What is for the eye alone (controller prompts, a pointer's label, the initials standing in for a face, the clocks beside a timeline that says the same) is not in the tree. With a screen reader on, the player's controls stay. With motion reduced, every state change takes no time and loading is a word rather than a spinner. Fixed heights are minimums, so large text grows a control instead of cutting it; only the navigation stops growing, at 1.5×, because its width is what has to fit. A finger gets 48 dp, a pointer the smaller targets it can hit.
- **Type:** the platform's system sans-serif. A bundled typeface is a later decision with licensing attached.

## Motion language (M7)

The Reflux mark is abstract on purpose: its meaning comes from motion. One small vocabulary is shared by the app, the Android launch screen, and the website, each built with its platform's own tools (Compose's animation APIs, Android's SplashScreen and animated vectors, CSS and the browser's View Transition API). A calm base, moments of movement, and the strongest movement around the product itself. Decision: DECISIONS.md, 2026-11-09, "The Reflux motion language".

| Kind | What it is | Where | Timing |
| --- | --- | --- | --- |
| **Brand** | The mark drawing itself along its centreline: the first pass flows in and turns (*flow, gather*), the return passes over it (*transform*), and the line settles into the solid mark with its over-under cut (*settle*) | Android 12+ launch screen (`drawable-v31/splash_mark`), the app's first run (`RefluxMarkDrawIn`), the website's hero and closing | About 1 s; ease-out `(0.2, 0.7, 0.2, 1)` for the line, a linear 220-260 ms settle |
| **Loading** | A length of the line flowing along the faint mark, again and again: media passing through | Genuine waits only: the player loading or buffering, a library being built, Home reading a large library on a cold start (`RefluxLoader`). Shown only after 400 ms, so a quick operation shows nothing; never a generic spinner | 1.5 s per pass |
| **Reveal** | Content rises 16-18 px and fades in, once, as it comes into view; groups stagger their children by 70 ms | Website sections | 720 ms, ease-out |
| **Depth** | A product surface comes into focus from a slight tilt, scale, and blur, then answers a fine pointer with at most 3 degrees | The website's hero render only | 1.1 s, then a 700 ms follow |
| **Media** | Several things becoming one, continuously: three copies of a film converge into one, which becomes Reflux's page for it | The website's "Every copy of a film is one film" | 1.5 s gather, 0.8 s settle |
| **Feedback** | Press scales to 0.98; hover lifts 2 px and brightens the edge | Buttons and cards | 100-160 ms |
| **Navigation** | Pages cross over quickly; the header holds still | Website (View Transitions; browsers without them navigate at once). The app keeps its existing page motion (`Motion.navigation`, the television's restrained profile) | 140 ms out, 260 ms in |

Rules:

- The product moves most; typography stays nearly still; body text is never animated while it is being read.
- Repeated items do not each perform; a group staggers once.
- Nothing waits for an animation: the launch screen is the platform's and leaves when the app is ready; the loader appears only for a real wait; every interaction answers immediately.
- **Reduced motion** (the system's setting on every platform, `prefers-reduced-motion` on the web): the finished state at once. The loader becomes the still mark and words; the draw-in shows the solid mark; the website shows every element in place and the convergence as its result.
- Performance: Compose animations run in the draw phase (no recomposition per frame); the website animates only transform, opacity, the hero's filter, and stroke-dashoffset, and loads no animation library.
