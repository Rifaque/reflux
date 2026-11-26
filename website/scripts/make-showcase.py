"""Makes the website's product images (public/showcase/*.webp) from the LaunchFrame source captures of a release
(docs/release/WEBSITE.md, "Product images"): real captures only, system bars cropped off, scaled uniformly, never edited.

    python -I website/scripts/make-showcase.py store-assets/release-0.9.0-beta.2/source-screenshots

Writes the images and their sizes into src/showcase.json ("images"), keeping its "artwork" credits.
"""
import json
import pathlib
import sys

from PIL import Image

SITE = pathlib.Path(__file__).resolve().parent.parent
# name: (source capture, crop box in source pixels (system bars off) or None, output width)
IMAGES = {
    "home-phone": ("phone-home.png", (0, 120, 1080, 2280), 640),
    "quickview-phone": ("phone-quickview.png", (0, 120, 1080, 2280), 640),
    "miniplayer-phone": ("phone-miniplayer.png", (0, 120, 1080, 2280), 640),
    "home-tablet": ("tablet10-home.png", (0, 64, 2560, 1536), 1600),
    "details-tablet": ("tablet10-details.png", (0, 64, 2560, 1536), 1400),
    "movies-tv": ("tv-movies-rail.png", None, 1680),
    "quickview-tv": ("tv-quickview.png", None, 1680),
    "keyboard-tv": ("tv-keyboard.png", None, 1680),
    "player-tv": ("tv-player.png", None, 1680),
}


def main():
    sources = pathlib.Path(sys.argv[1])
    out = SITE / "public" / "showcase"
    data_path = SITE / "src" / "showcase.json"
    data = json.loads(data_path.read_text(encoding="utf-8"))
    for name, (file, box, width) in IMAGES.items():
        im = Image.open(sources / file).convert("RGB")
        if box:
            im = im.crop(box)
        height = round(im.height * width / im.width)
        im = im.resize((width, height), Image.LANCZOS)
        im.save(out / f"{name}.webp", "WEBP", quality=82, method=6)
        data["images"][name] = [width, height]
        print(f"{name}.webp {width}x{height} {(out / f'{name}.webp').stat().st_size // 1024} KB")
    data_path.write_text(json.dumps(data, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")


if __name__ == "__main__":
    sys.exit(main())
