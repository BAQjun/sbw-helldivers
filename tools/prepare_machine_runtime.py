"""Export the accepted machine/container GLBs, original sample glyphs and sample recipes."""
import base64,gzip,io,json,struct,sys,xml.etree.ElementTree as ET
from pathlib import Path
ART=Path('D:/Codex/projects/2026-09-19/new-chat')
sys.path[:0]=[str(ART/'work/survival06/python_libs'),str(ART/'work/survival06')]
import numpy as np
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'src/main/resources/assets/sbw_helldiver'
SRC=ART/'outputs/当前交付/模型/0.6生存机器'
def export(name,path):
    raw=path.read_bytes();size=struct.unpack_from('<I',raw,12)[0];g=json.loads(raw[20:20+size]);blob=raw[28+size:]
    def accessor(i):
        a=g['accessors'][i];v=g['bufferViews'][a['bufferView']];dtype={5126:'<f4',5125:'<u4',5123:'<u2',5121:'u1'}[a['componentType']];n={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4}[a['type']]
        return np.ndarray((a['count'],n),dtype=dtype,buffer=blob,offset=v.get('byteOffset',0)+a.get('byteOffset',0),strides=(v.get('byteStride',np.dtype(dtype).itemsize*n),np.dtype(dtype).itemsize)).copy()
    def rounded(a):return np.round(a,7).tolist()
    parents={c:i for i,n in enumerate(g['nodes'])for c in n.get('children',[])}
    nodes=[dict(parent=parents.get(i,-1),name=n.get('name',''),t=n.get('translation',[0,0,0]),r=n.get('rotation',[0,0,0,1]),s=n.get('scale',[1,1,1]),mesh=n.get('mesh',-1))for i,n in enumerate(g['nodes'])]
    assert not any('matrix'in n for n in g['nodes']), 'Preserve matrix nodes if introduced'
    meshes=[]
    for m in g.get('meshes',[]):
        primitives=[]
        for p in m['primitives']:
            a=p['attributes'];positions=accessor(a['POSITION'])
            primitives.append(dict(material=p.get('material',0),p=rounded(positions),uv=rounded(accessor(a['TEXCOORD_0'])),indices=accessor(p['indices']).ravel().tolist()))
        meshes.append(primitives)
    clips={}
    for a in g.get('animations',[]):
        channels=[]
        for c in a['channels']:
            s=a['samplers'][c['sampler']];channels.append(dict(node=c['target']['node'],path=c['target']['path'],times=accessor(s['input']).ravel().tolist(),values=rounded(accessor(s['output'])),interpolation=s.get('interpolation','LINEAR')))
        clips[a['name'].rsplit('.',1)[-1]]=channels
    # Blender machine exports reverse Z; Blockbench container exports already use
    # block-grid coordinates. Preserve both accepted sources without mirroring art.
    if not name.endswith('_container'):
        for n in nodes:
            n['t'][2]*=-1
            n['r'][0]*=-1;n['r'][1]*=-1
        for m in meshes:
            for p in m:
                for v in p['p']:v[2]*=-1
                for k in range(0,len(p['indices']),3):p['indices'][k+1],p['indices'][k+2]=p['indices'][k+2],p['indices'][k+1]
        for channels in clips.values():
            for c in channels:
                for v in c['values']:
                    if c['path']=='translation':v[2]*=-1
                    elif c['path']=='rotation':v[0]*=-1;v[1]*=-1
    # The closed shell has authored animation offsets: sample its actual pose.
    import copy
    closed=copy.deepcopy(nodes)
    for c in clips.get('deploy',[]):closed[c['node']][{'translation':'t','rotation':'r','scale':'s'}[c['path']]]=c['values'][0]
    matrices={}
    def world(i):
        if i in matrices:return matrices[i]
        n=closed[i];x,y,z,w=n['r']
        a=np.array([[1-2*y*y-2*z*z,2*x*y-2*z*w,2*x*z+2*y*w,0],[2*x*y+2*z*w,1-2*x*x-2*z*z,2*y*z-2*x*w,0],[2*x*z-2*y*w,2*y*z+2*x*w,1-2*x*x-2*y*y,0],[0,0,0,1]],dtype=float)
        a[:3,:3]*=np.array(n['s']);a[:3,3]=n['t'];matrices[i]=world(n['parent'])@a if n['parent']>=0 else a;return matrices[i]
    nozzles=[];allpoints=[];edges=[]
    for i,n in enumerate(closed):
        if n['mesh']<0:continue
        points=np.concatenate([np.array(p['p'])for p in meshes[n['mesh']]])
        points=(world(i)@np.column_stack([points,np.ones(len(points))]).T).T[:,:3];allpoints.extend(points)
        if '深处遮光板' in n['name']:
            mouth=(points.min(axis=0)+points.max(axis=0))/2;mouth[1]=-.23
            nozzles.append(np.round(mouth,5).tolist())
        if not name.endswith('_container'):
            for primitive in meshes[n['mesh']]:
                local=np.array(primitive['p']);vertices=(world(i)@np.column_stack([local,np.ones(len(local))]).T).T[:,:3]
                triangles=vertices[np.array(primitive['indices']).reshape(-1,3)]
                edges.extend(np.stack([triangles,np.roll(triangles,-1,axis=1)],axis=2).reshape(-1,2,3))
    points=np.array(allpoints)
    print(name,'closed bounds',points.min(axis=0).round(4).tolist(),points.max(axis=0).round(4).tolist(),'jets',nozzles)
    if not name.endswith('_container'):
        # Horizontal envelopes of the accepted mesh, clipped at each height.
        # Include edge/plane intersections, not only vertex positions: long
        # armour panels otherwise leave gaps in intermediate block layers.
        edge=np.array(edges);boxes=[]
        for low in np.arange(0,np.ceil(points[:,1].max()*4)/4,.25):
            high=low+.25;band=[points[(points[:,1]>=low)&(points[:,1]<=high)]]
            for plane in [low,high]:
                mask=(edge[:,0,1]<plane)&(edge[:,1,1]>=plane)|(edge[:,1,1]<plane)&(edge[:,0,1]>=plane)
                crossing=edge[mask]
                if len(crossing):
                    fraction=(plane-crossing[:,0,1])/(crossing[:,1,1]-crossing[:,0,1])
                    band.append(crossing[:,0]+fraction[:,None]*(crossing[:,1]-crossing[:,0]))
            selected=np.concatenate(band)
            if not len(selected):continue
            lo=np.floor(selected.min(axis=0)*16+1e-5)/16;hi=np.ceil(selected.max(axis=0)*16-1e-5)/16
            half=1.5 if name=='bioprocessor' else 2.5
            lo[0]=max(-half,lo[0]);hi[0]=min(half,hi[0]);lo[2]=max(-half,lo[2]);hi[2]=min(.5 if name=='bioprocessor' else 2.5,hi[2])
            boxes.append([float(lo[0]),float(low),float(lo[2]),float(hi[0]),float(min(high,hi[1])),float(hi[2])])
        (OUT/f'helldivers/{name}_collision.json').write_text(json.dumps(boxes,separators=(',',':'))+'\n')
    target=OUT/f'models/mesh/{name}.mesh.json.gz';target.parent.mkdir(parents=True,exist_ok=True)
    from optimize_machine_meshes import optimize
    for mesh in meshes:
        for primitive in mesh:optimize(primitive)
    target.write_bytes(gzip.compress(json.dumps(dict(nodes=nodes,meshes=meshes,clips=clips,nozzles=nozzles),separators=(',',':')).encode(),mtime=0))
    for i,m in enumerate(g['materials']):
        ti=m['pbrMetallicRoughness']['baseColorTexture']['index'];img=g['images'][g['textures'][ti]['source']];v=g['bufferViews'][img['bufferView']]
        im=Image.open(io.BytesIO(blob[v.get('byteOffset',0):v.get('byteOffset',0)+v['byteLength']])).convert('RGBA')
        im.save(OUT/f'textures/mesh/{name}_{i}.png')
    print(name,len(nodes),'nodes',sum(len(p['indices'])//3 for m in meshes for p in m),'triangles',list(clips))
for name,path in [('bioprocessor',SRC/'生物处理器_潜兵改编.glb'),('refinery',SRC/'精炼厂_潜兵改编.glb'),('bioprocessor_container',SRC/'部署集装箱/生物处理器_部署集装箱.glb'),('refinery_container',SRC/'部署集装箱/精炼厂_部署集装箱.glb')]:export(name,path)

# Render the native XAML paths, retaining their original tier colours.
import subprocess
native=ART/'work/survival06/ui_native/content/ui/shared/resources/generated_icons/sample_icons.xaml'
subprocess.run(['powershell.exe','-NoProfile','-STA','-File',str(ROOT/'tools/export_sample_icons.ps1'),'-Source',str(native),'-OutputDirectory',str(OUT/'textures/item')],check=True)
(OUT/'helldivers/sample_icon_sources.json').write_text(json.dumps({'source':'content/ui/shared/resources/generated_icons/sample_icons.xaml','templates':['DataTemplate.Icon.SampleGroupType.'+t for t in ['Common','Rare','Super']]},indent=2)+'\n')

def write(path,data):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
for tier in ['common','rare','super']:write(OUT/f'models/item/{tier}_sample.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'sbw_helldiver:item/{tier}_sample'}})
for machine in ['bioprocessor','refinery']:
    # Item-atlas sources include textures/item, not standalone GUI textures.
    (OUT/f'textures/item/{machine}_container.png').write_bytes((OUT/f'textures/gui/machines/{machine}.png').read_bytes())
    write(OUT/f'models/item/{machine}_container.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'sbw_helldiver:item/{machine}_container'}})
write(OUT/'blockstates/sample_machine.json',{'variants':{'':{'model':'minecraft:block/air'}}})
for tag in ['mineable/pickaxe','needs_diamond_tool']:write(ROOT/f'src/main/resources/data/minecraft/tags/block/{tag}.json',{'replace':False,'values':['sbw_helldiver:sample_machine']})

bio=[('rotten_flesh',5,70,5,29,1,1,1),('bone',5,70,5,29,1,1,1),('spider_eye',3,65,6,33,2,2,1),('gunpowder',5,70,5,29,1,1,1),('slime_ball',5,70,5,29,1,1,1),('magma_cream',2,55,10,40,4,5,1),('blaze_rod',2,50,12,45,5,5,1),('breeze_rod',1,45,16,48,6,7,2),('ender_pearl',1,50,12,45,5,5,1),('ghast_tear',1,35,20,55,8,10,2),('phantom_membrane',2,50,12,45,5,5,1),('prismarine_shard',5,60,8,37,3,3,1),('wet_sponge',1,35,24,55,10,10,2),('shulker_shell',1,35,24,55,10,10,2),('wither_skeleton_skull',1,20,48,60,16,20,4),('totem_of_undying',1,25,32,60,12,15,3),('saddle',1,45,16,48,6,7,2),('ominous_bottle',1,30,32,55,12,15,3),('porkchop',5,70,5,29,1,1,1),('echo_shard',1,20,64,60,20,20,5),('nether_star',1,10,64,60,32,30,8),('dragon_head',1,20,64,60,20,20,5)]
ref=[('coal',8,75,5,24,1,1,1),('raw_copper',8,70,5,29,1,1,1),('raw_iron',5,65,6,33,2,2,1),('raw_gold',3,55,10,40,4,5,1),('redstone',8,65,6,33,2,2,1),('lapis_lazuli',8,60,8,37,3,3,1),('quartz',8,65,6,33,2,2,1),('amethyst_shard',4,60,8,37,3,3,1),('glowstone_dust',8,65,6,33,2,2,1),('flint',8,75,5,24,1,1,1),('emerald',1,50,12,45,5,5,1),('diamond',1,30,24,60,10,10,2),('ancient_debris',1,20,48,60,16,20,4)]
for machine,rows in [('bioprocessor',bio),('refinery',ref)]:
    for item,count,*values in rows:
        write(ROOT/f'src/main/resources/data/sbw_helldiver/recipe/samples/{machine}/{item}.json',{'type':'sbw_helldiver:sample_processing','machine':machine,'ingredient':{'item':'minecraft:'+item},'count':count,'ticks':100,'outcomes':[dict(tier=i,chance=values[i*2],count=values[i*2+1])for i in range(3)]})
write(ROOT/'src/main/resources/data/sbw_helldiver/recipe/samples/bioprocessor/dragon_egg.json',{'type':'sbw_helldiver:sample_processing','machine':'bioprocessor','ingredient':{'item':'minecraft:dragon_egg'},'count':1,'ticks':100,'outcomes':[{'tier':2,'chance':100,'count':50}]})
for lang in ['zh_cn','en_us']:
    path=OUT/f'lang/{lang}.json';data=json.loads(path.read_text(encoding='utf-8'));cn=lang=='zh_cn'
    data.pop('screen.sbw_helldiver.machine_energy',None)
    data['terminal.sbw_helldiver.recall']='收回信标' if cn else 'Recall beacon'
    data.update({'block.sbw_helldiver.sample_machine':'样本生产设备' if cn else 'Sample Production Machine','item.sbw_helldiver.common_sample':'普通样本' if cn else 'Common Sample','item.sbw_helldiver.rare_sample':'稀有样本' if cn else 'Rare Sample','item.sbw_helldiver.super_sample':'超级样本' if cn else 'Super Sample','item.sbw_helldiver.bioprocessor_container':'生物处理器集装箱' if cn else 'Bioprocessor Container','item.sbw_helldiver.refinery_container':'精炼厂集装箱' if cn else 'Refinery Container','jei.sbw_helldiver.deployment':'设备部署材料' if cn else 'Equipment Deployment Materials','jei.sbw_helldiver.chance':'概率：%s%%（每批随机一种）' if cn else '%s%% chance (one result per batch)','jei.sbw_helldiver.processing':'%s 秒 · %s RF/t' if cn else '%s seconds · %s RF/t','jei.sbw_helldiver.one_result':'每批随机产出其中一种' if cn else 'One random result per batch','terminal.sbw_helldiver.deployment_blocked':'落点被阻挡，已退还设备集装箱' if cn else 'Landing site blocked; equipment container returned'})
    write(path,data)
print('Exported 36 processing recipes, accepted meshes and native sample glyphs.')
