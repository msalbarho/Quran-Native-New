"""Compact packaged TTFs without dropping QCF ligature glyphs.

Keeps every outline/cmap entry. Removes Android-unused AAT/metadata tables
and stores the `post` table in format 3 (no glyph names). WOFF2 is never
written or packaged.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FONTS = ROOT / "public" / "fonts"
DROP_TABLES = {"prop", "DSIG", "FFTM", "meta", "hdmx", "VDMX", "LTSH", "PCLT"}


def optimize_ttf(path: Path) -> tuple[int, int]:
    from fontTools.ttLib import TTFont

    before = path.stat().st_size
    font = TTFont(str(path), recalcBBoxes=False, recalcTimestamp=False)
    font.flavor = None
    for tag in list(font.keys()):
        if tag in DROP_TABLES:
            del font[tag]
    if "post" in font:
        font["post"].formatType = 3.0
    tmp = path.with_name(path.name + ".tmp")
    font.save(str(tmp))
    font.close()
    tmp.replace(path)
    after = path.stat().st_size
    return before, after


def iter_targets() -> list[Path]:
    files: list[Path] = []
    qcf = FONTS / "qcf4"
    if qcf.is_dir():
        files.extend(sorted(qcf.glob("QCF4_Hafs_*.ttf")))
    for name in ("qbsml.ttf", "hafs.ttf", "ksa.ttf"):
        candidate = FONTS / name
        if candidate.is_file():
            files.append(candidate)
    return files


STAMP = FONTS / "qcf4" / ".optimized-v1"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--path", type=Path, default=None)
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()
    try:
        from fontTools.ttLib import TTFont  # noqa: F401
    except ModuleNotFoundError:
        import subprocess

        subprocess.check_call([sys.executable, "-m", "pip", "install", "fonttools", "brotli"])

    targets = [args.path] if args.path else iter_targets()
    if not args.force and args.path is None and STAMP.is_file():
        stamp_mtime = STAMP.stat().st_mtime
        if all(path.stat().st_mtime <= stamp_mtime for path in targets if path is not None and path.is_file()):
            print("fonts already optimized")
            return 0
    total_before = total_after = 0
    for path in targets:
        if path is None or not path.is_file():
            continue
        before, after = optimize_ttf(path)
        total_before += before
        total_after += after
        print(f"{path.name}: {before} -> {after}")
    if args.path is None:
        STAMP.parent.mkdir(parents=True, exist_ok=True)
        STAMP.write_text("v1\n", encoding="utf-8")
    print(f"total: {total_before} -> {total_after}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
