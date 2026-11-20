#!/usr/bin/env python3
"""Renders Reflux's artwork for a Steam library shortcut from the master brand geometry (design/brand/README.md).

    python -I tools/brand/make-steam-artwork.py

Writes design/brand/steam/:

  steam-grid-600x900.png     the portrait cover of the library grid ("p" in Steam's grid folder)
  steam-wide-920x430.png     the landscape capsule of Recent games and Big Picture's shelves (the plain ID)
  steam-hero-3840x1240.png   the wide banner behind a game's page ("_hero"); no lettering, Steam puts the logo over it
  steam-logo-1280x264.png    the lockup on transparency, for over the hero ("_logo")
  steam-icon-256.png         the app icon, as the desktop app's own (reflux.png, reflux.ico)

One collection: the same graphite ground, the same light from the top left as a projector's beam, and the mark's own line
drawn large and faint behind it, as the Loop running on through the dark. The mark and the lockup keep the brand's rules:
ink #F5F5F6, never coloured, outlined or glowing, always clear of the busy part of a picture. Needs Pillow and NumPy;
deterministic (the grain is seeded).
"""
import importlib.util
import pathlib

import numpy as np
from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("brand", HERE / "make-brand-assets.py")
brand = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(brand)

ROOT = brand.ROOT
OUT = brand.BRAND / "steam"
WORD_VIEW, _, WORD = brand.rings(brand.BRAND / "reflux-wordmark.svg")


def scene(size, beam=(0.18, -0.25), loop_box=None, loop_alpha=0.07, seed=7):
    """The ground: graphite, a soft beam of light from [beam] (fractions of the frame), a vignette, the Loop faint at
    [loop_box] (x, y, w, h in pixels), and a fine grain so the dark never bands. Returns RGBA."""
    w, h = size
    top, bottom = np.array(brand.TOP, float), np.array(brand.BOTTOM, float)
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    t = np.clip((0.35 * xs / w + 0.65 * ys / h) / 0.75, 0, 1)[..., None]
    rgb = top + (bottom - top) * t
    # The beam: a wide, soft fall-off from a point above the frame, neutral and faint (no colour of its own, as the brand).
    span = max(w, h)
    bx, by = beam[0] * w, beam[1] * h
    d = np.hypot((xs - bx) / span, (ys - by) / (span * 0.9))
    light = np.clip(1.0 - d / 0.95, 0, 1) ** 2.2
    rgb = rgb + light[..., None] * np.array([34.0, 34.0, 38.0])
    # A vignette, so the edges sit back.
    v = np.hypot((xs / w - 0.5) * 1.1, (ys / h - 0.5) * 1.25)
    rgb = rgb * (1.0 - np.clip(v - 0.35, 0, 1)[..., None] * 0.55)
    img = Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), "RGB").convert("RGBA")
    if loop_box:
        mask = brand.coverage((brand.MARK_VIEW, brand.MARK), size, loop_box)
        faint = Image.new("RGBA", size, brand.INK + (255,))
        img.paste(faint, (0, 0), mask.point(lambda p: round(p * loop_alpha)))
    # Grain, very light: breaks the gradient's steps in 8-bit, which is all a dark ground needs.
    rng = np.random.default_rng(seed)
    arr = np.asarray(img).astype(np.int16)
    noise = rng.normal(0, 1.6, (h, w, 1)).astype(np.int16)
    arr[..., :3] = np.clip(arr[..., :3] + noise, 0, 255)
    return Image.fromarray(arr.astype(np.uint8), "RGBA")


def put(img, shape, box):
    return brand.ink(img, brand.coverage(shape, img.size, box))


def grid():
    w, h = 600, 900
    # The Loop rises out of the foot of the cover, much larger than the frame and clear of the lettering above it.
    img = scene((w, h), beam=(0.15, -0.2), loop_box=(-40, 690, 980, 980), loop_alpha=0.06)
    mark_w = w * 0.46
    put(img, (brand.MARK_VIEW, brand.MARK), ((w - mark_w) / 2, h * 0.27, mark_w, mark_w))
    word_w = w * 0.52
    word_h = word_w * WORD_VIEW[3] / WORD_VIEW[2]
    put(img, (WORD_VIEW, WORD), ((w - word_w) / 2, h * 0.27 + mark_w + h * 0.07, word_w, word_h))
    return img.convert("RGB")


def wide():
    w, h = 920, 430
    img = scene((w, h), beam=(0.12, -0.45), loop_box=(520, -260, 900, 900), loop_alpha=0.06)
    bw = w * 0.52
    return put(img, (brand.LOCK_VIEW, brand.LOCK), ((w - bw) / 2, 0, bw, h)).convert("RGB")


def hero():
    # No lettering (Steam lays the logo over it, at the left by default): the light and the Loop carry it, on the right.
    w, h = 3840, 1240
    return scene((w, h), beam=(0.30, -0.55), loop_box=(2150, -620, 2300, 2300), loop_alpha=0.09, seed=11).convert("RGB")


def logo():
    w, h = 1280, 264
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    mask = brand.coverage((brand.LOCK_VIEW, brand.LOCK), (w, h), (0, 0, w, h))
    img.paste(Image.new("RGBA", (w, h), brand.INK + (255,)), (0, 0), mask)
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    grid().save(OUT / "steam-grid-600x900.png", optimize=True)
    wide().save(OUT / "steam-wide-920x430.png", optimize=True)
    hero().save(OUT / "steam-hero-3840x1240.png", optimize=True)
    logo().save(OUT / "steam-logo-1280x264.png", optimize=True)
    brand.tile(256, 0.22).save(OUT / "steam-icon-256.png", optimize=True)
    print("steam artwork written")


if __name__ == "__main__":
    main()
