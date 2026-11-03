"""Run with Blender --background --python to generate optional distant meshes.

The accepted source and full-detail meshes are never modified. Node transforms,
animation channels, material slots and UV layers survive the distant export.
"""
import bpy,bmesh,gzip,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from optimize_machine_meshes import optimize
ROOT=Path(__file__).resolve().parents[1]
folder=ROOT/'src/main/resources/assets/sbw_helldiver/models/mesh'
report={}
for name in ['bioprocessor','refinery','bioprocessor_container','refinery_container']:
    data=json.loads(gzip.decompress((folder/f'{name}.mesh.json.gz').read_bytes()))
    original=final=0
    for parts in data['meshes']:
        for p in parts:
            original+=len(p['indices'])//3
            mesh=bpy.data.meshes.new('lod');mesh.from_pydata(p['p'],[],[p['indices'][i:i+3] for i in range(0,len(p['indices']),3)])
            uv=mesh.uv_layers.new(name='UVMap')
            for loop in mesh.loops:uv.data[loop.index].uv=p['uv'][loop.vertex_index]
            bm=bmesh.new();bm.from_mesh(mesh);bmesh.ops.remove_doubles(bm,verts=list(bm.verts),dist=1e-6);bm.to_mesh(mesh);bm.free()
            obj=bpy.data.objects.new('lod',mesh);bpy.context.collection.objects.link(obj)
            # Small boxes and sheet panels already have very few triangles.
            # Collapsing those individually removes whole sides of the shell.
            mod=obj.modifiers.new('distant detail','DECIMATE');mod.ratio=.25 if len(p['indices'])//3>48 else 1.0;mod.use_collapse_triangulate=True
            deps=bpy.context.evaluated_depsgraph_get();evaluated=obj.evaluated_get(deps);result=evaluated.to_mesh();result.calc_loop_triangles()
            points=[];coords=[];indices=[];keys={}
            for tri in result.loop_triangles:
                for loop_index in tri.loops:
                    loop=result.loops[loop_index];co=result.vertices[loop.vertex_index].co;tex=result.uv_layers.active.data[loop_index].uv
                    key=tuple(round(v,7) for v in (*co,*tex))
                    if key not in keys:keys[key]=len(points);points.append(list(key[:3]));coords.append(list(key[3:]))
                    indices.append(keys[key])
            p.update(p=points,uv=coords,indices=indices);p.pop('faces',None);optimize(p);final+=len(indices)//3
            evaluated.to_mesh_clear();bpy.data.objects.remove(obj,do_unlink=True);bpy.data.meshes.remove(mesh)
    (folder/f'{name}_lod.mesh.json.gz').write_bytes(gzip.compress(json.dumps(data,separators=(',',':'),ensure_ascii=False).encode(),mtime=0))
    report[name]={'full_triangles':original,'lod_triangles':final}
target=ROOT/'.codex/machines-alpha6/lod-report.json';target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
