#!/usr/bin/env python3
"""Pixel-art launcher icon: a speech bubble with Č over Triglav at dusk. 36×36 px of 3 dp (108 dp).

  python3 companion/android/tools/launcher_icon.py companion/android/app/src/main/res/drawable [PREVIEW_DIR]

writes ic_launcher_background.xml, ic_launcher_foreground.xml and ic_launcher_monochrome.xml, and with
PREVIEW_DIR PNG previews (the full layer, round and squircle masks, 48 px).
"""
import sys, zlib, struct
from pathlib import Path

N = 36
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".")
PREVIEW = Path(sys.argv[2]) if len(sys.argv) > 2 else None

# --- background: dusk sky, stars, Triglav -------------------------------------------------------
SKY = ["#12295C", "#15306A", "#1A3A7C", "#1F458E", "#2553A0", "#2C61B0", "#3570BE", "#3F7FC8"]
bg = [[None] * N for _ in range(N)]
for y in range(N):
    band = min(len(SKY) - 1, y * len(SKY) // 30)
    for x in range(N):
        c = SKY[band]
        # ordered dither between bands for a soft gradient
        if band + 1 < len(SKY) and (y * len(SKY)) % 30 >= 15 and (x + y) % 2 == 0:
            c = SKY[band + 1]
        bg[y][x] = c
# Triglav: three peaks, the middle one highest, snow on the tops
PEAKS = [(10, 28), (18, 23), (26, 28)]
def ridge(x):
    return min(min(abs(x - px) + py for px, py in PEAKS), 33)
for x in range(N):
    top = ridge(x)
    for y in range(top, N):
        bg[y][x] = "#2A4A84" if y - top <= 1 else "#1A3364"
# snow on the tops only: two rows on Triglav, one on the side peaks
for i, (px, py) in enumerate(PEAKS):
    rows = 3 if i == 1 else 2
    for dy in range(rows):
        for x in range(px - dy, px + dy + 1):
            if 0 <= x < N and ridge(x) <= py + dy:
                bg[py + dy][x] = "#E8EEF8"
for (x, y) in [(8, 9), (27, 7), (9, 17), (28, 16), (22, 5), (13, 5)]:
    if bg[y][x] in SKY: bg[y][x] = "#FFF4D6"

# --- foreground: the bubble and Č ------------------------------------------------------------------
fg = [[None] * N for _ in range(N)]
X0, Y0, W, H = 11, 8, 14, 12  # bubble box (inside the 22 px safe circle, corners rounded off)
OUTLINE, FILL, SHADE, INK = "#0B1B3A", "#FFFFFF", "#DCE6F5", "#0B4EA2"
def inside(x, y):
    if not (X0 <= x < X0 + W and Y0 <= y < Y0 + H):
        return False
    cx = min(x - X0, X0 + W - 1 - x); cy = min(y - Y0, Y0 + H - 1 - y)
    return not (cx + cy < 2)  # cut the corners: rounded
tail = [(13, 20), (14, 20), (12, 21), (13, 21), (12, 22)]
for y in range(N):
    for x in range(N):
        if inside(x, y) or (x, y) in tail:
            fg[y][x] = FILL
# shade: the bubble's lower-right edge band
for y in range(N):
    for x in range(N):
        if fg[y][x] == FILL and (not inside(x + 1, y + 1) and (x + 1, y + 1) not in tail):
            fg[y][x] = SHADE
# outline around everything filled
filled = {(x, y) for y in range(N) for x in range(N) if fg[y][x]}
for (x, y) in list(filled):
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        p = (x + dx, y + dy)
        if p not in filled and 0 <= p[0] < N and 0 <= p[1] < N:
            fg[p[1]][p[0]] = OUTLINE
# the letter Č, centred in the bubble
GLYPH = [
    ".#...#.",
    "..#.#..",
    "...#...",
    ".......",
    "..####.",
    ".##..##",
    "##.....",
    "##.....",
    ".##..##",
    "..####.",
]
gw, gh = len(GLYPH[0]), len(GLYPH)
gx = X0 + (W - gw) // 2; gy = Y0 + (H - gh) // 2
for j, row in enumerate(GLYPH):
    for i, ch in enumerate(row):
        if ch == "#":
            fg[gy + j][gx + i] = INK

# --- output ------------------------------------------------------------------------------------------
def vector(grid, comment):
    runs = {}
    for y in range(N):
        x = 0
        while x < N:
            c = grid[y][x]
            if c is None:
                x += 1; continue
            x1 = x
            while x1 < N and grid[y][x1] == c:
                x1 += 1
            runs.setdefault(c, []).append(f"M{x*3},{y*3}h{(x1-x)*3}v3h-{(x1-x)*3}z")
            x = x1
    paths = "\n".join(f'    <path android:fillColor="{c}" android:pathData="{"".join(p)}" />' for c, p in runs.items())
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {comment} Generated pixel art: 36×36 pixels of 3 dp. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
{paths}
</vector>
'''

mono = [[("#FFFFFF" if (fg[y][x] in (FILL, SHADE, OUTLINE) and fg[y][x] != INK) else None) for x in range(N)] for y in range(N)]
for y in range(N):
    for x in range(N):
        if fg[y][x] in (INK, "#E4002B"):
            mono[y][x] = None  # the letter is cut out of the bubble

OUT.mkdir(parents=True, exist_ok=True)
(OUT / "ic_launcher_background.xml").write_text(vector(bg, "Dusk over Triglav, a few stars."))
(OUT / "ic_launcher_foreground.xml").write_text(vector(fg, "A speech bubble with Č: learning Slovene."))
(OUT / "ic_launcher_monochrome.xml").write_text(vector(mono, "The bubble with Č cut out, for themed icons."))

# previews: PNG of the composite at 8× (288 px), plus round and squircle masks
def png(path, grid_fn, scale, mask=None):
    size = N * scale
    raw = bytearray()
    for Y in range(size):
        raw.append(0)
        for X in range(size):
            c = grid_fn(X // scale, Y // scale)
            a = 255
            if mask and not mask(X / size, Y / size):
                c, a = "#FFFFFF", 0
            v = int(c[1:], 16)
            raw += bytes(((v >> 16) & 255, (v >> 8) & 255, v & 255, a))
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))

comp = lambda x, y: fg[y][x] or bg[y][x]
# launchers show the middle 72 of 108 dp: crop to that
def cropped(x, y): return comp(x + 6, y + 6)
circle = lambda u, v: (u - 0.5) ** 2 + (v - 0.5) ** 2 <= 0.25
squircle = lambda u, v: abs(u - 0.5) ** 4 + abs(v - 0.5) ** 4 <= 0.5 ** 4
if PREVIEW:
    PREVIEW.mkdir(parents=True, exist_ok=True)
    png(PREVIEW / "preview-full.png", comp, 8)
    N = 24  # the 72 dp a launcher shows
    png(PREVIEW / "preview-circle.png", cropped, 12, circle)
    png(PREVIEW / "preview-squircle.png", cropped, 12, squircle)
    png(PREVIEW / "preview-48.png", cropped, 2, circle)
