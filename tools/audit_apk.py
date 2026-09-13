#!/usr/bin/env python3
"""Quick APK/AAB size breakdown by top-level folder and extension."""
from __future__ import annotations

import collections
import os
import sys
import zipfile


def audit(path: str) -> None:
    z = zipfile.ZipFile(path)
    by_ext: dict[str, int] = collections.defaultdict(int)
    by_prefix: dict[str, int] = collections.defaultdict(int)
    entries: list[tuple[int, str]] = []
    total = 0
    for info in z.infolist():
        total += info.file_size
        ext = os.path.splitext(info.filename)[1].lower()
        by_ext[ext or "(none)"] += info.file_size
        top = info.filename.split("/")[0] if "/" in info.filename else info.filename
        by_prefix[top] += info.file_size
        entries.append((info.file_size, info.filename))

    print(f"Archive: {path}")
    print(f"Compressed MB: {os.path.getsize(path) / 1024 / 1024:.2f}")
    print(f"Uncompressed MB: {total / 1024 / 1024:.2f}")
    print("\nBy top-level:")
    for k, v in sorted(by_prefix.items(), key=lambda x: -x[1])[:25]:
        print(f"  {k:24} {v / 1024 / 1024:8.2f} MB")
    print("\nBy extension:")
    for k, v in sorted(by_ext.items(), key=lambda x: -x[1])[:25]:
        print(f"  {k:12} {v / 1024 / 1024:8.2f} MB")
    print("\nTop 50 largest files:")
    for sz, name in sorted(entries, reverse=True)[:50]:
        print(f"  {sz / 1024 / 1024:8.2f} MB  {name}")


if __name__ == "__main__":
    default = r"app/build/outputs/apk/release/app-release.apk"
    audit(sys.argv[1] if len(sys.argv) > 1 else default)
