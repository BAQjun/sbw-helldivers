"""Export accepted static rack meshes, retaining per-case visibility and shutter nodes."""
import argparse,base64,gzip,json
from pathlib import Path

def export(source,root,name):
 d=json.loads(source.read_text(encoding='utf8')); nodes=[];meshes=[]
 for e in d['elements']:
  if e['type']!='mesh' or not e.get('export',True):continue
  assert not any(e.get('rotation',[0,0,0]))
  label=e['name']
  node='front_shield' if label.startswith('Front sliding') else 'rear_shield' if label.startswith('Rear sliding') else 'case_'+label[-2:] if label.startswith('Supply case ') else 'rack_lift'
  mats={}
  for face in e['faces'].values():
   tex=face.get('texture')
   if tex is None or tex is False:continue
   tex=int(tex);p=mats.setdefault(tex,dict(material=tex,p=[],uv=[],indices=[],faces=[]));ids=[]
   for v in face['vertices']:
    ids.append(len(p['p']));p['p'].append([n/16 for n in e['vertices'][v]])
    u,w=face['uv'][v];t=d['textures'][tex];p['uv'].append([u/t['uv_width'],w/t['uv_height']])
   assert 3<=len(ids)<=4
   p['faces'].append(ids)
   for i in range(1,len(ids)-1):p['indices'] += [ids[0],ids[i],ids[i+1]]
  nodes.append(dict(parent=-1,mesh=len(meshes),name=node,t=[0,0,0],r=[0,0,0,1],s=[1,1,1]));meshes.append(list(mats.values()))
 out=root/'src/main/resources/assets/sbw_helldiver'
 (out/'models/mesh'/f'{name}.mesh.json.gz').write_bytes(gzip.compress(json.dumps(dict(nodes=nodes,meshes=meshes,clips={},nozzles=[]),separators=(',',':')).encode(),mtime=0))
 for i,t in enumerate(d['textures']):(out/'textures/mesh'/f'{name}_{i}.png').write_bytes(base64.b64decode(t['source'].split(',',1)[1]))
 print(name,len(nodes),'nodes',sum(len(p['faces'])for m in meshes for p in m),'faces')
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('name');p.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1]);a=p.parse_args();export(a.source,a.root,a.name)
