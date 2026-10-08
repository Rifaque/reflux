# Reflux Design Direction

## Identity

Reflux should combine:

- restrained, premium consumer-software ergonomics,
- cinematic media presentation,
- liquid-glass materials.

The goal is not to copy Apple, Netflix, Infuse, Plex, or any other product. Those products are references for principles, not visual templates.

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

- **Palette:** dark cinematic base; text tokens are tested to meet WCAG AA on every surface, and glass materials raise their tint over bright artwork until text stays at 4.5:1.
- **Materials:** Base, Elevated, Floating, Interactive, Focused, and Modal, each with tint, backdrop blur, highlight, shadow, and corner radius. Blur applies to what is behind a material, never to its content.
- **Motion:** standard, emphasized, and exit curves; focus changes 140 ms, navigation 320 ms, poster-to-details 420 ms, input morph 360 ms; player controls auto-hide after 3.5 s.
- **Adaptive layout:** form factor (phone, tablet, desktop, TV) comes from the window and device; input modality only changes density, focus scale, target size, and button prompts. That is what makes the controller morph a transition rather than a mode. TVs keep a 5% overscan-safe margin and a 1.5× type scale.
- **Artwork accent:** a deterministic, saturation-weighted hue histogram picks an accent from artwork; it is adjusted until text on it meets AA, and grey artwork keeps the default accent.
