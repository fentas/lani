#!/usr/bin/env python3
"""Lani's app icon and wordmark: five pixel-art concepts and their changing variants (docs/plans/05-built/app-icon.md).

Every concept is drawn here pixel for pixel, on a grid of 54 × 54 pixels of 2 dp: the adaptive icon's 108 dp layer, of
which a launcher shows the middle 36 × 36 (72 dp) and every mask keeps the circle of 33 px (66 dp) in the middle. A
concept draws three layers for a variant (the learner's culture, the season, a festival, the time of day, today's
practice): the background, the foreground and the monochrome (the themed icon's), so a picked concept builds straight
into the app:

  python3 app_icon.py build [APP_DIR] [--check]           the app's icon: the 176 aliases' drawables, the shared
                                                            backgrounds and monochrome layers, the application's icon,
                                                            ic_notify, the manifest's alias block (--check: compare only)
  python3 app_icon.py list                                  the concepts and their variants
  python3 app_icon.py xml CONCEPT RES_DIR [VARIANT ...]     the vector drawables and mipmap-anydpi-v26 XML of each
                                                            variant (default: the base one), named as their aliases
  python3 app_icon.py wordmark CONCEPT OUT_DIR              the wordmark "Lani" as SVG, for a light and a dark ground
  uv run --no-project --with pillow python3 app_icon.py sheets OUT_DIR [CONCEPT ...]
  uv run --no-project --with pillow python3 app_icon.py ages OUT_DIR    the ages' sheets (step 1b)
                                                            a contact sheet of each concept (masks, sizes, wallpapers,
                                                            the themed icon, the variants, the wordmark, a home screen)

A VARIANT is written culture[.season][.time][.fest][.waiting], e.g. "friuli.winter", "base.summer.night",
"primorska.winter.day.lights". The XML needs nothing but Python; the sheets need Pillow.
"""
from __future__ import annotations

import math
import sys
from dataclasses import dataclass, replace
from pathlib import Path

N = 54      # the layer, in pixels of 2 dp (108 dp)
VIS = 36    # what a launcher shows of it (72 dp)
OFF = (N - VIS) // 2  # the visible window's corner in the layer
SAFE_R = 16.5  # the circle every mask keeps (66 dp), around the visible window's middle


def hx(v: int) -> tuple[int, int, int]:
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255)


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def shade(c, f):
    """Lighter (f > 1) or darker (f < 1)."""
    if f >= 1:
        return mix(c, (255, 255, 255), f - 1)
    return tuple(round(v * f) for v in c)


# ---------------------------------------------------------------------------------------------------------- palette
# The village's own colours (game/render/Env.kt Pal, FireVillage.kt FireStyle), so the icon is painted as the map is.
OUTLINE = hx(0x2A1D1A)
INK = hx(0x14213D)        # the dark outline round a shape that must hold at 1 cm
WALL = (hx(0xF6F1E4), hx(0xE0D7C3), hx(0xB9AD95))
ROOF = (hx(0xB5553A), hx(0x96412A), hx(0x6E2C1C))
SHINGLE = (hx(0x7C6552), hx(0x654F3F), hx(0x4A3A2F))
SLATE = (hx(0x6F7482), hx(0x575B68), hx(0x40434E))
WOOD = (hx(0xC08850), hx(0x9A673A), hx(0x6F4527), hx(0x4A2D1A))
LOG = (hx(0xA87444), hx(0x8A5A32), hx(0x643F22))
STONE = (hx(0xB8B2A6), hx(0x96907F), hx(0x716C62), hx(0x57534C))
LIT = (hx(0xFFD77A), hx(0xFFF1C2))
GLASS = (hx(0x34465A), hx(0x7F9CB8))
DOOR = hx(0x4A2E1C)
FLAME = (hx(0xFFF6C8), hx(0xFFD74A), hx(0xFF9A1F), hx(0xE8501A), hx(0xA8301A))
GERANIUM = (hx(0xE8303A), hx(0xA81E2A))
LEAF = hx(0x3E8A3A)
SKIN = (hx(0xF0C09A), hx(0xC98E6A))
CREAM = (hx(0xFFF8E8), hx(0xEADFC8), hx(0xC9BCA0))  # the speech bubble's paper
SNOW = (hx(0xF6F9FF), hx(0xDDE7F3), hx(0xB9CAE0))
PINE = (hx(0x4E9460), hx(0x3B7A4A), hx(0x2C5E3A), hx(0x1E452C))
GOLD = (hx(0xFFE08A), hx(0xFFB300))
WHITE = (255, 255, 255)

GRASS = {  # light, middle, dark, highlight (Env.grass)
    "spring": (hx(0x86C455), hx(0x74B24A), hx(0x60993D), hx(0xA3D66A)),
    "summer": (hx(0x72AC47), hx(0x5F983D), hx(0x4C8234), hx(0x8CC25A)),
    "autumn": (hx(0xA79E4E), hx(0x918844), hx(0x78703A), hx(0xC0B060)),
    "winter": (hx(0xF3F7FE), hx(0xDDE7F3), hx(0xC3D2E6), hx(0xFFFFFF)),
}
FOLIAGE = {  # a broadleaf crown by season: light, middle, dark
    "spring": (hx(0xF6C6D8), hx(0xE89AB8), hx(0x8FBF5A)),   # blossom over fresh green
    "summer": (hx(0x7FC25A), hx(0x5A9E44), hx(0x3E7A34)),
    "autumn": (hx(0xF2B13A), hx(0xE07A2A), hx(0xB04A22)),
    "winter": (hx(0xF6F9FF), hx(0xC9D6E6), hx(0x7A6A5A)),
}
SKY = {  # top, middle, horizon
    "day": (hx(0x3F7FC8), hx(0x6FA8DE), hx(0xBFE0F4)),
    "dusk": (hx(0x2A3F86), hx(0x8A5A9E), hx(0xF2A65A)),
    "night": (hx(0x0E1E48), hx(0x16306A), hx(0x22427E)),
}
BRAND = hx(0x0B4EA2)  # the app's blue (colors.xml, the window behind the splash)


# ---------------------------------------------------------------------------------------------------------- cultures
@dataclass(frozen=True)
class Look:
    """A culture's village in the icon: its walls, roof, shutters, church tower and mountains (FireStyle, MapLook)."""
    id: str
    wall: tuple
    roof: tuple
    shutter: tuple | None
    tower: str            # watch (the camp's), spire, campanile, onion, battlements
    tower_wall: tuple
    tower_top: tuple
    range: str            # the mountains: forest, julian, prealps, karawanken, fells
    balcony: bool = False
    timber: bool = False
    stone: bool = False
    tree: str = "linden"  # linden, oak, spruce
    coppi: bool = False


LOOKS = {
    # The base form, culture-free: any village (cream walls, brick roofs, wooden shutters, a plain tower with a pyramid
    # roof): what a new install shows before the learner picks a language, and the mark on the README and prints.
    "base": Look("base", (hx(0xF4EBD8), hx(0xDED2BA), hx(0xB8A88C)), (hx(0xC2603E), hx(0xA04A30), hx(0x74321F)),
                 (hx(0xB07A48), hx(0x8A5A32)), "pyramid", (hx(0xF4EBD8), hx(0xDED2BA), hx(0xB8A88C)),
                 (hx(0xC2603E), hx(0xA04A30), hx(0x74321F)), "forest"),
    "primorska": Look("primorska", WALL, ROOF, (hx(0x5E8A50), hx(0x3F6B3A)), "spire", WALL,
                      (hx(0x7A6A60), hx(0x5E5048), hx(0x42382F)), "julian", balcony=True),
    "friuli": Look("friuli", (hx(0xF0CB86), hx(0xD8AE66), hx(0xAE8848)), (hx(0xC8683E), hx(0xA4502E), hx(0x7A3820)),
                   (hx(0x6E8E64), hx(0x4F6E46)), "campanile", (hx(0xC47A56), hx(0xA4603E), hx(0x7E4628)),
                   (hx(0xC8683E), hx(0xA4502E), hx(0x7A3820)), "prealps", coppi=True),
    "kaernten": Look("kaernten", (hx(0xF7F3EA), hx(0xE2DACB), hx(0xBDB29D)), SHINGLE, (hx(0x4A7A56), hx(0x2F5A3A)),
                     "onion", (hx(0xF7F3EA), hx(0xE2DACB), hx(0xBDB29D)), (hx(0x6FA890), hx(0x4E8C78), hx(0x2F5E50)),
                     "karawanken", balcony=True, timber=True),
    "lakeland": Look("lakeland", (hx(0x9AA0A4), hx(0x7C8286), hx(0x5E6468)), SLATE, (hx(0x4A6E92), hx(0x2E4F6E)),
                     "battlements", (hx(0x9AA0A4), hx(0x7C8286), hx(0x5E6468)), SLATE, "fells", stone=True, tree="oak"),
}
CULTURES = ["base", "primorska", "friuli", "kaernten", "lakeland"]
SEASONS = ["spring", "summer", "autumn", "winter"]
TIMES = ["day", "dusk", "night"]
# A festival's touch, shared by the cultures (each culture's own days map to one, festivals.json):
#   lights   Christmas to New Year (bozic, natale, weihnachten, christmas; novo_leto …)
#   carnival pust, carnevale, fasching, pancake_day
#   bonfire  kres, sonnwende, bonfire_night (and the fire's own feasts)
#   harvest  trgatev, vendemmia, almabtrieb, harvest
FESTS = ["lights", "carnival", "bonfire", "harvest"]
CULTURE_NAMES = {"base": "Osnova · Base", "primorska": "Primorska", "friuli": "Friuli", "kaernten": "Kärnten",
                 "lakeland": "Lake District"}
SEASON_NAMES = {"spring": "Pomlad · Spring", "summer": "Poletje · Summer", "autumn": "Jesen · Autumn",
                "winter": "Zima · Winter"}
TIME_NAMES = {"day": "Dan · Day", "dusk": "Mrak · Dusk", "night": "Noč · Night"}
AGES = ["ogenj", "tabor", "zaselek", "vas", "trg", "mesto"]   # game/Model.kt Age
AGE_NAMES = {"ogenj": "🔥 Ogenj · Campfire", "tabor": "⛺ Tabor · Camp", "zaselek": "🛖 Zaselek · Hamlet",
             "vas": "🏘️ Vas · Village", "trg": "🏛️ Trg · Market town", "mesto": "🏰 Mesto · Town"}
# What's happening, a sign drawn into the age's icon for the day (docs/plans/05-built/app-icon.md "What's happening"):
EVENTS = ["newage", "festival", "built", "newcomer", "watch"]
EVENT_NAMES = {"newage": "Nova doba · A new age", "festival": "Praznik · A festival", "built": "Zgrajeno · Built",
               "newcomer": "Prišlek · Someone new", "watch": "Straža · The watch"}
FEST_NAMES = {"lights": "Luči · Lights", "carnival": "Pust · Carnival", "bonfire": "Kres · Bonfire",
              "harvest": "Trgatev · Harvest"}


@dataclass(frozen=True)
class Variant:
    culture: str = "base"
    season: str = "summer"
    time: str = ""          # "": the concept's own light; day, dusk, night
    fest: str = ""
    waiting: bool = False   # today's practice isn't done yet: the gentle state
    age: str = ""           # "": the concept's own; ogenj, tabor, zaselek, vas, trg, mesto (the "ages" concept)
    event: str = ""         # "": none; newage, festival, built, newcomer, watch (the "ages" concept)

    @property
    def look(self) -> Look:
        return LOOKS[self.culture]

    @property
    def key(self) -> str:
        parts = [self.culture] + ([self.age] if self.age else []) + [self.season] + ([self.time] if self.time else []) + \
            ([self.fest] if self.fest else []) + ([self.event] if self.event else []) + (["waiting"] if self.waiting else [])
        return ".".join(parts)

    @staticmethod
    def parse(s: str) -> "Variant":
        v = Variant()
        for p in s.split("."):
            if p in LOOKS: v = replace(v, culture=p)
            elif p in SEASONS: v = replace(v, season=p)
            elif p in TIMES: v = replace(v, time=p)
            elif p in FESTS: v = replace(v, fest=p)
            elif p == "waiting": v = replace(v, waiting=True)
            elif p in AGES: v = replace(v, age=p)
            elif p in EVENTS: v = replace(v, event=p)
            elif p: raise SystemExit(f"unknown variant part: {p}")
        return v


# ---------------------------------------------------------------------------------------------------------- drawing
class Layer:
    """N × N pixels, None where empty; drawn in the visible window's coordinates (0..35; the layer reaches -9..44)."""

    def __init__(self):
        self.px = [[None] * N for _ in range(N)]

    def get(self, x, y):
        X, Y = x + OFF, y + OFF
        return self.px[Y][X] if 0 <= X < N and 0 <= Y < N else None

    def put(self, x, y, c):
        X, Y = int(x) + OFF, int(y) + OFF
        if 0 <= X < N and 0 <= Y < N:
            self.px[Y][X] = c

    def rect(self, x0, y0, x1, y1, c):
        """Inclusive."""
        x0, y0, x1, y1 = int(x0), int(y0), int(x1), int(y1)
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, c)

    def fill(self, test, c):
        """Every pixel whose centre passes test(x + 0.5, y + 0.5); c a colour or a function of (x, y)."""
        for y in range(-OFF, N - OFF):
            for x in range(-OFF, N - OFF):
                if test(x + 0.5, y + 0.5):
                    self.put(x, y, c(x, y) if callable(c) else c)

    def poly(self, pts, c):
        xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
        x0, x1, y0, y1 = int(min(xs)) - 1, int(max(xs)) + 1, int(min(ys)) - 1, int(max(ys)) + 1
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                if inside(pts, x + 0.5, y + 0.5):
                    self.put(x, y, c(x, y) if callable(c) else c)

    def sprite(self, x0, y0, rows, pal, flip=False):
        """ASCII art: each char a key of pal ('.' and ' ' are empty)."""
        for j, row in enumerate(rows):
            if flip: row = row[::-1]
            for i, ch in enumerate(row):
                if ch in ". ":
                    continue
                c = pal[ch]
                if c is not None:
                    self.put(x0 + i, y0 + j, c)

    def outline(self, c, diagonal=False, only_outside_of=None):
        """A 1 px line round everything drawn (into empty pixels)."""
        filled = {(x, y) for y in range(N) for x in range(N) if self.px[y][x] is not None}
        nbrs = [(1, 0), (-1, 0), (0, 1), (0, -1)] + ([(1, 1), (1, -1), (-1, 1), (-1, -1)] if diagonal else [])
        for (x, y) in filled:
            for dx, dy in nbrs:
                X, Y = x + dx, y + dy
                if 0 <= X < N and 0 <= Y < N and (X, Y) not in filled:
                    self.px[Y][X] = c

    def over(self, other: "Layer"):
        for y in range(N):
            for x in range(N):
                if other.px[y][x] is not None:
                    self.px[y][x] = other.px[y][x]
        return self

    def copy(self):
        l = Layer(); l.px = [r[:] for r in self.px]; return l

    def mask_of(self, keep=lambda c: True, colour=WHITE):
        l = Layer()
        for y in range(N):
            for x in range(N):
                c = self.px[y][x]
                if c is not None and keep(c):
                    l.px[y][x] = colour
        return l

    def erase(self, other: "Layer"):
        for y in range(N):
            for x in range(N):
                if other.px[y][x] is not None:
                    self.px[y][x] = None
        return self


def inside(pts, x, y):
    n = len(pts); c = False
    j = n - 1
    for i in range(n):
        xi, yi = pts[i]; xj, yj = pts[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            c = not c
        j = i
    return c


def rrect(x0, y0, x1, y1, r):
    """A test for a rounded rectangle (pixel edges x0..x1+1)."""
    def t(x, y):
        if not (x0 <= x <= x1 + 1 and y0 <= y <= y1 + 1):
            return False
        cx = min(max(x, x0 + r), x1 + 1 - r); cy = min(max(y, y0 + r), y1 + 1 - r)
        return (x - cx) ** 2 + (y - cy) ** 2 <= r * r
    return t


def ellipse(cx, cy, rx, ry):
    return lambda x, y: ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1


def dither(a, b, t, x, y):
    """Ordered (Bayer 4 × 4) dither between a and b, t 0..1."""
    m = ((0, 8, 2, 10), (12, 4, 14, 6), (3, 11, 1, 9), (15, 7, 13, 5))
    return b if (m[y % 4][x % 4] + 0.5) / 16 < t else a


def sky(layer: Layer, time: str, y0=-OFF, y1=N - OFF - 1, top=None, mid=None, hor=None):
    """A sky from y0 to y1, dithered between its three colours."""
    t_, m_, h_ = SKY[time]
    t_ = top or t_; m_ = mid or m_; h_ = hor or h_
    for y in range(y0, y1 + 1):
        f = (y - y0) / max(1, y1 - y0)
        for x in range(-OFF, N - OFF):
            if f < 0.5:
                g = f / 0.5; bands = 4
                k = g * bands; i = int(k)
                a = mix(t_, m_, i / bands); b = mix(t_, m_, min(bands, i + 1) / bands)
            else:
                g = (f - 0.5) / 0.5; bands = 4
                k = g * bands; i = int(k)
                a = mix(m_, h_, i / bands); b = mix(m_, h_, min(bands, i + 1) / bands)
            layer.put(x, y, dither(a, b, k - i, x + OFF, y + OFF))


def stars(layer: Layer, pts, c=hx(0xFFF4D6)):
    for (x, y) in pts:
        layer.put(x, y, c)


@dataclass
class Icon:
    bg: Layer
    fg: Layer
    mono: Layer

    def composite(self) -> Layer:
        return self.bg.copy().over(self.fg)


# ---------------------------------------------------------------------------------------------------------- concepts
CONCEPTS: dict = {}


def concept(id, sl, en, line):
    def deco(fn):
        CONCEPTS[id] = dict(id=id, sl=sl, en=en, line=line, draw=fn)
        return fn
    return deco


# ---------------------------------------------------------------------------------------------------------- shared parts
DOTS = [(0, 0), (2, 0), (4, 0)]


def bubble(layer: Layer, x0, y0, w, h, tail="left", paper=CREAM, ink=INK, dots=True, r=3):
    """A speech bubble: paper, its shade along the bottom, a 1 px ink line round it, the tail under it, three dots."""
    x0, y0 = int(x0), int(y0)
    x1, y1 = x0 + w - 1, y0 + h - 1
    body = rrect(x0, y0, x1, y1, r)
    tmp = Layer()
    tmp.fill(body, paper[0])
    tails = {
        "left": [(2, 5), (2, 4), (1, 2)],
        "down": [(w // 2 - 2, w // 2 + 1), (w // 2 - 1, w // 2), (w // 2 - 1, w // 2 - 1)],
        "downleft": [(w // 2 - 2, w // 2), (w // 2 - 3, w // 2 - 2), (w // 2 - 4, w // 2 - 4)],
        "downright": [(w // 2 - 1, w // 2 + 1), (w // 2 + 1, w // 2 + 2), (w // 2 + 3, w // 2 + 3)],
    }
    for i, (a, b) in enumerate(tails[tail]):
        tmp.rect(x0 + a, y1 + 1 + i, x0 + b, y1 + 1 + i, paper[0])
    for x in range(x0, x1 + 1):        # the shade: the body's bottom row
        if tmp.get(x, y1) is not None:
            tmp.put(x, y1, paper[1])
    if ink is not None:
        tmp.outline(ink)
    layer.over(tmp)
    if dots:
        dx = x0 + (w - 5) // 2; dy = y0 + (h - 2) // 2
        for (a, b) in DOTS:
            layer.put(dx + a, dy + b, ink or INK)
    return tmp


def bubble_mono(layer: Layer, x0, y0, w, h, tail):
    """The bubble's silhouette for the themed icon, its dots cut out."""
    b = Layer(); bubble(b, x0, y0, w, h, tail=tail, ink=None, dots=False)
    layer.over(b.mask_of())
    dx = int(x0) + (w - 5) // 2; dy = int(y0) + (h - 2) // 2
    for (a, c) in DOTS:
        layer.put(dx + a, dy + c, None)


def mountains(layer: Layer, kind: str, y_base: int, x0=-OFF, x1=N - OFF, col=None, snow=True, night=False, scale=1.0):
    """The range at the horizon: its silhouette from (x, height) peaks, snow on the tops."""
    peaks = {
        "julian": [(-6, 6), (2, 9), (8, 6), (14, 12), (20, 8), (27, 10), (33, 6), (40, 8)],
        "prealps": [(-6, 5), (3, 7), (12, 9), (21, 7), (30, 8), (40, 6)],
        "karawanken": [(-6, 6), (4, 9), (13, 14), (22, 9), (31, 10), (40, 7)],
        "fells": [(-6, 4), (5, 6), (15, 7), (25, 6), (35, 5), (44, 4)],
        "forest": [(-6, 5), (6, 7), (16, 10), (24, 7), (34, 8), (42, 5)],
    }[kind]
    round_ = kind == "fells"
    c = col or (hx(0x6F86B4), hx(0x5A6E9C))

    def h_at(x):
        best = 0
        for (px, ph) in peaks:
            v = ph - ((x - px) ** 2) / 10.0 if round_ else ph - abs(x - px) * (0.75 if kind != "karawanken" else 0.9)
            best = max(best, v)
        return best
    for x in range(x0, x1):
        h = int(round(h_at(x) * scale))
        for y in range(y_base - h, y_base + 1):
            layer.put(x, y, c[0] if (x + y) % 7 else c[1])
        if snow and not round_ and h >= 7 * scale:
            for y in range(y_base - h, y_base - h + (2 if h >= 9 * scale else 1)):
                layer.put(x, y, SNOW[1] if night else SNOW[0])


def spruce(layer: Layer, x, base, h, pal=PINE, snow=False, lights=False):
    """A spruce: tiers of three rows, each narrowing upwards, on a short trunk, lit from the left; snow on each tier."""
    tiers = max(1, h // 3)
    for j in range(h):
        y = base - 1 - j
        tier, k = divmod(j, 3)
        full = max(1, round((tiers - tier) * 1.25 + 0.5))
        half = max(0, full - k) if j < h - 1 else 0
        for i in range(-half, half + 1):
            c = pal[0] if i == -half else pal[1] if i <= 0 else pal[2]
            if snow and (j == h - 1 or (k == 2 and -half <= i <= -half + 1 and h >= 9)):
                c = SNOW[0] if j == h - 1 else SNOW[1]
            layer.put(x + i, y, c)
        if lights and k == 1 and half >= 1:
            layer.put(x - half + (tier % 2), y, LIGHTS[tier % 4])
            layer.put(x + half - 1, y, LIGHTS[(tier + 2) % 4])
    layer.put(x, base, LOG[2])


# ---- the festivals' touches ------------------------------------------------------------------------------------
LIGHTS = (hx(0xFF4A4A), hx(0xFFD74A), hx(0x5AD45A), hx(0x5AB4FF))
CONFETTI = (hx(0xFF5A8A), hx(0xFFD74A), hx(0x5AD45A), hx(0x5AB4FF), hx(0xFF9A3A), hx(0xC07AFF))


def string_lights(layer: Layer, pts, step=2):
    """Coloured bulbs every [step] pixels along a path of points."""
    for i, (x, y) in enumerate(pts[::step]):
        layer.put(x, y, LIGHTS[i % 4])


def confetti(layer: Layer, pts):
    for i, (x, y) in enumerate(pts):
        layer.put(x, y, CONFETTI[(i * 5) % len(CONFETTI)])
        if i % 3 == 0: layer.put(x + 1, y, CONFETTI[(i * 5) % len(CONFETTI)])


CONFETTI_PTS = [(2, 3), (7, 1), (12, 4), (24, 2), (30, 5), (33, 9), (4, 9), (29, 13), (1, 15), (34, 17), (9, 7), (20, 1),
                (26, 9), (-3, 6), (38, 4), (16, 0), (31, 1), (5, 13)]


def pumpkin(layer: Layer, x, y):
    """A pumpkin 4 × 3 with its stem, standing on y."""
    layer.sprite(x, y - 3, ["..g.", "oOoO", "OoOo", ".oO."], {"g": LEAF, "o": hx(0xF08A2A), "O": hx(0xC8601A)})


def grapes(layer: Layer, x, y):
    layer.sprite(x, y, ["gg.", "ppp", "pPp", ".p."], {"g": LEAF, "p": hx(0x7A3A8E), "P": hx(0x9A5AAE)})


def small_fire(layer: Layer, x, base):
    """A midsummer fire on a hill: logs and a flame 5 tall, two sparks."""
    layer.rect(x - 2, base, x + 2, base, LOG[2])
    fire_flame(layer, x + 0.5, base - 1, 6, 2.2)
    layer.put(x - 2, base - 7, FLAME[1]); layer.put(x + 2, base - 8, FLAME[1])


# ---- shared village pieces (front views) ------------------------------------------------------------------------
# Towers, 5 px wide, standing on their bottom row: t u v the top (light, middle, dark), w m d the wall, k an opening.
TOWERS = {
    "spire": ["..t..", "..t..", ".tv..", ".tuv.", ".tuv.", ".tuv.", ".wmd.", ".wkd.", ".wmd.", ".wmd.", ".wkd.", ".wmd.", ".wmd."],
    "campanile": [".tuv.", "tuuuv", "wwmmd", "wkmkd", "wkmkd", "wmmmd", "wmmmd", "wmkmd", "wmmmd", "wmmmd", "wmmmd", "wmmmd"],
    "onion": ["..t..", ".tuv.", "tuuuv", "tuuuv", ".tuv.", ".wmd.", ".wkd.", ".wmd.", ".wmd.", ".wkd.", ".wmd.", ".wmd."],
    "battlements": ["w.w.d", "wwmdd", "wkmkd", "wmmmd", "wmmmd", "wmmmd", "wmkmd", "wmmmd", "wmmmd", "wmmmd", "wmmmd"],
    "pyramid": ["..t..", ".tuv.", "tuuuv", ".wmd.", ".wkd.", ".wmd.", ".wmd.", ".wkd.", ".wmd.", ".wmd.", ".wmd."],
}
# Houses, a gable to the front: r s q the roof (light, middle, dark), w m d the wall, h a shutter, k a window, D the door.
HOUSES = {
    "small": ["...r...", "..rrs..", ".rrrss.", "rrrrsss", ".wwwmd.", ".hkhmd.", ".wwwmd.", ".wwDmd."],
    "wide": ["....r....", "...rrs...", "..rrrss..", ".rrrrsss.", "rrrrrssss", ".wwwwmmd.", ".hkhwhkd.", ".wwwwmmd.", ".wwwDmmd."],
    "tent": ["...c...", "..ccC..", "..ccC..", ".cccCC.", ".ccdCC.", "cccdCCC", "cccdCCC"],
}
CANVAS = (hx(0xF1E3BA), hx(0xD9C28E), hx(0xB39C6A))


def look_pal(look: Look, night=False, season="summer"):
    """The palette of a culture's tower and houses (night: the windows lit)."""
    sh = look.shutter or (look.wall[2], look.wall[2])
    win = LIT[0] if night else GLASS[0]
    roof = look.roof
    if season == "winter":
        roof = (SNOW[0], SNOW[1], SNOW[2])
    return {
        "t": look.tower_top[0], "u": look.tower_top[1], "v": look.tower_top[2],
        "w": look.wall[0], "m": look.wall[1], "d": look.wall[2], "k": win,
        "r": roof[0], "s": roof[1], "q": roof[2], "h": sh[1] if look.shutter else look.wall[1], "D": DOOR,
        "c": CANVAS[0], "C": CANVAS[1],
    }


def tower_pal(look: Look, night=False, season="summer"):
    p = look_pal(look, night, season)
    p.update({"w": look.tower_wall[0], "m": look.tower_wall[1], "d": look.tower_wall[2], "k": INK if not night else LIT[0]})
    if season == "winter" and look.tower in ("campanile", "onion", "pyramid"):
        p.update({"t": SNOW[0], "u": SNOW[1]})
    return p


def house(layer: Layer, x, base, look: Look, kind="small", night=False, season="summer", wall=None):
    rows = HOUSES[kind]
    pal = look_pal(look, night, season)
    if wall: pal.update({"w": wall[0], "m": wall[1], "d": wall[2]})
    if kind == "tent":
        pal.update({"d": LIT[0] if night else DOOR})
    top = base - len(rows) + 1
    layer.sprite(x, top, rows, pal)
    if kind == "tent":
        return len(rows[0])
    if look.timber:         # the upper storey of dark timber: the gable's first wall row under the roof
        j = next(k for k, r in enumerate(rows) if "w" in r)
        for i, ch in enumerate(rows[j]):
            if ch in "wmd":
                layer.put(x + i, top + j, WOOD[2] if ch != "w" else WOOD[1])
    if look.stone:          # stone: a few darker stones in the wall, white window frames
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == "w" and (i * 3 + j * 5) % 7 == 0:
                    layer.put(x + i, top + j, look.wall[1])
                if ch == "h":
                    layer.put(x + i, top + j, WALL[0])
    return len(rows[0])


def sun(layer: Layer, x, y, time):
    if time == "day":
        layer.sprite(x, y, [".gg.", "gggg", "gggg", ".gg."], {"g": GOLD[0]})
    elif time == "night":
        layer.sprite(x, y, [".mm.", "m...", "m...", ".mm."], {"m": hx(0xF4EFD8)})
    elif time == "dusk":
        layer.sprite(x, y, [".gg.", "gggg"], {"g": hx(0xFFC86A)})


def ground(season, time):
    """The grass of a season in a light: light, middle, dark, highlight."""
    g = GRASS[season]
    if time == "night": return tuple(shade(c, 0.48) for c in g)
    if time == "dusk": return tuple(mix(c, hx(0x8A5A6E), 0.3) for c in g)
    return g


def clip_to(layer: Layer, src: Layer, test):
    for y in range(N):
        for x in range(N):
            if src.px[y][x] is not None and test(x - OFF + 0.5, y - OFF + 0.5):
                layer.px[y][x] = src.px[y][x]


# ---- 1. Ogenj · The fire -----------------------------------------------------------------------------------------
def fire_flame(layer: Layer, cx, base, h, w, sway=0.0, mask=None):
    """A flame of the FLAME palette standing on (cx, base), h tall, w its half-width at the widest; the hottest inside."""
    for j in range(h):
        t = j / max(1, h - 1)
        half = w * (1 - t) ** 0.75 * min(1.0, 0.55 + t * 2.2)
        sh = math.sin(t * 4.2 + sway) * 0.9 * t
        y = base - j
        for x in range(int(cx - w - 2), int(cx + w + 3)):
            d = abs(x + 0.5 - (cx + sh)) / max(half, 0.45)
            if d > 1:
                continue
            if mask is not None:
                mask.put(x, y, None if (d < 0.42 and 0.12 < t < 0.6) else WHITE)
                continue
            if d > 0.78: k = 4
            elif d > 0.55: k = 3
            elif d > 0.3 or t > 0.62: k = 2
            elif t > 0.3: k = 1
            else: k = 0
            if t > 0.86: k = max(k, 3)
            layer.put(x, y, FLAME[k])


FIRE_SKY = (hx(0x0B1A40), hx(0x183470), hx(0x34558F))


@concept("fire", "Ogenj", "The fire", "The village fire at nightfall, its smoke rising into a speech bubble: where the village meets and talks.")
def draw_fire(v: Variant) -> Icon:
    bg, fg, mono = Layer(), Layer(), Layer()
    look = v.look
    time = v.time or "night"
    top, mid, hor = FIRE_SKY if time == "night" else SKY[time]
    sky(bg, time, top=top, mid=mid, hor=hor)
    if time == "night":
        stars(bg, [(3, 4), (31, 7), (6, 12), (28, 1), (33, 13), (1, 18), (-4, 8), (40, 3), (8, 1)])
    elif time == "day":
        sun(bg, 28, 4, "day")
    if v.fest == "carnival":
        confetti(bg, CONFETTI_PTS)
    tree_c = {"night": (hx(0x24504E), hx(0x173A3A), hx(0x0F2A2C)), "dusk": (hx(0x4A4A6E), hx(0x38385A), hx(0x2A2A48)),
              "day": (hx(0x4E9460), hx(0x3B7A4A), hx(0x2C5E3A))}[time]
    snow = v.season == "winter"
    # far off: the culture's tower over the trees, its window lit
    far = Layer()
    tc = {"night": hx(0x1E3460), "dusk": hx(0x4A3E6E), "day": hx(0x7A96C2)}[time]
    tp = {k: tc for k in "tuvwmd"}; tp["k"] = LIT[0] if time != "day" else tc
    rows = TOWERS[look.tower]
    far.sprite(27, 22 - len(rows) + 1, rows, tp)
    bg.over(far)
    # the woods round the clearing, the nearest at the sides
    for (x, h) in [(-6, 22), (-1, 19), (3, 15), (7, 10), (11, 7), (24, 7), (31, 13), (35, 18), (40, 22)]:
        spruce(bg, x, 25, h, pal=(tree_c[0], tree_c[1], tree_c[2], tree_c[2]), snow=snow, lights=v.fest == "lights" and h >= 13)
    bg.rect(-OFF, 25, N - OFF, N - OFF, tree_c[2])
    # the clearing, lit by the fire in rings
    g = ground(v.season, time if time != "day" else "day")
    if time == "night": g = tuple(shade(c, 0.9) for c in g)
    lit = not v.waiting
    c_mid = mix(g[1], FLAME[2], 0.28 if lit else 0.1); c_in = mix(g[0], FLAME[1], 0.5 if lit else 0.18)
    clearing = ellipse(18, 28.5, 15.5, 6)
    fg.fill(clearing, lambda x, y: g[1] if (x * 3 + y * 7) % 13 else g[2])
    fg.fill(ellipse(18, 28.3, 11.5, 4.6), lambda x, y: c_mid if (x * 3 + y * 7) % 13 else g[1])
    fg.fill(ellipse(18, 28.0, 7.5, 3.0), c_in)
    if v.season == "spring":
        for (x, y) in [(5, 28), (8, 31), (29, 27), (26, 31), (12, 33), (22, 33)]:
            fg.put(x, y, hx(0xF6E27A) if (x + y) % 2 else hx(0xF4A6C8))
    if v.season == "autumn":
        for (x, y) in [(5, 29), (9, 32), (28, 28), (25, 32), (31, 30), (15, 33)]:
            fg.put(x, y, FOLIAGE["autumn"][1])
    if v.fest == "harvest":
        pumpkin(fg, 5, 30); pumpkin(fg, 27, 31); grapes(fg, 9, 30)
    # the stones round the fire
    for i in range(18):
        a = i / 18 * 2 * math.pi
        x = round(18 + math.cos(a) * 6.8 - 0.5); y = round(28 + math.sin(a) * 2.7 - 0.5)
        fg.put(x, y, STONE[0] if math.sin(a) < 0.2 else STONE[1])
        if math.sin(a) > 0.3: fg.put(x, y + 1, STONE[3])
    smoke = [(18, 24), (19, 23), (19, 22), (18, 21), (17, 20), (17, 19), (18, 18)]
    if v.waiting:
        # embers: the fire low and glowing, a thin curl of smoke, a log laid ready by the stones
        for (x, y, k) in [(16, 27, 3), (17, 27, 2), (18, 27, 1), (19, 27, 2), (20, 27, 3), (17, 26, 3), (18, 26, 2), (19, 26, 3),
                          (15, 28, 4), (21, 28, 4), (16, 28, 3), (17, 28, 2), (18, 28, 2), (19, 28, 3), (20, 28, 3)]:
            fg.put(x, y, FLAME[k])
        for (x, y) in smoke:
            fg.put(x, y, hx(0xB8BCC8) if y > 20 else hx(0x9AA0B0))
        fg.rect(24, 31, 30, 31, LOG[1]); fg.rect(24, 32, 30, 32, LOG[2]); fg.put(30, 31, WOOD[0]); fg.put(30, 32, WOOD[1])
        mono.rect(15, 26, 21, 28, WHITE)
        for (x, y) in smoke:
            mono.put(x, y, WHITE)
        return Icon(bg, fg, mono)
    big = v.fest == "bonfire"
    fh, fw = (17, 5.8) if big else (12, 4.2)
    for i in range(-4, 5):   # the logs, crossed
        fg.put(18 + i, 28 + (i // 3), LOG[1] if i < 0 else LOG[2])
        fg.put(18 - i, 28 + (i // 3), LOG[0] if i < 0 else LOG[2])
    fg.put(13, 26, WOOD[0]); fg.put(23, 26, WOOD[0])
    fire_flame(fg, 18, 27, fh, fw)
    for (x, y) in ([(13, 17), (23, 15), (15, 13)] if not big else [(10, 13), (26, 11), (13, 8), (23, 6), (17, 4)]):
        fg.put(x, y, FLAME[1])
    if not big:   # the smoke rising into a speech bubble
        fg.put(19, 14, hx(0x9AA0B0))
        bubble(fg, 12, 3, 13, 8, tail="down", r=3)
        bubble_mono(mono, 12, 3, 13, 8, "down")
    fire_flame(None, 18, 27, fh, fw, mask=mono)
    for i in range(-5, 6):
        for dy in (0, 1):
            mono.put(18 + i, 28 + dy + (i // 3), WHITE); mono.put(18 - i, 28 + dy + (i // 3), WHITE)
    return Icon(bg, fg, mono)


# ---- 2. Hiša · The talking house ---------------------------------------------------------------------------------
@concept("house", "Hiša", "The talking house", "A house of the learner's village on its patch of grass, its chimney puffing a speech bubble.")
def draw_house(v: Variant) -> Icon:
    bg, fg, mono = Layer(), Layer(), Layer()
    look = v.look
    time = v.time or "day"
    night = time == "night"
    top, mid, hor = SKY[time]
    if time == "day":
        top, mid, hor = hx(0x2F6FC0), hx(0x5E9AD8), hx(0xA8D2F0)
    sky(bg, time, top=top, mid=mid, hor=hor)
    if night:
        stars(bg, [(3, 5), (31, 4), (6, 13), (33, 15), (-3, 9), (40, 6), (27, 1)])
    if v.fest == "carnival":
        confetti(bg, CONFETTI_PTS)
    # the patch of grass: an iso diamond with an earth edge
    g = ground(v.season, time)
    dirt = (hx(0x8C6440), hx(0x6E4C30), hx(0x533823))
    if night: dirt = tuple(shade(c, 0.6) for c in dirt)
    cx, cy = 17, 27
    diamond = lambda x, y: abs(x - cx) / 14.0 + abs(y - cy) / 6.2 <= 1
    below = lambda x, y: any(diamond(x, y - k) for k in range(1, 3)) and not diamond(x, y)
    fg.fill(below, lambda x, y: dirt[1] if x + 0.5 < cx else dirt[2])
    fg.fill(diamond, lambda x, y: g[1] if (x * 3 + y * 7) % 11 else g[2])
    fg.fill(lambda x, y: diamond(x, y) and not diamond(x, y - 1), g[3] if v.season != "winter" else SNOW[0])
    # the house: the gable to the front left, the long wall to the front right (2:1, as the map draws it)
    fx, fy = 15, 29
    W, L, H, R = 4, 5, 6, 4
    def gab(a, h): return (fx - 2 * a, fy - a - h)
    def side(b, h): return (fx + 2 * b, fy - b - h)
    wl, wm, wd = look.wall
    hs = Layer()
    hs.poly([gab(0, 0), gab(W, 0), gab(W, H), gab(W / 2, H + R), gab(0, H)], wl)
    hs.poly([side(0, 0), side(L, 0), side(L, H), side(0, H)], wm)
    for y in range(-OFF, N - OFF):
        for x in range(-OFF, N - OFF):
            c = hs.get(x, y)
            if c is None: continue
            if look.stone and (x * 5 + y * 3) % 7 == 0:
                hs.put(x, y, wm if c == wl else wd)
            if look.timber and inside([gab(0, 4), gab(W, 4), gab(W, H + 9), gab(0, H + 9)], x + 0.5, y + 0.5):
                hs.put(x, y, WOOD[1])
            if look.timber and inside([side(0, 4), side(L, 4), side(L, H + 1), side(0, H + 1)], x + 0.5, y + 0.5):
                hs.put(x, y, WOOD[2])
    win = LIT[0] if (night or time == "dusk") else GLASS[0]
    sh = look.shutter
    hs.poly([gab(1.5, 2), gab(2.5, 2), gab(2.5, 4), gab(1.5, 4)], win)
    if sh:
        hs.poly([gab(1, 2), gab(1.5, 2), gab(1.5, 4), gab(1, 4)], sh[1])
        hs.poly([gab(2.5, 2), gab(3, 2), gab(3, 4), gab(2.5, 4)], sh[1])
    hs.poly([gab(1.75, H + 1), gab(2.25, H + 1), gab(2.25, H + 2.5), gab(1.75, H + 2.5)], win)
    hs.poly([side(1, 0), side(2, 0), side(2, 4), side(1, 4)], DOOR)
    hs.poly([side(3, 1.5), side(4, 1.5), side(4, 3.5), side(3, 3.5)], win)
    if sh:
        hs.poly([side(2.5, 1.5), side(3, 1.5), side(3, 3.5), side(2.5, 3.5)], sh[0])
        hs.poly([side(4, 1.5), side(4.5, 1.5), side(4.5, 3.5), side(4, 3.5)], sh[0])
    if look.balcony and v.season == "summer":
        for a in (1, 2, 3):
            x, y = gab(a, 1.6); hs.put(x - 1, y, GERANIUM[0]); hs.put(x - 2, y, LEAF)
    # the roof: its long plane over the side, its edge over the gable
    roof = look.roof if v.season != "winter" else SNOW
    P = gab(W / 2, H + R); P2 = (P[0] + 2 * L, P[1] - L)
    eave0 = gab(0, H); eave1 = side(L, H)
    hs.poly([(eave0[0] + 1, eave0[1] + 1), (eave1[0] + 2, eave1[1]), (P2[0] + 1, P2[1] - 1), (P[0], P[1] - 1)], roof[1])
    for a in range(0, 2 * W + 3):
        t = a / 2
        if t <= W / 2:
            x, y = gab(-0.5 + t, H + 0.5 + t * (R + 0.5) / (W / 2))
        else:
            x, y = gab(-0.5 + t, H + 0.5 + (W + 1 - t) * (R + 0.5) / (W / 2 + 0.5))
        hs.put(int(x), int(y), roof[0])
    if v.season != "winter":
        for y in range(-OFF, N - OFF):
            for x in range(-OFF, N - OFF):
                if hs.get(x, y) == roof[1] and (x + 2 * y) % 6 in (0, 1):
                    hs.put(x, y, roof[2])
    # the chimney, on the ridge near the gable
    chx, chy = int(P[0] + 3), int(P[1] - 2)
    hs.rect(chx, chy - 3, chx + 1, chy + 1, STONE[1]); hs.rect(chx + 2, chy - 2, chx + 2, chy + 1, STONE[2])
    hs.rect(chx, chy - 3, chx + 2, chy - 3, STONE[0])
    if v.fest == "lights":   # bulbs along the eave and the gable's edge
        string_lights(hs, [(int(eave0[0] + 1 + k), int(eave0[1] + 1 - k / 2)) for k in range(0, 2 * L + 1)], step=2)
    hs.outline(INK)
    fg.over(hs)
    if v.fest == "bonfire":
        small_fire(fg, 6, 26)
    if v.fest == "harvest":
        pumpkin(fg, 22, 29); pumpkin(fg, 26, 28)
    if v.waiting:   # a wisp of smoke instead of the bubble
        for (x, y) in [(chx + 1, chy - 5), (chx + 2, chy - 6), (chx + 2, chy - 7), (chx + 1, chy - 8)]:
            fg.put(x, y, hx(0xC8CCD6))
    else:
        bx, by = chx - 5, chy - 13
        bubble(fg, bx, by, 13, 8, tail="down", r=3)
    # monochrome: the roof and the chimney solid, the walls drawn by their outline with the windows and the door, the
    # patch's earth edge, the bubble
    solid = set(roof) | {STONE[0], STONE[1], STONE[2], INK, win, DOOR}
    m = hs.mask_of(keep=lambda c: c in solid)
    for y in range(fy - H + 1, fy):   # the corner between the gable and the long wall
        m.put(fx, y, WHITE)
    m.fill(lambda x, y: below(x, y) and x > 4 and x < 30, WHITE)
    mono.over(m)
    if not v.waiting:
        bubble_mono(mono, chx - 5, chy - 13, 13, 8, "down")
    return Icon(bg, fg, mono)


# ---- 3. Mehurček · The bubble window -----------------------------------------------------------------------------
def skyline(layer: Layer, v: Variant, clip, x0, y_ground, w):
    """A village's skyline inside clip: the sky, the range, the hill, the church tower and two houses (the culture's)."""
    look = v.look
    time = v.time or ("dusk" if v.waiting else "day")
    night = time == "night"
    tmp = Layer()
    sky(tmp, time, y0=x0, y1=y_ground)
    if v.waiting:
        for (a, c) in DOTS:
            tmp.rect(x0 + w // 2 - 3 + a * 1, x0 + 2, x0 + w // 2 - 3 + a * 1, x0 + 3, None)
    else:
        sun(tmp, x0 + w - 6, x0 + 2, time)
    if night:
        stars(tmp, [(x0 + 2, x0 + 3), (x0 + 7, x0 + 1), (x0 + 12, x0 + 4), (x0 + 15, x0 + 1)])
    mc = {"day": (hx(0x9DB6DA), hx(0x8EA8D0)), "dusk": (hx(0x8A78AE), hx(0x7C6AA0)), "night": (hx(0x2E4478), hx(0x283C6C))}[time]
    mountains(tmp, look.range, y_ground - 3, x0 - 2, x0 + w + 2, col=mc, night=night, scale=0.55)
    g = ground(v.season, time)
    vil = Layer()
    tp = tower_pal(look, night, v.season)
    rows = TOWERS[look.tower]
    vil.sprite(x0 + 2, y_ground - len(rows) + 1, rows, tp)
    house(vil, x0 + 8, y_ground, look, "small", night, v.season)
    second = {"primorska": LOOKS["friuli"].wall, "base": (hx(0xF2DDA0), hx(0xDDC27E), hx(0xB49A5E))}.get(look.id)
    house(vil, x0 + 15, y_ground, look, "wide", night, v.season, wall=second)
    if v.fest == "lights":
        string_lights(vil, [(x0 + 9 + k, y_ground - 5) for k in range(0, 16)], step=2)
    vil.outline(INK)
    tmp.over(vil)
    hill = lambda x, y: y >= y_ground + 1 + ((x - (x0 + w * 0.4)) ** 2) / 120.0
    tmp.fill(hill, lambda x, y: g[1] if (x + 2 * y) % 5 else g[2])
    tmp.fill(lambda x, y: hill(x, y) and not hill(x, y - 1), g[0])
    if v.fest == "harvest":
        pumpkin(tmp, x0 + 2, y_ground + 3); pumpkin(tmp, x0 + 17, y_ground + 3)
    if v.fest == "bonfire":
        small_fire(tmp, x0 + w - 3, y_ground + 1)
    if v.waiting:   # the village waits for your answer: "…" in its sky
        for (a, c) in DOTS:
            tmp.rect(x0 + w // 2 - 3 + a, x0 + 3, x0 + w // 2 - 3 + a, x0 + 3, CREAM[0])
    clip_to(layer, tmp, clip)


@concept("bubble", "Mehurček", "The bubble window", "A speech bubble you look through into the village: its tower, roofs and hills.")
def draw_bubble(v: Variant) -> Icon:
    bg, fg, mono = Layer(), Layer(), Layer()
    sky(bg, "night", top=hx(0x0B2E6E), mid=hx(0x0B4EA2), hor=hx(0x1F66B8))
    if v.fest == "carnival":
        confetti(bg, CONFETTI_PTS + [(2, 30), (33, 28), (30, 33), (14, 33), (22, 31)])
    body = rrect(4, 4, 31, 25, 8)
    tail = [(7, 26, 12), (7, 27, 11), (6, 28, 9), (6, 29, 8), (5, 30, 6)]
    shape = Layer()
    shape.fill(body, CREAM[0])
    for (a, y, b) in tail:
        shape.rect(a, y, b, y, CREAM[0])
    window = rrect(6, 6, 29, 23, 6)
    skyline(shape, v, window, 6, 20, 24)
    for y in range(N - OFF):
        for x in range(-OFF, N - OFF):
            if shape.get(x, y) == CREAM[0] and shape.get(x, y + 1) is None:
                shape.put(x, y, CREAM[1])
    if v.fest == "lights":   # fairy lights round the ring
        ring = [(x, y) for y in range(4, 26) for x in range(4, 32)
                if body(x + 0.5, y + 0.5) and not rrect(5, 5, 30, 24, 7)(x + 0.5, y + 0.5)]
        ring.sort(key=lambda p: math.atan2(p[1] - 14.5, p[0] - 17.5))
        for i, (x, y) in enumerate(ring[::3]):
            shape.put(x, y, LIGHTS[i % 4])
    shape.outline(INK)
    fg.over(shape)
    m = Layer()
    m.fill(body, lambda x, y: WHITE if not window(x + 0.5, y + 0.5) else None)
    for (a, y, b) in tail:
        m.rect(a, y, b, y, WHITE)
    sil = Layer()
    rows = TOWERS[v.look.tower]
    sil.sprite(8, 20 - len(rows) + 1, rows, {k: WHITE for k in "tuvwmdk"})
    sil.sprite(14, 20 - 7, HOUSES["small"], {k: WHITE for k in "rsqwmdhkD"})
    sil.sprite(21, 20 - 8, HOUSES["wide"], {k: WHITE for k in "rsqwmdhkD"})
    sil.fill(lambda x, y: window(x, y) and y >= 21 + ((x - 15.6) ** 2) / 120.0, WHITE)
    for (x, y) in [(16, 18), (24, 18), (16, 20), (25, 20), (10, 10), (10, 15)]:
        sil.put(x, y, None)
    mono.over(m).over(sil)
    return Icon(bg, fg, mono)


# ---- 4. Sosed · The neighbour ------------------------------------------------------------------------------------
HEAD = [
    "...hhhhhh...",
    ".hhhhhhhhhh.",
    "hhhhhhhhhhhh",
    "hhHsssssHHhh",
    "hHssssssssHh",
    "hssesssssesh",
    "hssesssssesh",
    "hsbssssssbsh",
    ".ssssmmssss.",
    "..SssssssS..",
    "...SSSSSS...",
]
HAND = [".s.s.s", ".s.s.s", "ssssss", "ssssss", ".ssss.", "..ss.."]
WREATH = ["f.g.f.g.f.g.", "gfgfgfgfgfgf"]


@concept("neighbour", "Sosed", "The neighbour", "A villager waves from their window, flowers on the sill: someone in the village says hello.")
def draw_neighbour(v: Variant) -> Icon:
    bg, fg, mono = Layer(), Layer(), Layer()
    look = v.look
    time = v.time or "day"
    lit_room = time in ("night", "dusk")
    wl, wm, wd = look.wall
    for y in range(-OFF, N - OFF):     # the wall, full bleed: the culture's plaster, stone, logs or timber
        for x in range(-OFF, N - OFF):
            if look.stone:
                row = y // 3
                joint = y % 3 == 2 or (x + (row % 2) * 3) % 6 == 5
                c = wd if joint else (wl if (x // 6 + row) % 3 else wm)
            else:
                c = wm if (x * 7 + y * 13) % 23 == 0 else wl
            if look.timber and 5 <= y < 9:
                c = WOOD[2] if x % 6 == 0 or y == 8 else WOOD[1]
            roof = look.roof if v.season != "winter" else SNOW
            if y < 5:      # the eave across the top, in the culture's roof, and the plinth: they frame a pale wall
                c = roof[2] if y == 4 else (roof[0] if (x + (y % 2) * 2) % 4 else roof[1])
            elif y == 5 and not look.timber:
                c = shade(wl, 0.8)
            elif y >= 32:
                c = STONE[3] if y == 32 else (STONE[1] if (x + (y % 2) * 3) % 6 else STONE[2])
            bg.put(x, y, c)
    if time != "day":
        f, tint = (0.5, hx(0x1A2A5A)) if time == "night" else (0.8, hx(0x7A4A6E))
        for y in range(-OFF, N - OFF):
            for x in range(-OFF, N - OFF):
                bg.put(x, y, mix(shade(bg.get(x, y), f), tint, 0.3))
    if v.fest == "carnival":
        confetti(bg, CONFETTI_PTS[:10] + [(1, 26), (34, 30), (3, 33), (31, 24)])
    x0, y0, x1, y1 = 10, 12, 25, 27
    frame = WALL[0] if look.stone else WOOD[0] if look.id in ("base", "kaernten") else STONE[0]
    frame_d = WALL[2] if look.stone else WOOD[2] if look.id in ("base", "kaernten") else STONE[2]
    if look.id == "base": frame, frame_d = WOOD[1], WOOD[3]
    room = LIT[0] if lit_room else hx(0x4A3A44)
    room_d = hx(0xE8A848) if lit_room else hx(0x3A2C36)
    win = Layer()
    win.rect(x0 - 1, y0 - 1, x1 + 1, y1 + 1, frame)
    win.rect(x0, y0, x1, y1, room)
    win.rect(x0, y0, x1, y0 + 1, room_d)
    sh = look.shutter
    if sh:
        for (sx0, sx1) in ((x0 - 7, x0 - 2), (x1 + 2, x1 + 7)):
            win.rect(sx0, y0 - 1, sx1, y1 + 1, sh[0])
            for y in range(y0, y1 + 1, 2):
                win.rect(sx0 + 1, y, sx1 - 1, y, sh[1])
    win.rect(x0 - 2, y1 + 2, x1 + 2, y1 + 2, frame_d)   # the sill
    if v.fest == "lights":
        string_lights(win, [(x, y0 - 1) for x in range(x0 - 1, x1 + 2)], step=2)
        string_lights(win, [(x0 - 1, y) for y in range(y0 + 1, y1, 2)] + [(x1 + 1, y) for y in range(y0 + 1, y1, 2)], step=1)
    win.outline(INK)
    fg.over(win)
    # the neighbour: the head, the shoulders, an arm up and the hand waving out of the window
    hair = {"base": hx(0x8A4A22), "primorska": hx(0x4A2E1C), "friuli": hx(0x22181A), "kaernten": hx(0xD7B25A), "lakeland": hx(0xB5502A)}[look.id]
    shirt = {"base": hx(0x3E8E4E), "primorska": hx(0x2E63B0), "friuli": hx(0xC8392F), "kaernten": hx(0x3E8E4E), "lakeland": hx(0x7A4E9A)}[look.id]
    pal = {"h": hair, "H": shade(hair, 0.75), "s": SKIN[0], "S": SKIN[1], "e": INK, "b": hx(0xF08A8A), "m": hx(0xA8402A),
           "f": hx(0xF4A6C8), "g": LEAF}
    person = Layer()
    person.rect(x0 + 2, y1 - 2, x1 - 2, y1, shirt)
    person.rect(x0 + 4, y1 - 3, x1 - 4, y1 - 3, shirt)
    person.sprite(x0 + 2, y0 + 3, HEAD, pal)
    if v.fest == "bonfire":   # midsummer: a wreath of flowers on the head
        person.sprite(x0 + 2, y0 + 3, WREATH, {"f": hx(0xF4A6C8), "g": LEAF})
        person.put(x0 + 4, y0 + 3, hx(0xF6E27A)); person.put(x0 + 10, y0 + 3, hx(0xF6E27A))
    arm = [(x1 - 2, y1 - 3), (x1 - 1, y1 - 4), (x1, y1 - 5), (x1, y1 - 6), (x1 + 1, y1 - 7), (x1 + 1, y1 - 8)]
    for (x, y) in arm:
        person.put(x, y, shirt); person.put(x + 1, y, shirt)
    person.sprite(x1 - 1, y0 - 1, HAND, pal)
    if v.fest == "harvest":
        grapes(person, x0 - 1, y1 - 3)
    person.outline(INK)
    fg.over(person)
    if v.season == "winter":
        fg.rect(x0 - 2, y1 + 1, x1 + 2, y1 + 1, SNOW[0])
    else:   # the flowers on the sill
        fl = {"spring": (hx(0xF6E27A), hx(0xF4A6C8)), "summer": GERANIUM, "autumn": (hx(0xE07A2A), hx(0xB04A22))}[v.season]
        for i, x in enumerate(range(x0 - 1, x1 + 2, 2)):
            fg.put(x, y1 + 1, LEAF); fg.put(x + 1, y1 + 1, fl[i % 2])
            if i % 2 == 0: fg.put(x, y1, fl[0])
    if not v.waiting:
        bubble(fg, 6, 3, 12, 8, tail="downright", r=3)
    # monochrome: the window's frame and the neighbour in it, the face's eyes and mouth cut out
    m = Layer()
    m.rect(x0 - 2, y0 - 2, x1 + 2, y1 + 2, WHITE)
    m.rect(x0, y0, x1, y1, None)
    pm = Layer()
    pm.sprite(x0 + 2, y0 + 3, HEAD, {k: WHITE for k in "hHsSbm"} | {"e": None})
    pm.sprite(x1 - 1, y0 - 1, HAND, {"s": WHITE})
    for (x, y) in arm:
        pm.put(x, y, WHITE); pm.put(x + 1, y, WHITE)
    pm.rect(x0 + 2, y1 - 2, x1 - 2, y1, WHITE)
    pm.rect(x0 + 4, y1 - 3, x1 - 4, y1 - 3, WHITE)
    for (x, y) in [(x0 + 5, y0 + 8), (x0 + 5, y0 + 9), (x0 + 11, y0 + 8), (x0 + 11, y0 + 9), (x0 + 7, y0 + 11), (x0 + 8, y0 + 11)]:
        pm.put(x, y, None)
    m.over(pm)
    if not v.waiting:
        bubble_mono(m, 6, 3, 12, 8, "downright")
    mono.over(m)
    return Icon(bg, fg, mono)


# ---- 5. Zvonik · The L of the village ----------------------------------------------------------------------------
L_TOPS = {   # the tower's top above its 6 px body, 8 wide: t u v (light, middle, dark)
    "spire": ["...tv...", "...tv...", "..tuvv..", "..tuvv..", "..tuvv..", ".ttuuvv.", ".ttuuvv."],
    "campanile": ["...tv...", "..ttvv..", ".ttuuvv.", "tttuuvvv"],
    "onion": ["...t....", "...tv...", "..tuuv..", ".tuuuuv.", ".tuuuuv.", "..tuuv..", "...uv...", "..tuvv.."],
    "battlements": [".w.ww.d.", ".wwwwdd."],
    "pyramid": ["...tv...", "..tuvv..", ".ttuuvv.", "tttuuvvv"],
}


@concept("ltower", "Zvonik", "The L of the village", "The bell tower and the long house beside it make an L, Lani's first letter; the bell rings a speech bubble.")
def draw_ltower(v: Variant) -> Icon:
    bg, fg, mono = Layer(), Layer(), Layer()
    look = v.look
    time = v.time or "day"
    night = time == "night"
    top, mid, hor = SKY[time]
    if time == "day":
        top, mid, hor = hx(0x2F6FC0), hx(0x5E9AD8), hx(0xA8D2F0)
    sky(bg, time, top=top, mid=mid, hor=hor)
    if night:
        stars(bg, [(3, 5), (31, 4), (6, 13), (33, 15), (-3, 9), (40, 6), (24, 2)])
    if v.fest == "carnival":
        confetti(bg, CONFETTI_PTS)
    g = ground(v.season, time)
    hill = lambda x, y: y >= 29 + ((x - 18) ** 2) / 90.0
    bg.fill(hill, lambda x, y: g[1] if (x * 3 + y * 5) % 9 else g[2])
    bg.fill(lambda x, y: hill(x, y) and not hill(x, y - 1), g[3] if v.season != "winter" else SNOW[0])
    if v.fest == "bonfire":
        small_fire(bg, 31, 29)
    wl, wm, wd = look.tower_wall
    hl, hm, hd = look.wall
    roof = look.roof if v.season != "winter" else SNOW
    ttop = look.tower_top
    if v.season == "winter" and look.tower in ("campanile", "onion", "pyramid", "spire"):
        ttop = SNOW
    Lyr = Layer()
    tx0, tx1, ty0, base = 8, 13, 13, 28
    Lyr.rect(tx0, ty0, tx1, base, wl)
    Lyr.rect(tx1, ty0, tx1, base, wm)
    Lyr.rect(tx1 + 1, 22, 28, base, hl)
    Lyr.rect(tx1 + 1, 22, 28, 22, hm)
    for y, (a, b) in zip(range(17, 22), [(15, 26), (14, 27), (14, 28), (14, 29), (14, 29)]):
        Lyr.rect(a, y, b, y, roof[0] if y == 17 else roof[1] if y < 21 else roof[2])
    if v.season != "winter":
        for y in (19,):
            for x in range(15, 29, 3):
                Lyr.put(x, y, roof[2])
    rows = L_TOPS[look.tower]
    Lyr.sprite(tx0 - 1, ty0 - len(rows), rows, {"t": ttop[0], "u": ttop[1], "v": ttop[2], "w": wl, "d": wd})
    open_c = LIT[0] if night or time == "dusk" else INK
    if look.tower == "campanile":   # the belfry's two arches
        Lyr.rect(tx0 + 1, ty0 + 1, tx0 + 1, ty0 + 3, open_c); Lyr.rect(tx0 + 4, ty0 + 1, tx0 + 4, ty0 + 3, open_c)
    else:
        Lyr.rect(tx0 + 2, ty0 + 2, tx0 + 3, ty0 + 4, open_c)           # the belfry
    Lyr.rect(tx0 + 2, ty0 + 8, tx0 + 3, ty0 + 9, GOLD[1])              # the clock
    Lyr.rect(tx0 + 2, base - 3, tx0 + 3, base, DOOR)
    win = LIT[0] if night or time == "dusk" else GLASS[0]
    for wx in (17, 23):
        Lyr.rect(wx, 24, wx + 1, 26, win)
        if look.shutter:
            for y in (24, 25, 26):
                Lyr.put(wx - 1, y, look.shutter[1]); Lyr.put(wx + 2, y, look.shutter[1])
    if look.stone:
        for y in range(ty0, base + 1):
            for x in range(tx0, 29):
                if Lyr.get(x, y) in (wl, hl) and (x * 5 + y * 3) % 7 == 0:
                    Lyr.put(x, y, wm)
    if look.timber:    # the long house's upper storey of timber
        for x in range(tx1 + 1, 29):
            Lyr.put(x, 22, WOOD[2]); Lyr.put(x, 23, WOOD[1] if x % 3 else WOOD[2])
    if v.fest == "lights":
        string_lights(Lyr, [(x, 21) for x in range(14, 30)], step=2)
        string_lights(Lyr, [(tx0 + (k % 2) * 5, ty0 + 1 + k * 2) for k in range(7)], step=1)
    if v.fest == "harvest":   # a wreath of wheat and grapes on the door, pumpkins by the wall
        Lyr.put(tx0 + 1, base - 4, GOLD[1]); Lyr.put(tx0 + 4, base - 4, GOLD[1])
    Lyr.outline(INK)
    fg.over(Lyr)
    if v.fest == "harvest":
        pumpkin(fg, 20, 31); pumpkin(fg, 25, 31); grapes(fg, 15, 28)
    if not v.waiting:
        bubble(fg, 17, 5, 13, 8, tail="downleft", r=3)
    m = Lyr.mask_of(keep=lambda c: c != INK)
    for y in range(-OFF, N - OFF):
        for x in range(-OFF, N - OFF):
            if Lyr.get(x, y) in (open_c, DOOR, win, GOLD[1]) or Lyr.get(x, y) in LIGHTS:
                m.put(x, y, None)
    for x in range(14, 30):
        m.put(x, 21, None)
    if not v.waiting:
        bubble_mono(m, 17, 5, 13, 8, "downleft")
    mono.over(m)
    return Icon(bg, fg, mono)


# ---- 6. Zvonik through the ages: the L grows with the village (step 1b) -----------------------------------------
# The village's six ages (game/Model.kt Age, companion/GAME.md "Ages"): the fire in a clearing, the camp's tent, the
# hamlet's watchtower and hut behind the palisade, the village's church (the L of step 1), the market town's fair and
# two storeys, the town's clock tower, its flag and its wall. The vertical stroke on the left, the long shape along the
# bottom, the bubble at the top right: the same L at every age, so the icon stays Lani's as it grows.
HUT_LOOK = {   # the hamlet's huts by culture (FireStyle.hutStone): walls, roof
    "base": ("log", SHINGLE), "primorska": ("log", SHINGLE), "kaernten": ("log", SHINGLE),
    "friuli": ("stone", (hx(0xC8683E), hx(0xA4502E), hx(0x7A3820))), "lakeland": ("stone", SLATE),
}
HUT_STONE = {"friuli": (hx(0xD8C8A8), hx(0xBCA886), hx(0x968264)), "lakeland": (hx(0x9AA0A4), hx(0x7C8286), hx(0x5E6468))}
BUNTING = (hx(0xE8303A), hx(0xFFD74A), hx(0x2E8AE0), hx(0x3EB04E), hx(0xFF8A2A))


def slim_spruce(layer: Layer, cx, top, base, maxhalf=5, snow=False):
    """The tall spruce at the clearing's edge: tiers of four rows widening downwards, on a trunk."""
    for y in range(top, base):
        r = y - top
        t, k = divmod(r, 4)
        half = min(maxhalf, t + 1 + k // 2) if r > 0 else 0
        for i in range(-half, half + 1):
            c = PINE[0] if i == -half else PINE[1] if i <= 0 else PINE[2]
            if snow and (k == 0 and i <= 0 or r == 0):
                c = SNOW[0] if r == 0 or i == -half else SNOW[1]
            layer.put(cx + i, y, c)
    layer.rect(cx, base, cx, base, LOG[2])
    layer.put(cx - 1, base, LOG[1])


def bunting(layer: Layer, x0, y0, x1, y1, sag=2, lights=False):
    """A garland of little flags (or, on snow, a string of lights) hung between two points."""
    n = x1 - x0
    pts = []
    for i in range(n + 1):
        t = i / n
        pts.append((x0 + i, round(y0 + (y1 - y0) * t + sag * 4 * t * (1 - t))))
    for i, (x, y) in enumerate(pts):
        if lights:
            if i % 2 == 0: layer.put(x, y, LIGHTS[(i // 2) % 4])
            else: layer.put(x, y, INK)
        else:
            layer.put(x, y, WOOD[3])
            if i % 3 == 1:
                c = BUNTING[(i // 3) % len(BUNTING)]
                layer.put(x - 1, y + 1, c); layer.put(x, y + 1, c); layer.put(x + 1, y + 1, c); layer.put(x, y + 2, c)


def burst(layer: Layer, x, y, c):
    """A little firework: a centre and eight rays."""
    layer.put(x, y, WHITE)
    for dx, dy in ((0, -2), (0, 2), (-2, 0), (2, 0), (-1, -1), (1, 1), (-1, 1), (1, -1)):
        layer.put(x + dx, y + dy, c)


def likof(layer: Layer, x, y):
    """The roofing feast's little spruce with ribbons, its foot at (x, y): something was built."""
    layer.sprite(x - 2, y - 5, ["..g..", ".ggg.", "rgggb", ".ggg.", "rgggb", "..w.."],
                 {"g": PINE[1], "r": GERANIUM[0], "b": hx(0x2E8AE0), "w": WOOD[2]})


def traveller(layer: Layer, x, y):
    """Someone new at the road: a chibi with a hat and a bundle on a stick, standing on (x, y)."""
    layer.sprite(x, y - 8, [
        "......wb",
        ".hhh.wbb",
        "hhhhhw..",
        ".sese...",
        ".ssss...",
        "ccccw...",
        ".cc.....",
        ".p.p....",
    ], {"h": hx(0x8A5A32), "s": SKIN[0], "e": INK, "c": hx(0x2E8AE0), "p": DOOR, "w": WOOD[2], "b": hx(0xE8303A)})


def paws(layer: Layer, pts, c):
    for (x, y) in pts:
        layer.put(x, y, c); layer.put(x + 1, y, c); layer.put(x, y - 1, c)


def roof_band(L: Layer, x0, x1, y_top, roof, rows=5, stripes=True):
    """The long house's roof seen from its long side: a band widening to the eave, its tile courses."""
    for j in range(rows):
        a = x0 + max(0, 1 - j) + (1 if j == 0 else 0)
        b = x1 - 2 + min(j, 3)
        L.rect(a, y_top + j, b, y_top + j, roof[0] if j == 0 else roof[1] if j < rows - 1 else roof[2])
    if stripes:
        for x in range(x0 + 1, x1 + 1, 3):
            L.put(x, y_top + 2, roof[2])


@concept("ages", "Zvonik skozi dobe", "The L through the ages",
         "The L grows with the village: the fire, the camp's tent, the hamlet's watchtower, the church, the market town, the town.")
def draw_ages(v: Variant) -> Icon:
    bg, fg, mono = Layer(), Layer(), Layer()
    age = v.age or "vas"
    look = v.look
    snow = v.season == "winter"
    time = v.time or "day"
    night = time == "night"
    top, mid, hor = SKY[time]
    if time == "day":
        top, mid, hor = hx(0x2F6FC0), hx(0x5E9AD8), hx(0xA8D2F0)
    sky(bg, time, top=top, mid=mid, hor=hor)
    g = ground("winter" if snow else ("summer" if v.season == "summer" else v.season), time)
    hill = lambda x, y: y >= 29 + ((x - 18) ** 2) / 90.0
    bg.fill(hill, lambda x, y: g[1] if (x * 3 + y * 5) % 9 else g[2])
    bg.fill(lambda x, y: hill(x, y) and not hill(x, y - 1), SNOW[0] if snow else g[3])
    roof = (look.roof if not snow else SNOW)
    win = LIT[0] if night or time == "dusk" else GLASS[0]
    open_c = LIT[0] if night or time == "dusk" or v.event == "watch" else INK
    L = Layer()          # what's outlined: the age's L
    loose = Layer()      # drawn after the outline: the fire, the signs
    cut = set()          # the openings, cut out of the themed icon
    bubble_at = (17, 5)

    if age in ("ogenj", "tabor"):
        slim_spruce(L, 9, 8, 28, snow=snow)
        if age == "ogenj":
            # the log to sit on, its cut end to the right; the fire at the L's corner
            L.rect(22, 25, 29, 25, WOOD[0]); L.rect(22, 26, 29, 26, LOG[1]); L.rect(22, 27, 29, 27, LOG[2])
            L.rect(30, 25, 30, 27, WOOD[0]); L.put(30, 26, WOOD[2])
            if snow: L.rect(22, 24, 29, 24, SNOW[0])
            for i in range(10):
                a = i / 10 * 2 * math.pi
                loose.put(round(17.5 + math.cos(a) * 3.6 - 0.5), round(27.6 + math.sin(a) * 1.4 - 0.5), STONE[0] if math.sin(a) < 0.2 else STONE[2])
            loose.rect(15, 27, 19, 27, LOG[1])
            fire_flame(loose, 17.5, 26, 10, 3.2)
            loose.put(14, 18, FLAME[1]); loose.put(21, 16, FLAME[1])
        else:
            # the tent, its door, the pole and its pennant; a small fire between the spruce and the tent
            for y in range(17, 29):
                half = (y - 17) * 6 // 11
                for x in range(23 - half, 24 + half):
                    c = CANVAS[0] if x < 23 else CANVAS[1]
                    if x in (23 - half, 23 + half): c = CANVAS[2]
                    if snow and y - 17 <= 2 + (x - 23) // 3 and x <= 23: c = SNOW[0]
                    L.put(x, y, c)
            for y in range(23, 29):
                for x in range(23 - (y - 23) // 2, 24 + (y - 23) // 2):
                    L.put(x, y, DOOR if not night else LIT[1])
                    cut.add((x, y))
            L.rect(23, 14, 23, 16, WOOD[2])
            L.rect(24, 14, 26, 14, GERANIUM[0]); L.rect(24, 15, 25, 15, GERANIUM[0])
            for i in range(8):
                a = i / 8 * 2 * math.pi
                loose.put(round(14.5 + math.cos(a) * 2.6 - 0.5), round(28 + math.sin(a) * 1.1 - 0.5), STONE[1])
            fire_flame(loose, 14.5, 27, 6, 2.0)
    elif age == "zaselek":
        # the watchtower on its stilts, its cabin and roof; the hut beside it; the palisade's stakes at the end
        L.rect(8, 18, 8, 28, WOOD[1]); L.rect(13, 18, 13, 28, WOOD[2])
        for k in range(4):
            L.put(9 + k, 20 + k, WOOD[2]); L.put(12 - k, 20 + k, WOOD[1])
            L.put(9 + k, 24 + k, WOOD[2]); L.put(12 - k, 24 + k, WOOD[1])
        L.rect(7, 17, 14, 17, WOOD[0])
        L.rect(8, 13, 13, 16, LOG[0]); L.rect(8, 14, 13, 14, LOG[1]); L.rect(12, 13, 13, 16, LOG[1])
        L.rect(10, 14, 11, 15, open_c); cut |= {(10, 14), (11, 14), (10, 15), (11, 15)}
        hr = SHINGLE if not snow else SNOW
        for j, (a, b) in enumerate([(10, 11), (9, 12), (8, 13), (7, 14)]):
            L.rect(a, 9 + j, b, 9 + j, hr[0] if j < 2 else hr[1])
        walls, hroof = HUT_LOOK[look.id]
        hroof = hroof if not snow else SNOW
        for y in range(22, 29):
            for x in range(15, 28):
                if walls == "log":
                    c = (LOG[0] if x < 26 else LOG[1]) if y % 2 else (LOG[1] if x < 26 else LOG[2])
                else:
                    st = HUT_STONE[look.id]
                    c = st[2] if (y % 3 == 2 or (x + (y // 3) * 2) % 5 == 4) else (st[0] if x < 26 else st[1])
                L.put(x, y, c)
        roof_band(L, 14, 28, 17, hroof)
        L.rect(17, 25, 18, 28, DOOR); cut |= {(17, y) for y in range(25, 29)} | {(18, y) for y in range(25, 29)}
        L.rect(22, 24, 23, 25, win); cut |= {(22, 24), (23, 24), (22, 25), (23, 25)}
        for x in range(29, 33):
            top_y = 19 + (x % 2)
            L.rect(x, top_y, x, 28, LOG[1] if x % 2 else LOG[2])
            L.put(x, top_y - 1, LOG[0] if x % 2 else LOG[1])
    else:
        # the church tower and the long house (step 1's L), and what the market town and the town add
        wl, wm, wd = look.tower_wall
        hl, hm, hd = look.wall
        ttop = look.tower_top if not (snow and look.tower in ("campanile", "onion", "pyramid", "spire")) else SNOW
        tall = age == "mesto"
        two = age in ("trg", "mesto")
        tx0, tx1, base = 8, 13, 28
        ty0 = 11 if tall else 13
        L.rect(tx0, ty0, tx1, base, wl); L.rect(tx1, ty0, tx1, base, wm)
        wall_top = 19 if two else 22
        L.rect(tx1 + 1, wall_top, 28, base, hl); L.rect(tx1 + 1, wall_top, 28, wall_top, hm)
        roof_band(L, 14, 29, wall_top - 5, roof)
        rows = L_TOPS[look.tower]
        L.sprite(tx0 - 1, ty0 - len(rows), rows, {"t": ttop[0], "u": ttop[1], "v": ttop[2], "w": wl, "d": wd})
        if look.tower == "campanile":
            for xx in (tx0 + 1, tx0 + 4):
                L.rect(xx, ty0 + 1, xx, ty0 + 3, open_c); cut |= {(xx, ty0 + 1), (xx, ty0 + 2), (xx, ty0 + 3)}
        else:
            L.rect(tx0 + 2, ty0 + 2, tx0 + 3, ty0 + 4, open_c)
            cut |= {(tx0 + a, ty0 + b) for a in (2, 3) for b in (2, 3, 4)}
        if age != "vas":   # the clock: the market town's and the town's
            L.rect(tx0 + 2, ty0 + 8, tx0 + 3, ty0 + 9, GOLD[1]); cut |= {(tx0 + a, ty0 + b) for a in (2, 3) for b in (8, 9)}
        L.rect(tx0 + 2, base - 3, tx0 + 3, base, DOOR); cut |= {(tx0 + a, base - b) for a in (2, 3) for b in range(4)}
        rows_y = [24] if not two else [21, 25]
        for wy in rows_y:
            for wx in (17, 23):
                L.rect(wx, wy, wx + 1, wy + 2, win); cut |= {(wx + a, wy + b) for a in (0, 1) for b in (0, 1, 2)}
                if look.shutter:
                    for y in range(wy, wy + 3):
                        L.put(wx - 1, y, look.shutter[1]); L.put(wx + 2, y, look.shutter[1])
        if look.stone:
            for y in range(ty0, base + 1):
                for x in range(tx0, 29):
                    if L.get(x, y) in (wl, hl) and (x * 5 + y * 3) % 7 == 0:
                        L.put(x, y, wm)
        if look.timber:
            for x in range(tx1 + 1, 29):
                L.put(x, wall_top, WOOD[2]); L.put(x, wall_top + 1, WOOD[1] if x % 3 else WOOD[2])
        if age == "trg":   # the fair: a stall with a striped awning before the house, its counter of goods
            for x in range(19, 29):
                L.put(x, 24, GERANIUM[0] if (x // 2) % 2 else CREAM[0]); L.put(x, 25, GERANIUM[0] if (x // 2) % 2 else CREAM[0])
                if x % 2: L.put(x, 26, GERANIUM[1] if (x // 2) % 2 else CREAM[1])
            L.rect(20, 27, 27, 28, WOOD[1])
            for x, c in ((20, hx(0xF08A2A)), (22, GERANIUM[0]), (24, LEAF), (26, hx(0xF08A2A))):
                L.put(x, 26, c)
        if age == "mesto":  # the town: its banners down the tower, its wall with battlements and a gate before the house
            L.rect(20, wall_top + 1, 21, wall_top + 4, GERANIUM[0]); L.rect(20, wall_top + 1, 21, wall_top + 1, GOLD[1])
            L.put(20, wall_top + 5, GERANIUM[1])   # the town's banner down the house's front
            for y in range(25, 29):
                for x in range(14, 30):
                    L.put(x, y, STONE[0] if (x + (y % 2) * 2) % 4 else STONE[1])
            L.rect(14, 24, 29, 24, STONE[0])
            for x in range(14, 30, 4):
                L.rect(x, 22, x + 1, 23, STONE[0])
            L.rect(21, 26, 22, 28, INK); L.put(21, 25, STONE[2]); L.put(22, 25, STONE[2])
            cut |= {(21, y) for y in (26, 27, 28)} | {(22, y) for y in (26, 27, 28)}

    # the signs of what's happening, drawn into the age's picture
    ev = v.event
    signs = Layer()
    if ev == "festival":
        span = {"ogenj": (12, 15, 31, 21), "tabor": (12, 15, 22, 17), "zaselek": (14, 12, 31, 18), "vas": (14, 15, 29, 18),
                "trg": (14, 19, 29, 20), "mesto": (14, 18, 29, 19)}[age]
        bunting(signs, *span, sag=2, lights=snow)
    if ev == "newage":
        burst(signs, 4, 6, hx(0xFF5A8A)); burst(signs, 31, 16, GOLD[1]); burst(signs, 3, 15, hx(0x5AD45A))
        for (x, y) in [(6, 2), (1, 10), (33, 12), (15, 2), (31, 20)]:
            signs.put(x, y, CONFETTI[(x + y) % len(CONFETTI)])
    if ev == "built":
        spot = {"ogenj": (31, 24), "tabor": (31, 28), "zaselek": (16, 17), "vas": (15, 17), "trg": (15, 14), "mesto": (15, 14)}[age]
        likof(signs, *spot)
    if ev == "newcomer":
        traveller(signs, 26 if age not in ("ogenj",) else 26, 30)
    if ev == "watch":
        paws(signs, [(3, 31), (6, 30), (9, 31), (12, 30)], hx(0x6A5A4A) if not snow else hx(0x8A9AB0))
        if age in ("ogenj", "tabor"):
            loose.rect(16, 13, 18, 15, LIT[0])
    L.outline(INK)
    fg.over(L).over(loose).over(signs)
    bubble(fg, *bubble_at, 13, 8, tail="downleft", r=3)
    # monochrome: the age's L with its openings cut out, the flame, the bubble (no signs: the themed icon stays calm)
    m = L.mask_of(keep=lambda c: c != INK)
    for (x, y) in cut:
        m.put(x, y, None)
    if age in ("vas", "trg", "mesto", "zaselek"):
        wt = {"zaselek": 21, "vas": 21, "trg": 18, "mesto": 18}[age]
        for x in range(14, 30):
            if m.get(x, wt) is not None and m.get(x, wt + 1) is not None:
                m.put(x, wt, None)
    if age == "ogenj":
        fire_flame(None, 17.5, 26, 10, 3.2, mask=m)
    if age == "tabor":
        fire_flame(None, 14.5, 27, 6, 2.0, mask=m)
    bubble_mono(m, *bubble_at, 13, 8, "downleft")
    mono.over(m)
    return Icon(bg, fg, mono)


# ---------------------------------------------------------------------------------------------------------- wordmarks
# "Lani" in the app's own pixel letters: 2 px strokes, the capital 10 px, the small letters 7; a concept dresses it.
GLYPHS = {
    "L": ["##.....", "##.....", "##.....", "##.....", "##.....", "##.....", "##.....", "##.....", "#######", "#######"],
    "a": [".#####.", ".....##", ".######", "##...##", "##...##", "##..###", ".###.##"],
    "n": ["##.###.", "###..##", "##...##", "##...##", "##...##", "##...##", "##...##"],
    "i": ["##", "##", "..", "##", "##", "##", "##", "##", "##", "##"],
}
WM_INK = {"light": (BRAND, INK), "dark": (CREAM[0], BRAND)}


def wordmark(cid: str, dark: bool):
    """The wordmark's pixels {(x, y): colour}, its letters' fill and their 1 px shadow, and the concept's touch."""
    fill, shadow = WM_INK["dark" if dark else "light"]
    if cid == "bubble":
        fill, shadow = INK, CREAM[2]
    px = {}
    x = 0
    letters = []
    for ch in "Lani":
        rows = GLYPHS[ch]
        top = 10 - len(rows)
        letters.append((ch, x, top, rows))
        x += len(rows[0]) + 2
    width = x - 2
    ox, oy = 0, 0
    if cid == "fire": oy = 3
    if cid == "ltower": oy = 6
    if cid == "neighbour": oy = 3
    if cid == "house": oy = 4
    if cid == "bubble": ox, oy = 5, 3
    shape = {}
    for ch, lx, top, rows in letters:
        for j, row in enumerate(rows):
            for i, c in enumerate(row):
                if c == "#":
                    if ch == "i" and j < 2 and cid in ("fire", "house", "neighbour"):
                        continue   # the i's dot is the concept's touch
                    shape[(ox + lx + i, oy + top + j)] = fill
    ix = ox + letters[3][1]   # the i
    if cid == "fire":   # the i's dot is a flame
        for (dx, dy, k) in [(0, -3, 3), (1, -2, 2), (0, -2, 3), (1, -1, 1), (0, -1, 2), (-1, -1, 4), (2, -1, 3), (0, 0, 1), (1, 0, 1),
                            (-1, 0, 3), (2, 0, 3), (0, 1, 2), (1, 1, 2)]:
            px[(ix + dx, oy + dy + 0)] = FLAME[k]
    if cid == "house":  # the i's dot is a little house with a red roof
        for (dx, dy, c) in [(0, -4, ROOF[0]), (1, -4, ROOF[1]), (-1, -3, ROOF[0]), (0, -3, ROOF[0]), (1, -3, ROOF[1]), (2, -3, ROOF[1]),
                            (-1, -2, WALL[0]), (0, -2, WALL[0]), (1, -2, WALL[1]), (2, -2, WALL[1]), (-1, -1, WALL[0]), (0, -1, DOOR),
                            (1, -1, WALL[1]), (2, -1, WALL[1]), (3, -6, STONE[1]), (3, -5, STONE[1])]:
            px[(ix + dx, oy + dy + 2)] = c
    if cid == "neighbour":   # the i's dot is the neighbour's face
        for (dx, dy, c) in [(0, -3, hx(0x4A2E1C)), (1, -3, hx(0x4A2E1C)), (-1, -2, hx(0x4A2E1C)), (0, -2, SKIN[0]), (1, -2, SKIN[0]),
                            (2, -2, hx(0x4A2E1C)), (-1, -1, SKIN[0]), (0, -1, INK), (1, -1, INK), (2, -1, SKIN[0]), (-1, 0, SKIN[0]),
                            (0, 0, SKIN[0]), (1, 0, SKIN[0]), (2, 0, SKIN[0]), (0, 1, SKIN[1]), (1, 1, SKIN[1])]:
            px[(ix + dx, oy + dy + 0)] = c
        px[(ix - 1, oy - 1)] = SKIN[0]; px[(ix + 2, oy - 1)] = SKIN[0]
    if cid == "ltower":   # the L's stem is the tower: a roof on it, the belfry cut in, the stem a pixel wider
        lx = ox + letters[0][1]
        for yy in range(oy, oy + 8):
            shape[(lx + 2, oy + yy - oy)] = fill
        for (dx, dy, c) in [(1, -3, ROOF[0]), (2, -3, ROOF[1]), (0, -2, ROOF[0]), (1, -2, ROOF[0]), (2, -2, ROOF[1]), (3, -2, ROOF[1]),
                            (-1, -1, ROOF[0]), (0, -1, ROOF[0]), (1, -1, ROOF[0]), (2, -1, ROOF[1]), (3, -1, ROOF[1]), (4, -1, ROOF[1])]:
            px[(lx + dx, oy + dy)] = c
        shape.pop((lx + 1, oy + 2), None); shape.pop((lx + 1, oy + 3), None)
    # the shadow, one down and one right, under the letters only
    for (x, y), c in list(shape.items()):
        if (x + 1, y + 1) not in shape:
            px.setdefault((x + 1, y + 1), shadow)
    px.update(shape)
    if cid == "bubble":   # the word in a speech bubble
        x0, y0, x1, y1 = 0, 0, width + 10, 15
        body = rrect(x0, y0, x1, y1, 4)
        paper = {}
        for yy in range(y0, y1 + 1):
            for xx in range(x0, x1 + 1):
                if body(xx + 0.5, yy + 0.5):
                    paper[(xx, yy)] = CREAM[0] if yy < y1 else CREAM[1]
        for i, (a, b) in enumerate([(3, 6), (3, 5), (2, 3)]):
            for xx in range(a, b + 1):
                paper[(xx, y1 + 1 + i)] = CREAM[0]
        out = dict(paper)
        for (xx, yy) in paper:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if (xx + dx, yy + dy) not in paper:
                    out[(xx + dx, yy + dy)] = INK
        out.update(px)
        px = out
    return px


def wordmark_svg(cid: str, dark: bool, scale=4) -> str:
    px = wordmark(cid, dark)
    xs = [p[0] for p in px]; ys = [p[1] for p in px]
    x0, y0 = min(xs), min(ys)
    w, h = max(xs) - x0 + 1, max(ys) - y0 + 1
    rects = "".join(f'<rect x="{x - x0}" y="{y - y0}" width="1" height="1" fill="#{c[0]:02X}{c[1]:02X}{c[2]:02X}"/>' for (x, y), c in sorted(px.items()))
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w * scale}" height="{h * scale}" '
            f'shape-rendering="crispEdges"><title>Lani</title>{rects}</svg>\n')


# ---------------------------------------------------------------------------------------------------------- output
def vector(layer: Layer, comment: str) -> str:
    """A vector drawable of the layer: one path per colour, a rectangle per run, each a hair wider (no seams)."""
    runs: dict = {}
    for y in range(N):
        x = 0
        while x < N:
            c = layer.px[y][x]
            if c is None:
                x += 1; continue
            x1 = x
            while x1 < N and layer.px[y][x1] == c:
                x1 += 1
            # 2 dp a pixel; 0.04 dp over to the right and down, so neighbouring runs meet without a hairline
            runs.setdefault(c, []).append(f"M{x*2},{y*2}h{(x1-x)*2 + 0.04:g}v2.04h-{(x1-x)*2 + 0.04:g}z")
            x = x1
    paths = "\n".join(
        f'    <path android:fillColor="#{c[0]:02X}{c[1]:02X}{c[2]:02X}" android:pathData="{"".join(p)}" />' for c, p in runs.items())
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {comment} Generated by companion/android/tools/app_icon.py: 54 × 54 pixels of 2 dp. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
{paths}
</vector>
'''


def notify_icon(res: Path):
    """ic_notify: the notification's small icon, 24 dp: the neutral L's silhouette (tower, house, bubble), white on
    transparent, cropped to the glyph with a margin, so it fills the status bar's 24 dp as Android asks."""
    m = CONCEPTS["ages"]["draw"](Variant(culture="base", age="vas")).mono
    pts = [(x, y) for y in range(N) for x in range(N) if m.px[y][x] is not None]
    x0 = min(p[0] for p in pts); x1 = max(p[0] for p in pts); y0 = min(p[1] for p in pts); y1 = max(p[1] for p in pts)
    side = max(x1 - x0 + 1, y1 - y0 + 1) + 2
    ox = x0 - (side - (x1 - x0 + 1)) // 2; oy = y0 - (side - (y1 - y0 + 1)) // 2
    runs = []
    for y in range(N):
        x = 0
        while x < N:
            if m.px[y][x] is None:
                x += 1; continue
            x2 = x
            while x2 < N and m.px[y][x2] is not None:
                x2 += 1
            runs.append(f"M{x - ox},{y - oy}h{x2 - x + 0.02:g}v1.02h-{x2 - x + 0.02:g}z")
            x = x2
    (res / "drawable").mkdir(parents=True, exist_ok=True)
    (res / "drawable" / "ic_notify.xml").write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- The notification's small icon: Lani's L (the bell tower, the long house, the bubble), white on transparent.
     Generated by companion/android/tools/app_icon.py (notify). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="{side}" android:viewportHeight="{side}">
    <path android:fillColor="#FFFFFFFF" android:pathData="{"".join(runs)}" />
</vector>
''')


# ---------------------------------------------------------------------------------------------------------- the app's icon
# What the app ships (docs/plans/05-built/app-icon.md, step 2): one activity alias per picture of the village, its age,
# its culture from the hamlet on, and the day's look: plain or a sign (a festival, a new age, something built, someone
# new), on green or on snow. data/AppIcons.kt names them by the same rule; AppIconsTest keeps the list.
ICON_CULTURES = ["primorska", "friuli", "kaernten", "lakeland"]
ICON_SIGNS = ["festival", "newage", "built", "newcomer"]
MANIFEST_BEGIN = "<!-- app-icon aliases: generated by companion/android/tools/app_icon.py build; don't edit by hand -->"
MANIFEST_END = "<!-- end of the app-icon aliases -->"


def icon_looks():
    """Every alias's (age, culture or None, sign or "", snow), in the manifest's order."""
    for age in AGES:
        cultures = [None] if age in ("ogenj", "tabor") else ICON_CULTURES
        signs = [""] + (["festival", "built"] if age == "ogenj" else ICON_SIGNS)
        for c in cultures:
            for sign in signs:
                for snow in (False, True):
                    yield age, c, sign, snow


def alias_name(age, c, sign, snow) -> str:
    """".icon.Ogenj", ".icon.FriuliZaselekSnow", ".icon.PrimorskaVasFestival": never renamed once shipped."""
    return ".icon." + (c.capitalize() if c else "") + age.capitalize() + sign.capitalize() + ("Snow" if snow else "")


def res_name(age, c, sign, snow) -> str:
    return "ic_icon_" + "_".join(([c] if c else []) + [age] + ([sign] if sign else []) + (["snow"] if snow else []))


def build_files() -> dict:
    """{relative path under res/ or "manifest": content}: everything the app's icon needs."""
    out = {}
    shared: dict = {}   # content → name, for the backgrounds and the monochrome layers
    draw = CONCEPTS["ages"]["draw"]

    def share(kind, key, text):
        if text not in shared:
            shared[text] = f"ic_icon_{kind}_{key}"
            out[f"drawable/{shared[text]}.xml"] = text
        return shared[text]
    aliases = []
    for age, c, sign, snow in icon_looks():
        name = res_name(age, c, sign, snow)
        icon = draw(Variant(culture=c or "base", age=age, event=sign, season="winter" if snow else "summer"))
        what = f"{AGE_NAMES[age].split(' ', 1)[1]}{', ' + CULTURE_NAMES[c] if c else ''}{', ' + EVENT_NAMES[sign] if sign else ''}{', snow' if snow else ''}."
        bgn = share("bg", "snow" if snow else "green", vector(icon.bg, "The sky and the hill" + (" under snow." if snow else ".")))
        mn = share("mono", "_".join(([c] if c else []) + [age] + (["snow"] if snow else [])), vector(icon.mono, f"{AGE_NAMES[age].split(' ', 1)[1]}{', ' + CULTURE_NAMES[c] if c else ''}: the themed icon."))
        out[f"drawable/{name}_fg.xml"] = vector(icon.fg, what)
        out[f"mipmap-anydpi-v26/{name}.xml"] = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by companion/android/tools/app_icon.py build: {what} -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/{bgn}" />
    <foreground android:drawable="@drawable/{name}_fg" />
    <monochrome android:drawable="@drawable/{mn}" />
</adaptive-icon>
'''
        aliases.append((alias_name(age, c, sign, snow), name))
    # the application's own icon (Settings, the share sheet): the neutral L, as the README's logo
    app = draw(Variant(culture="base", age="vas"))
    out["drawable/ic_launcher_background.xml"] = vector(app.bg, "The sky and the hill.")
    out["drawable/ic_launcher_foreground.xml"] = vector(app.fg, "Lani's L: the bell tower, the long house, the bubble.")
    out["drawable/ic_launcher_monochrome.xml"] = vector(app.mono, "Lani's L for themed icons.")
    out["mipmap-anydpi-v26/ic_launcher.xml"] = '''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by companion/android/tools/app_icon.py build: the application's icon, Lani's L (the launcher shows an alias's). -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
'''
    lines = []
    for i, (alias, name) in enumerate(aliases):
        lines.append(f'''        <activity-alias android:name="{alias}" android:targetActivity=".MainActivity" android:exported="true"
            android:enabled="{"true" if i == 0 else "false"}" android:icon="@mipmap/{name}">
            <intent-filter><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent-filter>
        </activity-alias>''')
    out["manifest"] = "\n".join(lines)
    return out


def png_bytes(px_fn, w, h) -> bytes:
    """A PNG of w × h from px_fn(x, y) → (r, g, b, a): the site's favicons, without Pillow."""
    import struct, zlib
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw += bytes(px_fn(x, y))
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) +
            chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def wordmark_vector(cid: str, dark: bool) -> str:
    """The wordmark as an Android vector drawable (a pixel a viewport unit): the setup's welcome."""
    px = wordmark(cid, dark)
    xs = [p[0] for p in px]; ys = [p[1] for p in px]
    x0, y0 = min(xs), min(ys)
    w, h = max(xs) - x0 + 1, max(ys) - y0 + 1
    by = {}
    for (x, y), c in sorted(px.items(), key=lambda kv: (kv[0][1], kv[0][0])):
        by.setdefault(c, []).append(f"M{x - x0},{y - y0}h1.02v1.02h-1.02z")
    paths = "\n".join(f'    <path android:fillColor="#{c[0]:02X}{c[1]:02X}{c[2]:02X}" android:pathData="{"".join(v)}" />' for c, v in by.items())
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Lani's wordmark: the L is the bell tower. Generated by companion/android/tools/app_icon.py build. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{w * 4}dp" android:height="{h * 4}dp"
    android:viewportWidth="{w}" android:viewportHeight="{h}">
{paths}
</vector>
'''


def icon_svg(layer: Layer) -> str:
    """The neutral L, its 72 dp a launcher shows, as an SVG in a rounded square: the site's favicon and brand."""
    rects = []
    for y in range(VIS):
        x = 0
        while x < VIS:
            c = layer.px[y + OFF][x + OFF]
            x1 = x
            while x1 < VIS and layer.px[y + OFF][x1 + OFF] == c:
                x1 += 1
            if c is not None:
                rects.append(f'<rect x="{x}" y="{y}" width="{x1 - x}" height="1" fill="#{c[0]:02X}{c[1]:02X}{c[2]:02X}"/>')
            x = x1
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 36 36" shape-rendering="crispEdges"><title>Lani</title>'
            '<clipPath id="r"><rect width="36" height="36" rx="8"/></clipPath><g clip-path="url(#r)">' + "".join(rects) + "</g></svg>\n")


def site_files() -> dict:
    """{path under the repo's root: str or bytes}: the site's icons and the wordmark (the README's too)."""
    app = CONCEPTS["ages"]["draw"](Variant(culture="base", age="vas")).composite()
    def rgba(x, y, ox=0, scale=1, r=None):
        X, Y = x // scale + ox, y // scale + ox
        c = app.px[Y + OFF][X + OFF]
        if r is not None:   # the rounded square's corners transparent
            n = (VIS - 2 * ox) * scale; rr = r * scale
            cx = min(max(x + 0.5, rr), n - rr); cy = min(max(y + 0.5, rr), n - rr)
            if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 > rr * rr: return (0, 0, 0, 0)
        return (*c, 255)
    return {
        "site/favicon.svg": icon_svg(app),
        "site/lani-icon.svg": icon_svg(app),
        "site/favicon-32.png": png_bytes(lambda x, y: rgba(x, y, ox=2, r=7), 32, 32),
        "site/apple-touch-icon.png": png_bytes(lambda x, y: rgba(x, y, scale=5), 180, 180),
        "site/lani-wordmark.svg": wordmark_svg("ltower", False),
        "site/lani-wordmark-dark.svg": wordmark_svg("ltower", True),
    }


def build(app_dir: Path, check=False) -> int:
    """Writes (or with check, compares) the icon's resources and the manifest's alias block. Returns the problems."""
    res = app_dir / "src/main/res"
    manifest = app_dir / "src/main/AndroidManifest.xml"
    files = build_files()
    files["drawable/lani_wordmark.xml"] = wordmark_vector("ltower", True)
    root = app_dir.resolve().parents[2]
    site = site_files()
    block = files.pop("manifest")
    text = manifest.read_text()
    i, j = text.index(MANIFEST_BEGIN), text.index(MANIFEST_END)
    new_text = text[:i] + MANIFEST_BEGIN + "\n" + block + "\n        " + text[j:]
    problems = []
    if check:
        if new_text != text: problems.append("AndroidManifest.xml: the alias block differs")
        for rel, content in files.items():
            f = res / rel
            if not f.exists() or f.read_text() != content: problems.append(f"res/{rel}: differs or missing")
        for f in list((res / "drawable").glob("ic_icon_*.xml")) + list((res / "mipmap-anydpi-v26").glob("ic_icon_*.xml")):
            if f"{f.parent.name}/{f.name}" not in files: problems.append(f"res/{f.parent.name}/{f.name}: not generated any more")
        for rel, content in site.items():
            f = root / rel
            have = (f.read_bytes() if isinstance(content, bytes) else f.read_text()) if f.exists() else None
            if have != content: problems.append(f"{rel}: differs or missing")
        for p in problems: print(p)
        print(f"{len(problems)} problems" if problems else f"in sync: {sum(1 for k in files if k.startswith('mipmap') and 'ic_icon_' in k)} aliases")
        return len(problems)
    for f in list((res / "drawable").glob("ic_icon_*.xml")) + list((res / "mipmap-anydpi-v26").glob("ic_icon_*.xml")):
        if f"{f.parent.name}/{f.name}" not in files: f.unlink()
    for rel, content in files.items():
        (res / rel).parent.mkdir(parents=True, exist_ok=True)
        (res / rel).write_text(content)
    manifest.write_text(new_text)
    notify_icon(res)
    for rel, content in site.items():
        (root / rel).write_bytes(content) if isinstance(content, bytes) else (root / rel).write_text(content)
    print(f"wrote {len(files)} files, {sum(1 for k in files if k.startswith('mipmap') and 'ic_icon_' in k)} aliases")
    return 0


def write_xml(concept_id: str, res: Path, variants: list[Variant]):
    c = CONCEPTS[concept_id]
    (res / "drawable").mkdir(parents=True, exist_ok=True)
    (res / "mipmap-anydpi-v26").mkdir(parents=True, exist_ok=True)
    for v in variants:
        icon = c["draw"](v)
        name = "ic_launcher" if v == Variant() else "ic_launcher_" + v.key.replace(".", "_")
        what = f"{c['en']}, {v.key}."
        (res / "drawable" / f"{name}_background.xml").write_text(vector(icon.bg, what))
        (res / "drawable" / f"{name}_foreground.xml").write_text(vector(icon.fg, what))
        (res / "drawable" / f"{name}_monochrome.xml").write_text(vector(icon.mono, what + " For themed icons."))
        (res / "mipmap-anydpi-v26" / f"{name}.xml").write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Pixel art from companion/android/tools/app_icon.py: {what} -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/{name}_background" />
    <foreground android:drawable="@drawable/{name}_foreground" />
    <monochrome android:drawable="@drawable/{name}_monochrome" />
</adaptive-icon>
''')


def main(argv):
    if not argv or argv[0] == "list":
        for c in CONCEPTS.values():
            print(f"{c['id']:10} {c['sl']} · {c['en']}: {c['line']}")
        return
    cmd = argv[0]
    if cmd == "xml":
        variants = [Variant.parse(s) for s in argv[3:]] or [Variant()]
        write_xml(argv[1], Path(argv[2]), variants)
    elif cmd == "build":
        app_dir = Path(argv[1]) if len(argv) > 1 and not argv[1].startswith("-") else Path(__file__).resolve().parent.parent / "app"
        sys.exit(1 if build(app_dir, check="--check" in argv) else 0)
    elif cmd == "notify":
        notify_icon(Path(argv[1]))
    elif cmd == "wordmark":
        out = Path(argv[2]); out.mkdir(parents=True, exist_ok=True)
        for dark in (False, True):
            (out / f"lani-wordmark-{argv[1]}-{'dark' if dark else 'light'}.svg").write_text(wordmark_svg(argv[1], dark))
    elif cmd == "sheets":
        import app_icon_sheets  # Pillow
        app_icon_sheets.sheets(Path(argv[1]), argv[2:] or list(CONCEPTS))
    elif cmd == "ages":
        import app_icon_sheets  # Pillow
        app_icon_sheets.ages_sheets(Path(argv[1]))
    elif cmd == "preview":
        import app_icon_sheets
        app_icon_sheets.preview(Path(argv[1]), argv[2:] or list(CONCEPTS))
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
