#!/usr/bin/env python3
"""Rasterize chapter_header.svg via headless Chrome, then split tint layers.

Layer mapping (new SVG classes):
  transparent outside → chromeFill mask (theme)
  #fdfdfd white       → pageBody mask
  #000000 black near edge → outer gold mask
  #000000 remaining   → inner gold mask
  #6887ff blue        → accent-tintable mask (settings color)
  #fb68a2 pink        → color overlay (never tinted)
"""

from __future__ import annotations

import json
import shutil
import subprocess
import tempfile
from collections import deque
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE_SVG = ROOT / "public" / "assets" / "chapter_header.svg"
SOURCE_PNG = ROOT / "public" / "assets" / "chapter_header.png"
OUT_DIR = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"

VIEW_W, VIEW_H = 3218, 380
# Render at 2x for cleaner AA, then we keep that resolution for drawables.
SCALE = 2
RENDER_W, RENDER_H = VIEW_W * SCALE, VIEW_H * SCALE

ALPHA_CUT = 24
WHITE_MIN = 200
BLACK_MAX = 72
GRAY_SPLIT = 128
# Scaled with render resolution (was 3 at ~1x historical PNG).
OUTER_GOLD_DIST = 3 * SCALE

# Exact SVG fills
BLUE = (0x68, 0x87, 0xFF)
PINK = (0xFB, 0x68, 0xA2)


def find_chrome() -> str:
    candidates = [
        Path(r"C:\Program Files\Google\Chrome\Application\chrome.exe"),
        Path(r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"),
        Path(r"C:\Program Files\Microsoft\Edge\Application\msedge.exe"),
        Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"),
    ]
    for c in candidates:
        if c.is_file():
            return str(c)
    which = shutil.which("chrome") or shutil.which("msedge")
    if which:
        return which
    raise SystemExit("Chrome/Edge not found for SVG rasterization")


def rasterize_svg() -> Path:
    if not SOURCE_SVG.is_file():
        raise SystemExit(f"Missing {SOURCE_SVG}")

    chrome = find_chrome()
    html = f"""<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
  html, body {{ margin:0; padding:0; background:transparent; overflow:hidden; }}
  img {{ display:block; width:{RENDER_W}px; height:{RENDER_H}px; }}
</style></head>
<body><img src="{SOURCE_SVG.as_uri()}" width="{RENDER_W}" height="{RENDER_H}"/></body></html>
"""
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        html_path = tmp_path / "frame.html"
        shot_path = tmp_path / "shot.png"
        html_path.write_text(html, encoding="utf-8")
        cmd = [
            chrome,
            "--headless=new",
            "--disable-gpu",
            "--hide-scrollbars",
            "--default-background-color=00000000",
            f"--window-size={RENDER_W},{RENDER_H}",
            f"--screenshot={shot_path}",
            html_path.as_uri(),
        ]
        print("rasterizing via", chrome)
        subprocess.run(cmd, check=True, capture_output=True)
        if not shot_path.is_file():
            raise SystemExit("Chrome screenshot failed")
        img = Image.open(shot_path).convert("RGBA")
        # Chrome may pad; crop to expected size if larger.
        if img.size != (RENDER_W, RENDER_H):
            print(f"screenshot size {img.size}, cropping/resizing to {RENDER_W}x{RENDER_H}")
            img = img.crop((0, 0, min(img.width, RENDER_W), min(img.height, RENDER_H)))
            if img.size != (RENDER_W, RENDER_H):
                canvas = Image.new("RGBA", (RENDER_W, RENDER_H), (0, 0, 0, 0))
                canvas.paste(img, (0, 0))
                img = canvas
        SOURCE_PNG.parent.mkdir(parents=True, exist_ok=True)
        img.save(SOURCE_PNG, format="PNG")
        print(f"wrote {SOURCE_PNG.relative_to(ROOT)} ({SOURCE_PNG.stat().st_size // 1024} KB) {img.size}")
        return SOURCE_PNG


def luminance(r: int, g: int, b: int) -> float:
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def color_dist(a: tuple[int, int, int], b: tuple[int, int, int]) -> float:
    return abs(a[0] - b[0]) + abs(a[1] - b[1]) + abs(a[2] - b[2])


def is_white(r: int, g: int, b: int) -> bool:
    if min(r, g, b) >= WHITE_MIN:
        return True
    lum = luminance(r, g, b)
    spread = max(r, g, b) - min(r, g, b)
    return lum >= 210 and spread <= 36


def is_black(r: int, g: int, b: int) -> bool:
    if max(r, g, b) <= BLACK_MAX:
        return True
    return luminance(r, g, b) <= 58


def is_blue(r: int, g: int, b: int) -> bool:
    """Match #6887ff and AA blues (dominant blue, not pink)."""
    if color_dist((r, g, b), BLUE) <= 90:
        return True
    # Blue channel dominant vs red/green; exclude pinks (high R).
    if b < 90:
        return False
    if b <= r + 10 or b <= g + 8:
        return False
    if r > 200 and b < 220:
        return False  # pinkish
    if b >= 120 and b >= r + 30 and b >= g + 20:
        return True
    if b >= 80 and b > r and b > g and (b - max(r, g)) >= 18 and r < 180:
        return True
    return False


def is_pink(r: int, g: int, b: int) -> bool:
    if color_dist((r, g, b), PINK) <= 90:
        return True
    if r < 80:
        return False
    if r <= g or r <= b:
        return False
    if r > 175 and g < 165 and b > 90 and g < b:
        return True
    if r >= 96 and b >= 40 and r >= g + 24 and b >= g and (r - g) >= 20:
        return True
    return False


def theme_distance(theme_a: list[int], w: int, h: int) -> list[list[int]]:
    dist = [[9999] * w for _ in range(h)]
    q: deque[tuple[int, int]] = deque()
    for y in range(h):
        row = y * w
        for x in range(w):
            if theme_a[row + x]:
                dist[y][x] = 0
                q.append((x, y))
    while q:
        x, y = q.popleft()
        d = dist[y][x]
        if d >= OUTER_GOLD_DIST + 2:
            continue
        for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if 0 <= nx < w and 0 <= ny < h and dist[ny][nx] > d + 1:
                dist[ny][nx] = d + 1
                q.append((nx, ny))
    return dist


def write_mask(path: Path, size: tuple[int, int], alpha: list[int]) -> None:
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    px = img.load()
    w, h = size
    for y in range(h):
        row = y * w
        for x in range(w):
            a = alpha[row + x]
            if a:
                px[x, y] = (255, 255, 255, a)
    _save_webp(path, img)


def write_color_overlay(path: Path, size: tuple[int, int], rgba: list[tuple[int, int, int, int]]) -> None:
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    px = img.load()
    w, h = size
    for y in range(h):
        row = y * w
        for x in range(w):
            px[x, y] = rgba[row + x]
    _save_webp(path, img)


def _save_webp(path: Path, img: Image.Image) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, format="WEBP", lossless=True, method=6)
    print(f"wrote {path.relative_to(ROOT)} ({path.stat().st_size // 1024} KB)")


def measure_slots(page_a: list[int], w: int, h: int) -> dict:
    """Estimate LTR white-slot centers from page mask (for SurahNameFrame)."""
    # Consider upper/mid band where side + center text slots live.
    y0, y1 = int(h * 0.25), int(h * 0.75)
    cols = [0] * w
    for y in range(y0, y1):
        row = y * w
        for x in range(w):
            if page_a[row + x] > 180:
                cols[x] += 1
    # Find contiguous runs with enough hits.
    thr = max(2, (y1 - y0) // 8)
    runs: list[tuple[int, int]] = []
    x = 0
    while x < w:
        if cols[x] >= thr:
            start = x
            while x < w and cols[x] >= thr:
                x += 1
            runs.append((start, x - 1))
        else:
            x += 1
    # Keep three widest runs (left, center, right).
    runs = sorted(runs, key=lambda r: r[1] - r[0], reverse=True)[:3]
    runs = sorted(runs, key=lambda r: (r[0] + r[1]) / 2)
    slots = []
    for a, b in runs:
        mid = (a + b) / 2 / w
        width = (b - a + 1) / w
        slots.append({"center": round(mid, 4), "width": round(width, 4)})
    return {"slots": slots, "runs": runs}


def split_layers(png: Path) -> None:
    src = Image.open(png).convert("RGBA")
    w, h = src.size
    px = src.load()

    theme_a = [0] * (w * h)
    page_a = [0] * (w * h)
    black_a = [0] * (w * h)
    blue_a = [0] * (w * h)  # accent tint mask (white alpha)
    pink_rgba = [(0, 0, 0, 0)] * (w * h)

    counts = {"theme": 0, "page": 0, "black": 0, "blue": 0, "pink": 0}
    for y in range(h):
        for x in range(w):
            i = y * w + x
            r, g, b, a = px[x, y]
            if a < ALPHA_CUT:
                theme_a[i] = 255
                counts["theme"] += 1
                continue
            if is_pink(r, g, b):
                pink_rgba[i] = (r, g, b, a)
                counts["pink"] += 1
                continue
            if is_blue(r, g, b):
                blue_a[i] = a
                counts["blue"] += 1
                continue
            if is_white(r, g, b):
                page_a[i] = a
                counts["page"] += 1
                continue
            if is_black(r, g, b):
                black_a[i] = a
                counts["black"] += 1
                continue
            # AA leftovers by nearest channel family
            if is_blue(r, g, b) or (b > r + 8 and b > g + 8 and r < 190):
                blue_a[i] = a
                counts["blue"] += 1
            elif r > g + 12 and r > b:
                pink_rgba[i] = (r, g, b, a)
                counts["pink"] += 1
            elif luminance(r, g, b) >= GRAY_SPLIT:
                page_a[i] = a
                counts["page"] += 1
            else:
                black_a[i] = a
                counts["black"] += 1

    dist = theme_distance(theme_a, w, h)
    gold_outer_a = [0] * (w * h)
    gold_inner_a = [0] * (w * h)
    outer_n = inner_n = 0
    for y in range(h):
        for x in range(w):
            i = y * w + x
            a = black_a[i]
            if not a:
                continue
            if dist[y][x] <= OUTER_GOLD_DIST:
                gold_outer_a[i] = a
                outer_n += 1
            else:
                gold_inner_a[i] = a
                inner_n += 1

    write_mask(OUT_DIR / "surah_header_theme.webp", (w, h), theme_a)
    write_mask(OUT_DIR / "surah_header_page.webp", (w, h), page_a)
    write_mask(OUT_DIR / "surah_header_gold_outer.webp", (w, h), gold_outer_a)
    write_mask(OUT_DIR / "surah_header_gold_inner.webp", (w, h), gold_inner_a)
    # Keep drawable name surah_header_green for Compose tint → accent.
    write_mask(OUT_DIR / "surah_header_green.webp", (w, h), blue_a)
    write_color_overlay(OUT_DIR / "surah_header_pink.webp", (w, h), pink_rgba)

    for legacy_name in ("surah_header_black.webp", "surah_header_gold.webp", "surah_header_blue.webp"):
        legacy = OUT_DIR / legacy_name
        if legacy.exists():
            legacy.unlink()
            print(f"removed {legacy.relative_to(ROOT)}")

    slots = measure_slots(page_a, w, h)
    meta = {
        "source": str(SOURCE_SVG.relative_to(ROOT)).replace("\\", "/"),
        "size": [w, h],
        "aspect": round(w / h, 6),
        "viewBoxAspect": round(VIEW_W / VIEW_H, 6),
        "counts": counts,
        "gold": {"outer": outer_n, "inner": inner_n},
        "slots": slots,
    }
    meta_path = ROOT / "public" / "assets" / "chapter_header_layers.json"
    meta_path.write_text(json.dumps(meta, indent=2), encoding="utf-8")
    print(f"source size: {w}x{h} aspect={w/h:.4f}")
    print("classified pixels:", ", ".join(f"{k}={v}" for k, v in counts.items()))
    print(f"gold split: outer={outer_n}, inner={inner_n}")
    print("slot estimate:", slots)
    print(f"wrote {meta_path.relative_to(ROOT)}")


def main() -> None:
    png = rasterize_svg()
    split_layers(png)


if __name__ == "__main__":
    main()
