from pathlib import Path
import bpy,bmesh,json,math,copy,uuid,base64,collections
import numpy as np
from mathutils import Vector,Matrix
ROOT=Path('D:/Project/sbw_helldiver');RES=ROOT/'src/main/resources/assets/sbw_helldiver';WORK=ROOT/'.codex/mortar_039';OUT=ROOT/'_asset_pack/当前模型/迫击炮';OUT.mkdir(parents=True,exist_ok=True)
def read(p):return json.loads(p.read_text('utf-8-sig'))
def save(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
arm=bpy.data.objects['0x51a0812e3bce2d74']; scene=bpy.context.scene;scene.use_nodes=False;scene.render.engine='CYCLES';scene.cycles.samples=1;scene.cycles.use_denoising=False
scene.render.bake.use_pass_direct=False;scene.render.bake.use_pass_indirect=False;scene.render.bake.use_pass_color=True;scene.render.bake.margin=2
atlas=bpy.data.images.load(str(RES/'textures/bedrock/vehicle/autocannon_hellpod_v1.png'));W,H=atlas.size;pixels=np.array(atlas.pixels[:],dtype=np.float32).reshape(H,W,4)
source_palette=np.array([[.16,.18,.15],[.22,.25,.21],[.29,.32,.27],[.38,.41,.35],[.09,.11,.10],[.47,.50,.46]],dtype=np.float32)
mesh_parts=[];stats=[];bone_map={'traverse':'cradle','elevator_rotator':'receiver','base_elevation_arm':'elevation_link','base_elevation_offseter':'elevation_offset','elevator_slider':'elevator_slider','barrel_01':'recoil_tube','barrel_02':'muzzle_tube','reload_cradle':'feed_cradle','reload_arm':'feed_arm','reload_slider':'feed_slider','reload_crucible':'feed_cup','reload_shell':'feed_round','feed_shells':'ammunition_column','sensor':'sensor','laser':'laser'}
base_names={'base','base_drum_body','base_trim','base_lower_sleeve'}
src_geo=read(RES/'models/bedrock/vehicle/autocannon_hellpod_v1.geo.json')['minecraft:geometry'][0]
common=[copy.deepcopy(b) for b in src_geo['bones'] if b['name'] in base_names or b['name'].startswith('pod') or b['name'] in ['hellpod_flight','root','deployment_lift']]
# The neutral model is horizontal, as required by SBW's native barrel transform.
# Its packed/idle poses restore the source's upright mortar, around the real elevation axis.
source_pivot=Vector(arm.data.bones['elevator_rotator'].head_local);s=20.;lift=-3.85
pivot=Vector((source_pivot.x*s,source_pivot.z*s+lift,-source_pivot.y*s));rot=Matrix.Rotation(-math.pi/2,3,'X')
fixed={'traverse','sensor','laser','base_elevation_arm','base_elevation_offseter','elevator_slider'}
def point(v,group):
 p=Vector((v[0]*s,v[2]*s+lift,-v[1]*s));return p if group in fixed else pivot+rot@(p-pivot)
def unit(v,group):
 p=Vector((v[0],v[2],-v[1]));return p if group in fixed else rot@p
bones=common+[{'name':'turret','parent':'deployment_lift','pivot':list(point(arm.data.bones['traverse'].head_local,'traverse'))},{'name':'stow_pivot','parent':'turret','pivot':list(pivot)},{'name':'barrel','parent':'stow_pivot','pivot':list(pivot)}]
# Reserve the lower-left atlas strip; inherited base/pod UVs never enter it.
for b in common:
 for c in b.get('cubes',[]):
  if isinstance(c.get('uv'),dict):
   for f in c['uv'].values():assert f['uv'][1]+abs(f.get('uv_size',[0,0])[1])<3000,'Atlas reservation overlaps a shared part'
for mi,name in enumerate(['CastMesh.004','CastMesh.005']):
 ob=bpy.data.objects[name];bpy.ops.object.select_all(action='DESELECT');ob.hide_set(False);ob.select_set(True);bpy.context.view_layer.objects.active=ob
 image=bpy.data.images.new('MortarBake'+str(mi),width=512,height=512,alpha=True)
 for material in ob.data.materials:
  node=material.node_tree.nodes.new('ShaderNodeTexImage');node.image=image;material.node_tree.nodes.active=node
 bpy.ops.object.bake(type='DIFFUSE');image.filepath_raw=str(WORK/f'source_color_{mi}.png');image.file_format='PNG';image.save()
 original=np.array(image.pixels[:],dtype=np.float32).reshape(512,512,4);srgb=np.where(original[:,:,:3]<=.0031308,original[:,:,:3]*12.92,1.055*original[:,:,:3]**(1/2.4)-.055)
 lum=srgb.mean(axis=2);idx=np.digitize(lum,[.085,.19,.34,.56,.77]);clean=source_palette[np.clip(idx,0,5)]
 # Retain original light/yellow decals; replace surface noise with a small uniform palette.
 yellow=(srgb[:,:,0]>srgb[:,:,2]*1.4)&(srgb[:,:,1]>srgb[:,:,2]*1.22)&(lum>.19);white=(lum>.63)&(np.max(srgb,axis=2)-np.min(srgb,axis=2)<.10)
 clean[yellow]=[.72,.65,.34];clean[white]=[.86,.88,.79]
 # A repeated-majority pass removes isolated wear specks, while keeping contiguous emblems.
 for _ in range(2):
  masks=np.stack([np.all(clean==c,axis=2) for c in np.vstack((source_palette,[[.72,.65,.34],[.86,.88,.79]])).astype(np.float32)]);votes=sum(np.roll(np.roll(masks,dy,axis=1),dx,axis=2) for dy,dx in [(0,0),(-1,0),(1,0),(0,-1),(0,1)]);allp=np.vstack((source_palette,[[.72,.65,.34],[.86,.88,.79]])).astype(np.float32);clean=allp[np.argmax(votes,axis=0)]
 patch=np.ones((512,512,4),dtype=np.float32);patch[:,:,:3]=np.where(clean<=.04045,clean/12.92,((clean+.055)/1.055)**2.4)
 x0=mi*512;y0=3072;pixels[H-y0-512:H-y0,x0:x0+512]=patch
 groups=[g.name for g in ob.vertex_groups];assigned=[groups[max(v.groups,key=lambda g:g.weight).group] if v.groups else 'boss' for v in ob.data.vertices]
 by_group=collections.defaultdict(list)
 for p in ob.data.polygons:
  g=collections.Counter(assigned[v] for v in p.vertices).most_common(1)[0][0]
  if g in bone_map:by_group[g].append(p)
 for g,faces in by_group.items():
  verts=[];index={};ff=[];uvs=[]
  for f in faces:
   inds=[];uf=[]
   for vi,li in zip(f.vertices,f.loop_indices):
    if vi not in index:index[vi]=len(verts);verts.append(tuple(ob.data.vertices[vi].co))
    inds.append(index[vi]);uf.append(tuple(ob.data.uv_layers.active.data[li].uv))
   ff.append(inds);uvs.extend(uf)
  me=bpy.data.meshes.new(g+'_source');me.from_pydata(verts,[],ff);me.update();uv=me.uv_layers.new(name='UVMap')
  for li,v in enumerate(uvs):uv.data[li].uv=v
  part=bpy.data.objects.new('mortar_'+g,me);scene.collection.objects.link(part);bpy.context.view_layer.objects.active=part
  # Preserve planar surfaces and UV boundaries, then reduce dense rounded source details.
  ratio=.30 if len(me.polygons)>1000 else .65 if len(me.polygons)>300 else 1
  if ratio<1:
   mod=part.modifiers.new('Source reduction','DECIMATE');mod.ratio=ratio;mod.use_collapse_triangulate=True;bpy.ops.object.modifier_apply(modifier=mod.name)
  me=part.data;me.calc_loop_triangles();positions=[list(point(v.co,g)) for v in me.vertices];normal=[];texuv=[];polys=[];bbfaces={}
  for ti,tr in enumerate(me.loop_triangles):
   poly=[];bbuv={};keys=[]
   for vi,li in zip(tr.vertices,tr.loops):
    nn=unit(me.corner_normals[li].vector,g);normal.append(list(nn));uvv=me.uv_layers.active.data[li].uv;uvpixel=[x0+uvv.x*512,y0+(1-uvv.y)*512];texuv.append(uvpixel);poly.append([vi,len(normal)-1,len(texuv)-1]);key='v'+str(vi);keys.append(key);bbuv[key]=uvpixel
   polys.append(poly);bbfaces['f'+str(ti)]={'vertices':keys,'uv':bbuv,'texture':0}
  bounds={'min':[min(p[i] for p in positions) for i in range(3)],'max':[max(p[i] for p in positions) for i in range(3)]};stats.append({'group':g,'source_faces':len(faces),'runtime_triangles':len(polys),'bounds':bounds})
  name=bone_map[g];parent='turret' if g in fixed else 'barrel';source_bone=arm.data.bones[g];bp=list(point(source_bone.head_local,g))
  if g in ['barrel_02','reload_arm','reload_slider','reload_crucible','reload_shell']:parent=bone_map[source_bone.parent.name]
  bone={'name':name,'parent':parent,'pivot':bp,'poly_mesh':{'normalized_uvs':False,'positions':positions,'normals':normal,'uvs':texuv,'polys':polys}}
  if name=='muzzle_tube':
   source_tip=Vector((0,.08460993,2.534994));bone['locators']={'muzzle_exit':list(point(source_tip,g))}
  bones.append(bone);mesh_parts.append({'bone':name,'name':g,'origin':bp,'vertices':{'v'+str(i):p for i,p in enumerate(positions)},'faces':bbfaces})
atlas.pixels[:]=pixels.ravel();atlas.filepath_raw=str(RES/'textures/bedrock/vehicle/mortar_hellpod_v1.png');atlas.file_format='PNG';atlas.save()
save(WORK/'mesh_measurements.json',stats)
# Preserve the existing deployment stages, cap ejection and three deployed flaps.
model='mortar_hellpod_v1';doc={'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.'+model,'texture_width':W,'texture_height':H,'visible_bounds_width':9,'visible_bounds_height':8,'visible_bounds_offset':[0,1.6,0]},'bones':bones}]};save(RES/f'models/bedrock/vehicle/{model}.geo.json',doc)
anim=read(RES/'animations/bedrock/vehicle/amg43_hellpod_v3.animation.json');A=anim['animations']
for name,a in list(A.items()):
 if not name.startswith('animation.hellpod.') and not name.startswith('animation.helldiver.') and name!='animation.Main.idle':del A[name];continue
 b=a.setdefault('bones',{})
 for old in list(b):
  if old not in {x['name'] for x in bones}:del b[old]
 if 'stow_pivot' in b:
  r=b['stow_pivot']['rotation']
  def pose(v):return [-90 if v[0]<-89 else -70,0,0]
  b['stow_pivot']['rotation']={t:pose(v) for t,v in r.items()} if isinstance(r,dict) else pose(r)
# Keep runtime barrel upright when no enemy is being tracked; deployment ends at 70 degrees.
A['animation.helldiver.deployed']['bones']['stow_pivot']={'rotation':[0,0,0]}
A['animation.helldiver.destroyed']['bones']['stow_pivot']={'rotation':[0,0,0]}
# Mortar is not an inverted rifle: only twenty degrees of elevation is released on deployment.
A['animation.hellpod.deploy']['animation_length']=2.12
A['animation.hellpod.retract']['animation_length']=2.8
A['animation.Main.fire']={'animation_length':.92,'loop':False,'bones':{
 'recoil_tube':{'position':{'0.0':[0,0,0],'.05':[0,0,1.2],'.13':[0,0,1.2],'.36':[0,0,0],'.92':[0,0,0]}},
 'muzzle_tube':{'position':{'0.0':[0,0,0],'.05':[0,0,.6],'.13':[0,0,.6],'.32':[0,0,0]}},
 'feed_cradle':{'rotation':{'0.0':[0,0,0],'.12':[0,0,0],'.28':[0,22,0],'.48':[0,22,0],'.74':[0,0,0],'.92':[0,0,0]}},
 'feed_arm':{'rotation':{'0.0':[0,0,0],'.28':[0,-16,0],'.48':[0,-16,0],'.74':[0,0,0]}},
 'feed_slider':{'position':{'0.0':[0,0,0],'.24':[0,0,-1.1],'.48':[0,0,-1.1],'.78':[0,0,0]}},
 'feed_round':{'scale':{'0.0':1,'.42':1,'.5':0,'.7':0,'.71':1,'.92':1}},
 'ammunition_column':{'position':{'0.0':[0,0,0],'.4':[0,0,0],'.56':[0,0,-1.35],'.68':[0,0,-1.35],'.70':[0,0,0]}}}}
A['animation.Main.idle']={'loop':True,'animation_length':1,'bones':{}}
exec((ROOT/'tools/recover_mortar_cycle.py').read_text('utf-8-sig'),globals()); recover_cycle(A,bones,arm,rot,pivot,lift)
save(RES/f'animations/bedrock/vehicle/{model}.animation.json',anim)
# Standalone: same real parts/bones, restoring the shared first-layer base height.
stand=copy.deepcopy(doc);sg=stand['minecraft:geometry'][0];sg['description']['identifier']='geometry.mortar_sentry_v1';sg['bones']=[b for b in sg['bones'] if not b['name'].startswith('pod') and b['name']!='hellpod_flight'];sg['bones'][0].pop('parent',None)
for b in sg['bones']:
 b['pivot'][1]+=3.85
 for c in b.get('cubes',[]):
  c['origin'][1]+=3.85
  if 'pivot' in c:c['pivot'][1]+=3.85
 for p in b.get('poly_mesh',{}).get('positions',[]):p[1]+=3.85
 for p in b.get('locators',{}).values():p[1]+=3.85
save(RES/'models/bedrock/vehicle/mortar_sentry_v1.geo.json',stand);save(RES/'animations/bedrock/vehicle/mortar_sentry_v1.animation.json',{'format_version':'1.8.0','animations':{k:v for k,v in A.items() if k in ['animation.Main.fire','animation.Main.idle']}})
# Blockbench meshes remain grouped into the source's major solid moving components.
def make_bb(doc,name):
 geo=doc['minecraft:geometry'][0];elements=[];groups={};order=[];uids={b['name']:str(uuid.uuid4()) for b in geo['bones']}
 for b in geo['bones']:
  group={'name':b['name'],'origin':b.get('pivot',[0,0,0]),'uuid':uids[b['name']],'rotation':b.get('rotation',[0,0,0]),'children':[],'export':True,'isOpen':False};groups[b['name']]=group
  if 'poly_mesh' in b:
   part=next(p for p in mesh_parts if p['bone']==b['name']);uid=str(uuid.uuid4());part=copy.deepcopy(part);part.update(type='mesh',uuid=uid,color=0,visibility=True,export=True,rotation=[0,0,0]);part.pop('bone');
   if name=='mortar_sentry_v1':
    part['origin'][1]+=3.85
    for p in part['vertices'].values():p[1]+=3.85
   part['vertices']={k:[v[j]-part['origin'][j] for j in range(3)] for k,v in part['vertices'].items()};elements.append(part);group['children'].append(uid)
  for i,c in enumerate(b.get('cubes',[])):
   uid=str(uuid.uuid4());e={'type':'cube','name':b['name']+'_'+str(i),'uuid':uid,'from':c['origin'],'to':[c['origin'][j]+c['size'][j] for j in range(3)],'origin':c.get('pivot',b['pivot']),'rotation':c.get('rotation',[0,0,0]),'box_uv':False,'faces':{f:{'uv':v['uv']+[v['uv'][j]+v.get('uv_size',[0,0])[j] for j in range(2)],'texture':0} for f,v in c.get('uv',{}).items()}};elements.append(e);group['children'].append(uid)
 for b in geo['bones']:
  if b.get('parent') in groups:groups[b['parent']]['children'].append(groups[b['name']])
  else:order.append(groups[b['name']])
 bbanim=[]
 for an,a in (A if name==model else {'animation.Main.fire':A['animation.Main.fire'],'animation.Main.idle':A['animation.Main.idle'],'animation.mortar.upright':{'loop':True,'animation_length':1,'bones':{'stow_pivot':{'rotation':[-70,0,0]}}}}).items():
  animators={}
  for bn,ch in a.get('bones',{}).items():
   if bn not in uids:continue
   frames=[]
   for channel,values in ch.items():
    if not isinstance(values,dict):values={'0.0':values}
    for stamp,value in values.items():
     if isinstance(value,(int,float)):value=[value]*3
     if isinstance(value,list):frames.append({'channel':channel,'time':float(stamp),'data_points':[{'x':str(value[0]),'y':str(value[1]),'z':str(value[2])}],'uuid':str(uuid.uuid4()),'interpolation':'linear'})
   animators[uids[bn]]={'name':bn,'type':'bone','keyframes':frames}
  bbanim.append({'name':an,'uuid':str(uuid.uuid4()),'length':a.get('animation_length',1),'loop':'loop' if a.get('loop') is True else 'hold' if a.get('loop')=='hold_on_last_frame' else 'once','animators':animators})
 save(OUT/('迫击炮与空投仓.bbmodel' if name==model else '迫击炮哨戒炮.bbmodel'),{'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':name,'resolution':{'width':W,'height':H},'elements':elements,'outliner':order,'textures':[{'name':'迫击炮.png','uuid':str(uuid.uuid4()),'source':'data:image/png;base64,'+base64.b64encode((RES/f'textures/bedrock/vehicle/{model}.png').read_bytes()).decode(),'mode':'bitmap','saved':True,'width':W,'height':H,'uv_width':W,'uv_height':H,'id':'0'}],'animations':bbanim})
make_bb(doc,model);make_bb(stand,'mortar_sentry_v1')
# Source-derived projectile, retaining the fins instead of rendering a rifle tracer.
round_ob=bpy.data.objects['CastMesh.014'];rr=Vector((0,0,0));positions=[]
for v in round_ob.data.vertices:
 world=round_ob.matrix_world@v.co;positions.append([(world.x+2)*16,(world.z-.094353)*16,-(world.y-1)*16])
roundgeo={'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.mortar_round','texture_width':16,'texture_height':16,'visible_bounds_width':2,'visible_bounds_height':2,'visible_bounds_offset':[0,0,0]},'bones':[{'name':'round','pivot':[0,0,0],'poly_mesh':{'normalized_uvs':False,'positions':positions,'normals':[],'uvs':[[8,8]],'polys':[[[vi,-1,0] for vi in f.vertices] for f in round_ob.data.polygons]}}]}]};save(RES/'models/bedrock/projectile/mortar_round.geo.json',roundgeo)
ri=bpy.data.images.new('MortarRound',width=16,height=16);ri.pixels[:]=np.tile([.30,.32,.23,1],16*16);ri.filepath_raw=str(RES/'textures/bedrock/projectile/mortar_round.png');Path(ri.filepath_raw).parent.mkdir(parents=True,exist_ok=True);ri.file_format='PNG';ri.save()
print('MORTAR_MODEL_BUILT',len(bones),sum(x['runtime_triangles'] for x in stats),'triangles',W,H)
