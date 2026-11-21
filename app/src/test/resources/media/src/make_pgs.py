"""Writes a minimal PGS (.sup) subtitle: a 200x40 white box from 0.2 s to 4.5 s on a 320x180 frame.

Used to build image-pgs.mkv for EmbeddedSubtitlesTest:
  python3 make_pgs.py box.sup
  ffmpeg -i text-srt.mkv -i box.sup -map 0:v -map 0:a -map 1 -c copy -metadata:s:s:0 language=eng image-pgs.mkv
"""
import struct, sys

W, H, BOX_W, BOX_H, X, Y = 320, 180, 200, 40, 60, 120

def seg(pts_ms, kind, data):
    pts = pts_ms * 90
    return b"PG" + struct.pack(">IIBH", pts, pts, kind, len(data)) + data

def display_set(pts_ms, show, number):
    pcs = struct.pack(">HHBHBBBB", W, H, 0x10, number, 0x80 if show else 0x00, 0, 0, 1 if show else 0)
    if show:
        pcs += struct.pack(">HBBHH", 0, 0, 0, X, Y)
    wds = struct.pack(">BBHHHH", 1, 0, X, Y, BOX_W, BOX_H)
    out = seg(pts_ms, 0x16, pcs) + seg(pts_ms, 0x17, wds)
    if show:
        out += seg(pts_ms, 0x14, bytes([0, 0]) + bytes([1, 235, 128, 128, 255]))  # entry 1: white, opaque
        line = bytes([0x00, 0xC0 | (BOX_W >> 8), BOX_W & 0xFF, 0x01, 0x00, 0x00])  # BOX_W pixels of colour 1, end of line
        rle = line * BOX_H
        ods = struct.pack(">HBB", 0, 0, 0xC0) + (len(rle) + 4).to_bytes(3, "big") + struct.pack(">HH", BOX_W, BOX_H) + rle
        out += seg(pts_ms, 0x15, ods)
    return out + seg(pts_ms, 0x80, b"")

with open(sys.argv[1], "wb") as f:
    f.write(display_set(200, True, 0) + display_set(4500, False, 1))
