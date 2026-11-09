"""Export the accepted, user-edited Blockbench sample pouch without raw game resources."""
import argparse, base64, gzip, json
from pathlib import Path


def export(source, root):
    model = json.loads(source.read_text(encoding="utf-8"))
    # The accepted pouch is static; fail instead of silently ignoring later animation edits.
    assert all(not any(e.get("rotation", [0, 0, 0])) for e in model["elements"])
    assert all(not any(g.get("rotation", [0, 0, 0])) for g in model.get("groups", []))
    out = root / "src/main/resources/assets/sbw_helldiver"
    nodes, meshes = [], []
    for element in model["elements"]:
        if not element.get("export", True):
            continue
        materials = {}
        for face in element["faces"].values():
            if face.get("texture") is None or face.get("texture") is False:
                continue
            texture = int(face["texture"])
            primitive = materials.setdefault(texture, dict(material=texture, p=[], uv=[], indices=[], faces=[]))
            vertices = face["vertices"]
            assert 3 <= len(vertices) <= 4
            start = len(primitive["p"])
            size = model["textures"][texture]
            for key in vertices:
                primitive["p"].append([v / 16 for v in element["vertices"][key]])
                u, v = face["uv"][key]
                primitive["uv"].append([u / size["uv_width"], v / size["uv_height"]])
            ids = list(range(start, start + len(vertices)))
            primitive["faces"].append(ids)
            for i in range(1, len(ids) - 1):
                primitive["indices"].extend([ids[0], ids[i], ids[i + 1]])
        for material, primitive in materials.items():
            nodes.append(dict(parent=-1, mesh=len(meshes), name=element["name"] + ("_light" if material == 1 else ""), t=[0, 0, 0], r=[0, 0, 0, 1], s=[1, 1, 1]))
            meshes.append([primitive])
    target = out / "models/mesh/sample_container.mesh.json.gz"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(gzip.compress(json.dumps(dict(nodes=nodes, meshes=meshes, clips={}, nozzles=[]), separators=(",", ":")).encode(), mtime=0))
    for i, texture in enumerate(model["textures"]):
        data = base64.b64decode(texture["source"].split(",", 1)[1])
        path = out / f"textures/mesh/sample_container_{i}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        if i == 1:
            path.with_name("sample_container_1_glow.png").write_bytes(data)
    print(f"Exported {len(nodes)} meshes, {sum(len(p['faces']) for m in meshes for p in m)} faces")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    export(args.source, args.root)
