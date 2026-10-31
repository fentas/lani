"""Contact sheets of the app icon's concepts (app_icon.py): the icon in the launchers' masks at 48, 72 and 192 dp on a light
and a dark wallpaper, as the themed icon, its changing variants, the wordmark and a home screen row beside other apps'.
Needs Pillow: uv run --no-project --with pillow python3 app_icon.py sheets OUT_DIR [CONCEPT ...]

Everything is drawn at 2 px a dp (xhdpi), a pixel of the icon 4 px at 72 dp, so the sheet shows the sizes side by side as
a phone does; a launcher rasterises the vector drawable at its size, which is what the box filter below does.
"""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

import app_icon as A

DENSITY = 2  # px a dp on the sheet
FONT = "/usr/share/fonts/noto/NotoSans-Regular.ttf"
BOLD = "/usr/share/fonts/noto/NotoSans-Bold.ttf"


def font(size, bold=False):
    try:
        return ImageFont.truetype(BOLD if bold else FONT, size)
    except OSError:
        return ImageFont.load_default(size=size)


# ---------------------------------------------------------------------------------------------------------- masks
def _bez(p0, p1, p2, p3, n=24):
    out = []
    for i in range(1, n + 1):
        t = i / n; u = 1 - t
        out.append((u**3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t**3 * p3[0],
                    u**3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t**3 * p3[1]))
    return out


def _arc(cx, cy, r, a0, a1, n=24):
    return [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * i / n)), cy + r * math.sin(math.radians(a0 + (a1 - a0) * i / n)))
            for i in range(n + 1)]


def mask_points(kind):
    """The launchers' icon masks on 0..100 (AOSP's config_icon_mask shapes)."""
    if kind == "circle":
        return _arc(50, 50, 50, 0, 360, 96)
    if kind == "squircle":
        pts = [(50, 0)]
        pts += _bez((50, 0), (10, 0), (0, 10), (0, 50))
        pts += _bez((0, 50), (0, 90), (10, 100), (50, 100))
        pts += _bez((50, 100), (90, 100), (100, 90), (100, 50))
        pts += _bez((100, 50), (100, 10), (90, 0), (50, 0))
        return pts
    if kind == "rounded":
        r = 8
        return (_arc(100 - r, r, r, -90, 0) + _arc(100 - r, 100 - r, r, 0, 90) + _arc(r, 100 - r, r, 90, 180)
                + _arc(r, r, r, 180, 270))
    if kind == "teardrop":
        return (_arc(50, 50, 50, -90, 0) + [(100, 85)] + _arc(85, 85, 15, 0, 90) + [(50, 100)]
                + _arc(50, 50, 50, 90, 270))
    raise ValueError(kind)


MASKS = ["circle", "squircle", "rounded", "teardrop"]
MASK_NAMES = {"circle": "circle", "squircle": "squircle", "rounded": "rounded square", "teardrop": "teardrop"}


def mask_image(kind, size):
    big = size * 4
    m = Image.new("L", (big, big), 0)
    ImageDraw.Draw(m).polygon([(x * big / 100, y * big / 100) for (x, y) in mask_points(kind)], fill=255)
    return m.resize((size, size), Image.LANCZOS)


# ---------------------------------------------------------------------------------------------------------- layers to images
def layer_image(layer: A.Layer, scale=1, crop=True) -> Image.Image:
    n = A.VIS if crop else A.N
    o = A.OFF if crop else 0
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = im.load()
    for y in range(n):
        for x in range(n):
            c = layer.px[y + o][x + o]
            if c is not None:
                px[x, y] = (c[0], c[1], c[2], 255)
    return im.resize((n * scale, n * scale), Image.NEAREST) if scale != 1 else im


def rasterise(im: Image.Image, size: int) -> Image.Image:
    """The icon at size px as a launcher draws its vector: area coverage (a box filter from a big nearest upscale)."""
    k = max(1, math.ceil(size * 4 / im.width))
    big = im.resize((im.width * k, im.height * k), Image.NEAREST)
    return big.resize((size, size), Image.BOX)


THEMED = {"light": ((0xDC, 0xE4, 0xF5), (0x28, 0x3E, 0x6E)), "dark": ((0x2C, 0x34, 0x42), (0xC8, 0xD8, 0xF8))}


def icon_image(icon: A.Icon, dp: int, mask="squircle", themed: str | None = None) -> Image.Image:
    size = dp * DENSITY
    if themed:
        bgc, fgc = THEMED[themed]
        m = rasterise(layer_image(icon.mono), size).split()[3]
        base = Image.new("RGBA", (size, size), bgc + (255,))
        base.paste(Image.new("RGBA", (size, size), fgc + (255,)), (0, 0), m)
        im = base
    else:
        im = rasterise(layer_image(icon.composite()), size)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(im, (0, 0), mask_image(mask, size))
    return out


def shadowed(im: Image.Image) -> Image.Image:
    """A launcher's soft shadow under the icon."""
    pad = max(2, im.width // 24)
    sh = Image.new("RGBA", (im.width + pad * 2, im.height + pad * 2), (0, 0, 0, 0))
    a = im.split()[3].point(lambda v: v * 0.35)
    blk = Image.new("RGBA", im.size, (0, 0, 0, 255)); blk.putalpha(a)
    sh.paste(blk, (pad, pad + pad // 2), blk)
    from PIL import ImageFilter
    sh = sh.filter(ImageFilter.GaussianBlur(pad / 1.5))
    sh.alpha_composite(im, (pad, pad))
    return sh


def wallpaper(w, h, dark):
    im = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(im)
    a, b = ((0xE9, 0xEE, 0xF3), (0xC9, 0xD6, 0xE3)) if not dark else ((0x1B, 0x20, 0x2B), (0x0E, 0x11, 0x18))
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,))
    # a soft blob so it isn't flat
    blob = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bd = ImageDraw.Draw(blob)
    col = (0xF6, 0xC8, 0x9A, 70) if not dark else (0x3A, 0x55, 0x8A, 80)
    bd.ellipse([w * 0.55, -h * 0.3, w * 1.2, h * 0.9], fill=col)
    from PIL import ImageFilter
    blob = blob.filter(ImageFilter.GaussianBlur(min(w, h) / 6))
    im.alpha_composite(blob)
    return im


# ---------------------------------------------------------------------------------------------------------- generic apps
GENERIC = [("Telefon", (0x3C, 0x8D, 0x5A), "phone"), ("Sporočila", (0x2F, 0x6F, 0xD6), "chat"),
           ("Kamera", (0x5B, 0x5F, 0x6B), "camera"), ("Ura", (0x1F, 0x24, 0x2E), "clock"),
           ("Brskalnik", (0xE0, 0x6A, 0x2A), "globe"), ("Glasba", (0xB2, 0x3A, 0x8C), "note")]


def generic_icon(colour, glyph, dp, mask, themed=None):
    size = dp * DENSITY
    big = size * 4
    if themed:
        bgc, fgc = THEMED[themed]
    else:
        bgc, fgc = colour, (255, 255, 255)
    im = Image.new("RGBA", (big, big), bgc + (255,))
    d = ImageDraw.Draw(im)
    c = big / 2; r = big * 0.2; w = max(4, big // 26)
    if glyph == "phone":
        d.arc([c - r, c - r, c + r, c + r], 100, 260, fill=fgc, width=w * 2)
    elif glyph == "chat":
        d.rounded_rectangle([c - r, c - r * 0.8, c + r, c + r * 0.6], radius=r * 0.4, outline=fgc, width=w)
        d.polygon([(c - r * 0.5, c + r * 0.55), (c - r * 0.6, c + r), (c - r * 0.1, c + r * 0.55)], fill=fgc)
    elif glyph == "camera":
        d.rounded_rectangle([c - r * 1.1, c - r * 0.7, c + r * 1.1, c + r * 0.8], radius=r * 0.25, outline=fgc, width=w)
        d.ellipse([c - r * 0.45, c - r * 0.4, c + r * 0.45, c + r * 0.5], outline=fgc, width=w)
    elif glyph == "clock":
        d.ellipse([c - r, c - r, c + r, c + r], outline=fgc, width=w)
        d.line([(c, c), (c, c - r * 0.7)], fill=fgc, width=w); d.line([(c, c), (c + r * 0.5, c)], fill=fgc, width=w)
    elif glyph == "globe":
        d.ellipse([c - r, c - r, c + r, c + r], outline=fgc, width=w)
        d.ellipse([c - r * 0.45, c - r, c + r * 0.45, c + r], outline=fgc, width=w)
        d.line([(c - r, c), (c + r, c)], fill=fgc, width=w)
    elif glyph == "note":
        d.ellipse([c - r * 0.8, c + r * 0.2, c - r * 0.1, c + r * 0.8], fill=fgc)
        d.line([(c - r * 0.15, c + r * 0.5), (c - r * 0.15, c - r * 0.8)], fill=fgc, width=w)
        d.line([(c - r * 0.15, c - r * 0.8), (c + r * 0.6, c - r * 0.5)], fill=fgc, width=w)
    im = im.resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(im, (0, 0), mask_image(mask, size))
    return out


# ---------------------------------------------------------------------------------------------------------- sheet
BG = (0xF4, 0xF1, 0xEA)
TEXT = (0x22, 0x22, 0x28)
MUTED = (0x6A, 0x6A, 0x74)


class Sheet:
    def __init__(self, w, h):
        self.im = Image.new("RGBA", (w, h), BG + (255,))
        self.d = ImageDraw.Draw(self.im)

    def text(self, xy, s, size=20, bold=False, fill=TEXT, anchor="la"):
        self.d.text(xy, s, font=font(size, bold), fill=fill, anchor=anchor)

    def paste(self, im, xy):
        self.im.alpha_composite(im, (int(xy[0]), int(xy[1])))


def home_row(icon: A.Icon, dark: bool, mask: str, themed: str | None, w=824, dp=48):
    """A phone's home screen row at 2 px a dp: five apps a row, Lani in the middle, the labels under them."""
    h = 150 * DENSITY // 2 * 2
    wp = wallpaper(w, h, dark)
    cols = 5
    apps = GENERIC[:2] + [("Lani", None, None)] + GENERIC[2:4]
    cell = w / cols
    fg = (255, 255, 255) if dark else (0x20, 0x20, 0x24)
    d = ImageDraw.Draw(wp)
    for i, (name, col, glyph) in enumerate(apps):
        im = icon_image(icon, dp, mask, themed) if col is None else generic_icon(col, glyph, dp, mask, themed)
        im = shadowed(im)
        x = cell * i + (cell - im.width) / 2
        wp.alpha_composite(im, (int(x), 40))
        d.text((cell * i + cell / 2, 40 + dp * DENSITY + 22), name, font=font(24), fill=fg, anchor="mt")
    return wp


def sheet(cid: str, out: Path):
    c = A.CONCEPTS[cid]
    base = A.Variant()
    icon = c["draw"](base)
    W = 1760
    rows = []
    s = Sheet(W, 5600)
    y = 30
    s.text((40, y), f"{c['sl']} · {c['en']}", 44, True); y += 62
    s.text((40, y), c["line"], 24, fill=MUTED); y += 52

    # 1. the layers: the full 108 dp layer with the visible square and the safe circle, the foreground alone, monochrome
    s.text((40, y), "The layers (108 dp, a pixel 2 dp): the visible 72 dp square and the 66 dp safe circle; the foreground; the monochrome", 22, True); y += 38
    sc = 6
    full = layer_image(icon.composite(), sc, crop=False)
    g = ImageDraw.Draw(full)
    o = A.OFF * sc; v = A.VIS * sc
    g.rectangle([o, o, o + v, o + v], outline=(255, 255, 255, 200), width=2)
    cc = (A.OFF + A.VIS / 2) * sc; r = A.SAFE_R * sc
    g.ellipse([cc - r, cc - r, cc + r, cc + r], outline=(255, 80, 80, 220), width=2)
    s.paste(full, (40, y))
    fgim = layer_image(icon.fg, sc, crop=False)
    checker = Image.new("RGBA", fgim.size, (220, 220, 220, 255))
    cd = ImageDraw.Draw(checker)
    for yy in range(0, fgim.height, 24):
        for xx in range(0, fgim.width, 24):
            if (xx // 24 + yy // 24) % 2: cd.rectangle([xx, yy, xx + 23, yy + 23], fill=(245, 245, 245, 255))
    checker.alpha_composite(fgim)
    s.paste(checker, (40 + full.width + 30, y))
    mono = Image.new("RGBA", fgim.size, (0x2C, 0x34, 0x42, 255))
    mono.alpha_composite(layer_image(icon.mono, sc, crop=False))
    s.paste(mono, (40 + (full.width + 30) * 2, y))
    y += full.height + 40

    # 2. 192 dp in the four masks
    s.text((40, y), "192 dp in the four masks: circle, squircle, rounded square, teardrop", 22, True); y += 38
    x = 40
    for m in MASKS:
        im = icon_image(icon, 192, m)
        s.paste(shadowed(im), (x, y))
        s.text((x + im.width / 2, y + im.height + 14), MASK_NAMES[m], 20, fill=MUTED, anchor="mt")
        x += im.width + 46
    y += 192 * DENSITY + 60

    # 3. 72 and 48 dp on a light and a dark wallpaper, each mask and the themed icon
    for dark in (False, True):
        s.text((40, y), f"72 and 48 dp on a {'dark' if dark else 'light'} wallpaper (the last pair: the themed icon)", 22, True); y += 38
        ph = 240
        wp = wallpaper(W - 80, ph, dark)
        x = 30
        for m in MASKS + ["themed"]:
            for dp in (72, 48):
                im = icon_image(icon, dp, "circle" if m == "themed" else m, ("dark" if dark else "light") if m == "themed" else None)
                im = shadowed(im)
                wp.alpha_composite(im, (x, (ph - 30 - im.height) // 2 + (72 - dp)))
                x += im.width + 18
            ImageDraw.Draw(wp).text((x - 150, ph - 34), MASK_NAMES.get(m, "themed"), font=font(20),
                                    fill=(230, 230, 236) if dark else MUTED)
            x += 40
        s.paste(wp, (40, y)); y += ph + 30

    # 4. the themed icon, Android 13: light and dark, 192 / 72 / 48
    s.text((40, y), "The themed icon (Android 13+, the monochrome layer in the wallpaper's colours): light and dark, 192 / 72 / 48 dp", 22, True); y += 38
    x = 40
    for th in ("light", "dark"):
        for dp in (192, 72, 48):
            im = icon_image(icon, dp, "circle", th)
            s.paste(shadowed(im), (x, y + (192 - dp) * DENSITY // 2))
            x += im.width + 30
        x += 40
    y += 192 * DENSITY + 50

    # 5. the variants
    s.text((40, y), "How it changes (72 dp, squircle)", 30, True); y += 50
    groups = [
        ("By the learner's culture (the base: any village, before a language is picked)", [A.Variant(culture=k) for k in A.CULTURES],
         [A.CULTURE_NAMES[k] for k in A.CULTURES]),
        ("By the season (Primorska's village; Teo's tablet the same in Friuli's)", [A.Variant(culture="primorska", season=k) for k in A.SEASONS] +
         [A.Variant(culture="friuli", season="winter")], [A.SEASON_NAMES[k] for k in A.SEASONS] + ["Friuli · zima"]),
        ("By a festival (a touch shared by the cultures' own days)", [A.Variant(culture="primorska", season=se, fest=f) for f, se in
         zip(A.FESTS, ["winter", "winter", "summer", "autumn"])], [A.FEST_NAMES[k] for k in A.FESTS]),
        ("By the time of day", [A.Variant(culture="primorska", time=k) for k in A.TIMES], [A.TIME_NAMES[k] for k in A.TIMES]),
        ("By today's practice (gentle: done / still to do)", [A.Variant(culture="primorska", time="dusk"), A.Variant(culture="primorska", time="dusk", waiting=True)],
         ["Narejeno · Done", "Čaka · Waiting"]),
    ]
    for title, vs, names in groups:
        s.text((40, y), title, 22); y += 34
        x = 40
        for vv, nm in zip(vs, names):
            im = icon_image(c["draw"](vv), 72, "squircle")
            s.paste(shadowed(im), (x, y))
            s.text((x + im.width / 2, y + im.height + 10), nm, 18, fill=MUTED, anchor="mt")
            x += im.width + 120
        y += 72 * DENSITY + 50

    # 6. the wordmark
    s.text((40, y), "The wordmark", 30, True); y += 50
    pw = (W - 120) // 2
    for dark in (False, True):
        panel = Image.new("RGBA", (pw, 260), ((0x14, 0x21, 0x3D) if dark else (0xFF, 0xFB, 0xF2)) + (255,))
        big = wordmark_image(cid, dark, 8)
        panel.alpha_composite(big, (30, (260 - big.height) // 2))
        x = 30 + big.width + 40
        for sc_ in (4, 2):
            im = wordmark_image(cid, dark, sc_)
            panel.alpha_composite(im, (x, (260 - im.height) // 2))
            x += im.width + 30
        s.paste(panel, (40 + (pw + 40) * dark, y))
    y += 290

    # 7. the home screen rows
    s.text((40, y), "On a home screen at 48 dp (5 a row): light wallpaper and circles, dark and squircles, the themed row", 22, True); y += 38
    r1 = home_row(icon, False, "circle", None)
    r2 = home_row(icon, True, "squircle", None)
    s.paste(r1, (40, y)); s.paste(r2, (40 + r1.width + 32, y)); y += r1.height + 24
    r3 = home_row(icon, False, "circle", "light")
    r4 = home_row(icon, True, "circle", "dark")
    s.paste(r3, (40, y)); s.paste(r4, (40 + r3.width + 32, y)); y += r3.height + 40

    s.im.crop((0, 0, W, y)).convert("RGB").save(out / f"{cid}.png", optimize=True)


def sheets(out: Path, ids):
    out.mkdir(parents=True, exist_ok=True)
    for cid in ids:
        sheet(cid, out)
        print("wrote", out / f"{cid}.png")
    overview(out, ids)


def preview(out: Path, ids):
    """A quick strip per concept while drawing: the composite big, the masks at 48 dp, the themed icon, a few variants."""
    out.mkdir(parents=True, exist_ok=True)
    for cid in ids:
        c = A.CONCEPTS[cid]
        vs = [A.Variant(culture=k) for k in A.CULTURES] + [A.Variant(culture="primorska", season=k) for k in ("spring", "autumn", "winter")] + \
             [A.Variant(culture="primorska", time=k) for k in ("dusk", "night")] + [A.Variant(culture="primorska", fest=f, season="winter" if f in ("lights", "carnival") else "summer") for f in A.FESTS] + \
             [A.Variant(culture="primorska", waiting=True, time="dusk")]
        icon = c["draw"](A.Variant())
        W = 1700
        s = Sheet(W, 1500)
        big = layer_image(icon.composite(), 10)
        s.paste(big, (10, 10))
        x = 380; y = 10
        for m in MASKS:
            im = icon_image(icon, 48, m); s.paste(im, (x, y)); x += 110
        for th in ("light", "dark"):
            im = icon_image(icon, 48, "circle", th); s.paste(im, (x, y)); x += 110
        x = 380; y = 130
        for m in MASKS:
            im = icon_image(icon, 72, m); s.paste(im, (x, y)); x += 160
        im = icon_image(icon, 72, "circle", "dark"); s.paste(im, (x, y))
        x = 10; y = 400
        for i, vv in enumerate(vs):
            im = icon_image(c["draw"](vv), 72, "squircle")
            s.paste(im, (x, y)); s.text((x, y + 148), vv.key.replace("primorska.", "p."), 14, fill=MUTED)
            x += 165
            if x > W - 160: x = 10; y += 180
        s.im.crop((0, 0, W, y + 200)).convert("RGB").save(out / f"preview-{cid}.png")
        print("wrote", out / f"preview-{cid}.png")


def wordmark_image(cid: str, dark: bool, scale: int) -> Image.Image:
    px = A.wordmark(cid, dark)
    xs = [p[0] for p in px]; ys = [p[1] for p in px]
    x0, y0 = min(xs), min(ys)
    w, h = max(xs) - x0 + 1, max(ys) - y0 + 1
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for (x, y), c in px.items():
        im.putpixel((x - x0, y - y0), c + (255,))
    return im.resize((w * scale, h * scale), Image.NEAREST)


def overview(out: Path, ids):
    """All the concepts side by side: each at 144 dp, at 48 dp on both wallpapers, themed, by culture, its wordmark."""
    W = 1760
    rowh = 360
    s = Sheet(W, 80 + rowh * len(ids) + 40)
    s.text((40, 24), "Lani: the five icon concepts", 40, True)
    y = 90
    for cid in ids:
        c = A.CONCEPTS[cid]
        icon = c["draw"](A.Variant())
        s.text((40, y), f"{c['sl']} · {c['en']}", 26, True)
        im = icon_image(icon, 128, "squircle")
        s.paste(shadowed(im), (40, y + 44))
        x = 40 + im.width + 40
        wp = wallpaper(250, 124, False); wd = wallpaper(250, 124, True)
        for k, m in enumerate(("circle", "teardrop")):
            wp.alpha_composite(shadowed(icon_image(icon, 48, m)), (14 + k * 120, 14))
            wd.alpha_composite(shadowed(icon_image(icon, 48, "squircle" if k == 0 else "rounded")), (14 + k * 120, 14))
        s.paste(wp, (x, y + 44)); s.paste(wd, (x, y + 44 + 132))
        x += 270
        for th in ("light", "dark"):
            s.paste(shadowed(icon_image(icon, 48, "circle", th)), (x, y + 44 + (0 if th == "light" else 132) + 14))
        x += 130
        for k in A.CULTURES:
            im2 = icon_image(c["draw"](A.Variant(culture=k)), 64, "squircle")
            s.paste(shadowed(im2), (x, y + 60))
            s.text((x + im2.width / 2, y + 60 + im2.height + 8), A.CULTURE_NAMES[k], 17, fill=MUTED, anchor="mt")
            x += im2.width + 34
        wm = wordmark_image(cid, False, 4)
        s.paste(wm, (40 + 128 * DENSITY + 40 + 270 + 130, y + 236))
        wm2 = wordmark_image(cid, True, 4)
        panel = Image.new("RGBA", (wm2.width + 40, wm2.height + 24), (0x14, 0x21, 0x3D, 255))
        panel.alpha_composite(wm2, (20, 12))
        s.paste(panel, (40 + 128 * DENSITY + 40 + 270 + 130 + wm.width + 40, y + 236 - 12))
        y += rowh
    s.im.convert("RGB").save(out / "overview.png", optimize=True)
    print("wrote", out / "overview.png")
