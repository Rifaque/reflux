"""Reflux 0.9.0-beta.2 store screenshots: the storyboard as data, written out as one HTML composition per shot.

    python -I working/build.py          (from the release folder; then render with LaunchFrame's render_html.py)

Every UI pixel is a source capture from ../source-screenshots, scaled uniformly (never stretched): a frame is a crop
rectangle in source pixels placed at a destination width, its height following from the crop's own aspect. The
backdrop is the same capture blurred far past legibility, standing for Reflux's Media Atmosphere (DESIGN.md).
"""
import html
import pathlib

HERE = pathlib.Path(__file__).resolve().parent
LOCKUP = (HERE / "brand" / "reflux-lockup.svg").read_text(encoding="utf-8")
LOCKUP = LOCKUP[LOCKUP.index("<svg"):].replace('role="img" aria-label="Reflux"', 'class="lockup"')

SRC = {  # source pixel sizes
    "phone-home": (1080, 2400), "phone-details": (1080, 2400), "phone-quickview": (1080, 2400),
    "phone-player-landscape": (2400, 1080), "phone-miniplayer": (1080, 2400), "phone-search": (1080, 2400),
    "phone-movies": (1080, 2400),
    "tablet10-home": (2560, 1600), "tablet10-details": (2560, 1600), "tablet10-quickview": (2560, 1600),
    "tablet10-movies": (2560, 1600), "tablet10-player": (2560, 1600),
    "tablet7-home": (1920, 1200), "tablet7-details": (1920, 1200), "tablet7-quickview": (1920, 1200),
    "tablet7-movies": (1920, 1200), "tablet7-player": (1920, 1200),
    "tv-home": (1920, 1080), "tv-quickview": (1920, 1080), "tv-keyboard": (1920, 1080), "tv-movies-rail": (1920, 1080),
    "tv-details": (1920, 1080), "tv-player": (1920, 1080), "tv-movies-focus": (1920, 1080),
    # Fire TV Stick 4K Plus (AFTMA08C15, Fire OS 8.1.8.5), physical, 2026-11-21: the Amazon set's own captures.
    "firetv-home": (1920, 1080), "firetv-quickview": (1920, 1080), "firetv-keyboard": (1920, 1080),
    "firetv-movies-rail": (1920, 1080), "firetv-details": (1920, 1080), "firetv-player": (1920, 1080),
}


def frame(src, dest, crop=None, radius=40, rotate=False, edge=True):
    """A real capture, cropped (x, y, w, h in source px) and placed at dest (x, y, width) on the canvas."""
    sw, sh = SRC[src]
    cx, cy, cw, ch = crop or (0, 0, sw, sh)
    dx, dy, dw = dest
    scale = dw / (ch if rotate else cw)
    box_w, box_h = (ch * scale, cw * scale) if rotate else (cw * scale, ch * scale)
    img_w = sw * scale
    if rotate:  # the landscape capture turned a quarter anticlockwise, as a phone turned to the left
        inner = (f'<div class="rot" style="width:{cw*scale:.1f}px;height:{ch*scale:.1f}px;left:{(box_w-cw*scale)/2:.1f}px;'
                 f'top:{(box_h-ch*scale)/2:.1f}px"><img src="../../../source-screenshots/{src}.png" '
                 f'style="width:{img_w:.1f}px;left:{-cx*scale:.1f}px;top:{-cy*scale:.1f}px" alt=""></div>')
    else:
        inner = (f'<img src="../../../source-screenshots/{src}.png" style="width:{img_w:.1f}px;left:{-cx*scale:.1f}px;'
                 f'top:{-cy*scale:.1f}px" alt="">')
    cls = "frame" + ("" if edge else " flat")
    return (f'<div class="{cls}" style="left:{dx}px;top:{dy}px;width:{box_w:.1f}px;height:{box_h:.1f}px;'
            f'border-radius:{radius}px">{inner}</div>')


def text(head, sub, box, size, align="left", mark=False):
    x, y, w = box
    mk = f'<div class="mark">{LOCKUP}</div>' if mark else ""
    return (f'<div class="copy {align}" style="left:{x}px;top:{y}px;width:{w}px;--h:{size}px">{mk}'
            f'<h1>{html.escape(head)}</h1>' + (f'<p>{html.escape(sub)}</p>' if sub else "") + "</div>")


def page(w, h, atmos, body, plain=False):
    bg = "" if plain else f'<img class="atmos" src="../../../source-screenshots/{atmos}.png" alt="">'
    return f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><title>Reflux store screenshot</title>
<link rel="stylesheet" href="../../campaign.css">
<style>html, body {{ width: {w}px; height: {h}px; }}</style></head>
<body class="{'plain' if plain else ''}">{bg}<div class="veil"></div>
{body}
</body></html>
"""


# ---- the storyboard (design-system.md has each shot's feature, evidence and reasons) --------------------------------
P = (1080, 1920)
T10 = (2560, 1440)
T7 = (1920, 1080)
TV = (1920, 1080)

SHOTS = {
    "google-play/phone": [
        (P, "phone-home", [text("Your media, played well.", "Folders, Jellyfin, Plex and shares in one library.", (84, 150, 912), 92, mark=True),
                           frame("phone-home", (110, 560, 860), radius=56)]),
        (P, "phone-details", [frame("phone-details", (64, 120, 952), crop=(0, 280, 1080, 1620), radius=48),
                              text("Know it before you press Play.", "4K, HDR, sound, and where each copy lives.", (84, 1560, 912), 84)]),
        (P, "phone-quickview", [text("A closer look, one long press away.", "Play, favourite or download, straight from the grid.", (84, 150, 912), 84),
                                frame("phone-quickview", (0, 560, 1080), crop=(0, 300, 1080, 1260), radius=0, edge=False)]),
        (P, "phone-player-landscape", [text("The picture comes first.", "The controls step aside while you watch.", (84, 150, 912), 92),
                                       frame("phone-player-landscape", (236, 470, 608), rotate=True, radius=44)]),
        (P, "phone-miniplayer", [text("Keep browsing while it plays.", "The mini-player stays with you.", (84, 150, 912), 92),
                                 frame("phone-miniplayer", (110, 560, 860), crop=(0, 760, 1080, 1410), radius=44)]),
        (P, "phone-search", [frame("phone-search", (64, 150, 952), crop=(0, 60, 1080, 1060), radius=48),
                             text("Your library answers as you type.", "Results come from the films and shows you have.", (84, 1300, 912), 84)]),
    ],
    "google-play/tablet-10-inch": [
        (T10, "tablet10-home", [text("Your media, played well.", "Folders, Jellyfin, Plex and network shares, in one library.", (160, 420, 760), 116, mark=True),
                                frame("tablet10-home", (980, 200, 1840), radius=36)]),
        (T10, "tablet10-details", [frame("tablet10-details", (80, 180, 1700), crop=(430, 0, 2130, 1600), radius=36),
                                   text("Pick up exactly where you stopped.", "Resume, or start again from the beginning.", (1840, 470, 600), 104)]),
        (T10, "tablet10-quickview", [frame("tablet10-quickview", (300, 90, 1960), crop=(440, 300, 2120, 1150), radius=36),
                                     text("A closer look without leaving the grid.", "", (300, 1190, 1960), 96, align="center")]),
        (T10, "tablet10-movies", [frame("tablet10-movies", (100, -70, 1700), radius=36),
                                  text("Your whole collection at a glance.", "Every film, with what you were in the middle of.", (1840, 700, 600), 100)]),
        (T10, "tablet10-player", [text("Made for the big screen in your hands.", "", (300, 110, 1960), 96, align="center"),
                                  frame("tablet10-player", (380, 330, 1800), radius=36)]),
    ],
    "google-play/tablet-7-inch": [
        (T7, "tablet7-home", [text("Your media, played well.", "In one library, from wherever it lives.", (112, 110, 640), 84, mark=True),
                              frame("tablet7-home", (800, 120, 1340), radius=28)]),
        (T7, "tablet7-details", [frame("tablet7-details", (80, 150, 1150), crop=(160, 0, 1760, 1200), radius=28),
                                 text("Pick up exactly where you stopped.", "Resume, or start from the beginning.", (1260, 330, 560), 76)]),
        (T7, "tablet7-quickview", [text("A closer look, in place.", "", (112, 80, 1696), 80, align="center"),
                                   frame("tablet7-quickview", (260, 240, 1400), crop=(160, 0, 1480, 720), radius=28)]),
        (T7, "tablet7-player", [frame("tablet7-player", (112, 110, 1180), radius=28),
                                text("The picture comes first.", "Controls step aside while you watch.", (1370, 330, 440), 76)]),
        (T7, "tablet7-movies", [frame("tablet7-movies", (220, -60, 1480), radius=28),
                                text("Your collection at a glance.", "", (112, 900, 1696), 76, align="center")]),
    ],
    "google-play/tv": [
        (TV, "tv-home", [text("Your media, played well.", "", (96, 84, 1200), 76, mark=True),
                         frame("tv-home", (180, 250, 1560), radius=24)]),
        (TV, "tv-quickview", [text("Press Menu for a quick look.", "Play, favourite or download without opening the page.", (96, 330, 520), 70),
                              frame("tv-quickview", (680, 170, 1240), crop=(0, 0, 1920, 1080), radius=24)]),
        (TV, "tv-keyboard", [frame("tv-keyboard", (60, 170, 1180), radius=24),
                             text("A keyboard made for the remote.", "Matching titles appear as you type.", (1300, 350, 540), 70)]),
        (TV, "tv-movies-rail", [frame("tv-movies-rail", (180, -40, 1560), radius=24),
                                text("Everything is one press of Left away.", "", (96, 912, 1728), 64, align="center")]),
        (TV, "tv-details", [text("Read it, then press Play.", "", (96, 64, 1728), 64, align="center"),
                            frame("tv-details", (240, 190, 1440), radius=24)]),
        (TV, "tv-player", None),  # unaltered capture (Google's TV quality guidance asks for at least one)
    ],
    "amazon-appstore/fire-tv": [
        (TV, "firetv-home", [text("Your media, played well.", "", (96, 84, 1200), 76, mark=True),
                         frame("firetv-home", (180, 250, 1560), radius=24)]),
        (TV, "firetv-quickview", [text("Press Menu for a quick look.", "Play, favourite or download without opening the page.", (96, 330, 520), 70),
                              frame("firetv-quickview", (680, 170, 1240), radius=24)]),
        (TV, "firetv-keyboard", [frame("firetv-keyboard", (60, 170, 1180), radius=24),
                             text("A keyboard made for the remote.", "Matching titles appear as you type.", (1300, 350, 540), 70)]),
        (TV, "firetv-movies-rail", [frame("firetv-movies-rail", (180, -40, 1560), radius=24),
                                text("Everything is one press of Left away.", "", (96, 912, 1728), 64, align="center")]),
        (TV, "firetv-player", None),
    ],
}

FEATURE = [  # Google Play feature graphic, 1024 x 500: brand-led, no device, the idea in the middle
    ((1024, 500), "tv-home", [text("Your media, played well.", "", (64, 164, 420), 56, mark=True),
                              frame("tv-home", (512, 118, 472), crop=(196, 130, 1100, 600), radius=16)]),
]


def main():
    out = []
    for target, shots in SHOTS.items():
        folder = HERE / target
        folder.mkdir(parents=True, exist_ok=True)
        for old in folder.glob("*.html"):
            old.unlink()
        for i, (size, atmos, layers) in enumerate(shots, 1):
            w, h = size
            if layers is None:  # a plain capture, full canvas, nothing added
                body = frame(atmos, (0, 0, w), radius=0, edge=False)
                doc = page(w, h, atmos, body, plain=True)
            else:
                doc = page(w, h, atmos, "\n".join(layers))
            (folder / f"{i:02d}.html").write_text(doc, encoding="utf-8")
            out.append(f"{target}/{i:02d}.html")
    fg = HERE / "google-play" / "feature-graphic"
    fg.mkdir(parents=True, exist_ok=True)
    size, atmos, layers = FEATURE[0]
    doc = page(*size, atmos, "\n".join(layers)).replace('<body class="">', '<body class="feature">')
    (fg / "01.html").write_text(doc, encoding="utf-8")
    print("wrote", len(out) + 1, "compositions")


if __name__ == "__main__":
    main()
