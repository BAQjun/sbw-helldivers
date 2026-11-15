"""Reconstruct planar panels by surface plane AND texture mapping, never across a UV fold.

Flat rebuilt panels receive their own vertices; authored curved parts retain their
original normal seams. No approximate curved edge collapse is performed.
"""
import argparse,base64,collections,copy,io,json,math,pathlib

def sub(a,b):return tuple(x-y for x,y in zip(a,b))
def dot(a,b):return sum(x*y for x,y in zip(a,b))
def cross(a,b):return (a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])
def length(a):return math.sqrt(dot(a,a))
def count(e):
 return sum(max(0,len(f['vertices'])-2)for f in e.get('faces',{}).values()) if e.get('type')=='mesh' else 2*sum(f.get('texture')is not None for f in e.get('faces',{}).values())
def mapping(p,uv,axes):
 a,b,c=p;u=sub(b,a);v=sub(c,a);x,y=axes;det=u[x]*v[y]-u[y]*v[x]
 if abs(det)<1e-14:return None
 rows=[]
 for j in range(2):
  s,t=uv[1][j]-uv[0][j],uv[2][j]-uv[0][j];gx=(s*v[y]-t*u[y])/det;gy=(t*u[x]-s*v[x])/det;rows.append((gx,gy,uv[0][j]-gx*a[x]-gy*a[y]))
 return rows

def evaluate(m,p,axes):return [r[0]*p[axes[0]]+r[1]*p[axes[1]]+r[2]for r in m]
def solid_uv(uv,texture,images,dims):
 if images is None or texture is None:return None
 im=images[int(texture)];sx,sy=im.width/dims[int(texture)][0],im.height/dims[int(texture)][1]
 l,r=math.floor(min(p[0]*sx for p in uv)),math.floor(max(p[0]*sx for p in uv));t,b=math.floor(min(p[1]*sy for p in uv)),math.floor(max(p[1]*sy for p in uv))
 if l<0 or t<0 or r>=im.width or b>=im.height:return None
 ext=im.crop((l,t,r+1,b+1)).getextrema()
 if not all(a==b for a,b in ext):return None
 return tuple(a for a,b in ext),[(l+.5)/sx,(t+.5)/sy]

def area2(a,b,c):return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])
def triangulate(poly):
 # Ear clipping with explicit winding and containment checks. Patches with holes
 # or branched boundaries are retained verbatim by the caller.
 ids=list(range(len(poly)));out=[];sign=1 if sum(poly[i][0]*poly[(i+1)%len(poly)][1]-poly[(i+1)%len(poly)][0]*poly[i][1]for i in ids)>0 else -1
 while len(ids)>3:
  found=False
  for j,b in enumerate(ids):
   a,c=ids[j-1],ids[(j+1)%len(ids)]
   if sign*area2(poly[a],poly[b],poly[c])<=1e-12:continue
   if any(min(sign*area2(poly[a],poly[b],poly[k]),sign*area2(poly[b],poly[c],poly[k]),sign*area2(poly[c],poly[a],poly[k]))>=-1e-12 for k in ids if k not in (a,b,c)):continue
   out.append([a,b,c]);ids.pop(j);found=True;break
  if not found:return None
 out.append(ids);return out

def boundary_loop(triangles):
 edges=collections.defaultdict(list)
 for t in triangles:
  p=t['p']
  for a,b in zip(p,p[1:]+p[:1]):edges[tuple(sorted((a,b)))].append((a,b))
 if any(len(v)>2 or(len(v)==2 and v[0]!=v[1][::-1])for v in edges.values()):return None
 boundary=[v[0]for v in edges.values()if len(v)==1];nexts={}
 for a,b in boundary:
  if a in nexts:return None
  nexts[a]=b
 if not nexts:return None
 start=next(iter(nexts));loop=[start];point=nexts[start]
 while point!=start:
  if point not in nexts or point in loop:return None
  loop.append(point);point=nexts[point]
 if len(loop)!=len(boundary):return None # Holes and disconnected loops stay authored.
 # Collinear edge vertices do not define the visible panel outline.
 changed=True
 while changed and len(loop)>3:
  changed=False
  for j,p in enumerate(loop):
   u,v=sub(p,loop[j-1]),sub(loop[(j+1)%len(loop)],p)
   if dot(u,v)>0 and length(cross(u,v))<=1e-9*max(1,length(u)*length(v)):
    loop.pop(j);changed=True;break
 return loop

def rebuild_element(element,images=None,dims=None):
 if element.get('type')!='mesh':return copy.deepcopy(element),{'patches':0,'removed':0}
 original=copy.deepcopy(element);groups=collections.defaultdict(list);unchanged=[]
 for key,f in original['faces'].items():
  vs=f['vertices']
  if len(vs)<3:unchanged.append((key,f));continue
  for j in range(1,len(vs)-1):
   keys=[vs[0],vs[j],vs[j+1]];p=[tuple(original['vertices'][v])for v in keys];uv=[f['uv'][v]for v in keys];n=cross(sub(p[1],p[0]),sub(p[2],p[0]));size=length(n)
   face={**f,'vertices':keys,'uv':{v:copy.deepcopy(f['uv'][v])for v in keys}}
   if size<1e-12:unchanged.append((key+'_'+str(j),face));continue
   n=tuple(x/size for x in n);plane=dot(n,p[0]);axes=tuple(k for k in range(3)if k!=max(range(3),key=lambda k:abs(n[k])));m=mapping(p,uv,axes);solid=solid_uv(uv,f.get('texture'),images,dims)
   sig=('solid',solid[0])if solid else('mapped',tuple(round(x,5)for row in m for x in row))
   # Full affine maps, not just matching shared UV endpoints, define a UV region.
   k=(tuple(round(x,5)for x in n),round(plane,5),axes,f.get('texture'),sig,json.dumps({x:v for x,v in f.items()if x not in ('vertices','uv','texture')},sort_keys=True))
   groups[k].append({'p':p,'uv':uv,'n':n,'plane':plane,'map':m,'solid':solid,'face':face,'id':key+'_'+str(j),'area':size/2,'axes':axes})
 result=copy.deepcopy(original);result['faces']=dict(unchanged);counter=0;patches=0;removed=0
 # Partition further into edge-connected regions so disconnected panels never join.
 for grouped in groups.values():
  byedge=collections.defaultdict(list)
  for i,t in enumerate(grouped):
   for a,b in zip(t['p'],t['p'][1:]+t['p'][:1]):byedge[tuple(sorted((a,b)))].append(i)
  graph=collections.defaultdict(set)
  for ids in byedge.values():
   if len(ids)==2:graph[ids[0]].add(ids[1]);graph[ids[1]].add(ids[0])
  unseen=set(range(len(grouped)))
  while unseen:
   component=[];stack=[unseen.pop()]
   while stack:
    i=stack.pop();component.append(grouped[i])
    for j in graph[i]&unseen:unseen.remove(j);stack.append(j)
   loop=boundary_loop(component)if len(component)>1 else None;t=component[0];axes=t['axes'];indices=triangulate([[p[k]for k in axes]for p in loop])if loop else None
   valid=bool(indices) and len(indices)<len(component)
   if valid:
    area=sum(length(cross(sub(loop[b],loop[a]),sub(loop[c],loop[a])))/2 for a,b,c in indices);expected=sum(x['area']for x in component)
    valid=abs(area-expected)<=1e-7*max(1,expected) and all(abs(dot(t['n'],sub(p,loop[0])))<=1e-6 for p in loop)
   if valid and not t['solid']:
    valid=all(max(abs(u-v)for u,v in zip(evaluate(t['map'],p,axes),uv))<1e-5 for x in component for p,uv in zip(x['p'],x['uv']))
   if not valid:
    for x in component:result['faces'][x['id']]=x['face']
    continue
   # New per-panel vertices keep the broad face exactly flat under smooth shading;
   # curved faces retain authored vertices and their original smoothing seams.
   keys=[]
   for p in loop:
    key='panel_'+str(counter);counter+=1;result['vertices'][key]=list(p);keys.append(key)
   for ids in indices:
    if dot(cross(sub(loop[ids[1]],loop[ids[0]]),sub(loop[ids[2]],loop[ids[0]])),t['n'])<0:ids=ids[::-1]
    vs=[keys[i]for i in ids];face={**t['face'],'vertices':vs,'uv':{keys[i]:list(t['solid'][1])if t['solid']else evaluate(t['map'],loop[i],axes)for i in ids}};result['faces']['panel_face_'+str(counter)]=face;counter+=1
   patches+=1;removed+=len(component)-len(indices)
 # Preserve untouched native quads: triangulating them would also change the
 # weighting of smooth vertex normals despite identical positions and UVs.
 for key,face in original['faces'].items():
  ids=[key+'_'+str(j)for j in range(1,len(face['vertices'])-1)]
  if len(face['vertices'])==4 and all(k in result['faces']for k in ids):
   for k in ids:del result['faces'][k]
   result['faces'][key]=copy.deepcopy(face)
 used={v for f in result['faces'].values()for v in f['vertices']};result['vertices']={k:v for k,v in result['vertices'].items()if k in used}
 return result,{'patches':patches,'removed':removed}

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--input',required=True,type=pathlib.Path);ap.add_argument('--output',required=True,type=pathlib.Path);ap.add_argument('--manifest',required=True,type=pathlib.Path);a=ap.parse_args()
 if a.input.resolve()==a.output.resolve():ap.error('Input and output must be different directories')
 from PIL import Image
 report=[];cache={}
 for rel in json.loads(a.manifest.read_text('utf8')):
  original=json.loads((a.input/rel).read_text('utf8'));d=copy.deepcopy(original);images=[Image.open(io.BytesIO(base64.b64decode(t['source'].split(',')[1]))).convert('RGBA')for t in d['textures']];dims=[(t.get('uv_width',d['resolution']['width']),t.get('uv_height',d['resolution']['height']))for t in d['textures']];parts=[]
  for i,e in enumerate(d['elements']):
   if e.get('type')!='mesh':continue
   # Cache across identical rigs while preserving each model's texture indices.
   key=json.dumps([e['vertices'],e['faces'],[t['source']for t in d['textures']],dims],sort_keys=True)
   if key in cache:
    v,f,stats=cache[key];new={**e,'vertices':copy.deepcopy(v),'faces':copy.deepcopy(f)}
   else:
    new,stats=rebuild_element(e,images,dims);cache[key]=(new['vertices'],new['faces'],stats)
   d['elements'][i]=new;parts.append({'part':e['name'],'before':count(e),'after':count(new),**stats})
  before=sum(map(count,original['elements']));after=sum(map(count,d['elements']));d['name']=d['name'].replace('_近景减面','')+'_平直重构';dest=a.output/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(json.dumps(d,ensure_ascii=False,separators=(',',':')),'utf8');row={'model':rel,'before':before,'after':after,'reduction':round(100*(1-after/before),1),'parts':parts};report.append(row);print(json.dumps({k:v for k,v in row.items()if k!='parts'},ensure_ascii=True),flush=True)
 (a.output/'减面对照.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),'utf8')
if __name__=='__main__':main()
