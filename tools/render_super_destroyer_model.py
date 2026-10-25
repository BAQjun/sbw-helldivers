"""Render final authored Blockbench geometry. Run with Blender --background --python.
Example: blender -b --python tools/render_super_destroyer_model.py -- --model ... --output ...
"""
import argparse,json,sys,math
from pathlib import Path
import bpy
from mathutils import Vector
p=argparse.ArgumentParser();p.add_argument('--model',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args(sys.argv[sys.argv.index('--')+1:]);a.output.mkdir(parents=True,exist_ok=True)
d=json.loads(a.model.read_text(encoding='utf-8'))
bpy.ops.wm.read_factory_settings(use_empty=True)
mat=bpy.data.materials.new('Hull atlas + emissive mask');mat.use_nodes=True
nt=mat.node_tree;n=nt.nodes;bs=next(q for q in n if q.type=='BSDF_PRINCIPLED');bs.inputs['Roughness'].default_value=.7;bs.inputs['Metallic'].default_value=.15
im=n.new('ShaderNodeTexImage');im.image=bpy.data.images.load(str(a.model.parent/'贴图/舰体图集.png'));im.interpolation='Closest';nt.links.new(im.outputs['Color'],bs.inputs['Base Color'])
em=n.new('ShaderNodeTexImage');em.image=bpy.data.images.load(str(a.model.parent/'贴图/舰体图集_自发光.png'));em.interpolation='Closest';nt.links.new(em.outputs['Color'],bs.inputs['Emission Color']);bs.inputs['Emission Strength'].default_value=1.6
for e in d['elements']:
 if e.get('type')!='mesh':continue
 names=list(e['vertices']);idx={k:i for i,k in enumerate(names)}
 vs=[(e['vertices'][k][0]/16,-e['vertices'][k][2]/16,e['vertices'][k][1]/16)for k in names]
 ff=list(e['faces'].values());mesh=bpy.data.meshes.new(e['name']);mesh.from_pydata(vs,[],[[idx[k]for k in f['vertices']]for f in ff]);mesh.update()
 ob=bpy.data.objects.new(e['name'],mesh);bpy.context.collection.objects.link(ob);ob.data.materials.append(mat)
 uv=mesh.uv_layers.new(name='atlas')
 for poly,f in zip(mesh.polygons,ff):
  for loop,key in zip(poly.loop_indices,f['vertices']):
   u,v=f['uv'][key];uv.data[loop].uv=(u/256,1-v/256)
scene=bpy.context.scene;scene.render.engine='CYCLES';scene.cycles.samples=24;scene.cycles.use_denoising=True
scene.render.resolution_x=1600;scene.render.resolution_y=1000;scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG';scene.render.film_transparent=False
scene.world=bpy.data.worlds.new('Studio');scene.world.use_nodes=True;bg=next(q for q in scene.world.node_tree.nodes if q.type=='BACKGROUND');bg.inputs['Color'].default_value=(.14,.19,.25,1);bg.inputs['Strength'].default_value=.45
scene.view_settings.view_transform='AgX'
for name,loc,power,size in [('Key',(-140,60,210),650000,180),('Fill',(130,20,100),300000,150),('Underside',(-10,30,-180),350000,150),('Rim',(0,-180,130),650000,130)]:
 ld=bpy.data.lights.new(name,'AREA');lo=bpy.data.objects.new(name,ld);scene.collection.objects.link(lo);lo.location=loc;ld.energy=power;ld.shape='DISK';ld.size=size;lo.rotation_euler=(Vector((0,0,0))-lo.location).to_track_quat('-Z','Y').to_euler()
cd=bpy.data.cameras.new('Preview');co=bpy.data.objects.new('Preview',cd);scene.collection.objects.link(co);scene.camera=co;cd.type='ORTHO';cd.clip_end=10000
views=[('超级驱逐舰_俯侧',(240,320,220),(0,0,0),310),('超级驱逐舰_仰视',(220,320,-190),(0,0,0),310),('超级驱逐舰_引擎',(-190,-330,135),(0,-15,0),310),('超级驱逐舰_侧面',(400,0,30),(0,0,0),320)]
for name,pos,target,size in views:
 co.location=pos;co.rotation_euler=(Vector(target)-co.location).to_track_quat('-Z','Y').to_euler();cd.ortho_scale=size;scene.render.filepath=str(a.output/(name+'.png'));bpy.ops.render.render(write_still=True)
# A distant ground-facing study, rendered from the same source model.
bg.inputs['Color'].default_value=(.14,.23,.32,1);bg.inputs['Strength'].default_value=.65
co.location=(350,450,-270);co.rotation_euler=(Vector((0,0,0))-co.location).to_track_quat('-Z','Y').to_euler();cd.ortho_scale=1100
scene.render.filepath=str(a.output/'超级驱逐舰_远景.png');bpy.ops.render.render(write_still=True)
print('Rendered actual bbmodel:',len(d['elements']),'mesh groups')
