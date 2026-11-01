"""Export the accepted Blockbench GLB without changing its authored geometry/animation."""
import json,struct,gzip,sys,io,math
from pathlib import Path
ART=Path('D:/Codex/projects/2026-09-19/new-chat')
sys.path.insert(0,str(ART/'work/survival06/python_libs'))
import numpy as np
from PIL import Image
SRC=ART/'outputs/当前交付/模型/撤离信标/空投部署/撤离信标与空投仓.glb'
OUT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/sbw_helldiver'
b=SRC.read_bytes();size=struct.unpack_from('<I',b,12)[0];g=json.loads(b[20:20+size]);blob=b[28+size:]
def accessor(i):
 a=g['accessors'][i];v=g['bufferViews'][a['bufferView']];dtype={5126:'<f4',5125:'<u4',5123:'<u2',5121:'u1'}[a['componentType']];count={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}[a['type']]
 off=v.get('byteOffset',0)+a.get('byteOffset',0);stride=v.get('byteStride',np.dtype(dtype).itemsize*count)
 return np.ndarray((a['count'],count),dtype=dtype,buffer=blob,offset=off,strides=(stride,np.dtype(dtype).itemsize)).copy()
def rounded(a):return np.round(a,7).tolist()
nodes=[];parents={c:i for i,n in enumerate(g['nodes'])for c in n.get('children',[])}
cap_root=next(i for i,n in enumerate(g['nodes']) if n.get('name')=='pod_eject_cap')
def is_cap(i):
 return i==cap_root or (i in parents and is_cap(parents[i]))
for i,n in enumerate(g['nodes']):
 nodes.append(dict(parent=parents.get(i,-1),t=n.get('translation',[0,0,0]),r=n.get('rotation',[0,0,0,1]),s=n.get('scale',[1,1,1]),mesh=n.get('mesh',-1),cap=is_cap(i)))
meshes=[]
for m in g['meshes']:
 ps=[]
 for p in m['primitives']:
  a=p['attributes'];ps.append(dict(material=p.get('material',0),p=rounded(accessor(a['POSITION'])),uv=rounded(accessor(a['TEXCOORD_0'])),indices=accessor(p['indices']).ravel().tolist()))
 meshes.append(ps)
clips={}
for a in g['animations']:
 name=a['name'].rsplit('.',1)[-1]
 if name not in ['packed','deploy','deployed','retract']:continue
 channels=[]
 for c in a['channels']:
  s=a['samplers'][c['sampler']];channels.append(dict(node=c['target']['node'],path=c['target']['path'],times=accessor(s['input']).ravel().tolist(),values=rounded(accessor(s['output'])),interpolation=s.get('interpolation','LINEAR')))
 clips[name]=channels
root=dict(nodes=nodes,meshes=meshes,clips=clips)
out=OUT/'models/mesh/equipment_beacon.mesh.json.gz';out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(gzip.compress(json.dumps(root,separators=(',',':')).encode(),mtime=0))
texdir=OUT/'textures/mesh';texdir.mkdir(exist_ok=True)
for i,m in enumerate(g['materials']):
 ti=m['pbrMetallicRoughness']['baseColorTexture']['index'];src=g['textures'][ti]['source'];v=g['bufferViews'][g['images'][src]['bufferView']];im=Image.open(io.BytesIO(blob[v.get('byteOffset',0):v.get('byteOffset',0)+v['byteLength']])).convert('RGBA')
 im.save(texdir/f'equipment_beacon_{i}.png');pix=np.array(im);mask=(pix[:,:,2]>110)&(pix[:,:,2]>pix[:,:,0]*1.2)&(pix[:,:,1]>70)&(pix[:,:,3]>0)
 glow=pix.copy();glow[~mask]=0
 # Screen material is emissive only where the authored screen is coloured; dynamic terminal UI covers it.
 if i==1:glow[:]=0
 Image.fromarray(glow).save(texdir/f'equipment_beacon_{i}_emissive.png')
def matrix(t,r,s):
 x,y,z,w=r;m=np.eye(4);m[:3,:3]=np.array([[1-2*y*y-2*z*z,2*x*y-2*z*w,2*x*z+2*y*w],[2*x*y+2*z*w,1-2*x*x-2*z*z,2*y*z-2*x*w],[2*x*z-2*y*w,2*y*z+2*x*w,1-2*x*x-2*y*y]])@np.diag(s);m[:3,3]=t;return m
def pose(clip,time):
 trs=[{k:n[k]for k in ['t','r','s']}for n in nodes]
 for c in clips[clip]:
  ts=c['times'];i=max(0,min(len(ts)-2,np.searchsorted(ts,time,side='right')-1));j=min(i+1,len(ts)-1);f=0 if i==j else max(0,min(1,(time-ts[i])/(ts[j]-ts[i])));a=np.array(c['values'][i]);b=np.array(c['values'][j]);key={'translation':'t','rotation':'r','scale':'s'}[c['path']]
  if key=='r' and a@b<0:b=-b
  val=a*(1-f)+b*f
  if key=='r':val/=np.linalg.norm(val)
  trs[c['node']][key]=val
 cache={}
 def calc(i):
  if i not in cache:cache[i]=(calc(nodes[i]['parent'])if nodes[i]['parent']>=0 else np.eye(4))@matrix(**trs[i])
  return cache[i]
 return [calc(i)for i in range(len(nodes))]
mats=pose('deployed',0)
allp=[];screen=[]
for i,n in enumerate(nodes):
 if n['mesh']<0:continue
 for p in meshes[n['mesh']]:
  pp=np.array(p['p']);world=(mats[i]@np.c_[pp,np.ones(len(pp))].T).T[:,:3];allp.extend(world)
  if p['material']==1:
   for k in range(0,len(p['indices']),3):
    vv=world[p['indices'][k:k+3]];cross=np.cross(vv[1]-vv[0],vv[2]-vv[0]);area=np.linalg.norm(cross)/2
    if area>1e-5:screen.append((area,vv,cross/np.linalg.norm(cross)))
print('bounds',np.min(allp,axis=0),np.max(allp,axis=0),'screen triangles')
for ar,v,n in sorted(screen,key=lambda x:-x[0])[:10]:print(ar,rounded(v),rounded(n))
# The two largest coplanar triangles form the actual display glass.
v=np.array(sorted(screen,key=lambda x:-x[0])[0][1]);v2=np.array(sorted(screen,key=lambda x:-x[0])[1][1]);corners=np.unique(np.round(np.r_[v,v2],6),axis=0);center=corners.mean(axis=0)
normal=sorted(screen,key=lambda x:-x[0])[0][2]
edges=sorted([(np.linalg.norm(q-v[0]),q-v[0])for q in corners if np.linalg.norm(q-v[0])>1e-4],key=lambda x:x[0])
up=edges[0][1]/edges[0][0]
if up[1]<0:up=-up
right=np.cross(up,normal);right/=np.linalg.norm(right)
meta=dict(center=rounded(center),normal=rounded(normal),up=rounded(up),right=rounded(right),width=round(edges[1][0],6),height=round(edges[0][0],6),beam=[0,4.23,0])
(OUT/'helldivers/equipment_beacon.json').write_text(json.dumps(meta,indent=2))
print('screen frame',meta)
print('written',out,out.stat().st_size)
