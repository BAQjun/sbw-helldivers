from pathlib import Path
import json,math,copy,subprocess,xml.etree.ElementTree as E,wave,hashlib
ROOT=Path('D:/Project/sbw_helldiver');R=ROOT/'.codex/mortar_039';A=ROOT/'src/main/resources/assets/sbw_helldiver';D=ROOT/'src/main/resources/data/sbw_helldiver/sbw/vehicles'
def read(p):return json.loads(p.read_text('utf-8-sig'))
def save(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
geo=read(A/'models/bedrock/vehicle/mortar_hellpod_v1.geo.json')['minecraft:geometry'][0];bones={b['name']:b for b in geo['bones']};bp=bones['barrel']['pivot'];tp=bones['turret']['pivot'];tip=bones['muzzle_tube']['locators']['muzzle_exit']
profiles=read(A/'helldivers/profiles.json');p=copy.deepcopy(profiles['machine_gun_sentry']);p.update(model='mortar_hellpod_v1',aim_height=bp[1]/16,muzzle_offsets=[[(tip[0]-bp[0])/16,(tip[1]-bp[1])/16,-(tip[2]-bp[2])/16]],turret_pos=[tp[0]/16,tp[1]/16,-tp[2]/16],barrel_pos=[(bp[0]-tp[0])/16,(bp[1]-tp[1])/16,-(bp[2]-tp[2])/16],shell_offset=None,shell_direction=None,shell_bone=None,shell_model_position=None,muzzle_bone='muzzle_tube',muzzle_model_position=[-tip[0]/16,tip[1]/16,tip[2]/16],burst_size=4,burst_interval_ticks=20,burst_pause_ticks=40.0,total_ammo=125,reload_ticks=0);profiles['mortar_sentry']=p
vehicle=read(D/'autocannon_sentry.json');vehicle.update(ID='sbw_helldiver:mortar_sentry',TurretPos=p['turret_pos'],BarrelPos=p['barrel_pos'],TurretTurnSpeed=[2.75,2.75],TurretPitchRange=[35,89]);vehicle['SeekInfo'].update(MinSeekRange=0,MaxSeekRange=125);gun=vehicle['Weapons']['Main'];gun.update(Projectile='sbw_helldiver:mortar_shell',RPM=60,Velocity=4,Gravity=.12,Damage=30,ExplosionDamage=40,ExplosionRadius=7,Magazine=125,Spread=0,Life=200,SoundInfo={},ShootPos={'Transform':'Barrel','Positions':p['muzzle_offsets'],'Directions':['Barrel']});save(D/'mortar_sentry.json',vehicle)
save(A/'sbw/vehicles/mortar_sentry.json',{'Animation':'sbw_helldiver:animations/bedrock/vehicle/mortar_hellpod_v1.animation.json','Models':[{'Model':'sbw_helldiver:models/bedrock/vehicle/mortar_hellpod_v1.geo.json','Texture':'sbw_helldiver:textures/bedrock/vehicle/mortar_hellpod_v1.png'}]})
for language,label in [('zh_cn','迫击炮哨戒炮'),('en_us','Mortar Sentry')]:
 f=A/f'lang/{language}.json';d=read(f);d[f'entity.sbw_helldiver.mortar_sentry']=label;d[f'item.sbw_helldiver.mortar_sentry_stratagem']=label;d['entity.sbw_helldiver.mortar_shell']='迫击炮弹' if language=='zh_cn' else 'Mortar Shell';save(f,d)
save(A/'models/item/mortar_sentry_stratagem.json',{'parent':'minecraft:item/generated','textures':{'layer0':'sbw_helldiver:item/mortar_sentry_stratagem'}})
t=E.parse(R/'mortar_bank.xml');objects={o.find('field[@name="ulID"]').get('value'):o for o in t.findall('.//list[@name="listLoadedItem"]/object') if o.find('field[@name="ulID"]') is not None}
def own_gain(ob):
 gain=0
 for bundle in ob.findall('.//object[@name="AkPropBundle"]'):
  prop=bundle.find('field[@name="pID"]');value=bundle.find('field[@name="pValue"]')
  if prop is not None and value is not None and any(x in prop.get('valuefmt','') for x in ['[Volume]','[MakeUpGain]']):gain+=float(value.get('value'))
 return gain
def leaves(i,gain=0):
 ob=objects[i];gain+=own_gain(ob);ids=[f.get('value') for f in ob.findall('.//field[@name="sourceID"]')]
 if ids:return [(v,gain) for v in ids if (R/f'wem/{v}.wem').exists()]
 out=[]
 for child in ob.findall('.//field[@name="ulChildID"]'):out+=leaves(child.get('value'),gain)
 return out
sounds=read(A/'sounds.json');audio=read(A/'helldivers/audio.json');cues=copy.deepcopy(audio['models']['amg43_hellpod_v3']);records=[];fire_effects=[];durations={}
ffmpeg=Path('D:/Codex/projects/2026-09-19/new-chat/work/audio_tools/v0.7.55/filediver-cli/ffmpeg.exe');vgm=ROOT/'tools/vgmstream/vgmstream-cli.exe';(R/'wav').mkdir(exist_ok=True)
for event_id,name in [('3818551486','fire'),('1182365127','deploy'),('3739887585','retract'),('1826333753','feed')]:
 event=objects[event_id]
 for ai,action in enumerate(event.findall('.//field[@name="ulActionID"]')):
  ac=objects[action.get('value')];node_id=ac.find('.//field[@name="idExt"]').get('value');node=objects[node_id]
  # A Layer container is simultaneous; a Random container chooses exactly one source.
  buckets=[(x.get('value'),leaves(x.get('value'))) for x in node.findall('.//field[@name="ulChildID"]')] if node.get('name')=='CAkLayerCntr' else [(node_id,leaves(node_id))]
  for bi,(group,variants) in enumerate(buckets):
   if not variants:records.append({'event_id':int(event_id),'container_id':int(group),'native_plugin_without_media':True});continue
   effect='sfx_mortar_'+name+(f'_{ai+1}_{bi+1}' if name=='fire' else '')
   entries=[];max_duration=0
   for source_id,gain in variants:
    wem=R/f'wem/{source_id}.wem';wav=R/f'wav/{source_id}.wav';ogg=A/f'sounds/mortar/{source_id}.ogg';ogg.parent.mkdir(parents=True,exist_ok=True)
    if not wav.exists():subprocess.run([str(vgm),'-o',str(wav),str(wem)],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    if not ogg.exists():subprocess.run([str(ffmpeg),'-hide_banner','-loglevel','error','-y','-i',str(wav),'-c:a','libvorbis','-q:a','7',str(ogg)],check=True)
    with wave.open(str(wav)) as f:duration=f.getnframes()/f.getframerate()
    max_duration=max(max_duration,duration);entries.append({'name':f'sbw_helldiver:mortar/{source_id}','volume':10**(gain/20),'stream':False,'attenuation_distance':96 if name=='fire' else 32})
   sounds[effect]={'sounds':entries,'subtitle':'subtitle.sbw_helldiver.mortar_'+name};audio['effects'][effect]={'kind':'one_shot','audible':True,'fade_in':0,'fade_out':0,'duration':max_duration,'stop_effects':[]};records.append({'effect':effect,'event_id':int(event_id),'action_id':int(action.get('value')),'container_id':int(group),'variants':[{'source_id':int(i),'gain_db':v} for i,v in variants],'max_duration':max_duration});durations[name]=max(durations.get(name,0),max_duration)
   if name=='fire':fire_effects.append(effect)
for value in cues.values():value['events']=[x for x in value['events'] if not x['effect'].startswith('sfx_mg_')]
cues['animation.Main.fire']={'length':1.0,'events':[{'time':0,'effect':n,'locator':'muzzle_exit'} for n in fire_effects]+[{'time':.08,'effect':'sfx_mortar_feed','locator':'pitch_axis'}]}
cues['animation.hellpod.deploy']['events'].append({'time':1.05,'effect':'sfx_mortar_deploy','locator':'pitch_axis'})
cues['animation.hellpod.retract']['events'].append({'time':0,'effect':'sfx_mortar_retract','locator':'pitch_axis'})
anim_path=A/'animations/bedrock/vehicle/mortar_hellpod_v1.animation.json';anim=read(anim_path);length=1.05+durations['deploy'];anim['animations']['animation.hellpod.deploy']['animation_length']=length;cues['animation.hellpod.deploy']['length']=length;p['deploy_ticks']=math.ceil(length*20);p['retract_ticks']=math.ceil(anim['animations']['animation.hellpod.retract']['animation_length']*20);save(anim_path,anim);save(A/'helldivers/profiles.json',profiles)
audio['models']['mortar_hellpod_v1']=cues;audio['models']['mortar_sentry_v1']={'animation.Main.fire':cues['animation.Main.fire']};save(A/'helldivers/audio.json',audio);save(A/'sounds.json',sounds)
for language in ['zh_cn','en_us']:
 f=A/f'lang/{language}.json';d=read(f)
 for n,zh,en in [('fire','迫击炮发射','Mortar fires'),('feed','迫击炮送弹机构运转','Mortar feed cycles'),('deploy','迫击炮部署','Mortar deploys'),('retract','迫击炮收纳','Mortar retracts')]:d[f'subtitle.sbw_helldiver.mortar_{n}']=zh if language=='zh_cn' else en
 save(f,d)
# Update editable sound-effect nodes for the same runtime cues, with no legacy gun audio.
for f in (ROOT/'_asset_pack/当前模型/迫击炮').glob('*.bbmodel'):
 d=read(f)
 for a in d['animations']:
  cue=cues.get(a['name']);
  if cue:
   a['length']=cue['length'];a['animators']['effects']={'type':'effect','keyframes':[{'channel':'sound','time':e['time'],'data_points':[{'effect':e['effect'],'locator':e['locator']}],'interpolation':'linear'} for e in cue['events']]}
 save(f,d)
call_settings=read(Path('D:/Codex/projects/2026-09-19/new-chat/work/audio_tools/settings_generated_animation_event_trigger_settings.json'))[0]['AnimationEventTriggerSettings']['settings'];confirmed=next(x for x in call_settings if x['unit_resource']==5881843148917714292)
save(R/'audio_binding_audit.json',{'database':'https://github.com/Dekr0/hd2_audio_db','bank':str(Path('D:/Codex/projects/2026-09-19/new-chat/work/audio_banks/decoded/stratagems_sentry_mortar.bnk')),'bank_sha256':hashlib.sha256(Path('D:/Codex/projects/2026-09-19/new-chat/work/audio_banks/decoded/stratagems_sentry_mortar.bnk').read_bytes()).hexdigest(),'bindings':records,'confirmed_animation_calls':confirmed,'notes':'单发事件3818551486由游戏ProjectileWeaponComponentData确认；随机层独立随机，Layer层同时播放。原送弹事件1826333753属于射击循环的机构运转，不增加弹匣换弹状态。未借用录屏或其他炮台声音。'})
print('MORTAR_STAGED',p['deploy_ticks'],'deploy ticks',len(records),'effects',durations)
