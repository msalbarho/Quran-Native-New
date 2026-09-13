"""Regenerate launcher icons + a single lightweight splash from public/icon.png."""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
PUBLIC = ROOT / "public"
RES = ROOT / "app" / "src" / "main" / "res"

DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}

# Adaptive foreground is 108dp; keep the circular mark inside the 72dp safe zone.
ADAPTIVE_SAFE_SCALE = 72 / 108

# One nodpi splash — Android scales; avoids ~6 MB of port/land/density PNGs in the APK.
SPLASH_MAX_EDGE = 512
SPLASH_ICON_RATIO = 0.78

GENERATED_STEMS = {
    "mipmap": ("ic_launcher", "ic_launcher_round"),
    "drawable": (
        "ic_launcher_foreground",
        "ic_launcher_monochrome",
        "ic_stat_playback",
        "ic_media_artwork",
    ),
}


def fit_center(src: Image.Image, canvas: int, scale: float = 1.0) -> Image.Image:
    out = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    inner = max(1, int(round(canvas * scale)))
    resized = src.convert("RGBA").resize((inner, inner), Image.Resampling.LANCZOS)
    offset = (canvas - inner) // 2
    out.alpha_composite(resized, (offset, offset))
    return out


def on_black(src: Image.Image, size: int, scale: float = 1.0) -> Image.Image:
    layered = fit_center(src, size, scale)
    bg = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    bg.alpha_composite(layered)
    return bg


def circular_mask(size: int) -> Image.Image:
    hi = size * 4
    circle = Image.new("L", (hi, hi), 0)
    ImageDraw.Draw(circle).ellipse((0, 0, hi - 1, hi - 1), fill=255)
    return circle.resize((size, size), Image.Resampling.LANCZOS)


def round_icon(src: Image.Image, size: int) -> Image.Image:
    squared = on_black(src, size, scale=1.0)
    mask = circular_mask(size)
    squared.putalpha(mask)
    bg = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    bg.alpha_composite(squared)
    return bg.convert("RGB")


def notification_white(src: Image.Image, size: int) -> Image.Image:
    rgba = src.convert("RGBA")
    alpha = rgba.getchannel("A")
    if size <= 48:
        alpha = alpha.filter(ImageFilter.GaussianBlur(radius=0.4))
    white = Image.new("L", rgba.size, 255)
    out = Image.merge("RGBA", (white, white, white, alpha))
    return out.resize((size, size), Image.Resampling.LANCZOS)


def notification_black(src: Image.Image, size: int) -> Image.Image:
    rgba = src.convert("RGBA")
    alpha = rgba.getchannel("A")
    black = Image.new("L", rgba.size, 0)
    out = Image.merge("RGBA", (black, black, black, alpha))
    return out.resize((size, size), Image.Resampling.LANCZOS)


def splash_frame(icon: Image.Image, width: int, height: int) -> Image.Image:
    """Full-bleed #000000 canvas with a large centered emblem."""
    canvas = Image.new("RGBA", (width, height), (0, 0, 0, 255))
    side = max(1, int(round(min(width, height) * SPLASH_ICON_RATIO)))
    mark = icon.convert("RGBA").resize((side, side), Image.Resampling.LANCZOS)
    x = (width - side) // 2
    y = (height - side) // 2
    canvas.alpha_composite(mark, (x, y))
    return canvas.convert("RGB")


def save_webp(image: Image.Image, path: Path, *, lossless: bool = False) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if lossless:
        image.save(path, format="WEBP", lossless=True, method=6)
    else:
        image.save(path, format="WEBP", quality=88, method=6)


def write_density(folder_prefix: str, stem: str, factory, *, lossless: bool = False) -> None:
    for qualifier, scale in DENSITIES.items():
        folder = RES / f"{folder_prefix}-{qualifier}"
        save_webp(factory(scale), folder / f"{stem}.webp", lossless=lossless)


def delete_legacy_assets() -> None:
    removed = 0
    for folder_prefix, stems in GENERATED_STEMS.items():
        for qualifier in DENSITIES:
            folder = RES / f"{folder_prefix}-{qualifier}"
            if not folder.is_dir():
                continue
            for stem in stems:
                for ext in (".png", ".webp"):
                    path = folder / f"{stem}{ext}"
                    if path.is_file():
                        path.unlink()
                        removed += 1
    for path in RES.rglob("splash.png"):
        path.unlink(missing_ok=True)
        removed += 1
    for path in RES.rglob("splash.webp"):
        path.unlink(missing_ok=True)
        removed += 1
    for path in sorted(RES.glob("drawable*"), reverse=True):
        if path.is_dir() and not any(path.iterdir()):
            path.rmdir()
    print(f"Removed {removed} legacy launcher/splash assets")


def write_single_splash(icon: Image.Image) -> None:
    """One nodpi WebP referenced by launch_splash + Compose brand overlay."""
    base_w, base_h = 320, 480
    if max(base_w, base_h) > SPLASH_MAX_EDGE:
        scale = SPLASH_MAX_EDGE / max(base_w, base_h)
        base_w = max(1, int(round(base_w * scale)))
        base_h = max(1, int(round(base_h * scale)))
    frame = splash_frame(icon, base_w, base_h)
    out = RES / "drawable-nodpi" / "splash.webp"
    save_webp(frame, out)
    print(f"Wrote single splash {out.relative_to(ROOT)} ({out.stat().st_size // 1024} KB)")


def main() -> None:
    icon = Image.open(PUBLIC / "icon.png")
    favicon_path = PUBLIC / "favicon.png"
    favicon = Image.open(favicon_path) if favicon_path.is_file() else icon

    delete_legacy_assets()

    write_density(
        "mipmap",
        "ic_launcher",
        lambda s: on_black(icon, max(1, int(round(48 * s))), scale=1.0).convert("RGB"),
    )
    write_density(
        "mipmap",
        "ic_launcher_round",
        lambda s: round_icon(icon, max(1, int(round(48 * s)))),
    )
    write_density(
        "drawable",
        "ic_launcher_foreground",
        lambda s: fit_center(icon, max(1, int(round(108 * s))), scale=ADAPTIVE_SAFE_SCALE),
        lossless=True,
    )
    write_density(
        "drawable",
        "ic_launcher_monochrome",
        lambda s: notification_white(favicon, max(1, int(round(108 * s)))),
        lossless=True,
    )
    write_density(
        "drawable",
        "ic_stat_playback",
        lambda s: notification_white(favicon, max(1, int(round(24 * s)))),
        lossless=True,
    )
    write_density(
        "drawable",
        "ic_media_artwork",
        lambda s: notification_black(favicon, max(1, int(round(108 * s)))),
        lossless=True,
    )
    write_single_splash(icon)
    print("Generated WebP launcher icons and lightweight splash from public/icon.png")


if __name__ == "__main__":
    main()
