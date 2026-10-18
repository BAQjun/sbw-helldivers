"""Extract authored display triangles and exact UVs for the isolated glow pass."""
from pathlib import Path
import json

root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/sbw_helldiver'
geometry=json.loads((root/'models/bedrock/vehicle/laser_hellpod_v1.geo.json').read_text(encoding='utf8'))['minecraft:geometry'][0]
width=geometry['description']['texture_width'];height=geometry['description']['texture_height']
meshes=[]
for bone in geometry['bones']:
    name=bone['name']
    if not (name.startswith('ls_displays_') or name.startswith('ls_heat_') and name[8:].isdigit()):continue
    mesh=bone['poly_mesh'];segment=-1 if name.startswith('ls_displays_') else int(name[8:])
    out={'segment':segment,'positions':mesh['positions'],
         'uvs':[[p[0]/width,1-p[1]/height] for p in mesh['uvs']],
         'faces':[[[v[0],v[2]] for v in f[:3]] for f in mesh['polys']]}
    meshes.append(out)
assert len(meshes)==21
(root/'helldivers/laser_displays.json').write_text(json.dumps({'meshes':meshes},separators=(',',':'))+'\n',encoding='utf8')
