# Navigation and Source Libraries

The specification of M3 (ROADMAP.md), with the final structure M8 gave it: how the viewer moves through Reflux, and how the sources and libraries the unified library is made of are controlled. What was run to verify it is in VERIFICATION.md; the decisions behind it are in DECISIONS.md, 2026-11-02, "Navigation and source libraries", and 2026-11-11, "The navigation's final structure".

## 1. Global destinations

Four primary destinations, the library itself, and a **utility area**, the same on every platform. M8 settled this structure; M9 changes how it looks, never where anything is.

| Destination | Kind | What it is |
| --- | --- | --- |
| Home | Primary | The unified library's feed: Continue Watching, Next Up, rows |
| Movies | Primary | Every film in the unified library |
| Shows | Primary | Every show in the unified library |
| Search | Primary | Smart Search over the unified library |
| Downloads | Utility | Every copy kept on this device (only where the platform keeps copies) |
| Sources | Utility | Every source, its state, its libraries, inclusion, and the way into each source's own library |
| Settings | Utility | The open profile's own settings (its name, its PIN, clearing its watch history), the device's (online lookups, Media Atmosphere), and the way to About |
| Profiles | Utility | Who is watching: the profiles, switching, adding one, deleting another (docs/WATCH_STATE.md, "Profiles"). On a phone, also the way to Downloads, Sources, and Settings |

Each is a **section** of the navigator: choosing one replaces whatever was stacked above the previous section. Home is the root. Everything else (a work's page, a listing, a source's library, forms) stacks above the section it was opened from.

## 2. The back stack

`Navigator` keeps one stack: `Home`, then at most one other section, then pages opened from there.

- Back pops one page. From a section other than Home it returns to Home. At Home it returns false and the platform leaves the application (Android) or does nothing (desktop).
- Choosing a section clears the pages above the previous one. Choosing the section already shown returns to its top (its stacked pages are dropped).
- Panels (the offline sheet, the folder picker) and the player are not pages: Back closes them first, in that order: text field (desktop), folder picker, offline sheet, player (the player owns its own Back), then the stack.
- On a television, Back on a section's own page while focus is in the page moves focus to the navigation's current destination first; Back from the navigation then pops. This is the platform convention, and it means Back never leaves the application from the middle of a row.
- Each page keeps its scroll position, season choice, and the item it was left from (`rememberSaveableStateHolder` keyed by the page, and `rememberFocusReturn`), so returning to it finds it as it was.

## 3. Navigation by platform

The information architecture never changes; where the navigation sits follows the window (`AdaptiveLayout.navigation`):

| Window | Navigation | The utility area |
| --- | --- | --- |
| Phone, portrait | A floating bar along the bottom, as wide as its four destinations: Home, Movies, Shows, Search as icons with no permanent words | One entry at the top right of each section's page: the profile ("Profile and settings", or the viewer's initial once there are several), which opens Profiles, where Downloads, Sources, and Settings are |
| Phone, landscape | A compact rail on the left: the primary four as icons | One entry at the rail's foot, the profile, as when the phone is upright: a phone's short side has the height for the library and one more, not for eight (found by the M8 architecture challenge) |
| Tablet | A rail on the left: icons with their names beneath | At the rail's foot |
| Desktop | A sidebar on the left: the wordmark, then icons with their names beside | At the sidebar's foot |
| Television | A rail on the left, icons only, which widens to show names while it has focus | At the rail's foot |

The profile entry is named after the viewer once there are several profiles, and "Profiles" (or "Profile and settings" on a phone) while there is one. Not in 1.0: a collapsible desktop sidebar; minimizing the phone's bar on scroll, unless M9's accessibility review supports it (DECISIONS.md, 2026-11-11).

How each of these looks (M4, refined in M9): one family of functional material, one lit mark with a leading bar for where the viewer is, which travels to the destination they go to, Reflux's own lockup or mark at the head of a sidebar or rail, and, on capable devices, Home's hero and a work's header running under a rail or sidebar while every other page starts beside it. DESIGN.md, "Visual System 2.0 (M4)", "Navigation surfaces" and "Hero artwork and the navigation". None of it changes where the navigation is, what it holds, or how focus moves through it.

- Icons without words always say their names to assistive technology, with the role of a tab and whether they are selected; a pointer resting on one shows its name.
- Pages stacked above a section show a Back button at their top-left on touch and pointer layouts (a remote has its own Back).
- The library-activity sign and, with a controller in hand, the button prompts sit at the top-right of the page on rail and sidebar layouts.
- Directional input, wherever the navigation runs down the side (television, and a keyboard or controller on the others): Left from the page's first column enters the rail, on the current destination. A Left that the toolkit would send only partly left (to a button above, in a centred column) goes to the rail instead. Right from the rail returns to the control focus left from, whether it got there by Left or, on a television, by Back. Up and Down move one destination at a time, through the primary four and then the utility area, in that order; there are no other stops in the rail. A controller's bumpers step through the same list.
- Television: the rail widens over the page while it has focus, to show the names; its items stay the same focusable controls, so focus never drops as it widens. A page that arrives, or gains its main action, takes focus unless the viewer is already in the page.
- Desktop: Ctrl+1 to Ctrl+6 go to Home, Movies, Shows, Search, Downloads, and Sources, also from a text field; Escape is Back; the mouse's back button is Back on Windows; `I` puts the player in the mini-player and brings it back (section 8). Tab walks the page in the order it is built, then the sidebar drawn beside it.

## 4. The unified library and source libraries

- **The unified library** ("show me my media") is Home, Movies, Shows, Search, Continue Watching, Next Up, Watch Next, collections, and every work's page reached from them. It is made of the versions that are **included** (section 5).
- **A source library** ("show me what is on this source") is a page under Sources: one source, or one library of a source, its films and shows, whatever its inclusion. It reads the same index; nothing is copied into another store.

A work's page and playback know the scope they were opened in (`LibraryScope`):

| Scope | Versions considered | Seasons and episodes listed |
| --- | --- | --- |
| Unified | Included versions | Those with an included version |
| A source (or one of its libraries) | That source's versions (in that library), and copies kept on this device of them | Those with such a version |

Both scopes use the same Media Core identity, Best Version, planner, and engines. The same film on two included sources is one work in the unified library with two versions; on each source's page it is the same work with only that source's version. Watch state belongs to the work in every scope.

## 5. The source model

Four independent questions about every source:

| Concept | Stored as | Changed by |
| --- | --- | --- |
| **Connection**: can Reflux sign in? | `source.sign_in_required` | A scan the server refuses (its credentials no longer work); signing in again clears it |
| **Availability**: is it reachable now? | `source.availability` | Every check and scan |
| **Inclusion**: does it take part in the unified library? | `source.included`, `excluded_section` | Only the viewer |
| **Browsing**: can it be opened on its own? | — | Always, for every source; its library page says when it is away |

None of them changes another. A source that is away stays included; a source excluded stays connected and browsable; a source that needs signing in keeps its inclusion and its library.

### Inclusion rules

A version takes part in the unified library when:

```text
its source is included
and its library is not excluded (a source without libraries has one, implicitly)
```

A copy kept on this device follows the version it was copied from: it is included when that version's source and library are. A copy whose origin source has been removed is included.

- **Source switch (master).** Off excludes all of the source's libraries. Each library's own switch is kept as it was and shown dimmed, with "Off because the source is off", so turning the source on again restores exactly what was chosen.
- **Library switch.** Off excludes only that library. Every library may be off while the source is on: the source then contributes nothing, and its row says "No libraries included" rather than leaving it to be wondered about.
- **A newly found library** is included (automatic by default). A library that disappears from the server leaves the list and its works leave the library at the next scan, as any removed file does; its switch is remembered, so a library that comes back with the same identity keeps the viewer's choice.
- **A source with no libraries** (a folder, an SMB or WebDAV share) has only the source switch. A source with one library shows that library, with its switch, under the source switch.
- **Removing and adding again.** Removal forgets the source's inclusion with the source. Adding it again is an explicit act of wanting it, so it starts included, with every library on. Watch history is kept either way.
- **Changing inclusion** is not a rescan and touches nothing: no credential, cached metadata, artwork, watch state, file, or download changes. The index already holds every version; inclusion is recomputed in the same transaction and every screen reloads.
- Libraries left out before M3 (the earlier "Libraries" choice, which stopped reading them) become excluded libraries at start, and are read again so that they can be browsed (DECISIONS.md).

### Edge cases

| Case | Behavior |
| --- | --- |
| Connected but unavailable | Listed with "Can't be reached"; its works stay in the unified library if included (dimmed where unplayable); its library page lists its works with the same note |
| Excluded, opened from Sources | Its library page lists and plays its works; the unified library does not show them |
| Included, temporarily unreachable | Stays included; nothing about inclusion changes |
| No libraries (server with none of films or shows) | "No libraries with movies or shows" on its page; source switch still shown |
| One library / many libraries | Library rows under the source switch, each with its own switch and a way into its library |
| Source off with libraries on | Libraries shown dimmed and kept; restored by the source switch |
| Source on, every library off | "No libraries included" on its row; nothing in the unified library from it |
| Newly found library | Included |
| Removed library | Leaves the list and the library; its choice is remembered |
| Same work on several included sources | One work, several versions (unchanged Best Version) |
| Removed and added again | Starts included; watch history intact |
| Offline local folder | As unavailable above; "Its drive or folder isn't there right now" |
| Empty source | Its page says it has no movies or shows yet |
| Credentials no longer accepted | "Sign in again" on its row and its page; availability is unavailable; inclusion kept |

## 6. Sources

| Source | Kind | Libraries | Watch state | Plays through |
| --- | --- | --- | --- | --- |
| Local folder | Folder | None | Reflux's | Its files |
| Jellyfin | Server | The account's film and series libraries | Two-way | `PlaybackInfo` negotiation |
| Plex | Server | The server's film and show libraries | Two-way | Its files, direct (no server conversion in M3) |
| SMB | Share | None | Reflux's | The loopback stream server |
| WebDAV | Share | None | Reflux's | Its files |

**Extension point.** A new adapter implements `MediaSource` and one of `FileEnumeratingSource` or `CatalogSource`; if it has libraries, `SectionedSource` and `CatalogEntry.section`; if it is reached by address, a `SourceConnector`; and it is registered in each platform's factories and connectors. Inclusion, source pages, navigation, Best Version, and playback need nothing from it.

### Plex

- Connecting: the server's address, and either a plex.tv account (name and password: Reflux signs in once at plex.tv and keeps only the token), a Plex token in the password field with the name left empty, or nothing, for a server that lets the local network in without signing in.
- Identity: the server's machine identifier and the account, so adding it again is the same source.
- Libraries: the server's `movie` and `show` sections. Catalog: films and episodes per library, with their media, parts, and streams; shows for their names, years, and external identifiers.
- Playback: the part's file, direct, with the token in a header. Downloads: the same file.
- Watch state: viewed and position read on every scan and reconciled; progress, stop, played, and unplayed reported back (`/:/progress`, `/:/scrobble`, `/:/unscrobble`). Plex marks an item played past 90%, and keeps no position in an item about a minute long (seen on a real server).
- Titles: an item a server has no metadata agent for is titled after its folder ("Heat (1995)"); a trailing year is read as the year, as for Jellyfin, so the work unifies with other copies. A server in a container that calls itself by its container id is named "Plex".
- Not in M3: server-side conversion (the planner states what this device cannot play), plex.tv server discovery, PIN sign-in, and ending the device's plex.tv token on removal (the token is forgotten on the device). Recorded in ROADMAP.md.

## 7. Show, season, episode

A show's page lists its seasons as tabs and the chosen season's episodes. An episode plays from its row; one with several versions also opens its own page, with its versions, from the row. The page opens on the season of the episode to watch next. The season shown is the page's state: it survives the player, an episode's page, and Back. Play on a show plays its Next Up episode (or the first). Continue Watching and Next Up on Home open the show's page on that episode's season, and Play there continues it; Watch Next, from outside Reflux, opens the episode's own page and plays it. Next-episode autoplay and the player's episode list follow the scope the show was opened in.

## 8. The player

The player is a takeover above the current page, never a page itself. While it is open everything behind it is inert to touch, pointer, focus, and assistive technology. Closing it returns to the page it was opened from, which was never left: its scroll position and the focused item are where they were. Playback opened from a source library plays within that source's scope.

### The mini-player (M9)

Where a device has one, what is playing can step aside into the **mini-player** while the viewer browses: the same session, shown in the navigation's dock (docs/PLAYBACK.md, section 18; DECISIONS.md, 2026-11-12). In M9 that is the desktop; a television has none, and handhelds get theirs in M10.

| Window | The dock |
| --- | --- |
| Phone, portrait | A row above the bar: the picture, what is playing, play or pause, stop |
| Phone, landscape | The same row, in the page's bottom corner |
| Tablet, desktop | A card in the page's bottom corner: the picture above what is playing and its controls. A tablet window too narrow to give a card half its page (one of two side by side) has the row |
| Television | None |

- **Into it:** the player's "Mini player" button, or `I`. A player showing a failure stays. Fullscreen is left for the window the viewer had.
- **Out of it:** a click on the picture or on what is playing, the picture activated with a keyboard or a controller (it is a button), `I`, or Play on the same title. "Stop playing" ends playback and records the place.
- **While it plays there the page is the viewer's.** Navigation, focus, Back, and every key mean what they mean while browsing; nothing is inert. Media keys, mute, the volume keys, and `I` still reach what is playing. Back never ends it.
- **Its place is the navigation's.** Pages end clear of it and notices are said beside or above it. What covers the page (the offline sheet, a folder picker, the question of who is watching) covers it too, and takes its picture out of sight while the sound goes on.
- **One session.** Playing something else ends it first and the new playback has the screen. A profile switch, or removing the source it plays from, ends it first.
- **To assistive technology** it is one named part of the screen, "Mini player", with three stops in the page's focus order after the page: what is playing and how it stands (activating it returns to the player), play or pause, and stop. A failure and "Still watching?" are announced when they happen; nothing takes the screen to say them.
