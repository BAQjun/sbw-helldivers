"""Close the reviewed sensor cap, sight mount and ejector hinge gaps while keeping the hatch attached to its moving hinge."""
import argparse, copy, json, uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PAD_NAME = "ac_v2_sight_mount_foot"
PAD_ID = str(uuid.uuid5(uuid.NAMESPACE_URL, "sbw-helldivers/autocannon/sight-mount-foot"))

def load(path):
    return json.loads(path.read_text(encoding="utf-8"))

def save(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

def repair_source(path):
    model = load(path)
    strip = next(e for e in model["elements"] if e["name"] == "ac_v2_sight_box_endstrip_1")
    floor = strip["from"][1]
    for element in model["elements"]:
        if element["name"].startswith("ac_v2_sensor_base_"):
            element["to"][1] = element["from"][1] + .505
    if not any(e["name"] == PAD_NAME for e in model["elements"]):
        pad = copy.deepcopy(strip)
        pad.update(name=PAD_NAME, uuid=PAD_ID, **{"from": [1.65, floor-.27, .6], "to": [3.45, floor+.05, 4.9], "origin": [2.55, floor-.11, 2.75]})
        model["elements"].append(pad)
        barrel = next(g for g in model["groups"] if g["name"] == "barrel")
        def attach(nodes):
            for node in nodes:
                if isinstance(node, dict):
                    if node.get("uuid") == barrel["uuid"]:
                        node["children"].append(PAD_ID)
                        return True
                    if attach(node.get("children", [])):
                        return True
            return False
        assert attach(model["outliner"])
    # The hatch sits on the middle toggle and pivots on its rear hinge pin.
    # Keep that fixed pin attached while the cover opens and the toggle recoils.
    # Combined sources offset bone pivots, but keep cube vertices in the same coordinates.
    shift = 0.0
    ejector = next(g for g in model["groups"] if g["name"] == "ejector")
    middle = next(g for g in model["groups"] if g["name"] == "toggle_mid")
    ejector["origin"] = [.1342, 31.98+shift, 7.9]
    for element in model["elements"]:
        if element["name"].startswith("ac_v2_ejector_") and abs(element["from"][1]-(32.41+shift if "hatch" in element["name"] else 32.485+shift)) < 1e-4:
            for key in ["from", "to", "origin"]:
                element[key][1] = round(element[key][1]-.61, 6)
    def take(nodes, uid):
        for index, node in enumerate(nodes):
            if isinstance(node, dict):
                if node.get("uuid") == uid:
                    return nodes.pop(index)
                found = take(node.get("children", []), uid)
                if found is not None:
                    return found
    moved = take(model["outliner"], ejector["uuid"])
    def find(nodes, uid):
        for node in nodes:
            if isinstance(node, dict):
                if node.get("uuid") == uid:
                    return node
                result = find(node.get("children", []), uid)
                if result is not None:
                    return result
    middle_node = find(model["outliner"], middle["uuid"])
    assert moved is not None and middle_node is not None
    middle_node["children"].append(moved)
    lug_name = "ac_v2_ejector_fixed_hinge_seat"
    model["elements"] = [e for e in model["elements"] if e["name"] != lug_name]
    middle_node["children"] = [n for n in middle_node["children"] if n != str(uuid.uuid5(uuid.NAMESPACE_URL, "sbw-helldivers/autocannon/ejector-hinge-seat"))]
    if not any(e["name"] == lug_name for e in model["elements"]):
        lug = copy.deepcopy(next(e for e in model["elements"] if e["name"] == "ac_v2_ejector_hinge_bar_1"))
        lug.update(name=lug_name, uuid=str(uuid.uuid5(uuid.NAMESPACE_URL, "sbw-helldivers/autocannon/ejector-hinge-seat")), **{"from": [-1.0808, 31.70+shift, 7.81], "to": [1.3492, 31.98+shift, 7.99], "origin": [.1342, 31.84+shift, 7.9]})
        model["elements"].append(lug)
        middle_node["children"].append(lug["uuid"])
    for animation in model["animations"]:
        if not animation["name"].endswith((".deploy", ".retro_landing_preview")):
            continue
        anchor = 6.45 if animation["name"].endswith(".retro_landing_preview") else .75 if "空投仓" in path.stem else .55
        for key in animation.get("animators", {}).get("effects", {}).get("keyframes", []):
            if any(d.get("effect", "").split(":")[-1] == "sfx_native_autocannon_sentry_deploy_633699812_0" for d in key.get("data_points", [])):
                key["time"] = anchor
    save(path, model)

def repair_runtime(path):
    model = load(path)
    bones = {b["name"]: b for b in model["minecraft:geometry"][0]["bones"]}
    shift = 0.0
    sensor = bones["sensor"]["cubes"]
    for cube in sensor:
        if abs(cube["origin"][1] - (36.01+shift)) < 1e-4:
            cube["size"][1] = .505
    barrel = bones["barrel"]["cubes"]
    template = next(c for c in barrel if c["origin"] == [-3.58, 34.77, 0])
    barrel[:] = [c for c in barrel if c.get("size") != [1.8, .32, 4.3]]
    origin = [-3.45, round(34.5+shift, 5), .6]
    if not any(c.get("origin") == origin for c in barrel):
        pad = copy.deepcopy(template)
        pad.update(origin=origin, size=[1.8, .32, 4.3])
        pad.pop("pivot", None)
        pad.pop("rotation", None)
        barrel.append(pad)
    ejector = bones["ejector"]
    ejector["parent"] = "toggle_mid"
    ejector["pivot"] = [-.1342, round(31.98+shift, 6), 7.9]
    for cube in ejector["cubes"]:
        if abs(cube["origin"][1]-(32.41+shift)) < 1e-4 or abs(cube["origin"][1]-(32.485+shift)) < 1e-4:
            cube["origin"][1] = round(cube["origin"][1]-.61, 6)
    lug_origin = [-1.3492, round(31.7+shift, 6), 7.81]
    middle = bones["toggle_mid"]["cubes"]
    middle[:] = [c for c in middle if c.get("size") != [2.43, .28, .18]]
    if not any(c.get("origin") == lug_origin for c in middle):
        lug = copy.deepcopy(ejector["cubes"][-1])
        lug.update(origin=lug_origin, size=[2.43, .28, .18])
        middle.append(lug)
    save(path, model)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--delivery", type=Path, required=True)
    args = parser.parse_args()
    for folder, name in [("单独炮台", "机炮炮台.bbmodel"), ("炮台与空投仓", "机炮炮台与空投仓.bbmodel")]:
        repair_source(args.delivery / "模型" / folder / name)
    assets = ROOT / "src/main/resources/assets/sbw_helldiver"
    full = assets / "models/bedrock/vehicle"
    for base in [full, ROOT / "src/main/resources/resourcepacks/low_quality_models/assets/sbw_helldiver/models/bedrock/vehicle"]:
        for name in ["autocannon_sentry_v1.geo.json", "autocannon_hellpod_v1.geo.json"]:
            repair_runtime(base / name)
    print("Repaired both source models and both runtime model qualities.")
