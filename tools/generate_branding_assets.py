"""Regenerate launcher icons and splash resources from the final public artwork."""

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

# Adaptive foreground is 108dp. The artwork's letters reach the source edges, so the
# square is fit to the 66dp safe-zone diameter and the rest of the canvas is ivory.
ADAPTIVE_ART_SCALE = 66 / 108
# Sampled from the light edge of public/icon.jpg (not the corner ornaments).
LAUNCHER_BG = (252, 236, 214, 255)

GENERATED_STEMS = {
    "mipmap": ("ic_launcher", "ic_launcher_round"),
    "drawable": (
        "ic_launcher_foreground",
        "ic_launcher_monochrome",
        "ic_stat_playback",
        "ic_media_artwork",
    ),
}


def circular_mask(size: int) -> Image.Image:
    hi = size * 4
    circle = Image.new("L", (hi, hi), 0)
    ImageDraw.Draw(circle).ellipse((0, 0, hi - 1, hi - 1), fill=255)
    return circle.resize((size, size), Image.Resampling.LANCZOS)


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


def save_webp(image: Image.Image, path: Path, *, lossless: bool = False, quality: int = 88) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if lossless:
        image.save(path, format="WEBP", lossless=True, method=6)
    else:
        image.save(path, format="WEBP", quality=quality, method=6)


# One full-screen illustration. Density copies would repeat a large bitmap, and
# the source must not be squared or enlarged.
SPLASH_MAX_EDGE = 1440


def write_splash(splash: Image.Image) -> None:
    for path in list(RES.rglob("splash.png")) + list(RES.rglob("splash.webp")):
        path.unlink(missing_ok=True)
    image = splash.convert("RGB")
    width, height = image.size
    long_edge = max(width, height)
    if long_edge > SPLASH_MAX_EDGE:
        scale = SPLASH_MAX_EDGE / long_edge
        image = image.resize(
            (max(1, int(round(width * scale))), max(1, int(round(height * scale)))),
            Image.Resampling.LANCZOS,
        )
    save_webp(image, RES / "drawable-nodpi" / "splash.webp", quality=92)


def ink_alpha(src: Image.Image, *, lo: float = 18, hi: float = 55) -> Image.Image:
    """Silhouette alpha: ivory background stays clear, brown and gold strokes stay solid."""
    rgb = src.convert("RGB")
    bg = LAUNCHER_BG[:3]
    pixels = rgb.load()
    width, height = rgb.size
    alpha = Image.new("L", (width, height))
    out = alpha.load()
    span = hi - lo
    for y in range(height):
        for x in range(width):
            red, green, blue = pixels[x, y]
            distance = (
                (red - bg[0]) ** 2 + (green - bg[1]) ** 2 + (blue - bg[2]) ** 2
            ) ** 0.5
            if distance <= lo:
                out[x, y] = 0
            elif distance >= hi:
                out[x, y] = 255
            else:
                out[x, y] = int(round((distance - lo) * 255 / span))
    return alpha


def foreground_layer(src: Image.Image, canvas: int, scale: float) -> Image.Image:
    inner = max(1, int(round(canvas * scale)))
    resized = src.convert("RGB").resize((inner, inner), Image.Resampling.LANCZOS)
    out = Image.new("RGB", (canvas, canvas), LAUNCHER_BG[:3])
    offset = (canvas - inner) // 2
    out.paste(resized, (offset, offset))
    return out


def monochrome_layer(alpha: Image.Image, canvas: int, scale: float) -> Image.Image:
    inner = max(1, int(round(canvas * scale)))
    scaled = alpha.resize((inner, inner), Image.Resampling.LANCZOS)
    glyph = Image.new("RGBA", (inner, inner), (255, 255, 255, 0))
    glyph.putalpha(scaled)
    out = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    offset = (canvas - inner) // 2
    out.alpha_composite(glyph, (offset, offset))
    return out


def legacy_square(src: Image.Image, size: int) -> Image.Image:
    return src.convert("RGB").resize((size, size), Image.Resampling.LANCZOS)


def legacy_round(src: Image.Image, size: int) -> Image.Image:
    image = src.convert("RGBA").resize((size, size), Image.Resampling.LANCZOS)
    image.putalpha(circular_mask(size))
    return image


def write_launcher_icons(icon: Image.Image) -> None:
    """Replace launcher mipmaps and adaptive layers. Does not touch splash or notification art."""
    image = icon.convert("RGB")
    alpha = ink_alpha(image)
    write_density(
        "mipmap",
        "ic_launcher",
        lambda s: legacy_square(image, max(1, int(round(48 * s)))),
        lossless=True,
    )
    write_density(
        "mipmap",
        "ic_launcher_round",
        lambda s: legacy_round(image, max(1, int(round(48 * s)))),
        lossless=True,
    )
    write_density(
        "drawable",
        "ic_launcher_foreground",
        lambda s: foreground_layer(image, max(1, int(round(108 * s))), ADAPTIVE_ART_SCALE),
        lossless=True,
    )
    write_density(
        "drawable",
        "ic_launcher_monochrome",
        lambda s: monochrome_layer(alpha, max(1, int(round(108 * s))), ADAPTIVE_ART_SCALE),
        lossless=True,
    )


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
    icon_path = PUBLIC / "icon.jpg"
    if not icon_path.is_file():
        icon_path = PUBLIC / "icon.png"
    icon = Image.open(icon_path)
    splash = Image.open(PUBLIC / "splash.png")
    favicon_path = PUBLIC / "favicon.png"
    favicon = Image.open(favicon_path) if favicon_path.is_file() else icon

    delete_legacy_assets()

    write_launcher_icons(icon)
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
    write_splash(splash)
    print(f"Generated WebP launcher icons from {icon_path.name} and splash resources from public/splash.png")


if __name__ == "__main__":
    main()
