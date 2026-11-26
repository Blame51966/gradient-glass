"""Generate solid and transition sprites, with alpha stored in the texture."""

from itertools import combinations
from pathlib import Path
import struct
import zlib
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo


# Vanilla DyeColor texture values in ID order, followed by clear, tinted and white tint masks.
DYES = (0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
        0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21)
TONES = DYES + (0xE8F3F4, 0x473950)
ALPHAS = ((94, 190),) * len(DYES) + ((16, 172), (155, 212))
SUFFIXES = ("", "_border")
OUTPUT = Path(__file__).with_name("glass-textures.zip")
RESOURCE_PATH = "assets/gradientglass/textures/block/glass"


def rgb(color):
    return (color >> 16 & 255, color >> 8 & 255, color & 255)


def png_chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


def write_sprite(archive, name, pixels, height):
    header = struct.pack(">IIBBBBB", len(pixels), height, 8, 6, 0, 0, 0)
    row = b"\0" + bytes(channel for pixel in pixels for channel in pixel)
    image = (b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", header)
             + png_chunk(b"IDAT", zlib.compress(row * height)) + png_chunk(b"IEND", b""))
    entry = ZipInfo(f"{RESOURCE_PATH}/{name}.png")
    archive.writestr(entry, image, compress_type=ZIP_DEFLATED)


def ramp(first, second):
    for x in range(128):
        t = max(0, min(1, (x - 16) / 95))
        yield tuple(round(a + (b - a) * t) for a, b in zip(first, second))


def generate(archive):
    for index, color in enumerate(TONES):
        for suffix, alpha in zip(SUFFIXES, ALPHAS[index]):
            write_sprite(archive, f"color_{index}{suffix}", [(*rgb(color), alpha)] * 16, 16)
    for first, second in combinations(range(len(TONES)), 2):
        for suffix, first_alpha, second_alpha in zip(SUFFIXES, ALPHAS[first], ALPHAS[second]):
            pixels = list(ramp((*rgb(TONES[first]), first_alpha), (*rgb(TONES[second]), second_alpha)))
            write_sprite(archive, f"blend_{first}_{second}{suffix}", pixels, 32)
    write_sprite(archive, "neutral", [(255, 255, 255, min(255, max(0, x - 128))) for x in range(512)], 32)


if __name__ == "__main__":
    with ZipFile(OUTPUT, "w") as archive:
        generate(archive)
