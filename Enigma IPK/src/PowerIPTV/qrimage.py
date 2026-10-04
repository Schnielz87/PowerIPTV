# -*- coding: utf-8 -*-
"""QR-Code als PNG-Datei (ohne Zusatzpakete): qrcodegen (MIT, Project Nayuki) + eigener PNG-Schreiber."""
import struct
import zlib


def write_qr_png(text, path, size=360):
    from .qrcodegen import QrCode  # nur Python 3
    qr = QrCode.encode_text(text, QrCode.Ecc.MEDIUM)
    n = qr.get_size()
    border = 3
    total = n + 2 * border
    scale = max(1, size // total)
    width = total * scale
    rows = []
    for y in range(total):
        row = bytearray()
        for x in range(total):
            dark = border <= x < n + border and border <= y < n + border and qr.get_module(x - border, y - border)
            row += (b"\x00" if dark else b"\xff") * scale
        line = b"\x00" + bytes(row)  # Filter 0, Graustufen 8 Bit
        rows.append(line * 1)
        for _ in range(scale - 1):
            rows.append(line)
    raw = b"".join(rows)

    def chunk(kind, data):
        c = struct.pack(">I", len(data)) + kind + data
        return c + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, width, 8, 0, 0, 0, 0)) + \
        chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)
    return width
