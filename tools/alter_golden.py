#!/usr/bin/env python3
"""Invert a square in the middle of every PNG under a directory, in place: CI's proof that a changed
golden fails scenarioDiff (QA-18.2).

    python3 tools/alter_golden.py <goldens-copy-dir>

Reads 8-bit RGB and RGBA PNGs without interlacing (tools/png_diff.py's reader) and writes them back
as RGB; needs nothing beyond the standard library.
"""

import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png_diff import read_png  # noqa: E402

SIDE = 64


def write_png(path, width, height, rows):
    raw = b"".join(b"\x00" + bytes(row) for row in rows)

    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)))
        f.write(chunk(b"IDAT", zlib.compress(raw, 9)))
        f.write(chunk(b"IEND", b""))


def alter(path):
    width, height, channels, rows = read_png(path)
    out = []
    for y, row in enumerate(rows):
        rgb = bytearray()
        for x in range(width):
            pixel = row[x * channels:x * channels + 3]
            inside = abs(x - width // 2) < SIDE // 2 and abs(y - height // 2) < SIDE // 2
            rgb += bytes(255 - v for v in pixel) if inside else pixel
        out.append(rgb)
    write_png(path, width, height, out)


def main():
    altered = 0
    for root, _, files in os.walk(sys.argv[1]):
        for name in files:
            if name.endswith(".png"):
                alter(os.path.join(root, name))
                altered += 1
    print(f"altered {altered} goldens")
    return 0 if altered else 1


if __name__ == "__main__":
    sys.exit(main())
