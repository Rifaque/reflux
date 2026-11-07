#!/usr/bin/env python3
"""Renders Reflux's desktop icons from the same mark as the Android launcher icon and the window icon.

    python -I tools/release/make-desktop-icons.py

Writes desktop/app/packaging/reflux.png (512 px, for Linux) and desktop/app/packaging/reflux.ico (16 to 256 px, for
Windows). The mark: a square lit from the top left (graphite #34343A to #0E0E10) with a play triangle (#F5F5F6)
from (45,38) to (72,54) to (45,70) on a 108 grid, as in android/app/src/main/res/drawable/ic_launcher_*.xml.
Python's standard library only, so the output is the same wherever it runs.
"""
import pathlib
import struct
import zlib

OUT = pathlib.Path(__file__).resolve().parents[2] / "desktop" / "app" / "packaging"
TOP, BOTTOM, MARK = (0x34, 0x34, 0x3A), (0x0E, 0x0E, 0x10), (0xF5, 0xF5, 0xF6)
SAMPLES = 4  # per axis: antialiasing for the triangle's edges and the rounded corners


def inside_triangle(x: float, y: float) -> bool:
    (ax, ay), (bx, by), (cx, cy) = (45, 38), (72, 54), (45, 70)
    def side(px, py, qx, qy):
        return (x - qx) * (py - qy) - (px - qx) * (y - qy)
    d1, d2, d3 = side(ax, ay, bx, by), side(bx, by, cx, cy), side(cx, cy, ax, ay)
    return not ((d1 < 0 or d2 < 0 or d3 < 0) and (d1 > 0 or d2 > 0 or d3 > 0))


def inside_rounded(x: float, y: float, radius: float = 22.0) -> bool:
    cx = min(max(x, radius), 108 - radius)
    cy = min(max(y, radius), 108 - radius)
    return (x - cx) ** 2 + (y - cy) ** 2 <= radius ** 2


def render(size: int) -> bytes:
    rows = []
    for py in range(size):
        row = bytearray([0])
        for px in range(size):
            r = g = b = a = 0.0
            for sy in range(SAMPLES):
                for sx in range(SAMPLES):
                    x = (px + (sx + 0.5) / SAMPLES) * 108 / size
                    y = (py + (sy + 0.5) / SAMPLES) * 108 / size
                    if not inside_rounded(x, y):
                        continue
                    t = (x + y) / 216
                    color = MARK if inside_triangle(x, y) else tuple(TOP[i] + (BOTTOM[i] - TOP[i]) * t for i in range(3))
                    r, g, b, a = r + color[0], g + color[1], b + color[2], a + 1
            n = SAMPLES * SAMPLES
            row += bytes([round(r / a) if a else 0, round(g / a) if a else 0, round(b / a) if a else 0, round(255 * a / n)])
        rows.append(bytes(row))
    def chunk(kind: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    header = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(b"".join(rows), 9)) + chunk(b"IEND", b"")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "reflux.png").write_bytes(render(512))
    sizes = [16, 24, 32, 48, 64, 128, 256]
    images = [render(s) for s in sizes]
    offset = 6 + 16 * len(sizes)
    entries = b""
    for s, data in zip(sizes, images):
        entries += struct.pack("<BBBBHHII", s % 256, s % 256, 0, 0, 1, 32, len(data), offset)
        offset += len(data)
    (OUT / "reflux.ico").write_bytes(struct.pack("<HHH", 0, 1, len(sizes)) + entries + b"".join(images))
    print("wrote", OUT / "reflux.png", OUT / "reflux.ico")


if __name__ == "__main__":
    main()
