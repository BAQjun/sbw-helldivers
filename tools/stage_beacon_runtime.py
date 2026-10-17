"""Stage native mesh, action audio and correctly coloured game icon masks.
Run with Blender --background --python. Does not rebuild existing sentry models.
"""
from pathlib import Path
import bpy, json, numpy as np, shutil, struct, zlib, subprocess, hashlib

ROOT = Path('D:/Project/sbw_helldiver')
DELIVERY = Path('D:/Codex/projects/2026-09-19/new-chat/outputs/当前交付/信标球')
ASSETS = ROOT / 'src/main/resources/assets/sbw_helldiver'
ICONS = ROOT / '.codex/beacon/icons/content/ui/shared/stratagem'
FFMPEG = Path('D:/Codex/projects/2026-09-19/new-chat/work/audio_tools/v0.7.55/filediver-cli/ffmpeg.exe')

def png(path, array):
    array = np.asarray(array, dtype=np.uint8)
    h,w = array.shape[:2]
    def chunk(kind, data):
        return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(b'\0'+row.tobytes() for row in array),9))+chunk(b'IEND',b''))

for source,target in [
    ('模型/信标球.mesh.json','models/mesh/stratagem_beacon.mesh.json'),
    ('贴图/信标球.png','textures/mesh/stratagem_beacon.png'),
    ('贴图/信标球_发光.png','textures/mesh/stratagem_beacon_emissive.png'),
]:
    dst=ASSETS/target;dst.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(DELIVERY/source,dst)
png(ASSETS/'textures/mesh/white.png',np.full((1,1,4),255,dtype=np.uint8))

icon_report=[];previews=[]
for item, source in [('machine_gun_sentry','machinegun'),('gatling_sentry','gatling'),('rocket_sentry','rocket'),('autocannon_sentry','autocannon')]:
    native=ICONS/f'icon_strat_hud_defensive_turrets_{source}.png'
    image=bpy.data.images.load(str(native));image.colorspace_settings.name='Non-Color'
    w,h=image.size;pixels=np.array(image.pixels[:]).reshape(h,w,4)[::-1]
    # Native HUD textures pack shapes into colour channels, not literal red/green art.
    assert w%64==0 and h%64==0,(w,h)
    masks=pixels[:,:,:3].reshape(64,h//64,64,w//64,3).mean(axis=(1,3))
    out=np.full((64,64,4),255,dtype=np.uint8)
    background=np.array([25,35,21.])
    category=np.array([115,154,89.])
    ammunition=np.array([231,237,204.])
    rgb=np.broadcast_to(background,(64,64,3)).copy()
    rgb += masks[:,:,0,None]*(category-background)
    rgb += masks[:,:,1,None]*(ammunition-background)
    out[:,:,:3]=np.rint(rgb.clip(0,255))
    # Match the supplied in-game HUD: dark tile, green turret, pale ammunition.
    out[1:3,1:63,:3]=category;out[61:63,1:63,:3]=category
    out[1:63,1:3,:3]=category;out[1:63,61:63,:3]=category
    target=ASSETS/f'textures/item/{item}_stratagem.png';png(target,out);previews.append(np.repeat(np.repeat(out,3,axis=0),3,axis=1))
    icon_report.append({'stratagem':item,'source':str(native),'source_sha256':hashlib.sha256(native.read_bytes()).hexdigest(),'original_dimensions':[w,h],'runtime_dimensions':[64,64],'interpretation':'Native red channel is the green turret; green channel is pale ammunition; dark backdrop and green frame match the user game screenshot.'})
png(ROOT/'.codex/beacon/official_icons_preview.png',np.concatenate(previews,axis=1))

sounds=json.loads((ASSETS/'sounds.json').read_text(encoding='utf-8'))
for key,source in [('beacon_ready','01_战备选好_信标准备.wav'),('beacon_deploy','02_落地展开并启动光束.wav')]:
    path=ASSETS/f'sounds/beacon/{key}.ogg';path.parent.mkdir(parents=True,exist_ok=True)
    subprocess.run([str(FFMPEG),'-hide_banner','-loglevel','error','-y','-i',str(DELIVERY/'音效'/source),'-c:a','libvorbis','-q:a','7',str(path)],check=True)
    sounds[key]={'subtitle':f'subtitle.sbw_helldiver.{key}','sounds':[{'name':f'sbw_helldiver:beacon/{key}','stream':False}]}
sounds['beacon_deploy']['sounds'][0]['attenuation_distance']=64
(ASSETS/'sounds.json').write_text(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

model={'parent':'builtin/entity','gui_light':'front','display':{'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,1,0],'scale':[0.6,0.6,0.6]},'thirdperson_lefthand':{'rotation':[0,0,0],'translation':[0,1,0],'scale':[0.6,0.6,0.6]},'firstperson_righthand':{'rotation':[0,0,0],'translation':[-3,4,-2],'scale':[1,1,1]},'firstperson_lefthand':{'rotation':[0,0,0],'translation':[-3,4,-2],'scale':[1,1,1]},'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[0.7,0.7,0.7]}}}
(ASSETS/'models/item/stratagem_beacon.json').write_text(json.dumps(model,indent=2)+'\n')
zh={
 'item.sbw_helldiver.stratagem_beacon':'战备信标球',
 'screen.sbw_helldiver.loadout':'战备装载',
 'screen.sbw_helldiver.loadout_hint':'最多四个战备；同种战备只能装载一次',
 'message.sbw_helldiver.beacon_not_ready':'Shift＋右键装载战备；按住 Alt 输入方向代码',
 'item.sbw_helldiver.stratagem_card_hint':'将此战备放入信标球的装载页面',
 'tooltip.sbw_helldiver.beacon_controls':'Shift＋右键装载；Alt 输入／重选；左键投掷；切换物品取消',
 'tooltip.sbw_helldiver.beacon_ready':'已激活：%s',
 'hud.sbw_helldiver.stratagems':'已装载战备',
 'hud.sbw_helldiver.empty_slot':'槽位 %s：未装载',
 'hud.sbw_helldiver.ready':'已激活 · 左键投掷',
 'hud.sbw_helldiver.arrival':'空投抵达：%s 秒',
 'key.sbw_helldiver.stratagem':'战备方向输入（按住）',
 'key.categories.sbw_helldiver':'绝地潜兵战备',
 'subtitle.sbw_helldiver.beacon_ready':'信标准备就绪',
 'subtitle.sbw_helldiver.beacon_deploy':'信标展开并启动光束',
}
en={
 'item.sbw_helldiver.stratagem_beacon':'Stratagem Beacon',
 'screen.sbw_helldiver.loadout':'Stratagem Loadout',
 'screen.sbw_helldiver.loadout_hint':'Up to four stratagems; no duplicates',
 'message.sbw_helldiver.beacon_not_ready':'Shift + right-click to equip; hold Alt and enter a code',
 'item.sbw_helldiver.stratagem_card_hint':'Equip this stratagem in a beacon loadout',
 'tooltip.sbw_helldiver.beacon_controls':'Shift + right-click: loadout; Alt: select/reselect; left-click: throw; switch item: cancel',
 'tooltip.sbw_helldiver.beacon_ready':'Activated: %s',
 'hud.sbw_helldiver.stratagems':'EQUIPPED STRATAGEMS',
 'hud.sbw_helldiver.empty_slot':'Slot %s: empty',
 'hud.sbw_helldiver.ready':'Ready · Left-click to throw',
 'hud.sbw_helldiver.arrival':'ARRIVING IN %s s',
 'key.sbw_helldiver.stratagem':'Stratagem input (hold)',
 'key.categories.sbw_helldiver':'Helldivers Stratagems',
 'subtitle.sbw_helldiver.beacon_ready':'Beacon ready',
 'subtitle.sbw_helldiver.beacon_deploy':'Beacon unfolding and activating',
}
for language, additions in [('zh_cn',zh),('en_us',en)]:
    path=ASSETS/f'lang/{language}.json';existing=json.loads(path.read_text(encoding='utf-8')) if path.exists() else {};existing.update(additions);path.write_text(json.dumps(existing,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(ROOT/'.codex/beacon/runtime_asset_import.json').write_text(json.dumps({'icons':icon_report,'mesh_source':str(DELIVERY/'模型/信标球.mesh.json'),'audio_sources':[4086158401,1032351623]},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('BEACON_RUNTIME_STAGED: native mesh, correctly coloured 64px official icon masks, two native action cues')
