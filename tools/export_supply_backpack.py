"""Export the reduced Blockbench supply pack for the worn and drop-pod renderers."""
import argparse
import base64
import gzip
import json
from pathlib import Path


CASE_NODES = {
    "补给箱_左上": "case_01",
    "补给箱_右上": "case_02",
    "补给箱_左下": "case_03",
    "补给箱_右下": "case_04",
}


def export(source: Path, root: Path):
    model = json.loads(source.read_text(encoding="utf-8"))
    textures = model["textures"]
    batches = {}
    vertices = {}
    case_nodes = set()
    for element in model["elements"]:
        if not element.get("export", True):
            continue
        if element["type"] != "mesh" or any(element["rotation"]) or any(element["origin"]):
            raise ValueError(f"Expected a baked mesh for {element['name']}")
        name = CASE_NODES.get(element["name"], "frame")
        if name != "frame":
            if name in case_nodes:
                raise ValueError(f"Duplicate supply box: {name}")
            case_nodes.add(name)
        for face in element["faces"].values():
            material = face["texture"]
            texture = textures[material]
            key = (name, material)
            primitive = batches.setdefault(name, {}).setdefault(material, {
                "material": material, "p": [], "uv": [], "faces": [], "indices": [],
            })
            cache = vertices.setdefault(key, {})
            face_indices = []
            for vertex in face["vertices"]:
                position = tuple(value / 16 for value in element["vertices"][vertex])
                u, v = face["uv"][vertex]
                uv = (u / texture["uv_width"], v / texture["uv_height"])
                index = cache.get((position, uv))
                if index is None:
                    index = len(primitive["p"])
                    cache[position, uv] = index
                    primitive["p"].append(position)
                    primitive["uv"].append(uv)
                face_indices.append(index)
            if len(face_indices) not in (3, 4):
                raise ValueError(f"Expected triangles or quads for {element['name']}")
            primitive["faces"].append(face_indices)
            for index in range(1, len(face_indices) - 1):
                primitive["indices"].extend((face_indices[0], face_indices[index], face_indices[index + 1]))

    if case_nodes != set(CASE_NODES.values()) or "frame" not in batches:
        raise ValueError("Expected four independent supply boxes and a fixed frame")
    nodes = []
    meshes = []
    for name, primitives in sorted(batches.items()):
        nodes.append({"name": name, "parent": -1, "mesh": len(meshes),
                      "t": [0, 0, 0], "r": [0, 0, 0, 1], "s": [1, 1, 1]})
        meshes.append(list(primitives.values()))

    assets = root / "src/main/resources/assets/sbw_helldiver"
    runtime = json.dumps({"nodes": nodes, "meshes": meshes, "clips": {}, "nozzles": []}, separators=(",", ":"))
    (assets / "models/mesh/supply_backpack.mesh.json.gz").write_bytes(gzip.compress(runtime.encode(), mtime=0))
    for index, texture in enumerate(textures):
        png = base64.b64decode(texture["source"].split(",", 1)[1])
        (assets / f"textures/mesh/supply_backpack_{index}.png").write_bytes(png)
    triangle_count = sum(len(primitive["indices"]) // 3 for mesh in meshes for primitive in mesh)
    print(f"Supply backpack: {len(nodes)} nodes, {triangle_count} triangles")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    export(args.source, args.root)
