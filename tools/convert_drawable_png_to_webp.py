#!/usr/bin/env python3
"""Convert PNG drawables under app/src/main/res to lossless WebP and remove PNGs."""

from __future__ import annotations

import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    print("Pillow is required: pip install Pillow", file=sys.stderr)
    sys.exit(1)

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"

# drawable-* folders (excludes mipmap launcher icons).
DRAWABLE_GLOBS = [
    "drawable",
    "drawable-nodpi",
    "drawable-hdpi",
    "drawable-mdpi",
    "drawable-xhdpi",
    "drawable-xxhdpi",
    "drawable-xxxhdpi",
]

saved_bytes = 0
converted = 0
skipped = 0


def convert_png(png_path: Path) -> None:
    global saved_bytes, converted, skipped
    webp_path = png_path.with_suffix(".webp")
    if webp_path.exists():
        skipped += 1
        print(f"skip (webp exists): {png_path.relative_to(ROOT)}")
        return

    with Image.open(png_path) as img:
        img.save(webp_path, format="WEBP", lossless=True, method=6)

    png_size = png_path.stat().st_size
    webp_size = webp_path.stat().st_size
    saved_bytes += max(0, png_size - webp_size)
    converted += 1
    png_path.unlink()
    print(
        f"converted {png_path.relative_to(ROOT)} "
        f"({png_size:,} -> {webp_size:,} bytes, -{png_size - webp_size:,})"
    )


def main() -> None:
    pngs: list[Path] = []
    for folder in DRAWABLE_GLOBS:
        base = RES / folder
        if base.is_dir():
            pngs.extend(sorted(base.glob("*.png")))

    if not pngs:
        print("No PNG drawables found.")
        return

    for png in pngs:
        convert_png(png)

    print(
        f"\nDone: {converted} converted, {skipped} skipped, "
        f"saved ~{saved_bytes / 1024:.1f} KB"
    )


if __name__ == "__main__":
    main()
