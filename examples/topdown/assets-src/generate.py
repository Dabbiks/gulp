"""Generates the pixel art of the topdown example (run from anywhere; writes into src/main/resources/assets/topdown).

Everything here is drawn by code, so the example has no third-party art: a 16-pixel tile sheet with grass, sand,
stone, animated water and 16 shore pieces for autotiling, and sprites for the player, trees and a rock.
"""
import os
import random
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "topdown")
T = 16


def write_png(path, width, height, pixels):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    raw = b"".join(b"\x00" + bytes(v for p in pixels[y * width:(y + 1) * width] for v in p) for y in range(height))

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)))
        f.write(chunk(b"IDAT", zlib.compress(raw, 9)))
        f.write(chunk(b"IEND", b""))


class Image:
    def __init__(self, width, height):
        self.width = width
        self.height = height
        self.pixels = [(0, 0, 0, 0)] * (width * height)

    def set(self, x, y, color):
        if 0 <= x < self.width and 0 <= y < self.height:
            self.pixels[y * self.width + x] = color

    def fill(self, x0, y0, w, h, color):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                self.set(x, y, color)

    def save(self, path):
        write_png(path, self.width, self.height, self.pixels)


def speckle(img, ox, oy, base, dots, seed):
    rng = random.Random(seed)
    img.fill(ox, oy, T, T, base)
    for color, count in dots:
        for _ in range(count):
            img.set(ox + rng.randrange(T), oy + rng.randrange(T), color)


GRASS = (92, 166, 72, 255)
GRASS_DARK = (70, 138, 58, 255)
SAND = (222, 202, 140, 255)
SAND_DARK = (196, 174, 112, 255)
STONE = (128, 128, 136, 255)
WATER = (58, 118, 196, 255)
WATER_LIGHT = (104, 160, 226, 255)


def water(img, ox, oy, frame):
    img.fill(ox, oy, T, T, WATER)
    for i in range(4):
        y = (i * 4 + frame * 2) % T
        x = (i * 5 + frame * 3) % (T - 5)
        for dx in range(4):
            img.set(ox + x + dx, oy + y, WATER_LIGHT)


def tiles():
    cols = 8
    img = Image(cols * T, 4 * T)
    speckle(img, 0, 0, GRASS, [(GRASS_DARK, 30)], 1)
    speckle(img, T, 0, GRASS, [(GRASS_DARK, 20), ((236, 88, 88, 255), 4), ((250, 230, 90, 255), 4)], 2)
    speckle(img, 2 * T, 0, SAND, [(SAND_DARK, 24)], 3)
    speckle(img, 3 * T, 0, STONE, [((104, 104, 112, 255), 30)], 4)
    water(img, 4 * T, 0, 0)
    water(img, 5 * T, 0, 1)
    # Shore pieces 8..23: bit N=1, E=2, S=4, W=8 set when that neighbour is water; sand where it is not.
    for mask in range(16):
        index = 8 + mask
        ox = (index % cols) * T
        oy = (index // cols) * T
        water(img, ox, oy, 0)
        edge = 4
        if not mask & 1:
            img.fill(ox, oy, T, edge, SAND)
        if not mask & 2:
            img.fill(ox + T - edge, oy, edge, T, SAND)
        if not mask & 4:
            img.fill(ox, oy + T - edge, T, edge, SAND)
        if not mask & 8:
            img.fill(ox, oy, edge, T, SAND)
    img.save(os.path.join(OUT, "textures", "tiles.png"))


def sprites():
    tree = Image(16, 24)
    tree.fill(7, 16, 3, 8, (110, 76, 44, 255))
    for y in range(18):
        half = min(7, 2 + y // 2)
        for x in range(8 - half, 8 + half):
            tree.set(x, y, (40, 112, 58, 255) if (x + y) % 5 else (58, 140, 70, 255))
    tree.save(os.path.join(OUT, "sprites", "tree.png"))

    player = Image(16, 16)
    player.fill(5, 1, 6, 5, (240, 200, 160, 255))
    player.fill(6, 2, 1, 1, (30, 30, 30, 255))
    player.fill(9, 2, 1, 1, (30, 30, 30, 255))
    player.fill(4, 6, 8, 6, (200, 60, 70, 255))
    player.fill(5, 12, 2, 4, (60, 60, 100, 255))
    player.fill(9, 12, 2, 4, (60, 60, 100, 255))
    player.save(os.path.join(OUT, "sprites", "player.png"))

    rock = Image(16, 16)
    for y in range(6, 15):
        for x in range(2, 14):
            if (x - 8) ** 2 / 36 + (y - 10) ** 2 / 20 <= 1:
                rock.set(x, y, (140, 140, 150, 255) if y > 9 else (170, 170, 180, 255))
    rock.save(os.path.join(OUT, "sprites", "rock.png"))


if __name__ == "__main__":
    tiles()
    sprites()
    print("topdown assets written to", os.path.normpath(OUT))
