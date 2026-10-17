"""Import the reviewed D: delivery and produce SBW-compatible animation/audio resources."""
from pathlib import Path
import json,shutil,math,hashlib,itertools,copy
ROOT=Path(__file__).resolve().parents[1]
SOURCE=Path('D:/Codex/projects/2026-09-19/new-chat/outputs/当前交付')
RES=ROOT/'src/main/resources';ASSETS=RES/'assets/sbw_helldiver'
PROFILES={'machine_gun_sentry':'amg43_hellpod_v3','gatling_sentry':'gatling_hellpod_v3','rocket_sentry':'rocket_hellpod_v1','autocannon_sentry':'autocannon_hellpod_v1','supply_hellpod':'hellpod_v3'}
def load(p):return json.loads(p.read_text(encoding='utf8'))
def save(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
def remap(v):
 if isinstance(v,str):return v.replace('helldivers_sentries:','sbw_helldiver:')
 if isinstance(v,list):return [remap(x) for x in v]
 if isinstance(v,dict):return {k:remap(x) for k,x in v.items()}
 return v
def rotate(p,pivot,angles):
 x,y,z=[p[i]-pivot[i] for i in range(3)]
 for axis,deg in enumerate(angles):
  c,s=math.cos(math.radians(deg)),math.sin(math.radians(deg))
  if axis==0:y,z=y*c-z*s,y*s+z*c
  elif axis==1:x,z=x*c+z*s,-x*s+z*c
  else:x,y=x*c-y*s,x*s+y*c
 return [x+pivot[0],y+pivot[1],z+pivot[2]]
def transformed(p,bone,bones):
 while bone:
  b=bones[bone];p=rotate(p,b.get('pivot',[0,0,0]),b.get('rotation',[0,0,0]));bone=b.get('parent')
 return p
def native_nozzle(p,bone,bones,direction=False):
 # SBW mirrors X and negates authored X/Y rotations when baking Bedrock geometry.
 p=list(p) if direction else [-p[0],p[1],p[2]]
 while bone:
  b=bones[bone];pivot=b.get('pivot',[0,0,0]);angles=b.get('rotation',[0,0,0])
  p=rotate(p,[0,0,0] if direction else [-pivot[0],pivot[1],pivot[2]],[-angles[0],-angles[1],angles[2]])
  bone=b.get('parent')
 scale=1 if direction else 16
 return [-p[0]/scale,p[1]/scale,-p[2]/scale]
def last(v):
 if isinstance(v,dict):
  if 'post' in v or 'pre' in v:return last(v.get('post',v.get('pre')))
  return last(v[max(v,key=lambda x:float(x))])
 return v
def snapshot(animation):return {'loop':True,'animation_length':1,'bones':{b:{channel:last(v) for channel,v in channels.items()} for b,channels in animation.get('bones',{}).items()}}

source_assets=SOURCE/'assets/helldivers_sentries'
# Beacon UI audio belongs to the addon, not the turret asset bundle.
beacon_sounds={k:v for k,v in load(ASSETS/'sounds.json').items() if k.startswith('beacon_')} if (ASSETS/'sounds.json').exists() else {}
shutil.copytree(source_assets,ASSETS,dirs_exist_ok=True)
shutil.copytree(SOURCE/'模型',ROOT/'_asset_pack/当前模型',dirs_exist_ok=True)
for obsolete in ['amg43_hellpod_v2','gatling_hellpod_v2','hellpod_v2']:
 for folder,suffix in [('models','.geo.json'),('animations','.animation.json'),('textures','.png')]:
  old=ASSETS/f'{folder}/bedrock/vehicle/{obsolete}{suffix}'
  assert old.resolve().is_relative_to(ASSETS.resolve())
  if old.is_file():
   archive=ROOT/'.codex/backups/20261001_start/retired_runtime'/old.relative_to(ASSETS)
   archive.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(old,archive)
   assert hashlib.sha256(archive.read_bytes()).digest()==hashlib.sha256(old.read_bytes()).digest()
   old.unlink()
sounds=remap(load(ASSETS/'sounds.json'))
sounds.update(beacon_sounds)
for effect,event in sounds.items():
 if effect.startswith('beacon_'):continue
 for variant in event['sounds']:
  if isinstance(variant,dict):
   variant['attenuation_distance']=1024 if 'entry' in effect else 256 if any(t in effect for t in ['retro','thruster']) else 96 if 'impact' in effect else 64 if 'fire' in effect else 32
save(ASSETS/'sounds.json',sounds)
manifest=load(SOURCE/'音效说明/事件与音效.json')
registry=load(ASSETS/'sounds.json')
audio={'effects':{},'models':{}}
for effect,e in manifest['effects'].items():
 p=e.get('preview_playback',{})
 audio['effects'][effect]={'kind':e['kind'],'audible':effect in registry,'fade_in':p.get('fade_in_seconds',0),'fade_out':p.get('fade_out_seconds',e.get('fade_out_seconds',0)),'duration':p.get('duration_seconds',0),'stop_effects':p.get('stop_effects',e.get('stop_effects',[]))}
for file in (ASSETS/'animations/bedrock/vehicle').glob('*.json'):
 doc=load(file);model=file.name.removesuffix('.animation.json');cues={}
 for name,animation in doc['animations'].items():
  events=[]
  for stamp,entries in animation.pop('sound_effects',{}).items():
   for e in entries if isinstance(entries,list) else [entries]:events.append({'time':float(stamp),'effect':e['effect'],'locator':e.get('locator','')})
  cues[name]={'length':animation.get('animation_length',0),'events':sorted(events,key=lambda x:x['time'])}
  if 'rotor' in animation.get('bones',{}):
   # Runtime uses a three-barrel firing clock, including acceleration/deceleration.
   animation['bones']['rotor'].pop('rotation',None)
  # Physical debris replaces the old trajectory/scale-to-zero animation. Audio cues stay intact.
  if 'pod_eject_cap' in animation.get('bones',{}) and name in [
      'animation.hellpod.open','animation.hellpod.deploy','animation.hellpod.cap_eject',
      'animation.hellpod.retro_landing_preview']:
   animation['bones']['pod_eject_cap']={'position':[0,0,0],'rotation':[0,0,0],'scale':0}
 for source,target in [('animation.hellpod.deploy','animation.helldiver.deployed'),('animation.hellpod.retract','animation.helldiver.retracted')]:
  if source in doc['animations']:doc['animations'][target]=snapshot(doc['animations'][source])
 if model in [PROFILES[k] for k in PROFILES if k!='supply_hellpod']:
  destroyed=copy.deepcopy(doc['animations']['animation.helldiver.deployed']);destroyed.update(loop='hold_on_last_frame',animation_length=.4)
  for b,ch in destroyed['bones'].items():
   if b.startswith('pod_iris_'):
    ch['rotation']={'0.0':[0,-68,0],'0.4':[0,0,0]};ch['scale']={'0.0':[0,1,0],'0.4':[1,1,1]}
  doc['animations']['animation.helldiver.destroyed']=destroyed
 # Keep pod_root as a live runtime bone for separate intact/wreck material passes.
 geo=load(ASSETS/f'models/bedrock/vehicle/{model}.geo.json')
 if any(b['name']=='pod_root' for b in geo['minecraft:geometry'][0]['bones']):
  for a in doc['animations'].values():a.setdefault('bones',{})['pod_root']={'position':[0,0,0]}
 save(file,doc);audio['models'][model]=cues
save(ASSETS/'helldivers/audio.json',audio)

# Isolate the original 84-cube cap, preserving its atlas UVs and white backside catches.
capdoc=load(ASSETS/'models/bedrock/vehicle/hellpod_v3.geo.json')
capgeo=capdoc['minecraft:geometry'][0]
capbone=copy.deepcopy(next(b for b in capgeo['bones'] if b['name']=='pod_eject_cap'))
capbone.pop('parent',None)
shift=3.3225
capbone['pivot'][1]-=shift
for cube in capbone['cubes']:
 cube['origin'][1]-=shift
 if 'pivot' in cube:cube['pivot'][1]-=shift
capgeo['bones']=[capbone]
capgeo['description'].update(identifier='geometry.hellpod_cap',visible_bounds_width=2,
    visible_bounds_height=2,visible_bounds_offset=[0,0,0])
save(ASSETS/'models/bedrock/misc/hellpod_cap.geo.json',capdoc)
# MG cases must start outside the actual moving bolt at the centre of its side port.
def calibrate_mg_shell(doc):
 bones={b['name']:b for b in doc['minecraft:geometry'][0]['bones']}
 bolt=bones['bolt']['cubes'][0];port=bones['recoil']['cubes'][-1]
 assert port['origin'][2] < -3 and port['size'][2] > 1.7
 centre=[bolt['origin'][0]+bolt['size'][0]+.256,port['origin'][1]+port['size'][1]/2,port['origin'][2]+port['size'][2]/2]
 bones['recoil']['locators']['shell_exit']=centre
 return centre
for path in (ROOT/'_asset_pack/当前模型').rglob('机枪*.bbmodel'):
 doc=load(path);port=next(e for e in doc['elements'] if e.get('name')=='v4_ejection_port');bolt=next(e for e in doc['elements'] if e.get('name')=='v4_bolt')
 next(e for e in doc['elements'] if e.get('name')=='shell_exit')['position']=[bolt['from'][0]-.256,(port['from'][1]+port['to'][1])/2,(port['from'][2]+port['to'][2])/2]
 save(path,doc)
path=ASSETS/'models/bedrock/vehicle/amg43_sentry_v4_audio.geo.json';doc=load(path);calibrate_mg_shell(doc);save(path,doc)

definitions={}
for entity,model in PROFILES.items():
 definitions.setdefault(entity,{})
 geometry_path=ASSETS/f'models/bedrock/vehicle/{model}.geo.json';doc=load(geometry_path)
 bones={b['name']:b for b in doc['minecraft:geometry'][0]['bones']}
 # The rebuilt cannon has its bore at y=31.1; its inherited old marker was 3.85 units too low.
 if entity=='autocannon_sentry':
  for b in bones.values():
   if 'muzzle_exit' in b.get('locators',{}):b['locators']['muzzle_exit'][1]=31.1
  # The rebuilt receiver moved the ejection hatch to its top; its old marker stayed inside the magazine.
  hatch=bones['ejector']['cubes'][0]
  top=max(c['origin'][1]+c['size'][1] for c in bones['ejector']['cubes'])
  centre=[hatch['origin'][0]+hatch['size'][0]/2,top+.08,hatch['origin'][2]+hatch['size'][2]/2]
  bones['ejector']['locators']['shell_exit']={'offset':centre,'rotation':[60,0,0]}
  save(geometry_path,doc)
 # Use the bore centre at the actual terminal muzzle face, not an inherited gun-body marker.
 if entity=='machine_gun_sentry':
  calibrate_mg_shell(doc)
  tip=min(bones['muzzle']['cubes'],key=lambda c:c['origin'][2])
  centre=[tip['origin'][0]+tip['size'][0]/2,tip['origin'][1]+tip['size'][1]/2,tip['origin'][2]-.003]
  bones['muzzle']['locators']['muzzle_exit']=centre
  save(geometry_path,doc)
 if entity=='gatling_sentry':
  # The firing station is fixed at the initial upper barrel, while RotorClock brings all 3 barrels here.
  rotor=bones['rotor'];axis=rotor['pivot'];radius=rotor['locators']['muzzle_0'][1]-axis[1]
  shifts=[]
  for index,sign in [(1,1),(2,-1)]:
   old=list(rotor['locators'][f'muzzle_{index}'])
   new=[axis[0]+sign*math.sqrt(3)*radius/2,axis[1]-radius/2,old[2]]
   shifts.append((old,new));rotor['locators'][f'muzzle_{index}']=new
  for cube in rotor['cubes']:
   centre=cube.get('pivot',[cube['origin'][i]+cube['size'][i]/2 for i in range(3)])
   for old,new in shifts:
    if abs(centre[0]-old[0])<.5 and abs(centre[1]-old[1])<.03 and centre[2]<-11:
     for i in [0,1]:
      cube['origin'][i]+=new[i]-old[i]
      if 'pivot' in cube:cube['pivot'][i]+=new[i]-old[i]
     break
  bones['rotor']['locators']['muzzle_exit']=list(bones['rotor']['locators']['muzzle_0'])
  save(geometry_path,doc)
 podpoints=[];nozzles=[];nozzle_bones=[];nozzle_positions=[];nozzle_axes=[];muzzles=[];shell_point=None;shell_direction=None
 for b in bones.values():
  if b['name'].startswith('pod'):
   for cube in b.get('cubes',[]):
    for corner in itertools.product([0,1],repeat=3):
     point=[cube['origin'][i]+corner[i]*cube['size'][i] for i in range(3)]
     point=rotate(point,cube.get('pivot',[0,0,0]),cube.get('rotation',[0,0,0]));podpoints.append(transformed(point,b['name'],bones))
  for name,loc in b.get('locators',{}).items():
   point=loc.get('offset',[0,0,0]) if isinstance(loc,dict) else loc
   point=transformed(point,b['name'],bones);world=[point[0]/16,point[1]/16,-point[2]/16]
   if name.startswith('particle_retro_nozzle'):
    exit_plate=b['cubes'][-1]
    outlet=[exit_plate['origin'][0]+exit_plate['size'][0]/2,exit_plate['origin'][1]-.005,exit_plate['origin'][2]+exit_plate['size'][2]/2]
    nozzles.append(native_nozzle(outlet,b['name'],bones));nozzle_bones.append(b['name'])
    nozzle_positions.append([-outlet[0]/16,outlet[1]/16,outlet[2]/16])
    nozzle_axes.append(native_nozzle([0,-1,0],b['name'],bones,direction=True))
   if name.startswith('muzzle_') and name!='muzzle_exit':muzzles.append((name,world))
   if name=='muzzle_exit':primary=world
   if name=='muzzle_exit':
    definitions.setdefault(entity,{})['muzzle_bone']=b['name']
    definitions[entity]['muzzle_model_position']=[-point[0]/16,point[1]/16,point[2]/16]
   if name=='shell_exit' and entity in ['machine_gun_sentry','gatling_sentry','autocannon_sentry']:
    # Native vehicle coordinates already face +Z. Renderer mirrors Bedrock X then rotates Y by 180.
    shell_point=[point[0]/16,point[1]/16,-point[2]/16]
    definitions[entity]['shell_bone']=b['name']
    definitions[entity]['shell_model_position']=[-point[0]/16,point[1]/16,point[2]/16]
    if entity=='autocannon_sentry':shell_direction=[0,math.sqrt(3)/2,-.5]
    else:shell_direction=[1 if point[0]>=0 else -1,0,0]
 anim=load(ASSETS/f'animations/bedrock/vehicle/{model}.animation.json')['animations']
 turret=bones.get('turret',{}).get('pivot',[0,0,0]);barrel=bones.get('barrel',{}).get('pivot',[0,0,0])
 tpos=[turret[0]/16,turret[1]/16,-turret[2]/16];bpos=[(barrel[0]-turret[0])/16,(barrel[1]-turret[1])/16,-(barrel[2]-turret[2])/16]
 bp=[barrel[0]/16,barrel[1]/16,-barrel[2]/16]
 mpos=[[p[i]-bp[i] for i in range(3)] for _,p in sorted(muzzles)] if entity=='rocket_sentry' else [[primary[i]-bp[i] for i in range(3)]] if entity!='supply_hellpod' else []
 definitions[entity].update({'model':model,'pod_height':-min(p[1] for p in podpoints)/16,'deploy_ticks':math.ceil(anim['animation.hellpod.deploy']['animation_length']*20),'retract_ticks':math.ceil(anim['animation.hellpod.retract']['animation_length']*20),'reload_ticks':math.ceil(anim.get('animation.rocket.reload',{}).get('animation_length',0)*20),'nozzles':nozzles,'nozzle_bones':nozzle_bones,'nozzle_model_positions':nozzle_positions,'nozzle_directions':nozzle_axes,'aim_height':barrel[1]/16,'muzzle_offsets':mpos,'turret_pos':tpos,'barrel_pos':bpos,'burst_size':3 if entity=='autocannon_sentry' else 1,'burst_interval_ticks':10 if entity=='autocannon_sentry' else 0,'burst_pause_ticks':20.0 if entity=='autocannon_sentry' else 0})
 definitions[entity]['shell_offset']=[shell_point[i]-bp[i] for i in range(3)] if shell_point else None
 definitions[entity]['shell_direction']=shell_direction
 definitions[entity]['total_ammo']={'machine_gun_sentry':263,'gatling_sentry':750,'rocket_sentry':48,'autocannon_sentry':36,'supply_hellpod':0}[entity]
 client_path=ASSETS/f'sbw/vehicles/{entity}.json'
 save(client_path,{'Animation':f'sbw_helldiver:animations/bedrock/vehicle/{model}.animation.json','Models':[{'Model':f'sbw_helldiver:models/bedrock/vehicle/{model}.geo.json','Texture':f'sbw_helldiver:textures/bedrock/vehicle/{model}.png'}]})
 if entity=='supply_hellpod':continue
 existing=RES/f'data/sbw_helldiver/sbw/vehicles/{entity}.json'
 data=load(existing) if existing.exists() else load(RES/'data/sbw_helldiver/sbw/vehicles/machine_gun_sentry.json')
 data['ID']=f'sbw_helldiver:{entity}';data['TurretPos']=tpos;data['BarrelPos']=bpos
 data['TurretTurnSpeed']=[2.4,2.4] if entity in ['rocket_sentry','autocannon_sentry'] else [8.0,8.0]
 data['MaxHealth']=80;data['ApplyDefaultDamageModifiers']=False;data['RepairAmount']=0;data['SelfHurtAmount']=20;data['SelfHurtPercent']=.2
 data['DestroyInfo']={'NoWreck':False,'ExplodeBlocks':False,'ExplosionDamage':0,'ExplosionRadius':0,'SympatheticDetonation':False}
 data['SeekInfo']['MinSeekRange']=10 if entity=='rocket_sentry' else 0
 data['SeekInfo']['MaxSeekRange']=100 if entity in ['rocket_sentry','autocannon_sentry'] else 75
 gun=data['Weapons']['Main'];gun['SoundInfo']={};gun['ShootPos']={'Transform':'Barrel','Positions':mpos,'Directions':['Barrel']*len(mpos)}
 if entity in ['machine_gun_sentry','gatling_sentry']:gun['Projectile']='sbw_helldiver:sentry_bullet'
 if entity=='autocannon_sentry':
  gun.update(Projectile='sbw_helldiver:sentry_cannon_shell',AmmoType='superbwarfare:small_shell_ap',RPM=90,Velocity=12,Gravity=.03,Damage=70,ExplosionDamage=20,ExplosionRadius=5,Headshot=1,Magazine=36,Spread=.35)
 if entity=='rocket_sentry':
  gun.update(Projectile='sbw_helldiver:sentry_rocket',AmmoType='superbwarfare:small_rocket',RPM=90,Velocity=11,Gravity=.001,Damage=80,ExplosionDamage=20,ExplosionRadius=4,Headshot=1,Magazine=16,Spread=.3)
 gun['NormalReloadTime']=0;gun['EmptyReloadTime']=0
 save(existing,data)
save(ASSETS/'helldivers/profiles.json',definitions)
for lang,labels in [('zh_cn',{'rocket_sentry':'火箭哨戒炮','autocannon_sentry':'机炮哨戒炮'}),('en_us',{'rocket_sentry':'Rocket Sentry','autocannon_sentry':'Autocannon Sentry'})]:
 path=ASSETS/f'lang/{lang}.json';data=load(path)
 for entity,label in labels.items():data[f'entity.sbw_helldiver.{entity}']=label;data[f'item.sbw_helldiver.{entity}_stratagem']=label
 data['entity.sbw_helldiver.hellpod_cap']='空投仓盖板' if lang=='zh_cn' else 'Hellpod Cap'
 save(path,data)
for entity in ['rocket_sentry','autocannon_sentry']:save(ASSETS/f'models/item/{entity}_stratagem.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'sbw_helldiver:item/{entity}_stratagem'}})
save(ROOT/'_asset_pack/当前模型/导入记录.json',{'source':str(SOURCE),'profiles':definitions,'sound_event_count':len(registry),'source_models':9,'runtime_audio':'动画音效由独立节点播放器处理循环/停止；SBW 原生声音轨已移出，避免重复播放。','sha256':{p.relative_to(source_assets).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in source_assets.rglob('*') if p.is_file()}})
print('Imported all 9 current models and',len(registry),'sound events; 5 deployable profiles:',{k:(v['deploy_ticks'],v['retract_ticks'],round(v['pod_height'],4)) for k,v in definitions.items()})
