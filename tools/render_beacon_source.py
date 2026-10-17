"""Render extracted closed/open source poses and report texture palette data."""
import bpy, sys, json
from mathutils import Vector
from pathlib import Path
import numpy as np
sys.path.insert(0,str(Path(__file__).parent))
import inspect_beacon_source as src
OUT=src.ROOT
bpy.ops.wm.read_factory_settings(use_empty=True)
for fn in ['0xaf1afe924780b8d6.png','0x5a06c3f25323e2e2.png']:
 im=bpy.data.images.load(str(OUT/'source_textures'/fn));im.colorspace_settings.name='Non-Color';ar=np.array(im.pixels[:]).reshape(im.size[1],im.size[0],4)
 if im.size[0]==23:print('LUT_ROWS',ar[:,0:7,:3].round(3).tolist())
 else:print('MASK',im.size[:],ar.reshape(-1,4).min(0),ar.reshape(-1,4).max(0))

mat=bpy.data.materials.new('Neutral source shell');mat.diffuse_color=(.28,.32,.32,1)
em=bpy.data.materials.new('Original blue light');em.diffuse_color=(.03,.42,.88,1)
objects=[]
for p in src.doc['meshes'][0]['primitives']:
 v=src.mesh_pose(p);v=v[:,[0,2,1]];v[:,1]*=-1
 idx=src.accessor(p['indices']).reshape(-1,3).tolist()
 mesh=bpy.data.meshes.new('original');mesh.from_pydata(v.tolist(),[],idx);mesh.update()
 ob=bpy.data.objects.new('source',mesh);bpy.context.collection.objects.link(ob);ob.data.materials.append(em if p['material']==0 else mat);objects.append((ob,p))

scene=bpy.context.scene;scene.render.engine='CYCLES';scene.cycles.samples=16;scene.cycles.use_denoising=True
scene.render.resolution_x=1000;scene.render.resolution_y=1000;scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG';scene.render.film_transparent=False
scene.view_settings.view_transform='Standard';scene.world=bpy.data.worlds.new('World');scene.world.color=(.35,.35,.35)
camera=bpy.data.objects.new('Camera',bpy.data.cameras.new('Camera'));scene.collection.objects.link(camera);scene.camera=camera;camera.data.type='ORTHO';camera.data.ortho_scale=.38
camera.location=(.38,-.5,.38);target=Vector((0,0,.06));camera.rotation_euler=(target-camera.location).to_track_quat('-Z','Y').to_euler()
for name,loc,power in [('key',(.3,-.4,.5),12),('fill',(-.3,-.1,.3),7),('rim',(.1,.3,.4),10)]:
 lamp=bpy.data.objects.new(name,bpy.data.lights.new(name,'AREA'));scene.collection.objects.link(lamp);lamp.location=loc;lamp.data.energy=power;lamp.data.size=.3;lamp.rotation_euler=(target-lamp.location).to_track_quat('-Z','Y').to_euler()
for name,anim,t in [('source_closed',src.doc['animations'][2],0),('source_open',src.doc['animations'][0],1)]:
 for ob,p in objects:
  v=src.mesh_pose(p,anim,t);v=v[:,[0,2,1]];v[:,1]*=-1
  for vert,co in zip(ob.data.vertices,v):vert.co=co
 scene.render.filepath=str(OUT/(name+'.png'));bpy.ops.render.render(write_still=True)
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'source_inspection.blend'))
