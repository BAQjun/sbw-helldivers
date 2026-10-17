"""Decode the native flame icon with the existing sentry icon palette. Run in Blender."""
from pathlib import Path
import ast,bpy,numpy as np,struct,zlib,json,hashlib
ROOT=Path('D:/Project/sbw_helldiver')
R=Path('D:/Codex/projects/2026-09-19/new-chat')
tree=ast.parse((ROOT/'tools/stage_beacon_runtime.py').read_text(encoding='utf-8'))
exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='png'],type_ignores=[]),'<existing PNG encoder>','exec'))
native=R/'work/laser_sentry/integration/native_icon/content/ui/shared/stratagem/icon_strat_hud_laser_cannon_turret.png'
image=bpy.data.images.load(str(native));image.colorspace_settings.name='Non-Color'
w,h=image.size;pixels=np.array(image.pixels[:]).reshape(h,w,4)[::-1]
assert w%64==0 and h%64==0
masks=pixels[:,:,:3].reshape(64,h//64,64,w//64,3).mean(axis=(1,3))
out=np.full((64,64,4),255,dtype=np.uint8)
background=np.array([25,35,21.]);category=np.array([115,154,89.]);ammunition=np.array([231,237,204.])
rgb=np.broadcast_to(background,(64,64,3)).copy()
rgb+=masks[:,:,0,None]*(category-background);rgb+=masks[:,:,1,None]*(ammunition-background)
out[:,:,:3]=np.rint(rgb.clip(0,255))
out[1:3,1:63,:3]=category;out[61:63,1:63,:3]=category
out[1:63,1:3,:3]=category;out[1:63,61:63,:3]=category
target=ROOT/'src/main/resources/assets/sbw_helldiver/textures/item/laser_sentry_stratagem.png'
png(target,out)
png(R/'work/laser_sentry/runtime/激光炮台绿色图标.png',np.repeat(np.repeat(out,4,axis=0),4,axis=1))
print(json.dumps({'size':[64,64],'sourceSha256':hashlib.sha256(native.read_bytes()).hexdigest(),'categoryRgb':category.tolist(),'target':str(target)},ensure_ascii=False))
