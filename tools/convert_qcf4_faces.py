"""Convert React QCF4_Hafs_NN_W.woff2 faces to TTF for the Android app.

WOFF2 is never packaged. Output is public/fonts/qcf4/QCF4_Hafs_NN.ttf.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

SRC_DIR = Path(r"D:\quran-app\public\fonts\qcf4")
DEFAULT_DEST = Path(r"D:\HolyQuran-Native\public\fonts\qcf4")


def convert_all(dest_dir: Path) -> list[Path]:
    try:
        from fontTools.ttLib import TTFont
    except ModuleNotFoundError:
        import subprocess

        subprocess.check_call([sys.executable, "-m", "pip", "install", "fonttools", "brotli"])
        from fontTools.ttLib import TTFont

    dest_dir.mkdir(parents=True, exist_ok=True)
    written: list[Path] = []
    for face in range(1, 48):
        src = SRC_DIR / f"QCF4_Hafs_{face:02d}_W.woff2"
        if not src.is_file():
            raise SystemExit(f"Missing source font: {src}")
        dest = dest_dir / f"QCF4_Hafs_{face:02d}.ttf"
        font = TTFont(str(src))
        font.flavor = None
        if "prop" in font:
            del font["prop"]
        if "post" in font:
            font["post"].formatType = 3.0
        font.save(str(dest))
        font.close()
        written.append(dest)
        print(f"wrote {dest.name} ({dest.stat().st_size} bytes)")
    return written


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--dest", type=Path, default=DEFAULT_DEST)
    args = parser.parse_args()
    convert_all(args.dest)
