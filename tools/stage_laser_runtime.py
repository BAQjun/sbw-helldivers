"""Import only the accepted laser model; existing turrets remain byte-identical."""
from pathlib import Path
import ast,math,json,copy,shutil,hashlib
ROOT=Path(__file__).resolve().parents[1]
MODEL=Path('D:/Codex/projects/2026-09-19/new-chat')
W=MODEL/'work/laser_sentry/runtime';A=ROOT/'src/main/resources/assets/sbw_helldiver';D=ROOT/'src/main/resources/data'
def load(p):return json.loads(p.read_text(encoding='utf8'))
def save(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
# Reuse pure coordinate helpers, never execute the old bulk importer.
tree=ast.parse((ROOT/'tools/import_sentry_assets.py').read_text(encoding='utf8'))
env={'math':math}
exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name in ['rotate','transformed','native_nozzle','last','snapshot']],type_ignores=[]),'<coordinate helpers>','exec'),env)
transformed,native_nozzle,snapshot=[env[n] for n in ['transformed','native_nozzle','snapshot']]
old_hashes={str(p.relative_to(A)):hashlib.sha256(p.read_bytes()).hexdigest() for folder in ['models/bedrock/vehicle','textures/bedrock/vehicle','animations/bedrock/vehicle','sbw/vehicles'] for p in (A/folder).glob('*') if 'laser' not in p.name}
model='laser_hellpod_v1'
geo=load(W/f'{model}.geo.json')
# SBW reserves ^laser.* for native beam emitters; structural meshes must not match.
rename={b['name']:'ls_'+b['name'][6:] for b in geo['minecraft:geometry'][0]['bones'] if b['name'].startswith('laser_')}
for b in geo['minecraft:geometry'][0]['bones']:
    b['name']=rename.get(b['name'],b['name'])
    if 'parent' in b:b['parent']=rename.get(b['parent'],b['parent'])
bones={b['name']:b for b in geo['minecraft:geometry'][0]['bones']}
# Reuse identical attribute entries without changing a single triangle corner value.
for b in bones.values():
    pm=b.get('poly_mesh')
    if not pm:continue
    maps=[]
    for key in ['positions','normals','uvs']:
        unique={};values=[];mapping=[]
        for value in pm[key]:
            t=tuple(value)
            if t not in unique:unique[t]=len(values);values.append(value)
            mapping.append(unique[t])
        pm[key]=values;maps.append(mapping)
    pm['polys']=[[[maps[k][v[k]] for k in range(3)] for v in poly] for poly in pm['polys']]
anim=load(W/f'{model}.animation.json')
for a in anim['animations'].values():
    a['bones']={rename.get(k,k):v for k,v in a.get('bones',{}).items() if not k.startswith(('laser_heat_','ls_heat_'))}
anim['animations'].pop('animation.laser.heat_preview',None)
audio=load(A/'helldivers/audio.json');cues={}
for name,a in anim['animations'].items():
    events=[]
    for stamp,rows in a.pop('sound_effects',{}).items():
        for e in rows if isinstance(rows,list) else [rows]:events.append({'time':float(stamp),'effect':e['effect'].replace('helldivers_sentries:','sbw_helldiver:').split(':')[-1],'locator':e.get('locator','')})
    cues[name]={'length':a.get('animation_length',0),'events':sorted(events,key=lambda e:e['time'])}
    if 'pod_eject_cap' in a.get('bones',{}) and name in ['animation.hellpod.open','animation.hellpod.deploy','animation.hellpod.cap_eject','animation.hellpod.retro_landing_preview']:
        a['bones']['pod_eject_cap']={'position':[0,0,0],'rotation':[0,0,0],'scale':0}
for src,dst in [('animation.hellpod.deploy','animation.helldiver.deployed'),('animation.hellpod.retract','animation.helldiver.retracted')]:anim['animations'][dst]=snapshot(anim['animations'][src])
destroyed=copy.deepcopy(anim['animations']['animation.helldiver.deployed']);destroyed.update(loop='hold_on_last_frame',animation_length=.4)
for b,ch in destroyed['bones'].items():
    if b.startswith('pod_iris_'):ch['rotation']={'0.0':[0,-68,0],'0.4':[0,0,0]};ch['scale']={'0.0':[0,1,0],'0.4':[1,1,1]}
anim['animations']['animation.helldiver.destroyed']=destroyed
for a in anim['animations'].values():
    a.setdefault('bones',{})['pod_root']={'position':[0,0,0]}
    # Preserve the separately hidden display segments as runtime bones after
    # removing the native laser prefix. Their authored pixels use the glow pass.
    for i in range(20):a['bones'][f'ls_heat_{i:02d}']={'scale':0}
for name in list(anim['animations']):
    if not name.startswith('animation.'):del anim['animations'][name]
save(A/f'models/bedrock/vehicle/{model}.geo.json',geo)
(A/f'models/bedrock/vehicle/{model}.geo.json').write_text(json.dumps(geo,separators=(',',':')),encoding='utf8')
save(A/f'animations/bedrock/vehicle/{model}.animation.json',anim)
(A/'textures/bedrock/vehicle').mkdir(parents=True,exist_ok=True);shutil.copy2(W/f'{model}.png',A/f'textures/bedrock/vehicle/{model}.png')
profiles=load(A/'helldivers/profiles.json');p=copy.deepcopy(profiles['machine_gun_sentry'])
barrel=bones['barrel']['pivot'];turret=bones['turret']['pivot'];bp=[barrel[0]/16,barrel[1]/16,-barrel[2]/16]
locs=[(b['name'],loc) for b in bones.values() for n,loc in b.get('locators',{}).items() if n=='muzzle_exit'];assert len(locs)==1,locs
mb,loc=locs[0];mp=loc.get('offset',[0,0,0]) if isinstance(loc,dict) else loc
point=transformed(mp,mb,bones);world=[point[0]/16,point[1]/16,-point[2]/16]
p.update(model=model,deploy_ticks=math.ceil(anim['animations']['animation.hellpod.deploy']['animation_length']*20),retract_ticks=68,reload_ticks=0,aim_height=barrel[1]/16,
    muzzle_offsets=[[world[i]-bp[i] for i in range(3)]],muzzle_bone=mb,muzzle_model_position=[-point[0]/16,point[1]/16,point[2]/16],total_ammo=0,
    burst_size=1,burst_interval_ticks=1,burst_pause_ticks=0,shell_offset=None,shell_direction=None,shell_bone=None,shell_model_position=None,
    turret_pos=[turret[0]/16,turret[1]/16,-turret[2]/16],barrel_pos=[(barrel[0]-turret[0])/16,(barrel[1]-turret[1])/16,-(barrel[2]-turret[2])/16])
profiles['laser_sentry']=p;save(A/'helldivers/profiles.json',profiles)
# Editable model clips contain native marker events, but the existing runtime adds
# the audible hellpod layers to them. Reuse those complete shared layers here too.
pod_audio=audio['models'][profiles['machine_gun_sentry']['model']]
shared_pod_prefixes=('sfx_hellpod_','sfx_original_stop_')
for name,reference in pod_audio.items():
    if not name.startswith('animation.hellpod.'):continue
    shared=[copy.deepcopy(e) for e in reference['events'] if e['effect'].startswith(shared_pod_prefixes)]
    current=cues.setdefault(name,{'length':reference['length'],'events':[]})
    current['events']=[e for e in current['events'] if not e['effect'].startswith(shared_pod_prefixes)]+shared
    current['events'].sort(key=lambda e:e['time'])
audio['models'][model]=cues;save(A/'helldivers/audio.json',audio)
vehicle=load(D/'sbw_helldiver/sbw/vehicles/machine_gun_sentry.json');vehicle.update(ID='sbw_helldiver:laser_sentry',TurretPos=p['turret_pos'],BarrelPos=p['barrel_pos'],TurretTurnSpeed=[8,8])
vehicle['SeekInfo'].update(MinSeekRange=0,MaxSeekRange=50)
vehicle['Weapons']['Main'].update(RPM=1200,Damage=2,Magazine=1,Spread=0,Gravity=0,SoundInfo={},ShootPos={'Transform':'Barrel','Positions':p['muzzle_offsets'],'Directions':['Barrel']})
save(D/'sbw_helldiver/sbw/vehicles/laser_sentry.json',vehicle)
save(A/'sbw/vehicles/laser_sentry.json',{'Animation':f'sbw_helldiver:animations/bedrock/vehicle/{model}.animation.json','Models':[{'Model':f'sbw_helldiver:models/bedrock/vehicle/{model}.geo.json','Texture':f'sbw_helldiver:textures/bedrock/vehicle/{model}.png'}]})
for damage in ['laser_beam']:save(D/f'sbw_helldiver/damage_type/{damage}.json',{'message_id':f'sbw_helldiver.{damage}','scaling':'never','exhaustion':0})
for tag in ['bypasses_cooldown','bypasses_armor','bypasses_resistance','bypasses_enchantments']:
    path=D/f'minecraft/tags/damage_type/{tag}.json';doc=load(path)
    for damage in ['laser_beam']:
        value='sbw_helldiver:'+damage
        if value not in doc['values']:doc['values'].append(value)
    save(path,doc)
for rel,digest in old_hashes.items():assert hashlib.sha256((A/rel).read_bytes()).hexdigest()==digest,rel
save(W/'runtime_stage_report.json',{'profile':p,'otherRuntimeAssetsUnchanged':len(old_hashes),'existingHashManifest':old_hashes,'flameMeshTriangles':sum(len(b.get('poly_mesh',{}).get('polys',[])) for b in bones.values())})

for lang,label in [('zh_cn','激光哨戒炮'),('en_us','Laser Sentry')]:
    path=A/f'lang/{lang}.json';doc=load(path)
    doc.update({'entity.sbw_helldiver.laser_sentry':label,'item.sbw_helldiver.laser_sentry_stratagem':label,
        'death.attack.sbw_helldiver.laser_beam':'%1$s 被激光烧穿了' if lang=='zh_cn' else '%1$s was burned through by a laser',
        'death.attack.sbw_helldiver.laser_beam.player':'%1$s 被 %2$s 的激光烧穿了' if lang=='zh_cn' else '%1$s was burned through by %2$s laser'})
    save(path,doc)
save(A/'models/item/laser_sentry_stratagem.json',{'parent':'minecraft:item/generated','textures':{'layer0':'sbw_helldiver:item/laser_sentry_stratagem'}})
print(json.dumps({'profile':p,'existingModelAssetsUnchanged':len(old_hashes)},ensure_ascii=False))
