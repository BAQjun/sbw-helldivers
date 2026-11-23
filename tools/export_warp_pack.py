"""Export the accepted Warp Pack mesh and its energy-orb attachment."""
import argparse
import base64
import gzip
import json
from pathlib import Path


def export(source: Path, assets: Path):
    model = json.loads(source.read_text(encoding="utf-8"))
    textures = model["textures"]
    batches = {}
    vertex_indices = {}
    for element in model["elements"]:
        if not element.get("export", True):
            continue
        if element["type"] != "mesh" or any(element["rotation"]) or any(element["origin"]):
            raise ValueError(f"Expected baked mesh: {element['name']}")
        for face in element["faces"].values():
            material = face["texture"]
            texture = textures[material]
            primitive = batches.setdefault(material, {
                "material": material, "p": [], "uv": [], "faces": [], "indices": [],
            })
            cache = vertex_indices.setdefault(material, {})
            indices = []
            for vertex in face["vertices"]:
                position = tuple(value / 16 for value in element["vertices"][vertex])
                u, v = face["uv"][vertex]
                uv = (u / texture["uv_width"], v / texture["uv_height"])
                key = (position, uv)
                if key not in cache:
                    cache[key] = len(primitive["p"])
                    primitive["p"].append(position)
                    primitive["uv"].append(uv)
                indices.append(cache[key])
            if len(indices) not in (3, 4):
                raise ValueError(f"Expected triangle or quad: {element['name']}")
            primitive["faces"].append(indices)
            for index in range(1, len(indices) - 1):
                primitive["indices"].extend((indices[0], indices[index], indices[index + 1]))

    ring, = (group["origin"] for group in model["groups"] if group["name"] == "halo_fx")
    primitives = [batches[index] for index in sorted(batches)]
    data = {
        "nodes": [{"name": "backpack", "parent": -1, "mesh": 0,
                   "t": [0, 0, 0], "r": [0, 0, 0, 1], "s": [1, 1, 1]}],
        "meshes": [primitives],
        "clips": {},
        "nozzles": [[value / 16 for value in ring]],
    }
    encoded = json.dumps(data, separators=(",", ":")).encode()
    (assets / "models/mesh/warp_pack.mesh.json.gz").write_bytes(gzip.compress(encoded, mtime=0))
    for index, texture in enumerate(textures):
        image = base64.b64decode(texture["source"].split(",", 1)[1])
        (assets / f"textures/mesh/warp_pack_{index}.png").write_bytes(image)
    triangles = sum(len(primitive["indices"]) // 3 for primitive in primitives)
    print(f"Warp Pack: {triangles} triangles, {len(primitives)} material batches, orb socket {ring}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("--assets", type=Path, default=Path(__file__).resolve().parents[1] /
                        "src/main/resources/assets/sbw_helldiver")
    args = parser.parse_args()
    export(args.source, args.assets)
