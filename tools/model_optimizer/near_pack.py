"""Stage reversible pod/laser geometry overrides, retaining all live animation bones."""
import argparse,copy,json,math,pathlib,sys,zipfile

ap=argparse.ArgumentParser();ap.add_argument('--workspace',type=pathlib.Path,required=True);ap.add_argument('--repo',type=pathlib.Path,required=True);a=ap.parse_args()
w=a.workspace/'work/structural-near-test';assets=a.repo/'src/main/resources/assets/sbw_helldiver';dest=w/'pack/assets/sbw_helldiver';sys.path.insert(0,str(a.workspace/'work/survival06/python_libs'))
from PIL import Image
from clean_side_markings import repair
def read(p):return json.loads(p.read_text(encoding='utf8'))
def write(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,separators=(',',':')),encoding='utf8')
def rotate(v,angles):
    x,y,z=v;aa,b,c=[math.radians(q) for q in angles];y,z=y*math.cos(aa)-z*math.sin(aa),y*math.sin(aa)+z*math.cos(aa);x,z=x*math.cos(b)+z*math.sin(b),-x*math.sin(b)+z*math.cos(b);x,y=x*math.cos(c)-y*math.sin(c),x*math.sin(c)+y*math.cos(c);return [x,y,z]
def mesh_rows(d,meshes_only=False):
    parents={};groups={g['uuid']:g['name'] for g in d['groups']}
    def walk(rows,parent=None):
        for row in rows:
            if isinstance(row,str):parents[row]=parent
            else:walk(row.get('children',[]),groups[row['uuid']])
    walk(d['outliner']);rows={}
    for e in d['elements']:
        if e.get('type') not in ('mesh','cube') or meshes_only and e.get('type')!='mesh':continue
        name=parents[e['uuid']];row=rows.setdefault(name,{'positions':[],'normals':[],'uvs':[],'polys':[],'normalized_uvs':False});ori=e.get('origin',[0,0,0]);rot=e.get('rotation',[0,0,0])
        if e['type']=='mesh':
            vs={k:[x+y for x,y in zip(rotate(v,rot),ori)] for k,v in e['vertices'].items()};fs=[(f['vertices'],[f['uv'][k] for k in f['vertices']],f.get('texture')) for f in e['faces'].values()]
        else:
            aa=e['from'];bb=e['to'];vs={str(i):[x+y for x,y in zip(rotate([q[j]-ori[j] for j in range(3)],rot),ori)] for i,q in enumerate(( [x,y,z] for x in [aa[0],bb[0]] for y in [aa[1],bb[1]] for z in [aa[2],bb[2]] ))};fs=[]
            for face,ids in {'north':[4,0,2,6],'south':[1,5,7,3],'east':[5,4,6,7],'west':[0,1,3,2],'up':[2,3,7,6],'down':[0,4,5,1]}.items():
                f=e['faces'][face];u,v,x,y=f['uv'];uv=[[u,y],[x,y],[x,v],[u,v]];n=f.get('rotation',0)//90
                if n:uv=uv[-n:]+uv[:-n]
                fs.append(([str(i) for i in ids],uv,f.get('texture')))
        for keys,uv,tex in fs:
            if tex is None:continue
            for j in range(1,len(keys)-1):
                order=[0,j+1,j];ps=[vs[keys[i]] for i in order];ps=[[-p[0],p[1],p[2]] for p in ps];u=[ps[1][i]-ps[0][i] for i in range(3)];v=[ps[2][i]-ps[0][i] for i in range(3)];n=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]];length=math.sqrt(sum(x*x for x in n))
                if length<1e-9:continue
                ni=len(row['normals']);row['normals'].append([x/length for x in n]);poly=[]
                for p,i in zip(ps,order):
                    pi=len(row['positions']);ui=len(row['uvs']);row['positions'].append(p);row['uvs'].append([int(tex)*2048+uv[i][0],d['resolution']['height']-uv[i][1]]);poly.append([pi,ni,ui])
                row['polys'].append(poly)
    return rows

pod=read(w/'pod-output/bedrock/vehicle/hellpod_v3.geo.json')
pod_meshes=mesh_rows(read(w/'pod.bbmodel'),True)
for b in pod['minecraft:geometry'][0]['bones']:
    if b['name'] in pod_meshes:b['poly_mesh']=pod_meshes[b['name']]
write(dest/'models/bedrock/vehicle/hellpod_v3.geo.json',pod)
laser=read(assets/'models/bedrock/vehicle/laser_hellpod_v1.geo.json');before=copy.deepcopy(laser)
bones={b['name']:b for b in laser['minecraft:geometry'][0]['bones']}
for name,m in mesh_rows(read(w/'laser.bbmodel')).items():
    name='ls_'+name[6:] if name.startswith('laser_') else name
    if name.startswith('ls_displays'):continue
    b=bones[name];b.pop('cubes',None)
    for p in m['positions']:p[1]-=3.85
    b['poly_mesh']=m
for p in pod['minecraft:geometry'][0]['bones']:
    if p['name'] not in bones:continue
    b=bones[p['name']];b.pop('cubes',None);b.pop('poly_mesh',None)
    if 'cubes' in p:
        b['cubes']=copy.deepcopy(p['cubes'])
        for c in b['cubes']:
            for f in c.get('uv',{}).values():f['uv'][0]+=4096
    if 'poly_mesh' in p:
        b['poly_mesh']=copy.deepcopy(p['poly_mesh'])
        for uv in b['poly_mesh']['uvs']:uv[0]+=4096;uv[1]+=2560
write(dest/'models/bedrock/vehicle/laser_hellpod_v1.geo.json',laser)
for suffix in ['','_emissive']:
    p=assets/f'textures/bedrock/vehicle/laser_hellpod_v1{suffix}.png';im=Image.open(p).convert('RGBA');im=repair(im) if not suffix else im;tp=assets/f'textures/bedrock/vehicle/hellpod_v3{suffix}.png';tile=Image.open(tp).convert('RGBA') if tp.exists() else Image.new('RGBA',(2048,2048));im.paste((0,0,0,0),(4096,0,6144,4608));im.paste(tile,(4096,0));out=dest/f'textures/bedrock/vehicle/laser_hellpod_v1{suffix}.png';out.parent.mkdir(parents=True,exist_ok=True);im.save(out)
def count(d):return sum(sum((len(c.get('uv',{}))*2 if isinstance(c.get('uv'),dict) else 12) for c in b.get('cubes',[]))+sum(len(f)-2 for f in b.get('poly_mesh',{}).get('polys',[])) for b in d['minecraft:geometry'][0]['bones'])
rig=lambda d:[{k:v for k,v in b.items() if k not in ['cubes','poly_mesh']} for b in d['minecraft:geometry'][0]['bones']]
assert rig(laser)==rig(before)
report={'laser_with_pod_before':count(before),'laser_with_pod_after':count(laser),'pod_before':count(read(assets/'models/bedrock/vehicle/hellpod_v3.geo.json')),'pod_after':count(pod),'runtime_rig_unchanged':True}
write(w/'counts.json',report);write(w/'pack/pack.mcmeta',{'pack':{'pack_format':34,'supported_formats':{'min_inclusive':15,'max_inclusive':34},'description':'Pod + laser sentry near-model structural optimization experiment'}})
out=a.workspace/'outputs/当前交付/测试资源/空投仓与激光炮台_结构优化测试.zip';out.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
    for p in (w/'pack').rglob('*'):
        if p.is_file():z.write(p,p.relative_to(w/'pack').as_posix())
print(json.dumps(report,indent=2))
