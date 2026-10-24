import argparse
import copy
import json
import math
from pathlib import Path

p = argparse.ArgumentParser(description='Verify a reduced runtime model against its unreduced export.')
p.add_argument('--input', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
checked = 0
for path in (a.input / 'models').rglob('*.geo.json'):
    if '_lod' in path.stem:
        continue
    rel = path.relative_to(a.input)
    before = json.loads(path.read_text(encoding='utf8'))
    after = json.loads((a.output / rel).read_text(encoding='utf8'))
    assert before.keys() == after.keys(), rel
    for old_geo, geo in zip(before['minecraft:geometry'], after['minecraft:geometry'], strict=True):
        assert old_geo['description'] == geo['description'], rel
        assert len(old_geo['bones']) == len(geo['bones']), rel
        for old_bone, bone in zip(old_geo['bones'], geo['bones'], strict=True):
            assert {k:v for k,v in old_bone.items() if k not in ('poly_mesh','cubes')} == {k:v for k,v in bone.items() if k not in ('poly_mesh','cubes')}, (rel, bone['name'])
            assert len(old_bone.get('cubes', [])) == len(bone.get('cubes', [])), (rel, bone['name'])
            for old_cube, cube in zip(old_bone.get('cubes', []), bone.get('cubes', []), strict=True):
                assert {k:v for k,v in old_cube.items() if k != 'uv'} == {k:v for k,v in cube.items() if k != 'uv'}, (rel, bone['name'])
                if isinstance(cube['uv'], dict):
                    assert all(old_cube['uv'].get(k) == v for k,v in cube['uv'].items()), (rel, bone['name'])
                else:
                    assert old_cube['uv'] == cube['uv'], rel
            mesh = bone.get('poly_mesh')
            if not mesh:
                continue
            old_mesh = old_bone['poly_mesh']
            assert {k:v for k,v in old_mesh.items() if k not in ('positions','normals','uvs','polys')} == {k:v for k,v in mesh.items() if k not in ('positions','normals','uvs','polys')}, rel
            for key in ('positions','normals','uvs'):
                values = {tuple(v) for v in old_mesh[key]}
                assert all(tuple(v) in values for v in mesh[key]), (rel, bone['name'], key)
                assert all(math.isfinite(x) for v in mesh[key] for x in v), rel
            assert len(mesh['polys']) <= len(old_mesh['polys']) * 2, rel
            for face in mesh['polys']:
                assert len(face) == 3, (rel, bone['name'])
                for corner in face:
                    for i,key in enumerate(('positions','normals','uvs')):
                        assert 0 <= corner[i] < len(mesh[key]) or (key == 'normals' and corner[i] == -1 and not mesh[key]), (rel, bone['name'],key)
    checked += 1
print(f'{checked} runtime models verified: hierarchy, pivots, locators, cube transforms, original attributes and valid triangle indices')
