"""Write a tiny 48x48 black PNG with a teal star for launcher fallback."""
import struct
import zlib
from pathlib import Path

WIDTH = 48
HEIGHT = 48


def pixel(x: int, y: int) -> bytes:
    cx, cy = 23.5, 23.5
    dx, dy = x - cx, y - cy
    r2 = dx * dx + dy * dy
    # ring
    if 120 <= r2 <= 175:
        return bytes([0x2E, 0xC4, 0xB6, 0xFF])
    # 8-point star via intersecting diamonds
    star = (abs(dx) + abs(dy) * 0.55 < 8) or (abs(dy) + abs(dx) * 0.55 < 8)
    if star and r2 < 140:
        return bytes([0x2E, 0xC4, 0xB6, 0xFF])
    return bytes([0x00, 0x00, 0x00, 0xFF])


def png(path: Path) -> None:
    raw = b"".join(b"\x00" + b"".join(pixel(x, y) for x in range(WIDTH)) for y in range(HEIGHT))

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", WIDTH, HEIGHT, 8, 6, 0, 0, 0)
    content = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(content)


root = Path(r"d:\HolyQuran-Native\app\src\main\res")
for density in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"):
    png(root / f"mipmap-{density}" / "ic_launcher.png")
    png(root / f"mipmap-{density}" / "ic_launcher_round.png")
print("wrote launcher pngs")
