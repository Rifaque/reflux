# F-P4: Adaptive Virtual Keyboard and Cross-Modality Layout Morphing

**Status:** Not Started. Phase 3 (the owner's steer, 2026-11-20). Research and plan only; nothing is implemented. Implementation waits for Phase 3's authorisation.
**Tracker:** [BETA-BUG-TRACKER.md, F-P4](BETA-BUG-TRACKER.md#f-p4). **ID:** F-P4 (F-P3 is Media Quick View).
**Related:** B-023 (the Reflux keyboard), B-051 (the desk keyboard button, modality visuals), B-054 (cursor and held erase), B-004/B-011 (remote and controller text entry), B-025 (the input contract), B-057 (Search), INPUT_MODEL.md ("Text entry by input", "Controller mapping").

This document separates **evidence** (what was measured or found, with its source) from **recommendations** (Reflux's design calls), and says which is which.

---

## 1. Problem statement

Reflux has one on-screen keyboard: a uniform grid built for a D-pad. It is reused as-is for a mouse on Windows and Linux (B-051), where only the lit key and the press feedback differ.
- **For a mouse** it does not look or behave like a keyboard: equal-width keys, no stagger, no wide Shift, Backspace or Enter.
- **For a controller** it works and is tested, but it was never designed against evidence: the number row sits above the letters, the actions sit at the far bottom, and nothing wraps.
- **When the hand changes** (a controller put down for the mouse, or the reverse), nothing adapts while the keyboard is open.

The goal: **one keyboard system** with layouts for each kind of input that **transform into each other live**, keeping everything typed.

---

## 2. Audit of what exists (2026-11-20, on `open-source-gpl`)

| Part | Where | What it does | Keep / change |
| --- | --- | --- | --- |
| Key model | `ui/state/Keyboard.kt`: `KeyboardKey` (`Type(text, weight)`, `Shift`, `Page`, `Space`, `Erase`, `Clear`, `CursorBack`, `CursorForward`, `Done`) | Keys with a width *weight* in their row | **Keep the identities.** Add a stable `KeyId` and the layout-only keys (`Tab`, `Enter` as Done, `Caps`). Move geometry out of the key (§6) |
| Layouts | `KeyboardSession.layout(kind, page)` | Rows of keys per input kind (text, name, address, secret, PIN) and page (letters, symbols); a digits row on top; actions on one bottom row | **Replace** with layout *data* per modality (§4, §5), still per kind and page |
| Session | `KeyboardSession` | Text via the field's `KeyboardRequest`; the cursor (B-054: its own record, kept across a field's late hand-back); `ShiftState` OFF/ONCE/LOCKED; page; the chosen key (row, column); `handle(action)` → HANDLED/EDGE/DONE; `press`, `choose` | **Keep**: state is already modality-free. The chosen key becomes a `KeyId` instead of (row, column) |
| Navigation | `KeyboardSession.move` | Up/Down picks the key nearest in x (`middleOf`); Left/Right stop at row ends (EDGE: a boundary rumble) | **Replace** with geometric navigation over the layout's rectangles, with wrap rules (§4.4) |
| View | `ui/components/RefluxKeyboard.kt` (`RefluxKeyboard`, `Preview`, `KeyCap`) | Rows of `weight`-sized boxes; the chosen key lit under directional input or on a TV; a pointer's press sinks and brightens briefly, hover lightens (B-051); `key:<name>` tags | **Replace the layout** with an absolute-positioned, animated key field (§6). Keep `Preview`, the tags, and the press and hover feedback |
| Opening | `Modifier.textEntry` (`components/TextEntry.kt`), `FieldKeyboard`, `AppState.openKeyboard/closeKeyboard/pressKey/typeOnKeyboard`, `repeatsWhileHeld` | The keyboard opens by confirm/OK on a field, or by the desk button (B-051); it is first in input priority; held X and LB/RB repeat | **Keep** |
| Modality | `core/input/InputModalityTracker` | POINTER after 24 dp of pointer travel or a click; DIRECTIONAL on a gamepad button, or a stick at half deflection or more; TOUCH; KEYBOARD | **Extend** with keyboard-scoped arbitration (§7). The global tracker stays |
| Controller mapping on the keyboard | `InputMapping` `TEXT_ENTRY` | A types; B closes; X erase; Y space; LB/RB cursor; LT Shift; RT symbols; Start done | **Keep**: it matches the console convention (§3.4) |
| Motion | `design/Motion.kt`, `LocalMotion` | Durations by form factor; reduce-motion makes motion near-instant | **Use** for the morph's timing and its reduced-motion form |
| Tests | `KeyboardSessionTest`, `InteractionContractTest`, `PointerKeyboardTest`, `KeyboardTest`, `TelevisionTest` | The rules without a screen; controller, remote and mouse typing; modality visuals | **Keep, extend** (§10) |

**Shared already:** text, cursor, Shift, page, key identity (largely), special-key behaviour, accessibility names (`keyName`), the session's input handling.
**Tied to one input:** the row/column chosen-key model and the row-weight geometry (directional navigation); the hover and press visuals (pointer).

---

## 3. Research

### 3.1 Sources

| # | Source | Kind |
| --- | --- | --- |
| R1 | M. Ngo Van, T. Költringer, T. Grechenig, "Game controller text entry with alphabetic and multi-tap selection keyboards", CHI 2007 EA (TU Wien) | Peer-reviewed study |
| R2 | A. D. Wilson, M. Agrawala, "Text entry using a dual joystick game controller", CHI 2006 (Microsoft Research) | Peer-reviewed study |
| R3 | I. S. MacKenzie et al., soft-keyboard studies (1994 *Human Factors*; 1999 *BIT*; 2001), yorku.ca/mack | Peer-reviewed studies |
| R4 | T. Költringer, M. Ngo Van, T. Grechenig, "TwoStick", 2007 (TU Wien) | Peer-reviewed study |
| R5 | "Towards better gamepad text input", Game Developer | Practitioner analysis |
| R6 | Engadget: Microsoft testing a controller-optimised on-screen keyboard in Windows 11 (Insider build) | Platform report |
| R7 | Android Developers: TV on-screen keyboard (Gboard on TV); TV controllers (D-pad support) | Platform documentation |
| R8 | Xbox Accessibility Guidelines 101 (contrast for input text), 107 (input), 119 | Platform guidelines |
| R9 | AndroidX: `Modifier.animateBounds` (LookaheadScope); `SharedTransitionScope` | Framework documentation |
| R10 | Xbox 360 keyboard settings (an ABC default, with a QWERTY option added) | Platform history |
| R11 | J.-M. Santala, "Many designs of on-screen inputs" (game and console keyboard survey) | Practitioner survey |

### 3.2 Evidence: controller text entry

- **Selection keyboards are slow everywhere.** Controller selection keyboards run at about 5–13 wpm:
  - R1: alphabetic, 7.7 wpm for novices and 11.9 for experts;
  - R4: a QWERTY selection keyboard at 5.6 wpm early and 12.9 wpm late;
  - R2: dual-stick keyboards only modestly faster (about 5.8 → 6.4 wpm, as cited in R5).
  - The ceiling is the D-pad hop count, not the layout's familiarity.
- **Alphabetic vs multi-tap (R1):** alphabetic was faster for novices and for experts. R1 does **not** compare alphabetic with QWERTY on a controller. No head-to-head controller QWERTY-vs-ABC study was found.
- **Stylus soft keyboards (R3):**
  - ABC tapping had the lowest error rate (0.6%) but was the slowest method in the 1994 study.
  - Expert gains from non-QWERTY layouts need substantial practice.
  - Walk-up (novice) performance depends on visual search, which familiarity helps.
- **Industry convergence (R5, R10, R11):**
  - The Xbox 360 shipped an **alphabetic default** and later added QWERTY. Current consoles (Xbox, PlayStation), Android TV's Gboard (R7) and Windows 11's new controller keyboard (R6) use **QWERTY**.
  - Alphabetic grids survive in games and some TV apps. R11 notes that wide alphabetic rows (2 × 14) cost more horizontal movement.
- **Button shortcuts (R6):** Windows 11's controller keyboard maps X = Backspace, Y = Space and a trigger to symbols, and aligns keys in columns ("vertically aligned") for D-pad travel. Reflux already has X erase, Y space, LB/RB cursor, LT Shift, RT symbols and Start done (B-023, Phase 2).
- **Radial and dual-stick designs (R2, R5):** the daisy wheel and stick-direction mapping are fast once learned, with a learning curve, a large graphic and an unfamiliar gesture model (R5, R11).
- **TV guidance (R7):** D-pad support is required. Typing with a D-pad is tiresome; remote voice or phone entry are suggested elsewhere.
- **Accessibility (R8):** input text needs 4.5:1 contrast (3:1 for large text), and speech or dictation should be supported where possible.

### 3.3 Analysis for Reflux's use

Reflux's text is short: search terms (2–15 characters), titles, names, server addresses (`http://192.168…`), user names and PINs (their own number pad).
- **Familiarity versus hops.**
  - **Familiarity:** for short, infrequent entry, walk-up visual search dominates, and QWERTY's familiarity is the strongest walk-up aid for most people (R3, R10, and the industry's shift).
  - **Hops:** a uniform, **column-aligned** QWERTY grid makes Up/Down predictable (R6). A staggered grid makes vertical moves ambiguous for a D-pad.
- **Digits matter here:** years in titles ("2049") and addresses. They should stay one move from the letters, not behind a page.
- **The actions travel most.** Space, Erase and Done are the most common non-letters. Placed at the far bottom row they cost 3–4 hops from the top letter row, but beside the letters they cost 0–2. With the X/Y/Start shortcuts many people never travel to them; the keys still have to be there for remotes, which have no X/Y.
- **Wrap-around** Left/Right within a row saves hops to the far side (R5 notes it helps mostly once a layout is learned). On QWERTY, which is learned, it helps.

### 3.4 Concepts evaluated (controller)

| Concept | Description | For | Against | Verdict |
| --- | --- | --- | --- | --- |
| **A. Refined uniform QWERTY** | Today's grid, better ordered: digits, three QWERTY rows, actions row | Familiar; already tested | Actions far from letters; no wrap; digits row adds height | Better than today; not best |
| **B. Alphabetic grid** (e.g. 6 × 5 + digits) | a–z in reading order | Scannable for non-typists; R1 shows it is good for novices against multi-tap | No controller evidence against QWERTY; industry moved away (R10); its order is unfamiliar to typists, and Reflux's users type | Not as the default. Possible as an accessibility option (open question Q1) |
| **C. Hybrid: column-aligned QWERTY with an action column** | QWERTY letter rows, column-aligned; digits row on top; a **right-hand action column** (Erase, Space, Done) beside the letters; a short **bottom row** (Shift, symbols, ←, →, Clear); horizontal wrap; console shortcuts unchanged | Familiar letters; actions 0–2 hops from any letter; predictable verticals; shortcuts make the column optional for controllers and convenient for remotes | Not a "physical" keyboard picture (it is not meant to be: that is the mouse layout's job) | **Recommended** |
| Daisy wheel or dual-stick | Radial or split selection | Fast for experts | Learning curve; conflicts with the right stick's reservation (Trickplay); unfamiliar | Rejected |

### 3.5 Evidence: desktop (mouse) virtual keyboards

- Conventional on-screen keyboards (Windows' On-Screen Keyboard, macOS Keyboard Viewer, GNOME's) draw the **physical** keyboard: staggered rows (offsets of about 0.5u and 0.25u), wide modifiers, a number row and a long space bar, so typists' spatial memory transfers.
- **Pointing (Fitts):** the time to hit a target falls with its size. Desktop pointer targets of about 32 dp and up are comfortable. The physical keyboard's proportions give the most-used keys (Space, Backspace, Enter, Shift) the largest targets.
- **Hover** previews what a click will do. **Press feedback** should be immediate and transient. A *persistent* selection is a controller idiom that confuses a mouse user (the owner's B-051 finding).

---

## 4. Recommended controller layout (Concept C)

### 4.1 Letters page (text, search, name)

```
 1   2   3   4   5   6   7   8   9   0  │ ⌫ Erase
 q   w   e   r   t   y   u   i   o   p  │ ␣ Space
 a   s   d   f   g   h   j   k   l   '  │ ✓ Done
 z   x   c   v   b   n   m   ,   .   -  │
 ⇧ Shift   &123   ←   →   Clear          │
```

- Ten equal columns, aligned, so Up and Down keep the column.
- The action column is three keys at 1.5u, each spanning one letter row. Right from any letter's last column reaches it; Left from it returns to the row's end.
- Address kind: `:` in place of `'`, `/` in place of `,`, and the action column gains `http://` and `.com` keys in its free fourth slot. That replaces today's `http://` in the bottom row.
- Symbols page: the same grid with symbols in the letters' places; digits stay.
- PIN: the number pad, unchanged.

### 4.2 Focus visual

The chosen key keeps today's **lit fill** (white, dark glyph) and a **focus scale of 1.06**. It is never shown under a pointer.

### 4.3 Sizes

- TV: 10-foot sizes (keys of 48 dp or more, labels at the TV body size).
- Desktop and tablet under a controller: 44 dp keys.

The keyboard docks at the bottom and leaves the field and its results visible (today's behaviour).

### 4.4 Navigation rules

- Left/Right **wrap within a row**, the action column counting as the row's last stop. A wrap gives no boundary rumble; Up/Down at the top or bottom edge do.
- Up/Down move to the key whose centre is nearest in x in the next row; the action column maps by row.
- Shortcuts are unchanged (INPUT_MODEL.md): X erase, Y space, LB/RB cursor, LT Shift, RT symbols, Start done, B close. Held X and LB/RB repeat (B-054).

### 4.5 Option (open question Q2)

A row of three **title completions** from the library above the digits, reached by Up. For short title searches this saves the most hops of anything here. It reads the search index and needs no network.

---

## 5. Recommended mouse layout (Windows and Linux desktop only)

An ANSI-style physical picture. Unit u ≈ 40 dp on a desktop window; the minimum key is 36 dp.

```
 `  1  2  3  4  5  6  7  8  9  0  -  =  [ ⌫ Backspace 2u ]
 [Tab 1.5u] q  w  e  r  t  y  u  i  o  p  [  ]  [ \ 1.5u ]
 [Caps 1.75u] a  s  d  f  g  h  j  k  l  ;  '  [ Enter (Done) 2.25u ]
 [⇧ Shift 2.25u] z  x  c  v  b  n  m  ,  .  /  [ ⇧ Shift 2.75u ]
 [&123 1.5u] [ ← ] [ →  ] [        Space 6u        ] [Clear 1.5u] [Hide 1.5u]
```

- **Stagger:** 0 / 0.5 / 0.75 / 1.25u, as on a physical board.
- **Shift** is one-shot and **Caps** locks, mapping onto the session's ONCE and LOCKED. **&123** shows symbols in the letter positions, like a phone's symbols page; shifted digits give the familiar `!@#…` as well.
- **Enter** is Done: the field's own action. **Tab** goes to the next field where the request has one (Connect, Identify), and is hidden otherwise. **Hide** closes the keyboard.
- **Hover:** a light layer. **Press:** a 60 ms sink to 0.93 and back over 140 ms (B-051's feedback, kept). **No persistent chosen key.**
- The keyboard is a floating panel under the field, sized to its content (about 15 × 5u), not edge to edge.

---

## 6. Architecture: one keyboard, layouts as data

- **`KeyId`:** a stable identity per key:
  - `Char(c)` for every letter, digit and symbol;
  - `Space`, `Erase`, `Shift`, `Caps`, `Symbols`, `CursorBack`, `CursorForward`, `Clear`, `Done`, `Tab`, `Hide`;
  - `Literal("http://")`, `Literal(".com")`.

  Behaviour hangs on `KeyId`. Mouse-layout `Enter` is `Done`, and `Backspace` is `Erase`.
- **`KeyboardLayout`:** pure data for a modality, input kind and page. Each key is a `KeyId` with a rectangle in key units, and the layout has an overall size in units. It is built once per (modality, kind, page) and tested without a screen.
- **`KeyboardSession`** (kept) holds the text, cursor, Shift, page and **the chosen `KeyId`**, not (row, column). Navigation is geometric over the current layout's rectangles (§4.4).
- **The view**, an `AdaptiveKeyboard` replacing the rows in `RefluxKeyboard`, is one custom `Layout` placing each key at its rectangle, scaled by the unit for the form factor.
  - Keys are composed by `key(KeyId)`, so a key keeps its node and its animation state when the layout changes.
  - Each key animates its own rectangle (§8).
- **Modality** for the keyboard comes from `KeyboardMode` (§7): CONTROLLER or POINTER. TOUCH stays the system IME on Android and is not part of this feature.

---

## 7. Modality detection and arbitration (keyboard-scoped)

The global `InputModalityTracker` stays. While the keyboard is open, a **`KeyboardModeArbiter`** derives the layout mode with hysteresis, so incidental input never rearranges keys under someone.

| Signal | Effect while the keyboard is open |
| --- | --- |
| A gamepad button, the D-pad, or a stick at ≥ 0.5 | **CONTROLLER** at once. The press is also acted on: a D-pad press moves from the mapped focus (§9), and A types the mapped key **only if** that key was already visible as chosen before the switch. Otherwise the first press just shows the focus (no unintended key press) |
| A mouse click on a key | **POINTER** at once. The click types **the key under the pointer as drawn when it was pressed** (hit-tested against the current, pre-morph geometry), then the morph runs |
| Mouse movement | POINTER only after **≥ 48 dp of travel within 400 ms** that ends **over the keyboard panel**. A nudge of the desk, or travel elsewhere on screen, does not switch |
| Mouse wheel | No switch |
| Physical keyboard typing | No switch. Typing goes to the field, as now; the virtual keyboard stays as it is |
| Touch (Windows touchscreen) | POINTER (the mouse layout; a finger on a desk screen is a pointer here) |
| Remote D-pad (a TV) | CONTROLLER; a TV has no pointer layout |

- **Hysteresis:** after a switch, a *motion-only* signal for the other mode (pointer travel, or a stick under 0.8) is ignored for **500 ms**. Explicit acts (a click, a button press) always win at once.
- **No flicker:** a switch already under way is retargeted, never restarted (§8). Two switches within one frame resolve to the last explicit act.
- **Input priority is untouched.** The arbiter changes presentation only; text-entry priority and the B-019 to B-021 rules stay.

---

## 8. The morph: animated transformation between layouts

- **Approach (recommended): a custom per-key animation, not shared-element APIs.**
  - Each key holds an `Animatable<Rect>` (in pixels) keyed by `KeyId`. When the target layout changes, every key present in both animates from its current rectangle to its new one.
  - This works the same on every Compose target and needs no experimental API. It is interruptible: retargeting from the current value avoids jumps.
  - It also leaves the keys' nodes, and with them focus and semantics, in place.
  - **Alternative, kept in reserve:** `LookaheadScope` + `Modifier.animateBounds` (R9). It is still `@ExperimentalSharedTransitionApi`, and its availability and behaviour in Compose Multiplatform 1.12 on desktop and Android must be confirmed first. It would replace only the per-key animation, not the data model.
- **Timing (initial targets):**
  - position and size: a spring with damping ratio 0.9 and stiffness about 500, settling in about 300 ms with no visible overshoot;
  - the container's size on the same spring;
  - corner radius and label size interpolate with position.
- **Keys in both layouts** (letters, digits, Space, Erase, Shift, ←, →, Clear, Done/Enter, &123) slide and resize to their new places. All move together; nothing is sequenced.
- **Keys in one layout only:**
  - leaving keys (mouse: `` ` ``, `-`, `=`, `[`, `]`, `\`, `;`, `/`, Tab, Caps, Hide, the second Shift; controller: the action column's labels and `http://`) fade to 0 and scale to 0.92 over 120 ms, starting at once, so they are gone before others pass through them;
  - entering keys fade in and scale from 0.92 over 180 ms, **starting at 120 ms**, so they appear as their neighbours settle and never sit in a gap or under a moving key.
- **No overlap:** moving keys are drawn above static and fading ones; a key's label is clipped to its rectangle while it moves; rows of the two layouts are ordered alike, so paths cross little.
- **Usable throughout:**
  - input is accepted during a morph: clicks hit-test the *current* animated geometry, and D-pad moves use the *target* geometry;
  - typing is never delayed;
  - the preview of the text never moves.
- **Reduced motion** (system setting, via `LocalMotion`): a 150 ms crossfade of the whole panel between layouts, with nothing sliding.
- **Performance:** about 60 animated keys, each moved by one placement lambda with no recomposition per frame (positions read in the layout or placement phase). To be measured on the Fire TV Stick (the weakest target) under B-041's method.

---

## 9. State preservation and focus mapping

Never lost on a switch: the text, cursor, selection (none today), Shift (ONCE or LOCKED; mouse Caps ⇔ LOCKED), the symbols page, the keyboard's visibility, the field's focus, the query, the destination field.
- **Controller → mouse:** the chosen key is remembered as `lastControllerKey`; the mouse layout lights nothing.
- **Mouse → controller:** focus goes to the **last key clicked**, if it exists in the controller layout. If not, to `lastControllerKey`. If neither, to the controller key whose centre is **nearest to the last hovered key's centre**, in normalised layout coordinates. Never the first key.
- **A key unique to the destination** is never chosen by mapping; a mapping that lands on a key unique to the source layout uses that key's nearest shared neighbour.
- Switching never types, closes, refocuses the page, duplicates or drops a character: keys are pressed only by explicit acts (§7).

---

## 10. Platform scope

| Platform | Controller layout | Mouse layout | Notes |
| --- | --- | --- | --- |
| Windows, Linux desktop | Yes | **Yes** | Live morph between the two |
| Android phone and tablet | Yes (with a controller) | No | Touch keeps the system IME (no change) |
| Google TV, Android TV, Fire TV | Yes (remote and controller) | No | 10-foot sizes |
| macOS (not shipped) | — | — | Not in scope |

---

## 11. Accessibility

- Keys keep their names (`keyName`) and roles. Focus order follows the visual layout in each mode. A screen reader announces a layout change once ("Keyboard, mouse layout") and never per key.
- Contrast: key labels and input text at 4.5:1 or better (R8). The lit key's glyph is dark on light.
- Reduced motion (§8). Large text: key labels scale with the system's text size up to the key's height, and the unit grows on a TV.
- Speech and dictation: out of scope here (open question Q4).

---

## 12. Acceptance criteria and test matrix

**Controller.**
- [ ] The keyboard opens.
- [ ] D-pad and stick navigation follow §4.4, wrap included.
- [ ] Focus is always visible.
- [ ] Every key works, and every shortcut too.
- [ ] No focus trap and no accidental shortcut.
- [ ] Ten-foot legibility on a TV.

**Mouse.**
- [ ] The conventional staggered layout.
- [ ] Targets of 36 dp or more.
- [ ] Hover shows.
- [ ] Press feedback is transient.
- [ ] No persistent highlight.
- [ ] A search can be done with the mouse alone.
- [ ] Every special key works.

**Switching.**
- [ ] Live, both ways.
- [ ] No close or reopen.
- [ ] Text, cursor, Shift, Caps and page kept.
- [ ] Focus mapped per §9.
- [ ] Keys move and resize smoothly.
- [ ] Keys unique to one layout enter and leave as specified.
- [ ] No overlap or jump.
- [ ] No dropped or duplicated input.
- [ ] No flicker from incidental movement.
- [ ] Reduced motion honoured.

| Test | Level | Where |
| --- | --- | --- |
| `KeyboardLayout` data: every `KeyId` reachable, no overlap, rectangles inside the bounds, navigation and wrap tables | Unit (core/ui JVM) | CI |
| `KeyboardModeArbiter`: each signal, the hysteresis, flicker sequences, a click during a morph | Unit | CI |
| Session state across switches (text, cursor, Shift, Caps, page, chosen key) | Unit | CI |
| Focus mapping (§9): every key, both directions | Unit | CI |
| Morph: key bounds sampled across the transition (monotone, no overlap between moving keys, keys unique to one layout absent at the ends) | Compose UI test (`runDesktopComposeUiTest`, test clock) | CI |
| Mouse-only search; controller-only search; switching mid-word | UI tests | CI |
| Reduced motion | UI test | CI |
| Windows, physical: controller and mouse, switching, two DPIs | Walk | Owner's PC |
| Linux VM (`Reflux-QA-Ubuntu-26.04`): mouse layout, the morph | Walk (VM) | Labelled VM |
| Google TV emulator; Fire TV (physical): the controller layout, the remote | Instrumented walks | Labelled per device |
| Pad 7 and Phone 2a with a controller (physical) | Walks | Labelled physical |
| Window resizing, 125–200% scaling, long text (200 characters), the address and secret kinds | UI tests and walks | CI and PC |

---

## 13. Dependencies

- B-023 and B-054's `KeyboardSession` (kept).
- B-051's modality visuals and desk button (absorbed).
- `InputModalityTracker`, `InputMapping` `TEXT_ENTRY` (unchanged), `Motion`/`LocalMotion`, and B-041's performance method.

---

## 14. Milestones

1. **M1. Data:** `KeyId`; `KeyboardLayout` for controller and mouse × kinds × pages; geometric navigation; unit tests.
2. **M2. Controller layout (Concept C):** the new view placing keys from layout data; the session's chosen key becomes a `KeyId`; all existing keyboard tests green; TV and Fire TV walks.
3. **M3. Mouse layout:** the staggered desk layout, hover and press, Tab, Caps and Hide; mouse-only tests; a Windows walk.
4. **M4. Arbiter:** keyboard-scoped mode switching with hysteresis; unit tests.
5. **M5. Morph:** per-key animation, entering and leaving keys, the container, reduced motion; bound-sampling UI tests; performance measured on the Fire TV.
6. **M6. Focus mapping and state:** the full §9 rules; switch-mid-word tests.
7. **M7. Verification:** the §12 matrix on every listed environment; docs (INPUT_MODEL.md "Text entry by input"); the owner's review.

---

## 15. Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Morph jank on weak TV hardware | Placement-phase animation only; a Fire TV measurement gates M5; reduced-motion fallback |
| Flicker between modes | §7 hysteresis; explicit acts only switch at once; tests of noisy sequences |
| A click landing on a moving key | Hit-test the current animated geometry; ignore clicks on keys fading out |
| Experimental Compose APIs | Not used; a custom animation over a stable `Layout` |
| Remote users lose the action keys | The action column stays in the controller layout; shortcuts are an addition |
| Breaking B-023, B-051, B-054 behaviour | Their tests kept; layout data behind the same session API |
| Scope creep (a touch keyboard on Android) | Out of scope by the owner's steer; the system IME stays |

---

## 16. Open questions (owner)

- **Q1:** offer an **alphabetic controller layout** as an accessibility option (for those who do not type)? The evidence is mixed (R1, R3, R10). The default stays QWERTY.
- **Q2:** add the **title completions row** (§4.5) to F-P4, or later? It is the biggest speed gain for short title searches.
- **Q3:** mouse layout **locales** (AZERTY, QWERTZ, ISO Enter)? The default is ANSI US; others would follow the system's keyboard layout.
- **Q4:** dictation or phone entry for TV text (R7, R8)? Not in F-P4.
- **Q5:** should a physical keyboard's typing **hide** the desk virtual keyboard after a few keys? Recommended: no (§7). Typing goes to the field, and the virtual keyboard stays out of the way.
