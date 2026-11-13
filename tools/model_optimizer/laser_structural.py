"""Blender: bounded, per-part laser retopology; preserve UVs, rig and locators.

Run with --background --python this_file -- --workspace <artwork root> --output <work dir>.
Always reads the accepted standalone model, never a previously reduced candidate.
"""
import argparse, base64, copy, json, math, pathlib, sys
import bpy, bmesh
from mathutils import Vector, Euler
from mathutils.bvhtree import BVHTree
from mathutils.geometry import barycentric_transform, closest_point_on_tri

ap=argparse.ArgumentParser();ap.add_argument('--workspace',type=pathlib.Path,required=True);ap.add_argument('--output',type=pathlib.Path,required=True)
a=ap.parse_args(sys.argv[sys.argv.index('--')+1:]);W=a.output;W.mkdir(parents=True,exist_ok=True)
source=a.workspace/'outputs/当前交付/模型/单独炮台/激光炮台.bbmodel'
D=json.loads(source.read_text(encoding='utf8'));out=copy.deepcopy(D)
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
materials=[]
for i,t in enumerate(D['textures']):
    p=W/f'original_{i}.png';p.write_bytes(base64.b64decode(t['source'].split(',')[1]))
    m=bpy.data.materials.new(f'source_{i}');m.use_nodes=True
    nodes=m.node_tree.nodes;nodes.clear();o=nodes.new('ShaderNodeOutputMaterial');em=nodes.new('ShaderNodeEmission');tex=nodes.new('ShaderNodeTexImage');tex.image=bpy.data.images.load(str(p));tex.interpolation='Closest'
    m.node_tree.links.new(tex.outputs['Color'],em.inputs[0]);m.node_tree.links.new(em.outputs[0],o.inputs['Surface']);materials.append(m)
groups={g['uuid']:g for g in D['groups']};parents={}
def walk(rows,parent=None):
    for row in rows:
        if isinstance(row,str):parents[row]=groups[parent]['name'] if parent else None
        else:walk(row.get('children',[]),row['uuid'])
walk(D['outliner'])
def coord(v):return (v[0],-v[2],v[1])
def add(e):
    origin=Vector(e.get('origin',[0,0,0]));rot=Euler([math.radians(v) for v in e.get('rotation',[0,0,0])],'ZYX').to_matrix()
    ids=list(e['vertices']);lookup={v:i for i,v in enumerate(ids)}
    verts=[coord(rot@Vector(e['vertices'][v])+origin) for v in ids];faces=[];uvs=[];mats=[]
    for f in e['faces'].values():
        if f.get('texture') is None:continue
        keys=f['vertices'];faces.append([lookup[v] for v in keys]);uvs.append([f['uv'][v] for v in keys]);mats.append(int(f['texture']))
    me=bpy.data.meshes.new(e['name']);me.from_pydata(verts,[],faces);me.update();o=bpy.data.objects.new(e['name'],me);bpy.context.collection.objects.link(o)
    for m in materials:me.materials.append(m)
    uv=me.uv_layers.new(name='OriginalUV')
    for p,co,mat in zip(me.polygons,uvs,mats):
        p.material_index=mat
        for loop,(u,v) in zip(p.loop_indices,co):uv.data[loop].uv=(u/2048,1-v/4608)
    return o
def triangles(me):return sum(max(0,len(p.vertices)-2) for p in me.polygons)
def tree(me):
    me.calc_loop_triangles();return BVHTree.FromPolygons([v.co for v in me.vertices],[list(t.vertices) for t in me.loop_triangles],all_triangles=True)
def samples(me):
    # Include original corners and face centres, not just retained vertices.
    return [v.co.copy() for v in me.vertices]+[p.center.copy() for p in me.polygons]
def error(me,reference,points):
    target=tree(me)
    return max(max((reference.find_nearest(v.co)[3] or 0) for v in me.vertices),max((target.find_nearest(p)[3] or 0) for p in points))
rows=[]
for e in out['elements']:
    if e.get('type')!='mesh':continue
    o=add(e);before=triangles(o.data);source_mesh=o.data.copy();ref=tree(source_mesh);points=samples(source_mesh)
    me=o.data;areas={};me.update()
    for p in me.polygons:
        axis=max(range(3),key=lambda i:abs(p.normal[i]))
        if abs(p.normal[axis])>.99999:
            key=(axis,round(me.vertices[p.vertices[0]].co[axis],3));areas[key]=areas.get(key,0)+p.area
    largest={axis:max((area for (ax,pos),area in areas.items() if ax==axis),default=0) for axis in range(3)}
    planes=[(ax,pos,area) for (ax,pos),area in areas.items() if area>max(4,largest[ax]*.20)]
    bounds=[(min(v.co[i] for v in me.vertices),max(v.co[i] for v in me.vertices)) for i in range(3)]
    for v in me.vertices:
        for ax in range(3):
            x=v.co[ax];own=[(pos,area) for axis,pos,area in planes if axis==ax]
            if any(abs(x-end)<.003 for end in bounds[ax]) or any(abs(x-pos)<.003 for pos,area in own):continue
            choices=[(pos,area) for pos,area in own if .004<abs(x-pos)<.18]
            if choices:v.co[ax]=max(choices,key=lambda x:x[1])[0]
    bm=bmesh.new();bm.from_mesh(me);bmesh.ops.remove_doubles(bm,verts=list(bm.verts),dist=.0001);bmesh.ops.dissolve_degenerate(bm,edges=list(bm.edges),dist=.00002)
    dead=[f for f in bm.faces if f.calc_area()<1e-9]
    if dead:bmesh.ops.delete(bm,geom=dead,context='FACES_ONLY')
    bm.to_mesh(me);bm.free();me.update();cleaned=me.copy()
    # Engraving removal is intentional; bound decimation against this cleaned surface.
    cleaned_ref=tree(cleaned);cleaned_points=samples(cleaned)
    # No convex hulls, global remesh, texture baking, or arbitrary vertex displacement.
    ratio=.30 if before>180 else .55 if before>60 else 1.0
    if '支架' in e['name']:ratio=.50
    # Broad armour carries stripes and printed markings across shallow engravings.
    # Keep its chart boundaries intact; only flatten the engraving geometry.
    if e['name']=='共用_支架_0':ratio=1.0
    if any(n in e['name'] for n in ('标识','指示','光学','镜片','三角炮口','冷却栅')):ratio=max(ratio,.75)
    limit=.18 if '共用_支架' in e['name'] else .13
    attempts=[]
    while ratio<.995:
        o.data=cleaned.copy();bpy.context.view_layer.objects.active=o
        mod=o.modifiers.new('Bounded part reduction','DECIMATE');mod.ratio=ratio;mod.use_collapse_triangulate=True
        bpy.ops.object.modifier_apply(modifier=mod.name);o.data.update();err=error(o.data,cleaned_ref,cleaned_points);attempts.append([ratio,err])
        if err<=limit:break
        ratio=min(1,ratio+.12)
    if ratio>=.995:o.data=cleaned.copy()
    me=o.data;me.update();me.calc_loop_triangles();uv=me.uv_layers.active;origin=e.get('origin',[0,0,0])
    e['vertices']={f'v{v.index}':[round(v.co.x-origin[0],6),round(v.co.z-origin[1],6),round(-v.co.y-origin[2],6)] for v in me.vertices};e['rotation']=[0,0,0];e['faces']={}
    for i,t in enumerate(me.loop_triangles):
        keys=[f'v{v}' for v in t.vertices]
        # Reproject each reduced face into its original UV chart. Blender's
        # collapse interpolation can otherwise drag palette UVs across islands.
        center=sum((me.vertices[v].co for v in t.vertices),Vector())/3
        hit=ref.find_nearest(center);old=source_mesh.loop_triangles[hit[2]]
        pts=[source_mesh.vertices[v].co for v in old.vertices]
        old_uv=[Vector((*source_mesh.uv_layers.active.data[l].uv,0)) for l in old.loops]
        mapped=([uv.data[l].uv for l in t.loops] if ratio>=.995 else [barycentric_transform(closest_point_on_tri(me.vertices[v].co,*pts),*pts,*old_uv) for v in t.vertices])
        if any(not math.isfinite(value) for q in mapped for value in q):
            mapped=[sum(old_uv,Vector())/3]*3
        tex=me.polygons[t.polygon_index].material_index if ratio>=.995 else source_mesh.polygons[old.polygon_index].material_index
        e['faces'][f'f{i}']={'vertices':keys,'uv':{k:[p.x*2048,(1-p.y)*4608] for k,p in zip(keys,mapped)},'texture':tex}
    rows.append({'name':e['name'],'bone':parents[e['uuid']],'before':before,'after':triangles(me),'surface_error_units':round(error(me,ref,points),5),'attempts':attempts})
    print(e['name'],before,triangles(me),flush=True)
out['name']='激光炮台_结构优化测试'
(W/'laser.bbmodel').write_text(json.dumps(out,ensure_ascii=False,separators=(',',':'),allow_nan=False),encoding='utf8')
(W/'laser-report.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf8')
print('TOTAL',sum(x['before'] for x in rows),sum(x['after'] for x in rows),flush=True)
