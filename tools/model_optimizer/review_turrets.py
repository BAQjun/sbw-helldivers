"""Validate candidate rigs, topology and UVs, then render matched before/after views."""
import argparse,base64,collections,copy,io,itertools,json,math,pathlib,sys
from other_turrets import count,topology

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--workspace',type=pathlib.Path,required=True);ap.add_argument('--models',type=pathlib.Path,required=True);ap.add_argument('--previews',type=pathlib.Path,required=True);args=ap.parse_args()
    sys.path.insert(0,str(args.workspace/'work/survival06/python_libs'))
    import numpy as np
    from PIL import Image,ImageDraw,ImageFont
    args.previews.mkdir(parents=True,exist_ok=True)
    fontpath=pathlib.Path('C:/Windows/Fonts/msyh.ttc')
    font=ImageFont.truetype(str(fontpath),22) if fontpath.exists() else ImageFont.load_default()
    faceids={'north':[4,0,2,6],'south':[1,5,7,3],'east':[5,4,6,7],'west':[0,1,3,2],'up':[2,3,7,6],'down':[0,4,5,1]}
    def geometry(d,body=True):
        images=[np.array(Image.open(io.BytesIO(base64.b64decode(t['source'].split(',')[1]))).convert('RGBA')) for t in d['textures']]
        parts=[]
        for e in d['elements']:
            if e['type'] not in ('cube','mesh') or not e.get('visibility',True):continue
            if body and e['name'].startswith(('共用_','结构_')):continue
            origin=np.array(e['origin'],dtype=float)
            if e['type']=='mesh':
                ids={k:i for i,k in enumerate(e['vertices'])};ps=np.array(list(e['vertices'].values()),dtype=float)
                faces=[([ids[k] for k in f['vertices']],[f['uv'][k] for k in f['vertices']],int(f['texture'])) for f in e['faces'].values() if f.get('texture') is not None]
            else:
                ps=np.array(list(itertools.product(*zip(e['from'],e['to']))),dtype=float)-origin;faces=[]
                for name,keys in faceids.items():
                    f=e['faces'][name]
                    if f.get('texture') is None:continue
                    u,v,x,y=f['uv'];uv=np.array([[u,y],[x,y],[x,v],[u,v]],dtype=float);n=f.get('rotation',0)//90
                    if n:uv=np.roll(uv,n,axis=0)
                    faces.append((keys,uv,int(f['texture'])))
            for axis,angle in enumerate(e.get('rotation',[0,0,0])):
                a,b=(axis+1)%3,(axis+2)%3;c=math.cos(math.radians(angle));s=math.sin(math.radians(angle));ps[:,[a,b]]=ps[:,[a,b]]@np.array([[c,s],[-s,c]])
            parts.append((ps+origin,faces))
        return parts,images
    def render(parts,images,cam,target,scale,size=(660,520)):
        w,h=size;canvas=np.full((h,w,3),[39,43,48],dtype=np.uint8);depth=np.full((h,w),-np.inf);silhouette=np.zeros((h,w),dtype=bool)
        norm=lambda a:a/max(1e-15,np.linalg.norm(a))
        front=norm(np.array(cam,dtype=float));right=norm(np.cross([0,1,0],front));up=np.cross(front,right);light=norm(np.array([.25,.7,.65]))
        for points,faces in parts:
            pts=points-target;screen=np.column_stack((w/2+scale*(pts@right),h/2-scale*(pts@up),pts@front))
            for ids,uv,texid in faces:
                for j in range(1,len(ids)-1):
                    idx=[ids[0],ids[j],ids[j+1]];A,B,C=screen[idx];x0=max(0,int(min(A[0],B[0],C[0])));x1=min(w,int(max(A[0],B[0],C[0]))+1);y0=max(0,int(min(A[1],B[1],C[1])));y1=min(h,int(max(A[1],B[1],C[1]))+1)
                    if x1<=x0 or y1<=y0:continue
                    b=B-A;c=C-A;det=b[0]*c[1]-b[1]*c[0]
                    if det>=-1e-8:continue
                    y,x=np.ogrid[y0:y1,x0:x1];x=x+.5-A[0];y=y+.5-A[1];s=(x*c[1]-y*c[0])/det;t=(y*b[0]-x*b[1])/det;z=A[2]+s*b[2]+t*c[2];ds=depth[y0:y1,x0:x1];mask=(s>=0)&(t>=0)&(s+t<=1)&(z>ds)
                    if not mask.any():continue
                    ua,ub,uc=np.array([uv[0],uv[j],uv[j+1]]);U=np.floor(ua+s[mask,None]*(ub-ua)+t[mask,None]*(uc-ua)).astype(int);tex=images[texid];color=tex[U[:,1].clip(0,tex.shape[0]-1),U[:,0].clip(0,tex.shape[1]-1)]
                    visible=color[:,3]>127;locations=np.where(mask);mask[locations]=visible
                    if not visible.any():continue
                    color=color[visible,:3];ds[mask]=z[mask];normal=norm(np.cross(pts[idx[1]]-pts[idx[0]],pts[idx[2]]-pts[idx[0]]));shade=.80+.20*max(0,np.dot(normal,light));canvas[y0:y1,x0:x1][mask]=(color*shade).astype('uint8');silhouette[y0:y1,x0:x1][mask]=True
        return Image.fromarray(canvas),silhouette
    reports=[]
    for p in sorted(args.models.glob('*.bbmodel')):
        name=p.stem.removesuffix('_减面');folder='迫击炮' if '迫击' in name else '单独炮台';source=args.workspace/'outputs/当前交付/模型'/folder/(name+'.bbmodel');orig=json.loads(source.read_text(encoding='utf8'));d=json.loads(p.read_text(encoding='utf8'))
        assert d.get('groups')==orig.get('groups') and d.get('animations')==orig.get('animations')
        old={e['uuid']:e for e in orig['elements']};ids={e['uuid'] for e in d['elements']}
        def walk(rows):
            for row in rows:
                if isinstance(row,str):assert row in ids
                else:walk(row['children'])
        walk(d['outliner']);changed=[]
        for e in d['elements']:
            o=old[e['uuid']]
            if e['type']=='locator' or e['name'].startswith(('共用_','结构_')):assert e==o
            if e==o:continue
            if e['type']!='mesh':continue
            for f in e['faces'].values():
                assert len(set(f['vertices']))==len(f['vertices'])
                assert 0<=int(f['texture'])<len(d['textures'])
                assert all(v in e['vertices'] and all(math.isfinite(x) for x in f['uv'][v]) for v in f['vertices'])
            if o['type']=='mesh':assert topology(e)==topology(o),(name,e['name'],'new open boundary')
            else:assert topology(e)=={},(name,e['name'],'prism not closed')
            changed.append(e['name'])
        report=dict(model=name,before=sum(map(count,orig['elements'])),after=sum(map(count,d['elements'])),body_before=sum(count(e) for e in orig['elements'] if not e['name'].startswith(('共用_','结构_'))),body_after=sum(count(e) for e in d['elements'] if not e['name'].startswith(('共用_','结构_'))),changed_parts=len(changed),views=[])
        before,tex0=geometry(orig);after,tex1=geometry(d);ps=np.concatenate([a for a,f in before]);target=(ps.min(axis=0)+ps.max(axis=0))/2;extent=np.linalg.norm(ps.max(axis=0)-ps.min(axis=0));scale=470/extent
        sheet=Image.new('RGB',(1320,1120),(39,43,48));draw=ImageDraw.Draw(sheet)
        for row,cam in enumerate([(1,.42,-.9),(-1,.4,.8)]):
            a,ma=render(before,tex0,cam,target,scale);b,mb=render(after,tex1,cam,target,scale);sheet.paste(a,(0,row*560+40));sheet.paste(b,(660,row*560+40));report['views'].append(dict(silhouette_difference=int(np.count_nonzero(ma^mb)),original_pixels=int(ma.sum())))
        draw.text((20,8),name+'  原模型',font=font,fill='white');draw.text((680,8),name+'  减面后',font=font,fill='white');sheet.save(args.previews/(name+'_对比.png'))
        reports.append(report);print(json.dumps(report,ensure_ascii=True),flush=True)
    (args.models/'减面对照.json').write_text(json.dumps(reports,ensure_ascii=False,indent=2),encoding='utf8')

if __name__=='__main__':main()
