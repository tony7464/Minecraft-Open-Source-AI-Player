#!/usr/bin/env python3
"""Generates the GameTest structure templates (.nbt) used by the mod's GameTests.

Run from the repo root:  python3 tools/make_test_structures.py
Writes to src/main/resources/data/aiplayers/structure/.
No third-party packages needed: includes a tiny NBT writer.
"""
import gzip
import os
import struct

DATA_VERSION = 4790  # Minecraft 26.1.2 world_version
OUT = os.path.join("src", "main", "resources", "data", "aiplayers", "structure")

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


def _name(n):
    b = n.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def _payload(tag, value):
    if tag == TAG_INT:
        return struct.pack(">i", value)
    if tag == TAG_STRING:
        return _name(value)
    if tag == TAG_LIST:
        elem_tag, items = value
        out = struct.pack(">bi", elem_tag if items else TAG_END, len(items))
        return out + b"".join(_payload(elem_tag, i) for i in items)
    if tag == TAG_COMPOUND:
        out = b""
        for k, (t, v) in value.items():
            out += struct.pack(">b", t) + _name(k) + _payload(t, v)
        return out + struct.pack(">b", TAG_END)
    raise ValueError(tag)


def write_structure(name, size, blocks):
    """blocks: dict[(x,y,z)] -> block id. Every position inside `size` is written (missing = air)."""
    palette = ["minecraft:air"]
    entries = []
    sx, sy, sz = size
    for x in range(sx):
        for y in range(sy):
            for z in range(sz):
                block = blocks.get((x, y, z), "minecraft:air")
                if block not in palette:
                    palette.append(block)
                entries.append({"pos": (TAG_LIST, (TAG_INT, [x, y, z])), "state": (TAG_INT, palette.index(block))})
    root = {
        "DataVersion": (TAG_INT, DATA_VERSION),
        "size": (TAG_LIST, (TAG_INT, list(size))),
        "palette": (TAG_LIST, (TAG_COMPOUND, [{"Name": (TAG_STRING, b)} for b in palette])),
        "blocks": (TAG_LIST, (TAG_COMPOUND, entries)),
        "entities": (TAG_LIST, (TAG_COMPOUND, [])),
    }
    data = struct.pack(">b", TAG_COMPOUND) + _name("") + _payload(TAG_COMPOUND, root)
    os.makedirs(OUT, exist_ok=True)
    with gzip.open(os.path.join(OUT, name + ".nbt"), "wb") as f:
        f.write(data)
    print("wrote", name, size)


def platform(n, height):
    return {(x, 0, z): "minecraft:stone" for x in range(n) for z in range(n)}


if __name__ == "__main__":
    # Flat 24x6x24 stone platform for movement tests.
    write_structure("platform", (24, 6, 24), platform(24, 6))
    # Small forest: platform with 3 single-log "trees" and a stone patch (for gathering tests).
    forest = platform(16, 8)
    for (x, z) in [(10, 4), (12, 8), (9, 11)]:
        for y in range(1, 4):
            forest[(x, y, z)] = "minecraft:oak_log"
    for x in range(3, 6):
        for z in range(10, 13):
            forest[(x, 1, z)] = "minecraft:cobblestone"
    write_structure("forest", (16, 8, 16), forest)
