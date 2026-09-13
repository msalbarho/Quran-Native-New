"""Convert QCF4_QBSML.woff2 (React) to qbsml.ttf for the generated Android assets pack.

Writes only to public/fonts so Gradle can copy it into build/generated/publicAssets.
The WOFF2 source is never packaged, and src/main/assets/fonts is not used.
"""

from __future__ import annotations

import sys
from pathlib import Path

SRC = Path(r"D:\quran-app\public\fonts\qcf4\QCF4_QBSML.woff2")
DEST = Path(r"D:\HolyQuran-Native\public\fonts\qbsml.ttf")
STALE_ASSET = Path(r"D:\HolyQuran-Native\app\src\main\assets\fonts\qbsml.ttf")


def convert() -> Path:
    if not SRC.is_file():
        raise SystemExit(f"Missing source font: {SRC}")

    from fontTools.ttLib import TTFont

    DEST.parent.mkdir(parents=True, exist_ok=True)
    font = TTFont(str(SRC))
    font.flavor = None
    if "prop" in font:
        del font["prop"]
    if "post" in font:
        font["post"].formatType = 3.0
    font.save(str(DEST))
    font.close()
    if STALE_ASSET.is_file():
        STALE_ASSET.unlink()
    return DEST


if __name__ == "__main__":
    try:
        from fontTools.ttLib import TTFont  # noqa: F401
    except ModuleNotFoundError:
        import subprocess

        subprocess.check_call(
            [sys.executable, "-m", "pip", "install", "fonttools", "brotli"],
        )
    try:
        out = convert()
    except ModuleNotFoundError:
        print("fontTools missing", file=sys.stderr)
        sys.exit(2)
    print(f"wrote {out} ({out.stat().st_size} bytes)")
