"""Regenerate launcher icons and splash resources from the final public artwork."""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

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


def circular_mask(size: int) -> Image.Image:
    hi = size * 4
    circle = Image.new("L", (hi, hi), 0)
    ImageDraw.Draw(circle).ellipse((0, 0, hi - 1, hi - 1), fill=255)
    return circle.resize((size, size), Image.Resampling.LANCZOS)


def round_icon(src: Image.Image, size: int) -> Image.Image:
    squared = src.convert("RGBA").resize((size, size), Image.Resampling.LANCZOS)
    mask = circular_mask(size)
    squared.putalpha(ImageChops.multiply(squared.getchannel("A"), mask))
    return squared


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


def main() -> None:
    icon = Image.open(PUBLIC / "icon.png")
    splash = Image.open(PUBLIC / "splash.png")
    favicon_path = PUBLIC / "favicon.png"
    favicon = Image.open(favicon_path) if favicon_path.is_file() else icon

    delete_legacy_assets()

    write_density(
        "mipmap",
        "ic_launcher",
        lambda s: icon.convert("RGBA").resize(
            (max(1, int(round(48 * s))), max(1, int(round(48 * s)))),
            Image.Resampling.LANCZOS,
        ),
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
        lambda s: notification_white(icon, max(1, int(round(108 * s)))),
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
    write_density(
        "drawable",
        "splash",
        lambda s: splash.convert("RGB").resize(
            (max(1, int(round(360 * s))), max(1, int(round(360 * s)))),
            Image.Resampling.LANCZOS,
        ),
        lossless=True,
    )
    print("Generated WebP launcher icons from public/icon.png and splash resources from public/splash.png")


if __name__ == "__main__":
    main()
