#!/usr/bin/env python3
"""Generate the original 16x16 music box pixel-art textures.

Uses only the Python standard library so the assets are reproducible without
image libraries. Pixels are written as non-interlaced RGBA PNGs.
"""

from pathlib import Path
import struct
import zlib


SIZE = 16
ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT / "src/main/resources/assets/musicbox/textures"


def canvas(color=(0, 0, 0, 0)):
    return [[color for _ in range(SIZE)] for _ in range(SIZE)]


def rect(image, x0, y0, x1, y1, color):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            image[y][x] = color


def pixel(image, x, y, color):
    image[y][x] = color


def png_bytes(image):
    raw = b"".join(b"\x00" + b"".join(bytes(pixel) for pixel in row) for row in image)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))

    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )


INK = (20, 22, 30, 255)
WOOD_DARK = (48, 35, 38, 255)
WOOD = (84, 54, 47, 255)
WOOD_LIGHT = (130, 82, 61, 255)
GOLD = (198, 142, 73, 255)
RECORD = (26, 27, 34, 255)
RECORD_HI = (55, 55, 68, 255)
BLUE_DARK = (25, 91, 121, 255)
BLUE = (41, 173, 201, 255)
BLUE_HI = (111, 224, 226, 255)


def item_texture():
    im = canvas()
    # A compact dark-wood player: strong silhouette, cyan record/wave focal point.
    rect(im, 2, 5, 13, 13, INK)
    rect(im, 1, 7, 14, 12, INK)
    rect(im, 2, 6, 13, 11, WOOD)
    rect(im, 3, 6, 12, 7, WOOD_LIGHT)
    rect(im, 3, 11, 12, 12, WOOD_DARK)
    rect(im, 3, 13, 5, 14, INK)
    rect(im, 10, 13, 12, 14, INK)
    # Vinyl disc and bright blue spindle/label.
    rect(im, 5, 3, 10, 10, RECORD)
    rect(im, 3, 5, 12, 8, RECORD)
    pixel(im, 4, 4, RECORD)
    pixel(im, 11, 4, RECORD)
    pixel(im, 4, 9, RECORD)
    pixel(im, 11, 9, RECORD)
    for x, y in ((5, 4), (10, 5), (5, 9), (9, 9)):
        pixel(im, x, y, RECORD_HI)
    rect(im, 7, 6, 8, 7, BLUE_HI)
    # Audio-wave ticks remain readable at inventory scale.
    rect(im, 3, 10, 4, 10, BLUE_DARK)
    rect(im, 5, 9, 5, 11, BLUE)
    rect(im, 7, 10, 8, 11, BLUE_HI)
    rect(im, 10, 9, 10, 11, BLUE)
    rect(im, 11, 10, 12, 10, BLUE_DARK)
    pixel(im, 12, 6, GOLD)
    return im


def block_side():
    im = canvas(WOOD)
    rect(im, 0, 0, 15, 1, INK)
    rect(im, 0, 14, 15, 15, WOOD_DARK)
    rect(im, 0, 2, 1, 13, WOOD_DARK)
    rect(im, 14, 2, 15, 13, INK)
    for y in (4, 11):
        rect(im, 2, y, 13, y, WOOD_LIGHT if y == 4 else WOOD_DARK)
    rect(im, 3, 6, 12, 10, INK)
    for x, height in ((4, 1), (6, 3), (8, 2), (10, 3), (12, 1)):
        rect(im, x, 8 - height // 2, x, 8 + height // 2, BLUE_HI if x == 8 else BLUE)
    pixel(im, 2, 3, GOLD)
    pixel(im, 13, 3, GOLD)
    return im


def block_top():
    im = canvas(WOOD_DARK)
    rect(im, 1, 1, 14, 14, WOOD)
    rect(im, 2, 2, 13, 3, WOOD_LIGHT)
    rect(im, 3, 3, 12, 12, RECORD)
    rect(im, 2, 5, 13, 10, RECORD)
    for x, y in ((4, 4), (11, 5), (4, 11), (10, 11)):
        pixel(im, x, y, RECORD_HI)
    rect(im, 7, 7, 8, 8, BLUE_HI)
    rect(im, 12, 3, 13, 4, GOLD)
    rect(im, 11, 4, 12, 4, GOLD)
    return im


def block_bottom():
    im = canvas(WOOD_DARK)
    rect(im, 1, 1, 14, 14, WOOD)
    for x0, y0 in ((2, 2), (11, 2), (2, 11), (11, 11)):
        rect(im, x0, y0, x0 + 2, y0 + 2, INK)
    rect(im, 5, 6, 10, 9, WOOD_LIGHT)
    return im


def main():
    outputs = {
        TEXTURES / "item/music_box.png": item_texture(),
        TEXTURES / "block/music_box_side.png": block_side(),
        TEXTURES / "block/music_box_top.png": block_top(),
        TEXTURES / "block/music_box_bottom.png": block_bottom(),
    }
    for path, image in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(png_bytes(image))
        print(path.relative_to(ROOT))


if __name__ == "__main__":
    main()
