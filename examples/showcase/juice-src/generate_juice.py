"""Generates the art of the showcase juice screen (writes into src/main/resources/assets/showcase).

Sprites go to sprites/ (packed into the showcase atlas), the hero is exported like Aseprite does it (a sheet and a JSON
hash with frame tags) into animations/, and particle effects are written as JSON into particles/.
"""
import importlib.util
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "showcase")
spec = importlib.util.spec_from_file_location(
    "art", os.path.join(HERE, "..", "..", "topdown", "assets-src", "generate.py"))
art = importlib.util.module_from_spec(spec)
spec.loader.exec_module(art)
Image = art.Image

STONE = (92, 84, 110, 255)
STONE_LIGHT = (126, 117, 146, 255)
STONE_DARK = (58, 52, 74, 255)
WOOD = (150, 96, 52, 255)
WOOD_DARK = (98, 60, 32, 255)
WOOD_LIGHT = (190, 132, 76, 255)


def block():
    img = Image(16, 16)
    img.fill(0, 0, 16, 16, STONE)
    for x in range(16):
        img.set(x, 0, STONE_LIGHT)
        img.set(x, 15, STONE_DARK)
    for y in range(16):
        img.set(0, y, STONE_LIGHT)
        img.set(15, y, STONE_DARK)
    for x in range(1, 15):
        img.set(x, 7, STONE_DARK)
    for y in range(1, 7):
        img.set(8, y, STONE_DARK)
    for y in range(8, 15):
        img.set(4, y, STONE_DARK)
        img.set(12, y, STONE_DARK)
    img.save(os.path.join(OUT, "sprites", "block.png"))


def backwall():
    base = (74, 68, 92, 255)
    mortar = (52, 47, 66, 255)
    img = Image(16, 16)
    img.fill(0, 0, 16, 16, base)
    for x in range(16):
        img.set(x, 3, mortar)
        img.set(x, 11, mortar)
    for y in range(0, 3):
        img.set(4, y, mortar)
        img.set(12, y, mortar)
    for y in range(4, 11):
        img.set(0, y, mortar)
        img.set(8, y, mortar)
    for y in range(12, 16):
        img.set(4, y, mortar)
        img.set(12, y, mortar)
    img.save(os.path.join(OUT, "sprites", "backwall.png"))


def crate():
    img = Image(16, 16)
    img.fill(0, 0, 16, 16, WOOD)
    for i in range(16):
        img.set(i, 0, WOOD_LIGHT)
        img.set(i, 15, WOOD_DARK)
        img.set(0, i, WOOD_LIGHT)
        img.set(15, i, WOOD_DARK)
        img.set(i, i, WOOD_DARK)
        img.set(15 - i, i, WOOD_DARK)
    img.save(os.path.join(OUT, "sprites", "crate.png"))


def torch():
    img = Image(8, 16)
    img.fill(3, 6, 2, 10, WOOD_DARK)
    img.fill(2, 4, 4, 3, (70, 70, 80, 255))
    img.fill(3, 1, 2, 3, (255, 214, 90, 255))
    img.set(3, 0, (255, 160, 40, 255))
    img.save(os.path.join(OUT, "sprites", "torch.png"))


def spark():
    img = Image(8, 8)
    for i in range(8):
        img.set(i, 3, (255, 255, 255, 200))
        img.set(i, 4, (255, 255, 255, 200))
        img.set(3, i, (255, 255, 255, 200))
        img.set(4, i, (255, 255, 255, 200))
    img.fill(2, 2, 4, 4, (255, 255, 255, 255))
    img.save(os.path.join(OUT, "sprites", "spark.png"))


def hero():
    """Ten 16x16 frames: idle 0-3 (breathing), run 4-9 (legs and bounce)."""
    body = (232, 90, 80, 255)
    dark = (150, 50, 60, 255)
    skin = (255, 214, 170, 255)
    eye = (30, 30, 40, 255)
    frames = 10
    img = Image(16 * frames, 16)
    for f in range(frames):
        ox = f * 16
        if f < 4:
            lift = [0, 0, 1, 1][f]
            legs = [(5, 6), (9, 10)]
        else:
            step = f - 4
            lift = [0, 1, 0, 0, 1, 0][step]
            legs = [[(4, 5), (10, 11)], [(5, 6), (9, 10)], [(6, 7), (8, 9)],
                    [(10, 11), (4, 5)], [(9, 10), (5, 6)], [(8, 9), (6, 7)]][step]
        top = 3 + lift
        img.fill(ox + 4, top, 8, 5, skin)
        img.set(ox + 6, top + 2, eye)
        img.set(ox + 10, top + 2, eye)
        img.fill(ox + 3, top + 5, 10, 5, body)
        img.fill(ox + 3, top + 9, 10, 1, dark)
        for a, b in legs:
            img.fill(ox + a, 13, b - a + 1, 3, dark)
    img.save(os.path.join(OUT, "animations", "hero.png"))
    hash_frames = {}
    for f in range(frames):
        hash_frames["hero %d.aseprite" % f] = {
            "frame": {"x": f * 16, "y": 0, "w": 16, "h": 16},
            "rotated": False,
            "trimmed": False,
            "spriteSourceSize": {"x": 0, "y": 0, "w": 16, "h": 16},
            "sourceSize": {"w": 16, "h": 16},
            "duration": 180 if f < 4 else 90,
        }
    meta = {
        "app": "https://www.aseprite.org/",
        "image": "hero.png",
        "format": "RGBA8888",
        "size": {"w": 16 * frames, "h": 16},
        "scale": "1",
        "frameTags": [
            {"name": "idle", "from": 0, "to": 3, "direction": "pingpong"},
            {"name": "run", "from": 4, "to": 9, "direction": "forward"},
        ],
    }
    with open(os.path.join(OUT, "animations", "hero.json"), "w") as f:
        json.dump({"frames": hash_frames, "meta": meta}, f, indent=1)


def particles():
    effects = {
        "fire": {"emitters": [
            {"rate": 40, "loop": True, "duration": 1, "shape": {"type": "circle", "radius": 0.1},
             "direction": -90, "spread": 25, "speed": [0.8, 1.6], "gravity": [0, -0.6], "drag": 0.8,
             "lifetime": [0.35, 0.7], "size": [[0, 0.3], [1, 0.04]],
             "color": [[0, "#ffe070"], [0.4, "#ff7a10"], [1, "#5a1a0a"]], "alpha": [[0, 0.55], [1, 0]],
             "blend": "add"},
            {"rate": 6, "loop": True, "duration": 1, "direction": -90, "spread": 40, "speed": [0.6, 1.2],
             "lifetime": [0.8, 1.4], "size": 0.08, "color": "#ffd070", "alpha": [[0, 1], [1, 0]],
             "blend": "add", "region": "sprites/spark", "spin": [-180, 180]},
        ]},
        "sparks": {"emitters": [
            {"bursts": [{"count": 40, "at": 0}], "duration": 0.1, "shape": {"type": "point"},
             "direction": -90, "spread": 160, "speed": [3, 8], "gravity": [0, 18], "drag": 0.5,
             "lifetime": [0.3, 0.8], "size": [[0, 0.3], [1, 0]], "color": ["#ffffff", "#ffd23f", "#ff5a1f"],
             "blend": "add", "region": "sprites/spark", "rotation": [0, 360], "spin": [-360, 360],
             "collision": "bounce", "bounce": 0.4, "onDeath": "particles/ember"},
            {"bursts": [{"count": 1, "at": 0}], "duration": 0.1, "speed": [0, 0], "lifetime": [0.25, 0.25],
             "size": [[0, 0.5], [1, 3]], "color": "#fff6d0", "alpha": [[0, 0.8], [1, 0]], "blend": "add"},
        ]},
        "ember": {"emitters": [
            {"bursts": [{"count": 2, "at": 0}], "duration": 0.1, "direction": -90, "spread": 90,
             "speed": [0.3, 0.8], "lifetime": [0.3, 0.6], "size": 0.1, "color": "#ff9d3a",
             "alpha": [[0, 1], [1, 0]], "blend": "add"},
        ]},
        "dust": {"emitters": [
            {"rate": 12, "loop": True, "duration": 1, "shape": {"type": "rect", "width": 20, "height": 8},
             "direction": -90, "spread": 360, "speed": [0.05, 0.25], "lifetime": [2, 4],
             "size": [[0, 0], [0.5, 0.12], [1, 0]], "color": "#c8c0ff", "alpha": 0.5, "layer": "foreground"},
        ]},
    }
    os.makedirs(os.path.join(OUT, "particles"), exist_ok=True)
    for name, effect in effects.items():
        with open(os.path.join(OUT, "particles", name + ".json"), "w") as f:
            json.dump(effect, f, indent=1)


if __name__ == "__main__":
    os.makedirs(os.path.join(OUT, "animations"), exist_ok=True)
    block()
    backwall()
    crate()
    torch()
    spark()
    hero()
    particles()
