"""Export the reviewed distant Blockbench mesh without changing its silhouette."""
import base64,json,hashlib
from pathlib import Path
R=Path(__file__).resolve().parents[1];S=R/'art/models/超级驱逐舰/超级驱逐舰_远景.bbmodel';A=R/'src/main/resources/assets/sbw_helldiver';d=json.loads(S.read_text(encoding='utf-8-sig'));tri=[]
for part in d['elements']:
 assert part.get('rotation',[0,0,0])==[0,0,0] and part.get('origin',[0,0,0])==[0,0,0]
 for f in part['faces'].values():
  keys=f['vertices']
  for i in range(1,len(keys)-1):
   row=[]
   for k in (keys[0],keys[i],keys[i+1]):row.append([round(v/16,6) for v in part['vertices'][k]]+[round(v/256,6) for v in f['uv'][k]])
   tri.append([1 if part['name'].startswith('07_') else 0,row])
out=A/'models/mesh/super_destroyer.mesh.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps({'source_sha256':hashlib.sha256(S.read_bytes()).hexdigest(),'triangles':tri},separators=(',',':')))
textures=A/'textures/mesh';textures.mkdir(parents=True,exist_ok=True)
(textures/'super_destroyer.png').write_bytes(base64.b64decode(d['textures'][0]['source'].split(',',1)[1]))
print(len(tri),'triangles exported')
