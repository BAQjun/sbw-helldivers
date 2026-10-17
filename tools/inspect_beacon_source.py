"""Read the original beacon glTF; inspect its rigid segments and game animation poses."""
import json, struct
from pathlib import Path
import numpy as np

ROOT=Path('D:/Project/sbw_helldiver/.codex/beacon')
FILE=ROOT/'extracted/0x16f397ca5f51f271.unit.glb'
blob=FILE.read_bytes(); jlen=struct.unpack_from('<I',blob,12)[0]
doc=json.loads(blob[20:20+jlen]); raw=blob[28+jlen:]
def accessor(i):
 a=doc['accessors'][i]; v=doc['bufferViews'][a['bufferView']]
 n={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}[a['type']]
 dtype={5126:'<f4',5125:'<u4',5123:'<u2',5121:'u1'}[a['componentType']]
 item=np.dtype(dtype).itemsize
 ar=np.ndarray((a['count'],n),dtype=dtype,buffer=raw,offset=v.get('byteOffset',0)+a.get('byteOffset',0),strides=(v.get('byteStride',item*n),item)).copy()
 if a.get('normalized'):ar=ar.astype(float)/(255 if item==1 else 65535)
 return ar
def qmatrix(q):
 # Compressed source rotations contain quantization error. glTF rotation
 # quaternions represent unit rotations; normalize before building the matrix.
 q=np.asarray(q,dtype=np.float64);q/=np.linalg.norm(q)
 x,y,z,w=q;return np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)], [2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)], [2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])
parents={c:i for i,n in enumerate(doc['nodes']) for c in n.get('children',[])}
def pose(animation=None,t=0):
 overrides={}
 if animation is not None:
  for c in animation['channels']:
   s=animation['samplers'][c['sampler']]; times=accessor(s['input']).reshape(-1); vals=accessor(s['output']);i=min(np.searchsorted(times,t,side='right')-1,len(times)-1);i=max(i,0)
   # Source is already sampled at 30 Hz; preserve exact samples.
   overrides[c['target']['node'],c['target']['path']]=vals[i]
 result=[]
 for i,n in enumerate(doc['nodes']):
  T=overrides.get((i,'translation'),n.get('translation',[0,0,0]));R=overrides.get((i,'rotation'),n.get('rotation',[0,0,0,1]));S=overrides.get((i,'scale'),n.get('scale',[1,1,1]))
  m=np.eye(4);m[:3,:3]=qmatrix(R)@np.diag(S);m[:3,3]=T;result.append(m)
 memo={}
 def world(i):
  if i not in memo:memo[i]=world(parents[i])@result[i] if i in parents else result[i]
  return memo[i]
 return np.array([world(i) for i in range(len(result))])
skin=doc['skins'][0]; ibm=accessor(skin['inverseBindMatrices']).reshape(-1,4,4).transpose(0,2,1)
def skin_matrices(animation=None,t=0):return pose(animation,t)[skin['joints']]@ibm
def mesh_pose(primitive,animation=None,t=0):
 at=primitive['attributes'];v=accessor(at['POSITION']);js=accessor(at['JOINTS_0']).astype(int);w=accessor(at['WEIGHTS_0']); mats=skin_matrices(animation,t)[js]
 vh=np.column_stack((v,np.ones(len(v))));return np.einsum('nvij,nj,nv->ni',mats,vh,w)[:,:3]

if __name__=='__main__':
 ROOT.joinpath('source_textures').mkdir(exist_ok=True)
 for im in doc.get('images',[]):
  bv=doc['bufferViews'][im['bufferView']];off=bv.get('byteOffset',0);name=im['name']+('' if im['name'].endswith('.dds') else '.png')
  (ROOT/'source_textures'/name).write_bytes(raw[off:off+bv['byteLength']])
 print('Meshes',[(p['material'],len(accessor(p['attributes']['POSITION'])),len(accessor(p['indices']))//3) for p in doc['meshes'][0]['primitives']])
 for p in doc['meshes'][0]['primitives']:
  at=p['attributes'];w=accessor(at['WEIGHTS_0']);js=accessor(at['JOINTS_0']).astype(int);dominant=js[np.arange(len(js)),w.argmax(1)]
  print('Weight minimum strongest',float(w.max(1).min()),'bone counts',[(doc['nodes'][skin['joints'][i]]['name'],int((dominant==i).sum())) for i in np.unique(dominant)])
 for a in [None]+doc.get('animations',[]):
  length=0 if a is None else max(float(accessor(s['input']).max()) for s in a['samplers'])
  print('Animation',a['name'] if a else 'bind',length)
  for t in sorted({0,length*.5,length}):
   v=np.concatenate([mesh_pose(p,a,t) for p in doc['meshes'][0]['primitives']]);print(' t',t,'bounds',v.min(0).round(6).tolist(),v.max(0).round(6).tolist())
