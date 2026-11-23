"""Export the optimized Jump Pack and its authored exhaust sockets."""
import argparse
import base64
import gzip
import json
from pathlib import Path


def export(source: Path, assets: Path):
    model = json.loads(source.read_text(encoding="utf-8"))
    (texture,) = model["textures"]
    primitive = {"material": 0, "p": [], "uv": [], "faces": [], "indices": []}
    vertex_cache = {}
    for element in model["elements"]:
        if not element.get("export", True):
            continue
        if element["type"] != "mesh" or any(element["origin"]) or any(element["rotation"]):
            raise ValueError(f"Expected a baked mesh for {element['name']}")
        for face in element["faces"].values():
            if face["texture"] != 0 or len(face["vertices"]) not in (3, 4):
                raise ValueError(f"Expected atlas-mapped triangles or quads for {element['name']}")
            indices = []
            for key in face["vertices"]:
                position = tuple(value / 16 for value in element["vertices"][key])
                u, v = face["uv"][key]
                uv = (u / texture["uv_width"], v / texture["uv_height"])
                signature = (position, uv)
                index = vertex_cache.get(signature)
                if index is None:
                    index = len(primitive["p"])
                    vertex_cache[signature] = index
                    primitive["p"].append(position)
                    primitive["uv"].append(uv)
                indices.append(index)
            primitive["faces"].append(indices)
            for vertex in range(1, len(indices) - 1):
                primitive["indices"].extend((indices[0], indices[vertex], indices[vertex + 1]))

    groups = {group["name"]: group for group in model["groups"]}
    nozzles = [[coordinate / 16 for coordinate in groups[name]["origin"]]
               for name in ("fx_socket_l", "fx_socket_r")]
    runtime = {
        "nodes": [{"name": "frame", "parent": -1, "mesh": 0,
                   "t": [0, 0, 0], "r": [0, 0, 0, 1], "s": [1, 1, 1]}],
        "meshes": [[primitive]], "clips": {}, "nozzles": nozzles,
    }
    payload = json.dumps(runtime, separators=(",", ":")).encode("utf-8")
    (assets / "models/mesh/jump_pack.mesh.json.gz").write_bytes(gzip.compress(payload, mtime=0))
    (assets / "textures/mesh/jump_pack_0.png").write_bytes(base64.b64decode(texture["source"].split(",", 1)[1]))
    print(f"Jump Pack: {len(primitive['indices']) // 3} triangles, {len(nozzles)} exhaust sockets")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    export(args.source, args.root / "src/main/resources/assets/sbw_helldiver")
