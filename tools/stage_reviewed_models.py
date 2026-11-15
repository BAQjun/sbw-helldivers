"""Replace reviewed native mesh surfaces while retaining the runtime rigs and pod fittings.

Native rows must come from export_native_mesh.js / Blockbench geometry indices, with positions in the
Bedrock authoring frame. Standalone sentries require sentry_coordinate_spaces;
combined emplacement exports already use the runtime bind frame.
"""
from pathlib import Path
import argparse,base64,copy,hashlib,io,json,math
from PIL import Image
from sentry_coordinate_spaces import runtime_vertex
from fix_autocannon_alignment import repair_runtime

def count(doc):
 return sum(sum(len(c['uv'])*2 if isinstance(c.get('uv'),dict) else 12 for c in b.get('cubes',[]))+sum(len(set(tuple(x)for x in f))-2 for f in b.get('poly_mesh',{}).get('polys',[]))for b in doc['minecraft:geometry'][0]['bones'])

def stage(assets,export,name,combined=False,include_pod=False):
 p=assets/f'models/bedrock/vehicle/{name}.geo.json';tp=assets/f'textures/bedrock/vehicle/{name}.png'
 original=json.loads(p.read_bytes());doc=copy.deepcopy(original);geo=doc['minecraft:geometry'][0];bones={b['name']:b for b in geo['bones']}
 src=json.loads(export.read_bytes());sb={b['name']:b for b in src['bones']}
 # Native UVs use Blockbench's logical canvas dimensions. Solid swatches can
 # embed a much smaller PNG, so convert to actual texels before atlas packing.
 for ti,t in enumerate(src['textures']):
  image=Image.open(io.BytesIO(base64.b64decode(t['source'].split(',',1)[1])))
  assert image.convert('RGBA').getextrema()[3][1]>0, f'Transparent native texture {ti}: export the embedded source PNG'
  tw,th=t['w'],t['h']
  if not tw or not th:
   assert all(lo==hi for lo,hi in image.convert('RGBA').getextrema()), 'Missing logical dimensions on a non-uniform texture'
   tw,th=image.size
  sx,sy=image.width/tw,image.height/th
  for tris in src['rows'].values():
   for tri in tris:
    for v in tri:
     if v['ti']==ti:v['uv']=[max(.001,min(image.width-.001,v['uv'][0]*sx)),max(.001,min(image.height-.001,v['uv'][1]*sy))]
 rows={k:v for k,v in src['rows'].items()if include_pod or not k.startswith('pod_')}
 assert not(rows.keys()-bones.keys()),rows.keys()-bones.keys()
 if combined:
  assert all(all(sb[k].get(v)==bones[k].get(v)for v in ('parent','pivot','rotation'))for k in rows)
 atlas0=Image.open(tp).convert('RGBA');tiles=[];offsets={};x=atlas0.width;y=0;rh=0;h=atlas0.height;used={}
 for ti,t in enumerate(src['textures']):
  points=[v['uv']for tris in rows.values()for tri in tris for v in tri if v['ti']==ti]
  if not points:continue
  im=Image.open(io.BytesIO(base64.b64decode(t['source'].split(',',1)[1]))).convert('RGBA')
  box=(max(0,math.floor(min(v[0]for v in points))-1),max(0,math.floor(min(v[1]for v in points))-1),min(im.width,math.ceil(max(v[0]for v in points))+2),min(im.height,math.ceil(max(v[1]for v in points))+2));crop=im.crop(box)
  key=hashlib.sha256(im.tobytes()).hexdigest();match=None
  for ox in range(0,atlas0.width,512):
   if ox+im.width<=atlas0.width and im.height<=atlas0.height and atlas0.crop((ox+box[0],box[1],ox+box[2],box[3])).tobytes()==crop.tobytes():match=(ox,0);break
  if match is not None:offsets[ti]=match;continue
  if key in used:offsets[ti]=used[key];continue
  if x+crop.width>8192:x=0;y=max(h,y+rh);rh=0
  offsets[ti]=(x-box[0],y-box[1]);used[key]=offsets[ti];tiles.append((crop,(x,y)));x+=crop.width;rh=max(rh,crop.height);h=max(h,y+rh)
 assert h<=16384
 width=max(atlas0.width,max((xy[0]+im.width for im,xy in tiles),default=0));atlas=Image.new('RGBA',(width,h));atlas.paste(atlas0,(0,0))
 for im,xy in tiles:atlas.paste(im,xy)
 oldw=geo['description']['texture_width'];oldh=geo['description']['texture_height']
 for b in geo['bones']:
  pm=b.get('poly_mesh')
  if pm:
   for uv in pm['uvs']:
    if pm.get('normalized_uvs'):uv[0]*=oldw;uv[1]*=oldh
    uv[1]+=h-oldh
   pm['normalized_uvs']=False
 for name_b,tris in rows.items():
  b=bones[name_b];pivot=sb[name_b].get('pivot',[0,0,0]);pm=dict(normalized_uvs=False,positions=[],normals=[],uvs=[],polys=[]);cache={}
  for tri in tris:
   face=[]
   for v in tri:
    trans=lambda a,normal=False:a if combined else runtime_vertex(name,name_b,a,source_pivot=pivot,runtime_pivot=b.get('pivot',[0,0,0]),elevation_pivot=bones.get('barrel',{}).get('pivot'),normal=normal)
    pos=[round(a,7)for a in trans(v['p'])];normal=trans(v['n'],True);ox,oy=offsets[v['ti']];uv=[v['uv'][0]+ox,h-v['uv'][1]-oy];key=tuple(pos+normal+uv)
    if key not in cache:
     i=len(pm['positions']);cache[key]=i;pm['positions'].append(pos);pm['normals'].append(normal);pm['uvs'].append(uv)
    i=cache[key];face.append([i,i,i])
   face.append(face[-1]);pm['polys'].append(face)
  b.pop('cubes',None);b['poly_mesh']=pm
 # Standalone mortar sources intentionally keep their rig parents empty.
 # Retaining the old aggregate meshes doubles their visible side panels.
 if name in ('mortar_hellpod_v1','ems_mortar_hellpod_v1','gas_mortar_hellpod_v1'):
  for parent in ('turret','barrel'):
   if not rows.get(parent):
    bones[parent].pop('cubes',None);bones[parent].pop('poly_mesh',None)
 geo['description']['texture_width']=width;geo['description']['texture_height']=h
 rig=lambda d:[{k:v for k,v in b.items()if k not in('cubes','poly_mesh')}for b in d['minecraft:geometry'][0]['bones']]
 assert rig(doc)==rig(original)
 p.write_text(json.dumps(doc,separators=(',',':')),'utf8');atlas.save(tp)
 ep=tp.with_stem(tp.stem+'_emissive')
 if ep.exists():
  old=Image.open(ep).convert('RGBA');em=Image.new('RGBA',atlas.size);em.paste(old,(0,0));em.save(ep)
 if name=='autocannon_hellpod_v1':assert not repair_runtime(p),'Unexpected second alignment repair'
 return dict(model=name,before=count(original),after=count(doc),replaced_bones=len(rows))

if __name__=='__main__':
 a=argparse.ArgumentParser();a.add_argument('--assets',type=Path,required=True);a.add_argument('--export',type=Path,required=True);a.add_argument('--model',required=True);a.add_argument('--combined',action='store_true');v=a.parse_args();print(json.dumps(stage(v.assets,v.export,v.model,v.combined)))
