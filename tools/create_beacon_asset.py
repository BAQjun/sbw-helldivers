"""Create an editable beacon from the extracted game rig, with a clean pixel atlas.

Run using Blender --background --python. No existing turret assets are rewritten.
The renderer-ready file retains the original rigid vertex weights, including hinge seams.
"""
import bpy,bmesh,sys,json,math,uuid,base64,struct,zlib,shutil
from pathlib import Path
from mathutils import Matrix,Vector
import numpy as np
sys.path.insert(0,str(Path(__file__).parent))
import inspect_beacon_source as src

OUT=Path('D:/Codex/projects/2026-09-19/new-chat/outputs/当前交付/信标球')
for d in ['模型','贴图','预览','参考']: (OUT/d).mkdir(parents=True,exist_ok=True)
def save(p,v):p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
def uid(name):return str(uuid.uuid5(uuid.NAMESPACE_URL,'sbw_helldiver/beacon/'+name))
def png(p,ar):
 ar=np.asarray(ar,dtype=np.uint8);h,w=ar.shape[:2]
 def chunk(n,b):return struct.pack('>I',len(b))+n+b+struct.pack('>I',zlib.crc32(n+b)&0xffffffff)
 p.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(b'\0'+r.tobytes() for r in ar),9))+chunk(b'IEND',b''))

# Only the original material identity masks are used. Dirt, curvature scratches,
# weathering and blood/gunk texture inputs are deliberately absent from this atlas.
mask=bpy.data.images.load(str(src.ROOT/'source_textures/0x5a06c3f25323e2e2.png'))
mask.colorspace_settings.name='Non-Color'
ma=np.array(mask.pixels[:]).reshape(256,128,4)[::-1]
channels=np.concatenate([ma[:128],ma[128:]],axis=2)
# The original shader applies ordered, thresholded material overrides; these
# channels are not mutually exclusive weights. Slot 1 is the default material.
ids=np.zeros((128,128),dtype=np.uint8)
for slot in range(1,8):ids[channels[:,:,slot]>.496]=slot
palette=np.array([[161,167,170],[62,68,74],[47,53,59],[47,53,59],[211,199,35],[82,89,94],[61,67,73],[57,63,69]],dtype=np.uint8)
atlas=np.full((128,256,4),255,dtype=np.uint8);atlas[:,:,:3]=[43,51,48];atlas[:,:128,:3]=palette[ids]
# Trace only coherent engineered recesses from the original normal field into
# two flat pixel shades. This contains no curvature/wear maps or noisy shading.
ni=bpy.data.images.load(str(src.ROOT/'source_textures/0x0c9f2a7d067b2fa5.png'));ni.colorspace_settings.name='Non-Color'
normal=np.array(ni.pixels[:]).reshape(256,256,4)[::-1,:,:2]*2-1
normal=normal.reshape(128,2,128,2,2).mean(axis=(1,3))
signal=normal[:,:,0]-.8*normal[:,:,1]
edges=(np.abs(signal)>.14)&(np.linalg.norm(normal,axis=2)<.9)
visited=np.zeros((128,128),bool);clean_edges=np.zeros_like(visited)
for y,x in zip(*np.where(edges)):
 if visited[y,x]:continue
 stack=[(y,x)];component=[];visited[y,x]=True
 while stack:
  cy,cx=stack.pop();component.append((cy,cx))
  for sy,sx in [(cy-1,cx),(cy+1,cx),(cy,cx-1),(cy,cx+1)]:
   if 0<=sy<128 and 0<=sx<128 and edges[sy,sx] and not visited[sy,sx]:visited[sy,sx]=True;stack.append((sy,sx))
 if len(component)>=5:
  for cy,cx in component:clean_edges[cy,cx]=True
delta=np.where(signal<0,-17,9).astype(np.int16)
rgb=atlas[:,:128,:3].astype(np.int16)
rgb[clean_edges]=np.clip(rgb[clean_edges]+delta[clean_edges,None],0,255)
atlas[:,:128,:3]=rgb.astype(np.uint8)
# The large rectangular vent has orderly slots rather than noisy normal baking.
for y in range(98,126):
 for x in range(36,71):
  if ids[y,x] in [2,3,6,7]:atlas[y,x,:3]=[30,36,41] if (x-36)%4==0 or (y-98)%3==0 else [57,63,69]
atlas[16:48,144:176,:3]=[12,205,218];atlas[22:42,150:170,:3]=[24,232,239]
atlas[64:96,144:176,:3]=[234,83,69];atlas[70:90,150:170,:3]=[254,134,107]
atlas[104:120,144:176,:3]=[47,53,59]
emission=np.zeros_like(atlas);emission[16:48,144:176]=atlas[16:48,144:176];emission[64:96,144:176]=atlas[64:96,144:176]
# Clean top dial and a purpose-drawn, bilaterally symmetrical skull emboss.
yy,xx=np.mgrid[:64,:64];rr=np.hypot(xx+0.5-32,yy+0.5-32)
top=np.full((64,64,4),255,dtype=np.uint8);top[:,:,:3]=[147,155,162]
ring=(rr>=15)&(rr<=22);top[ring,:3]=[15,222,229];top[rr<15,:3]=[113,124,135]
half=['000000111','000011111','000111111','001111111',
      '011111111','011111111','111111111','111111111',
      '111111111','111111111','111111111','011111111',
      '001111111','000111111','000011111','000011111',
      '000011111','000011111','000001111','000000111']
badge=np.array([[c=='1' for c in row+row[::-1]] for row in half])
badge[8:11,3:6]=False;badge[8:11,12:15]=False
badge[12:15,8:10]=False
badge[17:20,6]=False;badge[17:20,11]=False
assert np.array_equal(badge,badge[:,::-1]),'Skull must be symmetrical'
outline=np.zeros((22,20),bool)
for dy,dx in [(0,1),(2,1),(1,0),(1,2),(1,1)]:outline[dy:dy+20,dx:dx+18]|=badge
top[21:43,22:42,:3][outline]=[90,102,113]
top[22:42,23:41,:3][badge]=[174,184,190]
png(OUT/'预览/骷髅纹样.png',np.repeat(np.repeat(top,8,axis=0),8,axis=1))
# The closed light ring is a separate animated part. Painting cyan onto these
# shell UVs would carry blue arcs into the opened petals.
top[ring,:3]=[147,155,162]
atlas[16:80,184:248]=top
png(OUT/'贴图/信标球.png',atlas);png(OUT/'贴图/信标球_发光.png',emission)
tex=OUT/'贴图/信标球.png'

anims=src.doc['animations'];closed=anims[2];deploy=anims[0];active=anims[1]
G0=src.pose(closed,0);G0i=np.linalg.inv(G0)
joint_nodes=src.skin['joints'];names={i:n['name'] for i,n in enumerate(src.doc['nodes'])}
used={11,*range(12,27)}
# Blockbench stores model-space pivots and initially unrotated, closed geometry.
# Conjugating original local motions by the closed parent basis gives exactly
# the same rigid transformations without exposing imported FBX conversion nodes.
def motion(a,t):
 G=src.pose(a,t);D=G@G0i;out={}
 for i in sorted(used):
  p=src.parents.get(i);local=np.linalg.inv(D[p])@D[i] if p in used else D[i]
  pivot=G0[i,:3,3];translation=(local[:3,:3]@pivot+local[:3,3]-pivot)*16
  # Blender XYZ is the extrinsic Rz*Ry*Rx product; THREE's intrinsic ZYX has
  # that same product. Use native 5.0 project coordinates to avoid legacy
  # conversion changing small fixed-point angle strings into Molang exponents.
  rotation=np.degrees(np.array(Matrix(local[:3,:3].tolist()).to_euler('XYZ')))
  out[i]={'position':translation,'rotation':rotation}
 return out,D

primitives=src.doc['meshes'][0]['primitives'];positions=src.accessor(primitives[0]['attributes']['POSITION'])
uv=src.accessor(primitives[0]['attributes']['TEXCOORD_0']);uv[:,0]%=1;js=src.accessor(primitives[0]['attributes']['JOINTS_0']).astype(int)
weights=src.accessor(primitives[0]['attributes']['WEIGHTS_0']);dominant=joint_nodes[0]+np.zeros(len(js),int)
dominant=np.array(joint_nodes)[js[np.arange(len(js)),weights.argmax(1)]]
closedv=src.mesh_pose(primitives[0],closed,0)*16
topology=[]
rigid_error=0.0;cross_faces=0
sampleD=[motion(deploy,t/30)[1] for t in range(31)]
for p in primitives:
 for f in src.accessor(p['indices']).reshape(-1,3).astype(int):
  joints=dominant[f];candidates=np.unique(joints)
  # Shared hinge skin faces are assigned to the closest rigid part in the editor.
  # Their original per-vertex skin weights stay intact in the game mesh export.
  if len(candidates)>1:
   cross_faces+=1;errors=[]
   for j in candidates:
    err=0
    for D in sampleD:
     v=np.column_stack((closedv[f]/16,np.ones(3)));actual=np.array([(D[k]@pt)[:3] for k,pt in zip(joints,v)]);rigid=(D[j]@v.T).T[:,:3]
     err=max(err,float(np.linalg.norm(actual-rigid,axis=1).max()))
    errors.append(err)
   bone=int(candidates[np.argmin(errors)]);rigid_error=max(rigid_error,min(errors))
  else:bone=int(joints[0])
  points=closedv[f]/16;center=points.mean(0);normal=np.cross(points[1]-points[0],points[2]-points[0]);normal/=max(np.linalg.norm(normal),1e-9)
  top_dial=(p['material']!=0 and bone in [14,19,24] and center[1]>.108 and np.linalg.norm(center[[0,2]])<.034 and normal[1]>.6)
  # Native emission also includes one stray side triangle extending beyond
  # the actual central disc. Treat that face as casing rather than blue light.
  mode=(1 if np.linalg.norm(center[[0,2]])<.024 else 3) if p['material']==0 else 2 if top_dial else 0
  topology.append((bone,mode,f.tolist()))

# One real mesh per moving component; coplanar triangles become quads. We keep
# silhouette and recess vertices instead of rebuilding a sphere from thin strips.
elements=[];mesh_info=[]
for i in sorted(used):
 print('BUILD_MESH',names[i],flush=True)
 fs=[x for x in topology if x[0]==i];vi=sorted(set(v for _,_,f in fs for v in f));imap={v:k for k,v in enumerate(vi)}
 mesh=bpy.data.meshes.new(names[i]);mesh.from_pydata(closedv[vi].tolist(),[],[[imap[v] for v in f] for _,_,f in fs]);mesh.update()
 layer=mesh.uv_layers.new(name='UVMap')
 for poly,(_,mode,f) in zip(mesh.polygons,fs):
  poly.material_index=mode
  for li,v in zip(poly.loop_indices,f):
   if mode==1:coords=(160.5/256,1-32.5/128)
   elif mode==3:coords=(160.5/256,1-112.5/128)
   elif mode==2:coords=((216+closedv[v,0]/16/.08*64)/256,1-(48+closedv[v,2]/16/.08*64)/128)
   else:coords=(uv[v,0]*.5,1-uv[v,1])
   layer.data[li].uv=coords
 bm=bmesh.new();bm.from_mesh(mesh)
 bmesh.ops.remove_doubles(bm,verts=list(bm.verts),dist=.000001)
 bmesh.ops.join_triangles(bm,faces=list(bm.faces),angle_face_threshold=.00001,angle_shape_threshold=.08,cmp_uvs=True,cmp_materials=True)
 bm.to_mesh(mesh);bm.free();mesh.update();layer=mesh.uv_layers.active
 vertices={f'v{k}':[round(x,7) for x in v.co] for k,v in enumerate(mesh.vertices)}
 faces={}
 for k,p in enumerate(mesh.polygons):
  keys=[f'v{v}' for v in p.vertices];coords={f'v{mesh.loops[l].vertex_index}':[round(float(layer.data[l].uv[0])*256,5),round((1-float(layer.data[l].uv[1]))*128,5)] for l in p.loop_indices}
  faces[f'f{k}']={'vertices':keys,'uv':coords,'texture':0}
 element={'name':names[i],'type':'mesh','uuid':uid('mesh/'+names[i]),'origin':[0,0,0],'rotation':[0,0,0],'vertices':vertices,'faces':faces,'visibility':True,'autouv':0,'export':True,'color':4}
 elements.append(element);mesh_info.append((i,mesh))

# Project the ring onto the actual shell triangles, clipping each angular
# sector at every surface crease. Broad interpolated quads can cut through
# the faceted shell between their corners, producing missing arcs.
top_triangles=[]
for _,mode,f in topology:
 tri=closedv[f]/16;normal=np.cross(tri[1]-tri[0],tri[2]-tri[0]);center=tri.mean(0)
 if normal[1]>0 and center[1]>.105 and np.linalg.norm(center[[0,2]])<.045:top_triangles.append(tri)
def cross2(a,b):return a[0]*b[1]-a[1]*b[0]
def clip(poly,edge0,edge1):
 out=[];direction=edge1-edge0
 for i,p in enumerate(poly):
  q=poly[(i+1)%len(poly)];dp=cross2(direction,p-edge0);dq=cross2(direction,q-edge0)
  if dp>=-1e-12:out.append(p)
  if (dp>=0)!=(dq>=0):out.append(p+(q-p)*dp/(dp-dq))
 return out
ring_vertices={};ring_faces={};segments=48;ring_vertex_map={}
for k in range(segments):
 t0=2*math.pi*k/segments;t1=2*math.pi*(k+1)/segments
 wedge=[np.array([r*math.cos(t),r*math.sin(t)]) for r,t in [(.01875,t0),(.0275,t0),(.0275,t1),(.01875,t1)]]
 for tri in top_triangles:
  plane=tri[:,[0,2]];basis=np.column_stack((plane[1]-plane[0],plane[2]-plane[0]))
  if abs(np.linalg.det(basis))<1e-12:continue
  poly=list(plane)
  for j in range(4):
   if not poly:break
   poly=clip(poly,wedge[j],wedge[(j+1)%4])
  if len(poly)<3:continue
  area=sum(cross2(poly[j],poly[(j+1)%len(poly)]) for j in range(len(poly)))
  if abs(area)<1e-12:continue
  keys=[]
  for xz in poly:
   b=np.linalg.solve(basis,xz-plane[0]);b=np.array([1-b.sum(),*b])
   co=tuple(np.round(np.array([xz[0],b@tri[:,1]+.0001,xz[1]])*16,7))
   if co not in ring_vertex_map:
    key=f'r{len(ring_vertices)}';ring_vertex_map[co]=key;ring_vertices[key]=list(co)
   keys.append(ring_vertex_map[co])
  keys=list(dict.fromkeys(keys))
  # Explicit triangles also avoid Blockbench reordering clipped five/six-sided
  # faces, which otherwise opens radial slits absent in Blender's rendering.
  for j in range(1,len(keys)-1):
   triangle=[keys[0],keys[j],keys[j+1]]
   ring_faces[f'f{len(ring_faces)}']={'vertices':triangle,'uv':{v:[160.5,32.5] for v in triangle},'texture':0}
ring_element={'name':'closed_light_ring','type':'mesh','uuid':uid('mesh/closed_light_ring'),'origin':[0,0,0],'rotation':[0,0,0],'vertices':ring_vertices,'faces':ring_faces,'visibility':True,'autouv':0,'export':True,'color':4}
elements.append(ring_element)
ring_origin=np.array([0,np.mean([v[1] for v in ring_vertices.values()]),0])
def ring_delta(a,t,D):
 factor=1 if a is closed else 1e-6 if a is active else max(1e-6,1-t*30)
 T=np.eye(4);T[:3,3]=ring_origin/16
 Ti=np.eye(4);Ti[:3,3]=-ring_origin/16
 return D[11]@T@np.diag([factor,factor,factor,1])@Ti
groups={i:{'name':names[i],'uuid':uid(names[i]),'origin':(G0[i,:3,3]*16).round(7).tolist(),'rotation':[0,0,0],'children':[uid('mesh/'+names[i])],'export':True,'isOpen':True,'visibility':True} for i in sorted(used)}
for i in sorted(used):
 p=src.parents.get(i)
 if p in used:groups[p]['children'].append(groups[i])
outliner=[groups[11]]
root_locator={'name':'fx_socket','uuid':uid('fx_socket'),'origin':[0,1.984,0],'rotation':[0,0,0],'children':[],'export':True,'isOpen':True,'visibility':True}
groups[11]['children'].append(root_locator)
groups[11]['children'].append({'name':'closed_light_ring','uuid':uid('closed_light_ring'),'origin':ring_origin.round(7).tolist(),'rotation':[0,0,0],'children':[ring_element['uuid']],'export':True,'isOpen':True,'visibility':True})
animations=[]
for name,a,length,loop in [('closed',closed,1,'loop'),('deploy',deploy,1,'hold'),('active',active,1/3,'loop')]:
 animators={}
 for i in sorted(used):
  kfs=[]
  times=[0] if name!='deploy' else [t/30 for t in range(31)]
  samples=[motion(a,t)[0][i] for t in times]
  for channel in ['rotation','position']:
   vs=np.array([v[channel] for v in samples]);vs=np.degrees(np.unwrap(np.radians(vs),axis=0)) if channel=='rotation' else vs
   if np.max(np.abs(vs))<1e-5:continue
   for t,v in zip(times,vs):
    # Molang does not support scientific notation: "1e-07" would be parsed
    # as an expression, producing an unintended seven-degree rotation.
    kfs.append({'uuid':uid(f'{name}/{i}/{channel}/{t}'),'channel':channel,'time':round(t,8),'interpolation':'linear','data_points':[dict(zip(['x','y','z'],[f'{float(x):.7f}' for x in v]))]})
  if kfs:animators[uid(names[i])]={'type':'bone','name':names[i],'keyframes':kfs}
 ring_keys=[(0,1)] if name=='closed' else [(0,1e-6)] if name=='active' else [(0,1),(1/30,1e-6)]
 animators[uid('closed_light_ring')]={'type':'bone','name':'closed_light_ring','keyframes':[{'uuid':uid(f'{name}/ring/{t}'),'channel':'scale','time':round(t,8),'interpolation':'linear','data_points':[{'x':f'{s:.7f}','y':f'{s:.7f}','z':f'{s:.7f}'}]} for t,s in ring_keys]}
 animations.append({'uuid':uid('animation/'+name),'name':name,'loop':loop,'length':length,'snapping':30,'animators':animators})
model={'meta':{'format_version':'5.0','model_format':'free','box_uv':False},'name':'信标球','model_identifier':'stratagem_beacon','resolution':{'width':256,'height':128},'elements':elements,'outliner':outliner,'textures':[{'path':str(tex),'name':'信标球.png','folder':'','namespace':'','id':'0','uuid':uid('texture'),'source':'data:image/png;base64,'+base64.b64encode(tex.read_bytes()).decode(),'width':256,'height':128,'uv_width':256,'uv_height':128,'render_mode':'default'}],'animations':animations}
save(OUT/'模型/信标球.bbmodel',model)

# This retains the exact original skinning, including all 59 flexible seam faces.
# It is ready for a small Java/Forge mesh renderer at the deployment-integration step.
runtime={'version':1,'units_per_block':16,'source_unit':'0x16f397ca5f51f271','source_version':src.doc['scenes'][0]['extras']['Helldivers 2 Version'],'texture':'sbw_helldiver:textures/mesh/stratagem_beacon.png','emissive_texture':'sbw_helldiver:textures/mesh/stratagem_beacon_emissive.png','bones':[{'index':i,'name':names[i],'parent':src.parents.get(i) if src.parents.get(i) in used else None,'closed_matrix':G0[i].round(9).tolist()} for i in sorted(used)],'vertices':closedv.round(7).tolist(),'joint':dominant.tolist(),'faces':[],'uv':np.column_stack((uv[:,0]*128,uv[:,1]*128)).round(7).tolist(),'clips':{}}
for _,mode,f in topology:
 face={'vertices':f,'emissive':mode==1}
 if mode==1:face['uv']=[[160.5,32.5]]*3
 elif mode==3:face['uv']=[[160.5,112.5]]*3
 elif mode==2:face['uv']=[[round(216+closedv[v,0]/16/.08*64,7),round(48+closedv[v,2]/16/.08*64,7)] for v in f]
 runtime['faces'].append(face)
base_vertex_count=len(runtime['vertices']);ring_keys=list(ring_vertices);ring_indices={k:base_vertex_count+i for i,k in enumerate(ring_keys)}
runtime['vertices'].extend(ring_vertices.values());runtime['joint'].extend([100]*len(ring_keys));runtime['uv'].extend([[160.5,32.5]]*len(ring_keys))
ring_closed_matrix=np.eye(4);ring_closed_matrix[:3,3]=ring_origin/16
runtime['bones'].append({'index':100,'name':'closed_light_ring','parent':11,'closed_matrix':ring_closed_matrix.tolist()})
for f in ring_faces.values():
 ids=[ring_indices[k] for k in f['vertices']]
 for j in range(1,len(ids)-1):runtime['faces'].append({'vertices':[ids[0],ids[j],ids[j+1]],'emissive':True,'uv':[[160.5,32.5]]*3})
for name,a,times in [('closed',closed,[0]),('deploy',deploy,[t/30 for t in range(31)]),('active',active,[0])]:
 frames=[]
 for t in times:
  D=motion(a,t)[1];matrices={str(i):D[i].round(9).tolist() for i in sorted(used)};matrices['100']=ring_delta(a,t,D).round(9).tolist()
  frames.append({'time':round(t,8),'matrices':matrices})
 runtime['clips'][name]={'length':1 if name!='active' else 1/3,'loop':name!='deploy','frames':frames}
runtime['locators']={'fx_socket':[0,1.984,0],'contact_origin':[0,0,0],'closed_ring_origin':ring_origin.round(7).tolist()}
save(OUT/'模型/信标球.mesh.json',runtime)
shutil.copy2(src.FILE,OUT/'参考/原版信标球.glb')

# Reproducible rig and silhouette audit, sampled over the complete game animation.
closed_bound=np.array([closedv.min(0),closedv.max(0)])/16
openv=src.mesh_pose(primitives[0],active,0);open_bound=np.array([openv.min(0),openv.max(0)])
audit={'source_unit':'0x16f397ca5f51f271','original_triangles':len(topology),'editor_mesh_components':len(elements),'editor_faces':sum(len(e['faces']) for e in elements),'original_deploy_seconds':1,'closed_extent_blocks':np.diff(closed_bound,axis=0)[0].round(6).tolist(),'active_extent_blocks':np.diff(open_bound,axis=0)[0].round(6).tolist(),'original_rigid_vertices':len(closedv),'flexible_seam_triangles':cross_faces,'editor_seam_max_error_blocks':rigid_error,'runtime_retains_exact_seam_weights':True,'skull_bilateral_symmetry':bool(np.array_equal(badge,badge[:,::-1])),'texture_style':'Clean flat palette and structural pixel details; no wear maps','recreated_closed_ring_vertices':len(ring_keys),'runtime_total_vertices':len(runtime['vertices']),'runtime_total_triangles':len(runtime['faces']),'no_cyan_on_open_shell_uvs':True}
save(OUT/'参考/核对记录.json',audit)
print('AUDIT',json.dumps(audit,ensure_ascii=False))
if '--no-render' in sys.argv:
 print('OUTPUT',OUT);sys.exit(0)

# Render the authored meshes using the same atlas and original component motion.
bpy.ops.wm.read_factory_settings(use_empty=True)
image=bpy.data.images.load(str(tex));image.colorspace_settings.name='sRGB'
material=bpy.data.materials.new('Clean Minecraft palette');material.use_nodes=True
n=material.node_tree.nodes;links=material.node_tree.links;n.clear();bs=n.new('ShaderNodeBsdfPrincipled');output=n.new('ShaderNodeOutputMaterial');links.new(bs.outputs['BSDF'],output.inputs['Surface']);bs.inputs['Roughness'].default_value=.8
tn=n.new('ShaderNodeTexImage');tn.image=image;tn.interpolation='Closest';links.new(tn.outputs['Color'],bs.inputs['Base Color'])
glow=bpy.data.images.load(str(OUT/'贴图/信标球_发光.png'));glow.colorspace_settings.name='sRGB';gn=n.new('ShaderNodeTexImage');gn.image=glow;gn.interpolation='Closest';links.new(gn.outputs['Color'],bs.inputs['Emission Color']);bs.inputs['Emission Strength'].default_value=.8
bs.inputs['Specular IOR Level'].default_value=.12
allob=[]
for e in elements:
 vkeys=list(e['vertices']);vmap={k:i for i,k in enumerate(vkeys)}
 me=bpy.data.meshes.new(e['name']);me.from_pydata([np.array(e['vertices'][k])[[0,2,1]]*[1,-1,1]/16 for k in vkeys],[],[[vmap[k] for k in f['vertices']] for f in e['faces'].values()]);me.update();layer=me.uv_layers.new(name='UVMap')
 for p,f in zip(me.polygons,e['faces'].values()):
  for li,k in zip(p.loop_indices,f['vertices']):u,v=f['uv'][k];layer.data[li].uv=(u/256,1-v/128)
 ob=bpy.data.objects.new(e['name'],me);bpy.context.collection.objects.link(ob);ob.data.materials.append(material);allob.append(ob)
scene=bpy.context.scene;scene.render.engine='CYCLES';scene.cycles.samples=20;scene.cycles.use_denoising=True
scene.render.resolution_x=1000;scene.render.resolution_y=1000;scene.render.resolution_percentage=100;scene.render.image_settings.file_format='PNG';scene.view_settings.view_transform='Standard'
scene.world=bpy.data.worlds.new('World');scene.world.use_nodes=True;wn=scene.world.node_tree.nodes;wn.clear();bg=wn.new('ShaderNodeBackground');wo=wn.new('ShaderNodeOutputWorld');scene.world.node_tree.links.new(bg.outputs[0],wo.inputs[0]);bg.inputs[0].default_value=(.12,.145,.14,1);bg.inputs[1].default_value=.6
cam=bpy.data.objects.new('Camera',bpy.data.cameras.new('Camera'));scene.collection.objects.link(cam);scene.camera=cam;cam.data.type='ORTHO'
target=Vector((0,0,.06))
for name,loc,power in [('key',(.3,-.4,.5),1.1),('fill',(-.3,-.1,.3),.6),('rim',(.1,.3,.4),.8)]:
 lamp=bpy.data.objects.new(name,bpy.data.lights.new(name,'AREA'));scene.collection.objects.link(lamp);lamp.location=loc;lamp.data.energy=power;lamp.data.size=.3;lamp.rotation_euler=(target-lamp.location).to_track_quat('-Z','Y').to_euler()
for name,a,t in [('收纳',closed,0),('展开',active,0)]:
 D=motion(a,t)[1]
 for ob,e in zip(allob,elements):
  matrix=ring_delta(a,t,D) if e['name']=='closed_light_ring' else D[next(i for i in used if names[i]==e['name'])]
  cv=np.array(list(e['vertices'].values()))/16;vv=(matrix@np.column_stack((cv,np.ones(len(cv)))).T).T[:,:3];vv=vv[:,[0,2,1]]*[1,-1,1]
  for vert,co in zip(ob.data.vertices,vv):vert.co=co
 for view,loc,zoom in [('斜视',(.38,-.5,.38),.34 if name=='展开' else .21),('俯视',(0,0,.6),.34 if name=='展开' else .20)]:
  cam.location=loc;cam.rotation_euler=(target-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.ortho_scale=zoom;scene.render.filepath=str(OUT/'预览'/f'{name}_{view}.png');bpy.ops.render.render(write_still=True)
print('OUTPUT',OUT)
