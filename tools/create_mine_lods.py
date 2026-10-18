"""Generate distant mine meshes; close views retain the accepted original model."""
from pathlib import Path
import json,copy
R=Path(__file__).resolve().parents[1]/'src/main/resources/assets/sbw_helldiver/models/bedrock/misc'
for name in ['contact_mine','incendiary_mine','gas_mine','anti_tank_mine']:
 source=json.loads((R/(name+'.geo.json')).read_text(encoding='utf-8'))
 for tier,step in [(1,.5),(2,1.0)]:
  out=copy.deepcopy(source)
  for bone in out['minecraft:geometry'][0]['bones']:
   mesh=bone.get('poly_mesh')
   if not mesh:continue
   points=[tuple(round(v/step)*step for v in p) for p in mesh['positions']]
   faces=[];seen=set()
   for face in mesh['polys']:
    for indices in [(0,1,2),(0,2,3)]:
     tri=[face[i] for i in indices];p=[points[v[0]] for v in tri]
     if len(set(p))<3:continue
     a,b,c=p;ab=[b[i]-a[i] for i in range(3)];ac=[c[i]-a[i] for i in range(3)]
     cross=[ab[1]*ac[2]-ab[2]*ac[1],ab[2]*ac[0]-ab[0]*ac[2],ab[0]*ac[1]-ab[1]*ac[0]]
     if sum(x*x for x in cross)<1e-8:continue
     key=tuple(sorted(p))
     if key in seen:continue
     seen.add(key);faces.append(tri+[tri[-1]])
   # Compact indices; preserve the original UV and normal at every surviving corner.
   pos=[];normal=[];uv=[];polys=[]
   for face in faces:
    poly=[]
    for v in face[:3]:
     i=len(pos);pos.append(points[v[0]]);normal.append(mesh['normals'][v[1]]);uv.append(mesh['uvs'][v[2]]);poly.append([i,i,i])
    polys.append(poly+[poly[-1]])
   mesh.update(positions=pos,normals=normal,uvs=uv,polys=polys)
  (R/(name+'_lod'+str(tier)+'.geo.json')).write_text(json.dumps(out,separators=(',',':')),encoding='utf-8')
