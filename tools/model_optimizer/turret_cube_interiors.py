"""Remove cube faces strictly inside an opaque solid on the same animated bone."""
import argparse
import base64
import io
import itertools
import json
from pathlib import Path

import numpy as np
from PIL import Image

SIDES = {"north": (2, 0), "south": (2, 1), "east": (0, 1), "west": (0, 0),
         "up": (1, 1), "down": (1, 0)}
BITS = np.array(list(itertools.product(range(2), repeat=3)))


def corners(cube):
    extent = np.array([cube["from"], cube["to"]], dtype=float)
    extent += np.array([[-1], [1]]) * cube.get("inflate", 0)
    origin = np.array(cube["origin"])
    points = np.where(BITS, extent[1], extent[0]) - origin
    for axis, angle in enumerate(cube.get("rotation", [0, 0, 0])):
        a, b = (axis + 1) % 3, (axis + 2) % 3
        c, s = np.cos(np.radians(angle)), np.sin(np.radians(angle))
        points[:, [a, b]] = points[:, [a, b]] @ np.array([[c, s], [-s, c]])
    return points + origin


def bone_members(rows):
    yield [row for row in rows if isinstance(row, str)]
    for row in rows:
        if isinstance(row, dict):
            yield from bone_members(row["children"])


def opaque(cube, images):
    for face in cube["faces"].values():
        if face.get("texture") is None:
            return False
        image = images[int(face["texture"])]
        u, v, x, y = face["uv"]
        bounds = (int(np.floor(min(u, x))), int(np.floor(min(v, y))),
                  int(np.floor(max(u, x)))+1, int(np.floor(max(v, y)))+1)
        if image.crop(bounds).getextrema() != (255, 255):
            return False
    return True


def reduce_model(path):
    model = json.loads(path.read_text(encoding="utf8"))
    cubes = {e["uuid"]: e for e in model["elements"] if e["type"] == "cube"
             and e.get("visibility", True) and not e["name"].startswith(("共用_", "结构_"))}
    images = [Image.open(io.BytesIO(base64.b64decode(t["source"].split(",")[1])))
              .convert("RGBA").getchannel("A") for t in model["textures"]]
    removed = 0
    for members in bone_members(model["outliner"]):
        points = {uid: corners(cubes[uid]) for uid in members if uid in cubes}
        solids = []
        for uid, pos in points.items():
            basis = pos[[4, 2, 1]] - pos[0]
            if abs(np.linalg.det(basis)) > 1e-8 and opaque(cubes[uid], images):
                solids.append((uid, pos[0], np.linalg.inv(basis)))
        for uid, pos in points.items():
            for side, (axis, bit) in SIDES.items():
                face = cubes[uid]["faces"][side]
                if face.get("texture") is None:
                    continue
                quad = pos[BITS[:, axis] == bit]
                for other, origin, inverse in solids:
                    if other == uid:
                        continue
                    local = (quad - origin) @ inverse
                    # Shared perimeter edges are safe when the entire
                    # face lies inside and its centre is strictly interior.
                    centre = local.mean(axis=0)
                    if (local.min() >= 0 and local.max() <= 1 and
                            centre.min() > 1e-6 and centre.max() < 1 - 1e-6):
                        face["texture"] = None
                        removed += 1
                        break
    path.write_text(json.dumps(model, ensure_ascii=False, separators=(",", ":")), encoding="utf8")
    print(path.stem, removed * 2, "interior triangles removed")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("models", type=Path)
    args = parser.parse_args()
    for name in ("机炮炮台", "火箭炮台"):
        reduce_model(args.models / (name + "_减面.bbmodel"))
