"""Union of four overlapping cubes into one closed octagonal prism; bakes original planar colors."""
def reduce_models(workspace,names,out):
    import pathlib,json,collections,copy,math,io,base64,sys,uuid
    sys.path.insert(0,str(workspace/'work/survival06/python_libs'))
    import numpy as np
    from PIL import Image
    OUT=out;OUT.mkdir(parents=True,exist_ok=True)
    F={'north':[4,0,2,6],'south':[1,5,7,3],'east':[5,4,6,7],'west':[0,1,3,2],'up':[2,3,7,6],'down':[0,4,5,1]}
    def rotation(e):
     r=np.eye(3)
     for ax,angle in enumerate(e.get('rotation',[0,0,0])):
      a,b=(ax+1)%3,(ax+2)%3;c=math.cos(math.radians(angle));s=math.sin(math.radians(angle));q=np.eye(3);q[a,a]=q[b,b]=c;q[a,b]=-s;q[b,a]=s;r=q@r
     return r
    def points(e):
     import itertools
     o=np.array(e['origin'])
     return (np.array(list(itertools.product(*zip(e['from'],e['to']))))-o)@rotation(e).T+o
    def faces(e):
     ps=points(e)
     for name,ids in F.items():
      f=e['faces'][name]
      if f.get('texture') is None:continue
      u,v,x,y=f['uv'];uv=np.array([[u,y],[x,y],[x,v],[u,v]])
      n=f.get('rotation',0)//90
      if n:uv=np.roll(uv,n,axis=0)
      yield ps[ids],uv,int(f['texture'])
    def count(e):
     if e['type']=='mesh':return sum(len(f['vertices'])-2 for f in e['faces'].values() if f.get('texture') is not None)
     return 2*sum(f.get('texture') is not None for f in e.get('faces',{}).values()) if e['type']=='cube' else 0
    reports=[]
    for name in names:
     source=(workspace/"outputs/当前交付/模型/单独炮台")/(name+'.bbmodel');original=json.loads(source.read_text(encoding='utf8'));d=copy.deepcopy(original)
     parents={}
     def walk(rows,parent=None):
      for row in rows:
       if isinstance(row,str):parents[row]=parent
       else:walk(row['children'],row['uuid'])
     walk(d['outliner'])
     buckets=collections.defaultdict(list)
     for e in d['elements']:
      if e['type']=='cube':buckets[str([parents[e['uuid']],e['from'],e['to'],e['origin']])].append(e)
     textures=[np.array(Image.open(io.BytesIO(base64.b64decode(t['source'].split(',')[1]))).convert('RGBA')) for t in d['textures']]
     atlas=np.zeros((4608,2048,4),dtype=np.uint8);tile=0;replacements={};removed=set();group_report=[]
     for group in buckets.values():
      if len(group)!=4:continue
      rotations=np.array([e.get('rotation',[0,0,0]) for e in group]);axes=np.flatnonzero(np.max(abs(rotations),axis=0)>1e-5)
      if len(axes)!=1:continue
      axis=int(axes[0]);ab=[i for i in range(3) if i!=axis];allps=np.concatenate([points(e) for e in group]);lo,hi=allps[:,axis].min(),allps[:,axis].max()
      xy=sorted(set(tuple(round(float(v),4) for v in p[ab]) for p in allps))
      def cross(o,a,b):return (a[0]-o[0])*(b[1]-o[1])-(a[1]-o[1])*(b[0]-o[0])
      low=[];high=[]
      for p in xy:
       while len(low)>1 and cross(low[-2],low[-1],p)<=.0002:low.pop()
       low.append(p)
      for p in reversed(xy):
       while len(high)>1 and cross(high[-2],high[-1],p)<=.0002:high.pop()
       high.append(p)
      hull=low[:-1]+high[:-1]
      if len(hull)!=8:continue
      verts=[]
      for z in [lo,hi]:
       for p in hull:
        v=np.zeros(3);v[axis]=z;v[ab]=p;verts.append(v)
      verts=np.array(verts);center=verts.mean(axis=0);polys=[list(range(8)),list(range(8,16))]+[[i,(i+1)%8,(i+1)%8+8,i+8] for i in range(8)]
      samples=list(f for e in group for f in faces(e));newfaces={}
      for ids in polys:
       pts=verts[ids];normal=np.cross(pts[1]-pts[0],pts[2]-pts[0]);normal/=np.linalg.norm(normal)
       if np.dot(normal,pts.mean(axis=0)-center)<0:ids.reverse();pts=verts[ids];normal=-normal
       u=pts[1]-pts[0];u/=np.linalg.norm(u);v=np.cross(normal,u)
       co=np.stack([pts@u,pts@v],axis=1);mn=co.min(axis=0);span=co.max(axis=0)-mn;span=np.maximum(span,1e-8)
       tx=(tile%32)*64;ty=(tile//32)*64;tile+=1
       yy,xx=np.mgrid[0:64,0:64];q=mn+np.stack([xx,yy],axis=-1)/63*span;pixelpts=pts[0]+(q[:,:,0]-pts[0]@u)[:,:,None]*u+(q[:,:,1]-pts[0]@v)[:,:,None]*v
       patch=np.zeros((64,64,4),dtype=np.uint8);distance=np.full((64,64),np.inf)
       for ps,uv,tex in samples:
        n=np.cross(ps[1]-ps[0],ps[2]-ps[0]);n/=np.linalg.norm(n)
        if abs(np.dot(n,normal))<.99999 or abs(np.dot(ps[0]-pts[0],normal))>.0003:continue
        b=ps[1]-ps[0];c=ps[3]-ps[0];delta=pixelpts-ps[0];s=delta@b/np.dot(b,b);t=delta@c/np.dot(c,c)
        dist=np.maximum(0,-s)+np.maximum(0,s-1)+np.maximum(0,-t)+np.maximum(0,t-1);mask=dist<distance
        mapped=uv[0]+s[:,:,None]*(uv[1]-uv[0])+t[:,:,None]*(uv[3]-uv[0]);im=textures[tex];ix=np.floor(mapped[:,:,0]).astype(int).clip(0,im.shape[1]-1);iy=np.floor(mapped[:,:,1]).astype(int).clip(0,im.shape[0]-1)
        patch[mask]=im[iy[mask],ix[mask]];distance[mask]=dist[mask]
       assert np.isfinite(distance).all(),(name,group[0]['name'],ids)
       atlas[ty:ty+64,tx:tx+64]=patch
       uv=(co-mn)/span*63+[tx+.01,ty+.01]
       newfaces['f'+str(len(newfaces))]={'vertices':['v'+str(i) for i in ids],'uv':{'v'+str(i):p.tolist() for i,p in zip(ids,uv)},'texture':len(textures)}
      e=copy.deepcopy(group[0]);e.update(type='mesh',name=e['name'].rsplit('_',1)[0]+'_八棱重布线',origin=[0,0,0],rotation=[0,0,0],vertices={'v'+str(i):p.tolist() for i,p in enumerate(verts)},faces=newfaces)
      for k in ['from','to','box_uv','autouv']:e.pop(k,None)
      # Each edge has two incident faces with opposite winding.
      edges=collections.defaultdict(list)
      for f in newfaces.values():
       vs=f['vertices']
       for a,b in zip(vs,vs[1:]+vs[:1]):edges[tuple(sorted([a,b]))].append((a,b))
      assert all(len(es)==2 and es[0]==es[1][::-1] for es in edges.values())
      replacements[e['uuid']]=e;removed.update(g['uuid'] for g in group[1:])
      group_report.append({'part':e['name'],'before':48,'after':count(e)})
     d['elements']=[replacements.get(e['uuid'],e) for e in d['elements'] if e['uuid'] not in removed]
     def prune(rows):
      result=[]
      for row in rows:
       if isinstance(row,str):
        if row not in removed:result.append(row)
       else:row['children']=prune(row['children']);result.append(row)
      return result
     d['outliner']=prune(d['outliner'])
     buf=io.BytesIO();Image.fromarray(atlas).save(buf,format='PNG')
     t=copy.deepcopy(d['textures'][0]);t.update(name=name+'_枪体重布线.png',uuid=str(uuid.uuid4()),id=str(len(d['textures'])),source='data:image/png;base64,'+base64.b64encode(buf.getvalue()).decode(),relative_path='',path='',saved=False)
     d['textures'].append(t);d['name']=name+'_枪体减面'
     # All shared meshes, locators, rig and animation records stay byte-for-byte equivalent as JSON objects.
     assert [e for e in d['elements'] if e['type']=='mesh' and e['name'].startswith('共用_')]==[e for e in original['elements'] if e['type']=='mesh' and e['name'].startswith('共用_')]
     assert d.get('groups')==original.get('groups') and d.get('animations')==original.get('animations')
     path=OUT/(name+'_减面.bbmodel');path.write_text(json.dumps(d,ensure_ascii=False,separators=(',',':')),encoding='utf8')
     body=lambda x:sum(count(e) for e in x['elements'] if not e['name'].startswith('共用_'))
     row={'model':name,'body_before':body(original),'body_after':body(d),'total_before':sum(map(count,original['elements'])),'total_after':sum(map(count,d['elements'])),'closed_prisms':len(group_report),'parts':group_report}
     reports.append(row);print({k:v for k,v in row.items() if k!='parts'},flush=True)
    (out/'octagonal-report.json').write_text(json.dumps(reports,ensure_ascii=False,indent=2),encoding='utf8')
