"""Builds Reflux's master brand geometry as polygon SVGs (absolute M/L/Z only, so any tool can read them).

    python tools/brand/make-brand-geometry.py <repo> <Manrope[wght].ttf>

Needs numpy, shapely, and fontTools (a design-time tool; the outputs in design/brand are committed). The font is
Manrope's variable TTF from https://github.com/google/fonts/raw/main/ofl/manrope/Manrope%5Bwght%5D.ttf (OFL-1.1;
SHA-256 at the time: see design/brand/README.md). Then run make-brand-assets.py for every platform's files.

The mark: one stroke (four cubic Béziers) 11 units wide with round ends, outlined; where the returning pass crosses
the first, the first is cut by a gap 0.42 of the stroke on each side, so the return reads as passing over it.
The wordmark: "reflux" in Manrope at weight 600, outlined, tracked −1.5%, with 0.06 em more after the f so it never meets the l.
"""
import json, sys, pathlib
import numpy as np
from shapely.geometry import LineString, Point, Polygon, MultiPolygon
from shapely.ops import unary_union
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.pens.basePen import BasePen

REPO = pathlib.Path(sys.argv[1])
OUT = REPO / "design" / "brand"
OUT.mkdir(parents=True, exist_ok=True)
HERE = pathlib.Path(__file__).parent

FIRST = [[(12, 80), (40, 80), (70, 70), (72, 46)], [(72, 46), (74, 24), (54, 16), (43, 26)]]
SECOND = [[(43, 26), (32, 37), (38, 58), (58, 64)], [(58, 64), (70, 68), (80, 64), (90, 56)]]
STROKE = 11.0
CROSSING = (62.21, 65.10)


def bez(p, n=400):
    p = np.array(p, float); t = np.linspace(0, 1, n)[:, None]
    return (1 - t) ** 3 * p[0] + 3 * (1 - t) ** 2 * t * p[1] + 3 * (1 - t) * t ** 2 * p[2] + t ** 3 * p[3]


def line(segments):
    return LineString(np.vstack([bez(s) for s in segments]))


first, second = line(FIRST), line(SECOND)
first_area = first.buffer(STROKE / 2, quad_segs=32)
second_area = second.buffer(STROKE / 2, quad_segs=32)
gap = second.buffer(STROKE / 2 + STROKE * 0.42, quad_segs=32).intersection(Point(CROSSING).buffer(STROKE * 2.4, quad_segs=32))
mark = unary_union([first_area.difference(gap), second_area])

# Centre the mark's bounds in the 100-unit box.
minx, miny, maxx, maxy = mark.bounds
from shapely import affinity
TX, TY = 50 - (minx + maxx) / 2, 50 - (miny + maxy) / 2
mark = affinity.translate(mark, TX, TY)

# The centreline the mark is drawn from, in the same frame: what every animation of the mark traces (the website's
# draw-in, the app's loader, the Android splash). Cubic Béziers, so each platform draws the same curve.
def cubic(segments):
    out = "M%.2f %.2f" % (segments[0][0][0] + TX, segments[0][0][1] + TY)
    for seg in segments:
        out += " C" + " ".join("%.2f %.2f" % (x + TX, y + TY) for x, y in seg[1:])
    return out
STROKE_SVG = "\n".join([
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">',
    '  <!-- The centreline of the mark: the first pass, then the return over it. Round ends. -->',
    '  <path id="first" fill="none" stroke="currentColor" stroke-width="%.2f" stroke-linecap="round" d="%s"/>',
    '  <path id="return" fill="none" stroke="currentColor" stroke-width="%.2f" stroke-linecap="round" d="%s"/>',
    '</svg>',
    '',
])
(OUT / "reflux-mark-stroke.svg").write_text(STROKE_SVG % (STROKE, cubic(FIRST), STROKE, cubic(SECOND)), encoding="utf-8", newline="\n")


def rings(geom):
    polys = list(geom.geoms) if isinstance(geom, MultiPolygon) else [geom]
    for poly in polys:
        yield poly.exterior.coords
        for interior in poly.interiors:
            yield interior.coords


def path_data(geom, precision=2):
    out = []
    for ring in rings(geom.simplify(0.02)):
        pts = list(ring)[:-1]
        out.append("M" + " L".join("%.*f %.*f" % (precision, x, precision, y) for x, y in pts) + " Z")
    return " ".join(out)


# --- wordmark ---------------------------------------------------------------------------------------------------
class Flatten(BasePen):
    def __init__(self, glyphs):
        super().__init__(glyphs); self.contours = []; self.cur = []
    def _moveTo(self, p): self.cur = [p]
    def _lineTo(self, p): self.cur.append(p)
    def _curveToOne(self, a, b, c):
        p0 = self.cur[-1]
        for t in np.linspace(0, 1, 24)[1:]:
            self.cur.append(tuple((1-t)**3*np.array(p0) + 3*(1-t)**2*t*np.array(a) + 3*(1-t)*t**2*np.array(b) + t**3*np.array(c)))
    def _qCurveToOne(self, a, b):
        p0 = self.cur[-1]
        for t in np.linspace(0, 1, 16)[1:]:
            self.cur.append(tuple((1-t)**2*np.array(p0) + 2*(1-t)*t*np.array(a) + t**2*np.array(b)))
    def _closePath(self):
        if len(self.cur) > 2: self.contours.append(self.cur)
        self.cur = []
    _endPath = _closePath


font = instantiateVariableFont(TTFont(sys.argv[2]), {"wght": 600})
glyphs = font.getGlyphSet(); cmap = font.getBestCmap(); upm = font["head"].unitsPerEm
x = 0.0; shapes = []
tracking = -0.015 * upm
for ch in "reflux":
    name = cmap[ord(ch)]
    pen = Flatten(glyphs); glyphs[name].draw(pen)
    # TrueType winding: outer contours run clockwise (negative signed area with y up), holes the other way.
    def signed(c): return 0.5 * sum(x0 * y1 - x1 * y0 for (x0, y0), (x1, y1) in zip(c, c[1:] + c[:1]))
    outer = unary_union([Polygon([(px + x, -py) for px, py in c]).buffer(0) for c in pen.contours if signed(c) < 0])
    holes = unary_union([Polygon([(px + x, -py) for px, py in c]).buffer(0) for c in pen.contours if signed(c) > 0])
    letter = outer.difference(holes)
    shapes.append(letter)
    x += glyphs[name].width + tracking + (0.06 * upm if ch == 'f' else 0)
word = unary_union(shapes)
# Scale: x-height of Manrope, so the word sits on a baseline at y=0; normalised to cap/x height later.
minx, miny, maxx, maxy = word.bounds
word = affinity.translate(word, -minx, -miny)
scale = 100 / (maxy - miny)  # 100 units tall (ascender of f/l to baseline)
word = affinity.scale(word, scale, scale, origin=(0, 0))
wminx, wminy, wmaxx, wmaxy = word.bounds

def svg(view, d, title):
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="%s" role="img" aria-label="%s">\n'
            '  <title>%s</title>\n  <path fill="currentColor" fill-rule="evenodd" d="%s"/>\n</svg>\n') % (view, title, title, d)

(OUT / "reflux-mark.svg").write_text(svg("0 0 100 100", path_data(mark), "Reflux"), encoding="utf-8", newline="\n")
(OUT / "reflux-wordmark.svg").write_text(svg("0 0 %.2f %.2f" % (wmaxx, wmaxy), path_data(word), "Reflux"), encoding="utf-8", newline="\n")

# Lockup: the mark at 1.5× the wordmark's height, the word's x-height centred on it, a gap of 0.35 mark.
m = affinity.scale(mark, 1.5, 1.5, origin=(0, 0))
mb = m.bounds
mh = mb[3] - mb[1]
m = affinity.translate(m, -mb[0], -mb[1])
w = affinity.translate(word, (mb[2] - mb[0]) + mh * 0.32, (mh - wmaxy) / 2 + wmaxy * 0.06)
lock = unary_union([m, w])
lb = lock.bounds
(OUT / "reflux-lockup.svg").write_text(svg("0 0 %.2f %.2f" % (lb[2], lb[3]), path_data(lock), "Reflux"), encoding="utf-8", newline="\n")
json.dump({"mark_bounds": mark.bounds, "word_bounds": word.bounds, "lockup_bounds": lb}, sys.stdout, indent=1)
