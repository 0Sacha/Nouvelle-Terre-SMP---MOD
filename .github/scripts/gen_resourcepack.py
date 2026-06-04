#!/usr/bin/env python3
"""Génère nouvelle-terre-hdv.zip — resource pack HDV dark-theme Nouvelle Terre."""
import struct, zlib, io, zipfile, hashlib, sys

def png_rgba(w, h, pixel_fn):
    """Encode une image RGBA w×h en PNG pur Python."""
    raw = bytearray()
    for y in range(h):
        raw += b'\x00'  # filtre None
        for x in range(w):
            r, g, b = pixel_fn(x, y)
            raw += bytes([r, g, b, 255])

    compressed = zlib.compress(bytes(raw))

    def chunk(name, data):
        c = name + data
        return struct.pack('>I', len(data)) + c + struct.pack('>I', zlib.crc32(c) & 0xFFFFFFFF)

    out = b'\x89PNG\r\n\x1a\n'
    out += chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
    out += chunk(b'IDAT', compressed)
    out += chunk(b'IEND', b'')
    return out

def hex_to_rgb(h):
    return (h >> 16) & 0xFF, (h >> 8) & 0xFF, h & 0xFF

def gen_chest_png():
    BG    = hex_to_rgb(0x1a1b1e)
    TITLE = hex_to_rgb(0x23272a)
    BORD  = hex_to_rgb(0x3a3c40)

    def pixel(x, y):
        if x >= 176: return BG
        if y <= 124:
            if y < 17:  return TITLE
            if y == 16: return BORD
            return BG
        if y == 125: return BG
        if y <= 221:
            if y < 143: return TITLE
            if y == 142: return BORD
            if y == 198 and 7 <= x < 169: return BORD
            return BG
        return BG

    return png_rgba(256, 256, pixel)

PACK_META = '{"pack":{"pack_format":15,"description":"Nouvelle Terre HDV"}}'

buf = io.BytesIO()
with zipfile.ZipFile(buf, 'w', zipfile.ZIP_DEFLATED) as z:
    z.writestr('pack.mcmeta', PACK_META)
    z.writestr('assets/minecraft/textures/gui/container/generic_54.png', gen_chest_png())

data = buf.getvalue()
sha1 = hashlib.sha1(data).hexdigest()

out = sys.argv[1] if len(sys.argv) > 1 else 'nouvelle-terre-hdv.zip'
with open(out, 'wb') as f:
    f.write(data)

print(f'Pack généré : {out}  ({len(data)} octets)  SHA-1: {sha1}')
print(f'HASH={sha1}')
