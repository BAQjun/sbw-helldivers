"""Align the legacy pod cannon geometry with its already lowered pitch rig.

The standalone model has no pod offset. Detect the misplaced shaft geometrically
so rerunning this repair cannot lower a corrected model a second time.
"""
from pathlib import Path
import argparse, json, copy

LIFT = 3.85

def load(path):
    return json.loads(path.read_bytes())

def save(path, doc):
    path.write_text(json.dumps(doc, ensure_ascii=False, separators=(",", ":")), encoding="utf8")

def descendants(bones, root):
    result = {root}
    while True:
        new = result | {name for name, b in bones.items() if b.get("parent") in result}
        if new == result:
            return result
        result = new

def points(bone):
    values = list(bone.get("poly_mesh", {}).get("positions", []))
    for cube in bone.get("cubes", []):
        values.extend([cube["origin"], [a+b for a,b in zip(cube["origin"], cube["size"])]])
    return values

def repair_runtime(path):
    doc = load(path)
    bones = {b["name"]: b for b in doc["minecraft:geometry"][0]["bones"]}
    shaft = [v for v in points(bones["pitch_mount"])
             if min(abs(abs(v[0])-2.67), abs(abs(v[0])-5.33)) < .001]
    assert shaft, (path, "Missing pitch shaft geometry")
    centre = (min(v[1] for v in shaft)+max(v[1] for v in shaft))/2
    axis = bones["barrel"]["pivot"][1]
    error = centre-axis
    # Authored latch dimensions round the bounding-box centre to 30.77.
    aligned = abs(error) < .01
    assert aligned or abs(error-LIFT) < .01, (path, centre, axis)
    before = copy.deepcopy(doc)
    for name in (() if aligned else descendants(bones, "barrel")):
        b = bones[name]
        for cube in b.get("cubes", []):
            cube["origin"][1] = round(cube["origin"][1]-LIFT, 7)
            if "pivot" in cube:
                cube["pivot"][1] = round(cube["pivot"][1]-LIFT, 7)
        for vertex in b.get("poly_mesh", {}).get("positions", []):
            vertex[1] = round(vertex[1]-LIFT, 7)
    # This later-authored hinge alone escaped the original pivot conversion.
    if abs(bones["ejector"]["pivot"][1]-31.98) < .001:
        bones["ejector"]["pivot"][1] = 28.13
    for b in bones.values():
        for name, locator in b.get("locators", {}).items():
            value = locator.get("offset") if isinstance(locator, dict) else locator
            if name == "muzzle_exit" and abs(value[1]-31.1) < .001:
                value[1] = 27.25
            if name == "shell_exit" and value[1] > 30:
                value[1] = round(value[1]-LIFT, 7)
    if doc == before:
        return False
    save(path, doc)
    return True

def repair_source(path):
    doc = load(path)
    groups = {g["uuid"]: g for g in doc["groups"]}
    barrel = next(g for g in groups.values() if g["name"] == "barrel")
    shaft = [e for e in doc["elements"] if e["name"].startswith("ac_v2_pitch_shaft")]
    centre = (min(e["from"][1] for e in shaft)+max(e["to"][1] for e in shaft))/2
    if abs(centre-barrel["origin"][1]) < .01:
        return False
    assert abs(centre-barrel["origin"][1]-LIFT) < .01
    elements = {e["uuid"]: e for e in doc["elements"]}
    def walk(nodes, moving=False):
        for node in nodes:
            if isinstance(node, dict):
                group = groups[node["uuid"]]
                active = moving or group["name"] == "barrel"
                if active and group["name"] == "ejector":
                    assert abs(group["origin"][1]-31.98) < .001
                    group["origin"][1] = 28.13
                walk(node.get("children", []), active)
            elif moving:
                element = elements[node]
                if element.get("type") == "locator":
                    continue  # These already use the pod bind space.
                for key in ("from", "to", "origin"):
                    if key in element:
                        element[key][1] = round(element[key][1]-LIFT, 7)
                for vertex in element.get("vertices", {}).values():
                    vertex[1] = round(vertex[1]-LIFT, 7)
    walk(doc["outliner"])
    save(path, doc)
    return True

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--runtime", type=Path, action="append", default=[])
    parser.add_argument("--source", type=Path, action="append", default=[])
    args = parser.parse_args()
    for path in args.runtime:
        print(path, "corrected" if repair_runtime(path) else "already aligned")
    for path in args.source:
        print(path, "corrected" if repair_source(path) else "already aligned")
