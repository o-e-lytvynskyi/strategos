#!/usr/bin/env python3
"""Compare the PNG frames of two scenario runs pixel by pixel, as the S3 and S10 spikes do.

    python3 tools/png_diff.py <run-a-dir> <run-b-dir>

For every PNG present in both directories, prints a CSV row: the frame, its pixel count, how many
pixels differ in any channel, how many differ by more than 8 levels in some channel, and the box
(x0, y0, x1, y1) holding those. Reads 8-bit RGB and RGBA PNGs without interlacing, which is what
Minecraft's screenshots are; needs nothing beyond the standard library.
"""

import csv
import os
import struct
import sys
import zlib

SIGNATURE = b"\x89PNG\r\n\x1a\n"
LOUD = 8


def read_png(path):
    """(width, height, channels, rows): rows are bytes, one per scanline, filters undone."""
    with open(path, "rb") as f:
        data = f.read()
    if not data.startswith(SIGNATURE):
        raise ValueError(f"{path}: not a PNG")
    pos = len(SIGNATURE)
    idat = []
    width = height = channels = None
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        kind = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, depth, color, _, _, interlace = struct.unpack(">IIBBBBB", body)
            if depth != 8 or color not in (2, 6) or interlace != 0:
                raise ValueError(f"{path}: only 8-bit RGB or RGBA without interlacing")
            channels = 3 if color == 2 else 4
        elif kind == b"IDAT":
            idat.append(body)
        elif kind == b"IEND":
            break
    raw = zlib.decompress(b"".join(idat))
    stride = width * channels
    rows = []
    previous = bytearray(stride)
    at = 0
    for _ in range(height):
        kind = raw[at]
        line = bytearray(raw[at + 1:at + 1 + stride])
        at += 1 + stride
        for i in range(stride):
            left = line[i - channels] if i >= channels else 0
            up = previous[i]
            corner = previous[i - channels] if i >= channels else 0
            if kind == 1:
                line[i] = (line[i] + left) & 0xFF
            elif kind == 2:
                line[i] = (line[i] + up) & 0xFF
            elif kind == 3:
                line[i] = (line[i] + (left + up) // 2) & 0xFF
            elif kind == 4:
                p = left + up - corner
                pa, pb, pc = abs(p - left), abs(p - up), abs(p - corner)
                pred = left if pa <= pb and pa <= pc else up if pb <= pc else corner
                line[i] = (line[i] + pred) & 0xFF
        rows.append(bytes(line))
        previous = line
    return width, height, channels, rows


def compare(a, b):
    wa, ha, ca, ra = read_png(a)
    wb, hb, cb, rb = read_png(b)
    if (wa, ha) != (wb, hb):
        raise ValueError(f"{a} is {wa}x{ha}, {b} is {wb}x{hb}")
    differ = loud = 0
    box = None
    for y in range(ha):
        la, lb = ra[y], rb[y]
        if la == lb and ca == cb:
            continue
        for x in range(wa):
            pa = la[x * ca:x * ca + 3]
            pb = lb[x * cb:x * cb + 3]
            if pa == pb:
                continue
            differ += 1
            if max(abs(pa[k] - pb[k]) for k in range(3)) > LOUD:
                loud += 1
                if box is None:
                    box = [x, y, x, y]
                else:
                    box = [min(box[0], x), min(box[1], y), max(box[2], x), max(box[3], y)]
    return wa * ha, differ, loud, box


def main(argv):
    if len(argv) != 3:
        print(__doc__.strip(), file=sys.stderr)
        return 2
    left, right = argv[1], argv[2]
    names = sorted(n for n in os.listdir(left) if n.endswith(".png") and os.path.exists(os.path.join(right, n)))
    out = csv.writer(sys.stdout)
    out.writerow(["frame", "pixels", "differing", "loud", "box"])
    for name in names:
        pixels, differ, loud, box = compare(os.path.join(left, name), os.path.join(right, name))
        out.writerow([name, pixels, differ, loud, "" if box is None else " ".join(map(str, box))])
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
