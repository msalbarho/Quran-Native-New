#!/usr/bin/env python3
"""Convert chapter-header.svg to Android vector drawable."""
import re
import sys
from pathlib import Path

svg_path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1] / "public/assets/chapter-header.svg"
out_path = Path(sys.argv[2]) if len(sys.argv) > 2 else Path(__file__).resolve().parents[1] / "app/src/main/res/drawable/chapter_header.xml"

svg = svg_path.read_text(encoding="utf-8")
paths = re.findall(r'<path[^>]*\bd="([^"]+)"', svg)
if not paths:
    raise SystemExit(f"No paths found in {svg_path}")

body = "\n".join(
    f'    <path\n        android:fillColor="#FF000000"\n        android:pathData="{p}"/>'
    for p in paths
)
xml = f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="2880dp"
    android:height="292dp"
    android:viewportWidth="2880"
    android:viewportHeight="292">
{body}
</vector>
"""
out_path.parent.mkdir(parents=True, exist_ok=True)
out_path.write_text(xml, encoding="utf-8")
print(f"Wrote {out_path} ({len(paths)} paths, {out_path.stat().st_size} bytes)")
