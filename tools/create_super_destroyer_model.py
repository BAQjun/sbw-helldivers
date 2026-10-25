"""Prepare an editable distant Super Destroyer from the verified complete hull LOD.

Run with Blender --background --python this_script -- --reference extracted.glb
--output directory. The native reference is intentionally not distributed with
this tool. Geometry retains the measured silhouette; palette textures are clean.
"""
from __future__ import annotations
import argparse,base64,json,math,struct,uuid,zlib
from pathlib import Path

SIZE=256
TILE=64
# Shared palette: body, top, dark steel, recess, bevel, paint, paint shadow,
# radiator, armor panel, identification stripe, equipment, bridge, engine, lamp.
COLORS=[(69,76,78),(92,100,101),(45,52,55),(20,27,31),(121,128,126),
        (219,176,42),(150,120,30),(34,42,46),(75,83,86),(79,87,88),
        (53,63,68),(70,120,141),(94,202,248),(206,238,246),(171,64,40),(27,33,36)]

def png(path,pixels):
    def chunk(t,d):return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
    raw=b''.join(b'\0'+bytes(pixels[y*SIZE*4:(y+1)*SIZE*4]) for y in range(SIZE))
    path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',SIZE,SIZE,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))

def atlas(path):
    pix=bytearray(SIZE*SIZE*4);em=bytearray(SIZE*SIZE*4)
    for y in range(SIZE):
        for x in range(SIZE):
            k=(y//64)*4+x//64;u=x%64;v=y%64;c=COLORS[k]
            if k==7:
                if 5<=u<=58 and 5<=v<=58:c=(15,22,26) if v%8<3 else (63,70,73)
            if k==8:
                if u in (3,4,32,33,59,60) or v in (3,4,59,60):c=(39,46,49)
                elif v==6:c=(110,118,117)
            if k==9:
                if 16<=v<=19 and 21<=u<=44:c=COLORS[5]
                if v>=57:c=COLORS[2]
                if u in (2,61):c=(57,65,67)
            if k==10:
                if 5<=u<=58 and 8<=v<=55:c=(32,41,46)
                if u in (8,9,54,55) and 12<v<52:c=(99,112,114)
            if k==11:
                c=(30,58,71)
                if 7<u<57 and 10<v<54:c=(101,160,176)
                if u in (23,24,39,40):c=(25,37,43)
            if k==12:
                d=math.hypot(u-31.5,v-31.5)
                c=(165,230,255) if d<21 else (67,171,222)
                if d<10:c=(216,248,255)
            if k==15:
                c=COLORS[0]
                if 5<=u<=7:c=COLORS[5]
            a=(y*SIZE+x)*4;pix[a:a+4]=bytes((*c,255))
            ec=c if k in (11,12,13,14) else (0,0,0)
            em[a:a+4]=bytes((*ec,255))
    png(path/'舰体图集.png',pix);png(path/'舰体图集_自发光.png',em)

uid=lambda s:str(uuid.uuid5(uuid.NAMESPACE_URL,'sbw-super-destroyer-distant-v3/'+s))
def sub(a,b):return [a[i]-b[i] for i in range(3)]
def cross(a,b):return [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
def dot(a,b):return sum(x*y for x,y in zip(a,b))
def unit(a):
    l=math.sqrt(dot(a,a));return [x/l for x in a]

class Ship:
    def __init__(self):self.parts={};self.mounts={};self.pieces=0
    def add(self,group,vs,fs,mat=0,orient=True):
        self.pieces+=1
        p=self.parts.setdefault(group,{'v':[],'f':[]});base=len(p['v'])
        center=[sum(v[i] for v in vs)/len(vs)for i in range(3)]
        for n,f in enumerate(fs):
            f=list(f);m=mat[n] if isinstance(mat,list) else mat
            if orient:
                normal=cross(sub(vs[f[1]],vs[f[0]]),sub(vs[f[2]],vs[f[0]]))
                fc=[sum(vs[j][i] for j in f)/len(f)for i in range(3)]
                if dot(normal,sub(fc,center))<0:f.reverse()
            # Blockbench mesh faces support triangles and quads.
            parts=[f] if len(f)<=4 else [[f[0],f[j],f[j+1]]for j in range(1,len(f)-1)]
            normal=cross(sub(vs[f[1]],vs[f[0]]),sub(vs[f[2]],vs[f[0]]))
            axis=max(range(3),key=lambda k:abs(normal[k]))
            # A single chart per source polygon, shared by its triangles.
            # Long side panels retain horizontal paint stripes.
            def project(v):
                return (v[2],-v[1]) if axis==0 else ((v[0],v[2]) if axis==1 else (v[0],-v[1]))
            chart={j:project(vs[j])for j in f};mn=[min(v[k]for v in chart.values())for k in range(2)];mx=[max(v[k]for v in chart.values())for k in range(2)]
            uv={j:[(chart[j][k]-mn[k])/max(mx[k]-mn[k],1e-9)for k in range(2)]for j in f}
            for ff in parts:p['f'].append(([i+base for i in ff],m,[uv[j]for j in ff]))
        p['v'].extend(vs)
    def mount(self,name,p):self.mounts[name]=p

def add_hull_liners(ship, source, convert):
    """Closed, inset hull sections cover native LOD seams without covering rail gaps."""
    def cross2(a,b):return a[0]*b[1]-a[1]*b[0]
    def hull(points):
        pts=sorted(set((round(p[0],5),round(p[1],5))for p in points))
        if len(pts)<3:return []
        def turn(a,b,c):return cross2((b[0]-a[0],b[1]-a[1]),(c[0]-a[0],c[1]-a[1]))
        lo=[];hi=[]
        for v in pts:
            while len(lo)>1 and turn(lo[-2],lo[-1],v)<=0:lo.pop()
            lo.append(v)
        for v in reversed(pts):
            while len(hi)>1 and turn(hi[-2],hi[-1],v)<=0:hi.pop()
            hi.append(v)
        return lo[:-1]+hi[:-1]
    def section(y, materials, xmin, xmax):
        points=[]
        for f in source.polygons:
            if f.material_index not in materials:continue
            vv=[source.vertices[i].co for i in f.vertices]
            for a,b in zip(vv,vv[1:]+vv[:1]):
                if (a.y<=y<b.y)or(b.y<=y<a.y):
                    t=(y-a.y)/(b.y-a.y);x=a.x+t*(b.x-a.x);z=a.z+t*(b.z-a.z)
                    if xmin<=x<=xmax:points.append((x,z))
        return hull(points)
    specs=[
        ('前中梁',[-105,-100,-85,-50,-25,-5,3],{0,3},-11,11),
        ('中部机舱',[-21,-17,-2,14,43,52,64,66],{4},-30,30),
        ('后部主机舱',[68,73,89,109,118,123,127],{5},-23,23),
    ]
    for sign in (-1,1):
        specs.append(('前侧梁'+str(sign),[-105,-100,-85,-50,-25,-5,3],{0,3},12 if sign>0 else -22,22 if sign>0 else -12))
        specs.append(('侧发动机舱'+str(sign),[81,89,106,117,121,123],{5},34 if sign>0 else -46,46 if sign>0 else -34))
    for name,ys,mats,xmin,xmax in specs:
        sections=[]
        for y in ys:
            poly=section(y,mats,xmin,xmax)
            if len(poly)<3:continue
            c=[sum(v[k]for v in poly)/len(poly)for k in range(2)];ring=[]
            for j in range(16):
                d=(math.cos(j*math.tau/16),math.sin(j*math.tau/16));hits=[]
                for a,b in zip(poly,poly[1:]+poly[:1]):
                    e=(b[0]-a[0],b[1]-a[1]);ac=(a[0]-c[0],a[1]-c[1]);den=cross2(d,e)
                    if abs(den)<1e-10:continue
                    t=cross2(ac,e)/den;u=cross2(ac,d)/den
                    if t>0 and -.00001<=u<=1.00001:hits.append(t)
                if not hits:raise ValueError('Open section: '+name)
                t=max(.03,min(hits)*.86-.35);ring.append(convert((c[0]+d[0]*t,y,c[1]+d[1]*t)))
            sections.append(ring)
        if len(sections)<2:raise ValueError('Missing hull section: '+name)
        vs=[v for ring in sections for v in ring];fs=[]
        for k in range(len(sections)-1):
            for j in range(16):fs.append([k*16+j,(k+1)*16+j,(k+1)*16+(j+1)%16,k*16+(j+1)%16])
        # Rings run clockwise when viewed along +Z in BB coordinates.
        fs += [list(range(16)),list(range(len(vs)-1,len(vs)-17,-1))]
        ship.add('08_封闭内壳_'+name,vs,fs,0,False)


def build():
    import bpy,bmesh
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=str(REFERENCE))
    ob=bpy.data.objects.get('g_se_destroyer_LOD4')
    if ob is None:raise ValueError('Complete normal hull LOD4 is required; export the unit with all LODs.')
    # Capture the static world-space reference, excluding lights and shadow proxies.
    ob.data.transform(ob.matrix_world);ob.matrix_world.identity()
    ob.modifiers.clear()
    bpy.context.view_layer.objects.active=ob;ob.select_set(True)
    bm=bmesh.new();bm.from_mesh(ob.data)
    bmesh.ops.remove_doubles(bm,verts=list(bm.verts),dist=.005)
    bmesh.ops.dissolve_degenerate(bm,edges=list(bm.edges),dist=.0001)
    bm.to_mesh(ob.data);bm.free()
    native_source=ob.data.copy()
    mod=ob.modifiers.new('Distant silhouette','DECIMATE')
    mod.ratio=.56;mod.use_symmetry=True;mod.symmetry_axis='X'
    bpy.ops.object.modifier_apply(modifier=mod.name)
    ob.data.update()
    # Only thin external fins and upper rails need both visible sides. Give
    # these real thickness and capped rims, rather than disabling face culling.
    thin=[f for f in ob.data.polygons if
          (f.material_index==5 and abs(f.center.x)>29 and (f.center.z<10 or f.center.z>26)) or
          (f.material_index==3 and abs(f.center.x)>12 and f.center.z>35)]
    thin_ids={f.index for f in thin}
    plate_mesh=bpy.data.meshes.new('Closed armor plates')
    plate_mesh.from_pydata([v.co.copy()for v in ob.data.vertices],[],[list(f.vertices)for f in thin])
    for f,original in zip(plate_mesh.polygons,thin):f.material_index=original.material_index
    plate=bpy.data.objects.new('Closed armor plates',plate_mesh);bpy.context.collection.objects.link(plate)
    for m in ob.data.materials:plate.data.materials.append(m)
    bpy.context.view_layer.objects.active=plate
    thick=plate.modifiers.new('Armor backs and closed rims','SOLIDIFY')
    thick.thickness=.18;thick.offset=-1;thick.use_even_offset=False;thick.use_quality_normals=True;thick.use_rim=True
    bpy.ops.object.modifier_apply(modifier=thick.name);plate.data.update()
    s=Ship()
    groups=['01_结构内衬','02_舰底设备','03_舰面设备','04_前部舰体','05_中部机舱','06_尾部机舱与侧板']
    # Original Blender forward is -Y. Rotating 180 degrees and converting to BB
    # keeps a right-handed mesh with the bow facing -Z and the keel centred.
    def convert(v):return (-float(v[0]),float(v[2])-19,float(v[1])-9.5)
    for data,polygons in [(ob.data,[f for f in ob.data.polygons if f.index not in thin_ids]),(plate.data,plate.data.polygons)]:
      for f in polygons:
          vs=[convert(data.vertices[j].co) for j in f.vertices]
          c=f.center;n=f.normal;mat=0
          if f.material_index==0:mat=2
          elif f.material_index in (1,2):mat=2
          if n.z>.9 and f.material_index in (3,4,5):mat=1
          if f.material_index==3 and abs(n.x)>.65 and -105<c.y<4 and c.z>22:mat=9
          if f.material_index==5 and abs(c.x)>29 and abs(n.x)>.3 and c.y>65:mat=15
          before=len(s.parts.get(groups[f.material_index],{}).get('f',[]))
          s.add(groups[f.material_index],vs,[list(range(len(vs)))],mat,False)
          # Shared spatial UVs across triangles: no per-triangle paint stretching.
          if mat in (9,15):
              part=s.parts[groups[f.material_index]]
              for i in range(before,len(part['f'])):
                  ids,m,chart=part['f'][i]
                  if mat==9:chart=[((part['v'][j][2]+114.5)/110,(36-(part['v'][j][1]+19))/16) for j in ids]
                  else:chart=[((part['v'][j][2]+9.5-64)/68,(44-(part['v'][j][1]+19))/50) for j in ids]
                  part['f'][i]=(ids,m,[[max(0,min(1,a))for a in q] for q in chart])
    add_hull_liners(s,native_source,convert)
    # Measured nozzle centres: a hexagonal group of six and two per outrigger.
    engines=[(sign*14.15,131.35,21.63,3.3)for sign in (-1,1)]
    engines += [(sign*4.95,131.25,z,3.1)for sign in (-1,1)for z in (15.35,27.92)]
    engines += [(sign*40.04,126.48,z,2.0)for sign in (-1,1)for z in (14.05,21.67)]
    for i,(x,y,z,r) in enumerate(engines):
        # Inset discs follow the existing octagonal nozzle rings.
        vs=[convert((x+math.cos(j*math.tau/8)*r,y,z+math.sin(j*math.tau/8)*r))for j in range(8)]
        s.add('07_引擎发光',vs,[list(range(7,-1,-1))],12,False)
        s.mount(f'engine_{i+1:02d}',convert((x,y,z)))
    s.mount('ship_origin',(0,0,0))
    return s

def export(out:Path):
    out.mkdir(parents=True,exist_ok=True);tex=out/'贴图';tex.mkdir(exist_ok=True);atlas(tex)
    s=build()
    # Share vertices within each editable part without changing per-face UVs.
    for part in s.parts.values():
        unique=[];lookup={};remap={}
        for i,v in enumerate(part['v']):
            key=tuple(round(a*16,4) for a in v)
            if key not in lookup:lookup[key]=len(unique);unique.append(v)
            remap[i]=lookup[key]
        part['v']=unique
        part['f']=[([remap[j] for j in f],m,uv)for f,m,uv in part['f']]
    d={'meta':{'format_version':'5.0','model_format':'free','box_uv':False},'name':'超级驱逐舰_远景','model_identifier':'super_destroyer_distant','resolution':{'width':SIZE,'height':SIZE},'elements':[],'groups':[],'outliner':[],'animations':[],'textures':[]}
    d['textures'].append({'name':'舰体图集.png','uuid':uid('texture'),'id':'0','width':SIZE,'height':SIZE,'uv_width':SIZE,'uv_height':SIZE,'source':'data:image/png;base64,'+base64.b64encode((tex/'舰体图集.png').read_bytes()).decode(),'relative_path':'贴图/舰体图集.png','path':'','internal':True,'visible':True,'saved':True,'render_mode':'default','render_sides':'front'})
    count=0;triangles=0
    for name,p in s.parts.items():
        gid=uid('group/'+name);mid=uid('mesh/'+name)
        origin=s.mounts.get('orbital_pivot_'+name.split('_')[2].zfill(2),(0,0,0)) if name.startswith('09_') else (0,0,0)
        d['groups'].append({'name':name,'uuid':gid,'origin':[round(v*16,4)for v in origin],'rotation':[0,0,0],'export':True,'visibility':True,'isOpen':False})
        d['outliner'].append({'uuid':gid,'children':[mid]})
        vertices={f'v{i}':[round(a*16,4)for a in v]for i,v in enumerate(p['v'])};faces={}
        for i,(f,m,chart)in enumerate(p['f']):
            u=(m%4)*64+1;v=(m//4)*64+1
            uv=[(round(u+q[0]*61,4),round(v+q[1]*61,4))for q in chart]
            faces[f'f{i}']={'vertices':[f'v{j}'for j in f],'uv':{f'v{j}':uv[k]for k,j in enumerate(f)},'texture':0};triangles+=len(f)-2
        d['elements'].append({'name':name,'type':'mesh','uuid':mid,'origin':[0,0,0],'rotation':[0,0,0],'vertices':vertices,'faces':faces,'shading':'flat','export':True,'visibility':True,'render_order':'default'})
        count+=len(faces)
    aid=uid('attachments');d['groups'].append({'name':'挂点_无几何','uuid':aid,'origin':[0,0,0],'export':True,'visibility':True,'isOpen':False})
    children=[]
    for name,pos in s.mounts.items():
        i=uid('mount/'+name);d['groups'].append({'name':name,'uuid':i,'origin':[round(a*16,4)for a in pos],'rotation':[0,0,0],'export':True,'visibility':True,'isOpen':False});children.append({'uuid':i,'children':[]})
    d['outliner'].append({'uuid':aid,'children':children})
    (out/'超级驱逐舰_远景.bbmodel').write_text(json.dumps(d,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
    # Portable measured manifest for a future renderer; no runtime implementation here.
    vv=[v for p in s.parts.values()for v in p['v']]
    bounds={'min':[min(v[i]for v in vv)for i in range(3)],'max':[max(v[i]for v in vv)for i in range(3)]}
    manifest={'model':'super_destroyer_distant','units_per_meter':16,'forward':[0,0,-1],'up':[0,1,0],'faces':count,'triangles':triangles,'mesh_groups':len(s.parts),'vertices':sum(len(p['v'])for p in s.parts.values()),'reference_lod':'g_se_destroyer_LOD4','bounds_meters':bounds,'emissive_mask':'贴图/舰体图集_自发光.png','attachments_meters':s.mounts}
    (out/'模型信息.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(manifest,ensure_ascii=False))

if __name__=='__main__':
    import sys
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);p.add_argument('--reference',type=Path,required=True)
    args=p.parse_args(sys.argv[sys.argv.index('--')+1:]);REFERENCE=args.reference;export(args.output)
