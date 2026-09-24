"""Generates the art and the LDtk project of the platformer example (writes into src/main/resources/assets/platformer).

The level layout is written as text below: '#' ground, '=' one-way platform, '*' coin, '@' player start, '"' grass
decoration, 'f' flower, 's' slime, 'L' lift (moves five tiles up and back). Ground cells get auto-layer tiles like LDtk's rules would make them: grass on top, dirt below.
"""
import importlib.util
import json
import os
import math
import random
import struct
import wave

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "platformer")
spec = importlib.util.spec_from_file_location(
    "art", os.path.join(HERE, "..", "..", "topdown", "assets-src", "generate.py"))
art = importlib.util.module_from_spec(spec)
spec.loader.exec_module(art)
T = 16

LEVELS = [
    [
        "                                        ",
        "                                        ",
        "                                        ",
        "                         * * *          ",
        "                        =======         ",
        "              * *                       ",
        "             =====                   *  ",
        "                                   #### ",
        "      *            L             ###### ",
        "   @     f  \"      \"   f s     ########",
        "#################  ######   ############",
        "#################  ######   ############",
        "#################  ######   ############",
        "########################################",
        "########################################",
    ],
    [
        "                                        ",
        "                                        ",
        "        * * *                           ",
        "       =======           *              ",
        "                        ###             ",
        "  *                   #####      * *    ",
        " ###          f s    #######   ======   ",
        "#####   \"   ######  #########           ",
        "######################################  ",
        "######################################  ",
        "######################################  ",
        "########################################",
        "########################################",
        "########################################",
        "########################################",
    ],
]

GRASS_TOP, DIRT, PLATFORM, TUFT, FLOWER, DIRT_DARK = 0, 1, 2, 3, 4, 5


def tileset():
    img = art.Image(8 * T, T)
    # 0: dirt with a grass top
    art.speckle(img, 0, 0, (150, 100, 60, 255), [((120, 80, 48, 255), 24)], 11)
    img.fill(0, 0, T, 5, (92, 166, 72, 255))
    for x in range(0, T, 3):
        img.set(x, 5, (70, 138, 58, 255))
    # 1: dirt
    art.speckle(img, T, 0, (150, 100, 60, 255), [((120, 80, 48, 255), 28), ((176, 128, 84, 255), 10)], 12)
    # 2: wooden one-way platform
    img.fill(2 * T, 0, T, 5, (170, 120, 70, 255))
    img.fill(2 * T, 5, T, 1, (110, 70, 40, 255))
    for x in (2 * T + 2, 2 * T + 13):
        img.fill(x, 6, 1, 4, (110, 70, 40, 255))
    # 3: grass tuft, 4: flower (decorations with transparent background)
    for i in range(5):
        img.fill(3 * T + 2 + i * 3, 10 - (i % 2) * 3, 1, 6 + (i % 2) * 3, (70, 150, 60, 255))
    img.fill(4 * T + 7, 8, 1, 8, (60, 130, 50, 255))
    img.fill(4 * T + 5, 5, 5, 4, (236, 88, 120, 255))
    img.fill(4 * T + 7, 6, 1, 1, (250, 230, 90, 255))
    # 5: darker dirt deep underground
    art.speckle(img, 5 * T, 0, (120, 80, 48, 255), [((96, 64, 38, 255), 30)], 13)
    img.save(os.path.join(OUT, "maps", "tiles.png"))


def backgrounds():
    sky = art.Image(320, 180)
    for y in range(180):
        t = y / 179
        color = (int(110 + 90 * t), int(170 + 50 * t), int(236 - 10 * t), 255)
        sky.fill(0, y, 320, 1, color)
    rng = random.Random(5)
    for _ in range(6):
        cx, cy = rng.randrange(320), rng.randrange(20, 80)
        for dx in range(-18, 19):
            for dy in range(-6, 7):
                if dx * dx / 324 + dy * dy / 36 <= 1:
                    sky.set((cx + dx) % 320, cy + dy, (250, 250, 255, 255))
    sky.save(os.path.join(OUT, "textures", "sky.png"))

    hills = art.Image(320, 90)
    for x in range(320):
        import math
        h = int(40 + 18 * math.sin(x / 320 * math.pi * 4) + 10 * math.sin(x / 320 * math.pi * 10))
        hills.fill(x, 90 - h, 1, h, (86, 150, 110, 255))
        hills.fill(x, 90 - h, 1, 2, (110, 176, 130, 255))
    hills.save(os.path.join(OUT, "textures", "hills.png"))


def sprites():
    player = art.Image(16, 16)
    player.fill(5, 1, 6, 5, (240, 200, 160, 255))
    player.fill(6, 3, 1, 1, (30, 30, 30, 255))
    player.fill(9, 3, 1, 1, (30, 30, 30, 255))
    player.fill(4, 0, 8, 2, (60, 90, 200, 255))
    player.fill(4, 6, 8, 6, (60, 90, 200, 255))
    player.fill(5, 12, 2, 4, (80, 60, 40, 255))
    player.fill(9, 12, 2, 4, (80, 60, 40, 255))
    player.save(os.path.join(OUT, "sprites", "player.png"))
    coin = art.Image(12, 12)
    for y in range(12):
        for x in range(12):
            d = (x - 5.5) ** 2 + (y - 5.5) ** 2
            if d <= 30:
                coin.set(x, y, (250, 200, 60, 255) if d > 14 else (255, 230, 110, 255))
    coin.save(os.path.join(OUT, "sprites", "coin.png"))
    slime = art.Image(16, 12)
    for y in range(12):
        for x in range(16):
            dx = (x - 7.5) / 8
            dy = (y - 11) / 11
            if dx * dx + dy * dy <= 1:
                slime.set(x, y, (60, 190, 90, 255) if y > 2 else (120, 230, 140, 255))
    slime.fill(5, 5, 2, 2, (20, 40, 20, 255))
    slime.fill(10, 5, 2, 2, (20, 40, 20, 255))
    slime.save(os.path.join(OUT, "sprites", "slime.png"))
    lift = art.Image(48, 8)
    lift.fill(0, 0, 48, 8, (120, 80, 50, 255))
    lift.fill(0, 0, 48, 2, (170, 120, 70, 255))
    for x in range(0, 48, 12):
        lift.fill(x, 2, 1, 6, (90, 60, 35, 255))
    lift.save(os.path.join(OUT, "sprites", "lift.png"))


def sounds():
    """A short rising blip for picking up coins, 16-bit mono WAV."""
    rate = 22050
    frames = bytearray()
    length = int(rate * 0.12)
    for i in range(length):
        t = i / rate
        freq = 880 + 1200 * t / 0.12
        envelope = min(1.0, i / 200) * (1 - i / length)
        frames += struct.pack("<h", int(12000 * envelope * math.sin(2 * math.pi * freq * t)))
    path = os.path.join(OUT, "sounds", "pickup.wav")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with wave.open(path, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(rate)
        out.writeframes(bytes(frames))


def ldtk():
    grid = T
    width, height = max(len(r) for level in LEVELS for r in level), len(LEVELS[0])
    uid = [100]

    def next_uid():
        uid[0] += 1
        return uid[0]

    levels = []
    for index, rows in enumerate(LEVELS):
        rows = [row.ljust(width)[:width] for row in rows]
        ground = [[c == "#" for c in row] for row in rows]
        platform = [[c == "=" for c in row] for row in rows]
        csv = []
        auto = []
        decor = []
        entities = []
        for y, row in enumerate(rows):
            for x, c in enumerate(row):
                value = 1 if c == "#" else 2 if c == "=" else 0
                csv.append(value)
                if c == "#":
                    above = y > 0 and ground[y - 1][x]
                    tile = DIRT if above else GRASS_TOP
                    if above and y > 1 and ground[y - 2][x] and y >= height - 3:
                        tile = DIRT_DARK
                    auto.append({"px": [x * grid, y * grid], "src": [tile * grid, 0], "f": 0, "t": tile, "d": [y * width + x], "a": 1})
                elif c == "=":
                    auto.append({"px": [x * grid, y * grid], "src": [PLATFORM * grid, 0], "f": 0, "t": PLATFORM, "d": [y * width + x], "a": 1})
                elif c in "\"f":
                    tile = TUFT if c == '"' else FLOWER
                    decor.append({"px": [x * grid, y * grid], "src": [tile * grid, 0], "f": (x % 2), "t": tile, "d": [y * width + x], "a": 1})
                elif c in "sL":
                    name = "Slime" if c == "s" else "Lift"
                    fields = []
                    if c == "L":
                        fields = [{"__identifier": "path", "__type": "Array<Point>", "defUid": 31, "realEditorValues": [],
                                   "__value": [{"cx": x, "cy": y}, {"cx": x, "cy": y - 5}]}]
                    entities.append({
                        "__identifier": name, "__grid": [x, y], "__pivot": [0.5, 1], "__tags": [], "__tile": None,
                        "__smartColor": "#50C878", "iid": "e-%d-%d-%d" % (index, x, y),
                        "width": 16 if c == "s" else 48, "height": 12 if c == "s" else 8,
                        "defUid": 22 if c == "s" else 23,
                        "px": [x * grid + grid // 2, y * grid + grid], "fieldInstances": fields,
                        "__worldX": index * width * grid + x * grid + grid // 2, "__worldY": y * grid + grid})
                elif c in "*@":
                    name = "Coin" if c == "*" else "Player"
                    w = 12 if c == "*" else 16
                    entities.append({
                        "__identifier": name, "__grid": [x, y], "__pivot": [0.5, 1], "__tags": [], "__tile": None,
                        "__smartColor": "#FFCC00", "iid": "e-%d-%d-%d" % (index, x, y),
                        "width": w, "height": w, "defUid": 20 if c == "*" else 21,
                        "px": [x * grid + grid // 2, y * grid + grid],
                        "fieldInstances": [{"__identifier": "value", "__type": "Int", "__value": 1, "defUid": 30,
                                            "realEditorValues": []}] if c == "*" else [],
                        "__worldX": index * width * grid + x * grid + grid // 2, "__worldY": y * grid + grid})
        level_uid = next_uid()
        common = {"__cWid": width, "__cHei": height, "__gridSize": grid, "__opacity": 1, "__pxTotalOffsetX": 0,
                  "__pxTotalOffsetY": 0, "levelId": level_uid, "pxOffsetX": 0, "pxOffsetY": 0, "visible": True,
                  "optionalRules": [], "overrideTilesetUid": None, "seed": 1}
        levels.append({
            "identifier": "Level_%d" % index, "iid": "level-%d" % index, "uid": level_uid,
            "worldX": index * width * grid, "worldY": 0, "worldDepth": 0, "pxWid": width * grid, "pxHei": height * grid,
            "__bgColor": "#6EA8EC", "bgColor": None, "useAutoIdentifier": True, "bgRelPath": None, "bgPos": None,
            "bgPivotX": 0.5, "bgPivotY": 0.5, "__smartColor": "#ADADB5", "__bgPos": None, "externalRelPath": None,
            "fieldInstances": [], "__neighbours": [],
            "layerInstances": [
                dict(common, __identifier="Entities", __type="Entities", iid="l-ent-%d" % index, layerDefUid=12,
                     __tilesetDefUid=None, __tilesetRelPath=None, intGridCsv=[], autoLayerTiles=[], gridTiles=[],
                     entityInstances=entities),
                dict(common, __identifier="Decor", __type="Tiles", iid="l-dec-%d" % index, layerDefUid=11,
                     __tilesetDefUid=1, __tilesetRelPath="tiles.png", intGridCsv=[], autoLayerTiles=[],
                     gridTiles=decor, entityInstances=[]),
                dict(common, __identifier="Ground", __type="IntGrid", iid="l-grd-%d" % index, layerDefUid=10,
                     __tilesetDefUid=1, __tilesetRelPath="tiles.png", intGridCsv=csv, autoLayerTiles=auto,
                     gridTiles=[], entityInstances=[]),
            ]})
    project = {
        "__header__": {"fileType": "LDtk Project JSON", "app": "LDtk", "doc": "https://ldtk.io/json",
                       "schema": "https://ldtk.io/files/JSON_SCHEMA.json", "appAuthor": "Sebastien 'deepnight' Benard",
                       "appVersion": "1.5.3", "url": "https://ldtk.io"},
        "iid": "project", "jsonVersion": "1.5.3", "appBuildId": 0, "nextUid": uid[0] + 1, "identifierStyle": "Capitalize",
        "worldLayout": "Free", "worldGridWidth": width * grid, "worldGridHeight": height * grid,
        "defaultLevelWidth": width * grid, "defaultLevelHeight": height * grid, "defaultPivotX": 0.5, "defaultPivotY": 1,
        "defaultGridSize": grid, "defaultEntityWidth": grid, "defaultEntityHeight": grid, "bgColor": "#40465B",
        "defaultLevelBgColor": "#6EA8EC", "minifyJson": False, "externalLevels": False, "exportTiled": False,
        "simplifiedExport": False, "imageExportMode": "None", "exportLevelBg": True, "pngFilePattern": None,
        "backupOnSave": False, "backupLimit": 10, "backupRelPath": None, "levelNamePattern": "Level_%idx",
        "tutorialDesc": None, "customCommands": [], "flags": [], "toc": [], "worlds": [], "dummyWorldIid": "world",
        "defs": {
            "layers": [
                {"__type": "IntGrid", "identifier": "Ground", "type": "IntGrid", "uid": 10, "gridSize": grid,
                 "tilesetDefUid": 1, "autoTilesetDefUid": 1, "displayOpacity": 1,
                 "intGridValues": [{"value": 1, "identifier": "ground", "color": "#6B4A2B", "tile": None, "groupUid": 0},
                                   {"value": 2, "identifier": "platform", "color": "#AA7846", "tile": None, "groupUid": 0}],
                 "intGridValuesGroups": [], "autoRuleGroups": []},
                {"__type": "Tiles", "identifier": "Decor", "type": "Tiles", "uid": 11, "gridSize": grid,
                 "tilesetDefUid": 1, "displayOpacity": 1, "intGridValues": [], "intGridValuesGroups": [], "autoRuleGroups": []},
                {"__type": "Entities", "identifier": "Entities", "type": "Entities", "uid": 12, "gridSize": grid,
                 "tilesetDefUid": None, "displayOpacity": 1, "intGridValues": [], "intGridValuesGroups": [], "autoRuleGroups": []},
            ],
            "entities": [
                {"identifier": "Coin", "uid": 20, "width": 12, "height": 12, "color": "#FFCC00", "pivotX": 0.5, "pivotY": 1,
                 "tags": [], "fieldDefs": [{"identifier": "value", "__type": "Int", "uid": 30, "type": "F_Int",
                                            "isArray": False, "canBeNull": False, "defaultOverride": None}]},
                {"identifier": "Player", "uid": 21, "width": 16, "height": 16, "color": "#3C5AC8", "pivotX": 0.5,
                 "pivotY": 1, "tags": [], "fieldDefs": []},
                {"identifier": "Slime", "uid": 22, "width": 16, "height": 12, "color": "#50C878", "pivotX": 0.5,
                 "pivotY": 1, "tags": [], "fieldDefs": []},
                {"identifier": "Lift", "uid": 23, "width": 48, "height": 8, "color": "#AA7846", "pivotX": 0.5,
                 "pivotY": 1, "tags": [], "fieldDefs": [{"identifier": "path", "__type": "Array<Point>", "uid": 31,
                                                         "type": "F_Point", "isArray": True, "canBeNull": False,
                                                         "defaultOverride": None}]},
            ],
            "tilesets": [{"identifier": "Tiles", "uid": 1, "relPath": "tiles.png", "pxWid": 8 * grid, "pxHei": grid,
                          "tileGridSize": grid, "spacing": 0, "padding": 0, "tags": [], "tagsSourceEnumUid": None,
                          "enumTags": [], "customData": [], "savedSelections": [], "cachedPixelData": None,
                          "__cWid": 8, "__cHei": 1, "embedAtlas": None}],
            "enums": [], "externalEnums": [], "levelFields": []},
        "levels": levels}
    path = os.path.join(OUT, "maps", "world.ldtk")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(project, f, indent=1)


if __name__ == "__main__":
    tileset()
    backgrounds()
    sprites()
    sounds()
    ldtk()
    print("platformer assets written to", os.path.normpath(OUT))
