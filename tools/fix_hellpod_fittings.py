"""Repair the common Hellpod shell in authored models and runtime exports.

Idempotent: lock tabs follow the tapered panel; a fixed header backs the nozzle
hinge opening without changing the moving nozzle, its pivot or its animation.
"""
import argparse
import copy
import json
import re
import uuid
from pathlib import Path


def repair_source(model):
    elements = model['elements']
    names = {e['name']: e for e in elements}
    groups = {g['uuid']: g['name'] for g in model.get('groups', [])}
    nodes = {}
    def visit(items):
        for node in items:
            if isinstance(node, dict):
                nodes[groups.get(node.get('uuid'), node.get('name'))] = node
                visit(node.get('children', []))
    visit(model['outliner'])
    edits = 0
    for e in elements:
        if re.fullmatch(r'pod_lower_lock_[0-5]_[+-]?1', e['name']):
            before = copy.deepcopy(e)
            e['from'][2], e['to'][2] = -16.9, -16.35
            e['rotation'] = [-8.4, 0, 0]
            e['origin'] = [0, -50.84, -15.75]
            edits += e != before
    for face in (0, 2, 4):
        name = f'pod_nozzle_header_bridge_{face}'
        donor = names.get(f'pod_mid_wall_channel_back_{face}')
        node = nodes.get(f'pod_face_{face}')
        if donor is None or node is None or name in names:
            continue
        e = copy.deepcopy(donor)
        e.update(name=name, uuid=str(uuid.uuid5(uuid.NAMESPACE_URL, model.get('model_identifier', '') + '/' + name)),
                 origin=[0, -35.825, -18.42], **{'from': [-5.5, -36.85, -19.22], 'to': [5.5, -34.8, -17.62]})
        e.pop('rotation', None)
        elements.append(e)
        node['children'].append(e['uuid'])
        edits += 1
    return edits


def repair_runtime(model):
    edits = 0
    for geo in model.get('minecraft:geometry', []):
        for bone in geo.get('bones', []):
            if not re.fullmatch(r'pod_face_[0-5]', bone['name']):
                continue
            for cube in bone.get('cubes', []):
                origin, size = cube.get('origin'), cube.get('size')
                if origin and size and abs(origin[1] + 62.3075) < .0001 and abs(size[0] - 1.2) < .0001 and abs(size[1] - 2.035) < .0001:
                    before = copy.deepcopy(cube)
                    cube['origin'][2] = -16.9
                    cube['pivot'] = [0, -50.84, -15.75]
                    cube['rotation'] = [8.4, 0, 0]
                    edits += cube != before
            if bone['name'][-1] not in '024':
                continue
            cubes = bone.get('cubes', [])
            if any(c.get('origin') == [-5.5, -36.85, -19.22] for c in cubes):
                continue
            donor = next((c for c in cubes if c.get('origin') == [-10.85, -35, -19.22]), None)
            if donor:
                cube = copy.deepcopy(donor)
                cube.update(origin=[-5.5, -36.85, -19.22], size=[11, 2.05, 1.6], pivot=[0, -35.825, -18.42])
                cube.pop('rotation', None)
                cubes.append(cube)
                edits += 1
    return edits


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--sources', type=Path)
    p.add_argument('--runtime', type=Path)
    args = p.parse_args()
    if not args.sources and not args.runtime:
        p.error('Specify --sources and/or --runtime')
    for root, suffix, repair in ((args.sources, '*.bbmodel', repair_source), (args.runtime, '*.geo.json', repair_runtime)):
        if root is None:
            continue
        changed = edits = 0
        for path in root.rglob(suffix):
            model = json.loads(path.read_text(encoding='utf-8-sig'))
            count = repair(model)
            if count:
                path.write_text(json.dumps(model, ensure_ascii=False, separators=(',', ':')), encoding='utf8')
                changed += 1
                edits += count
        print(f'{suffix}: {changed} models, {edits} fittings repaired')

if __name__ == '__main__':
    main()
