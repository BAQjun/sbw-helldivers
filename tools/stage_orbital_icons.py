"""Stage the current native orbital/mission icon masks; run with Blender Python."""
import bpy,numpy as np,json,hashlib,struct,zlib
from pathlib import Path
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/sbw_helldiver';evidence=[x for x in json.loads((R/'art/icons/orbital/sources.json').read_text()) if x['id'] not in ['orbital_precision_strike','call_super_destroyer']]
def png(p,a):
 h,w=a.shape[:2]
 def c(t,d):return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
 p.write_bytes(b'\x89PNG\r\n\x1a\n'+c(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+c(b'IDAT',zlib.compress(b''.join(b'\0'+r.tobytes() for r in a),9))+c(b'IEND',b''))
for id,hash in [('orbital_precision_strike','53031033ee7a60e3'),('call_super_destroyer','356f12d60ab2ecc5')]:
 source=R/f'art/icons/orbital/{id}.png';im=bpy.data.images.load(str(source));im.colorspace_settings.name='Non-Color';w,h=im.size
 pixels=np.array(im.pixels[:]).reshape(h,w,4)[::-1];m=pixels[:,:,:3].reshape(64,h//64,64,w//64,3).mean(axis=(1,3))
 bg=np.array([25,3,1] if id.startswith('orbital') else [25,24,8]);category=np.array([220,100,85] if id.startswith('orbital') else [219,200,107]);white=np.array([255,255,238])
 rgb=np.broadcast_to(bg,(64,64,3)).copy().astype(float)+m[:,:,0,None]*(category-bg)+m[:,:,1,None]*(white-bg)
 out=np.full((64,64,4),255,dtype=np.uint8);out[:,:,:3]=np.rint(rgb.clip(0,255));out[1:3,1:63,:3]=category;out[61:63,1:63,:3]=category;out[1:63,1:3,:3]=category;out[1:63,61:63,:3]=category
 targets=[id] if id.startswith('orbital') else [id,'destroyer_direction','destroyer_manage']
 for target in targets:png(A/f'textures/item/{target}_stratagem.png',out)
 evidence.append({'id':id,'nativeHash':'0x'+hash,'sourceSha256':hashlib.sha256(source.read_bytes()).hexdigest(),'nativeStratagemType':'StratagemType_OrbitalPrecisionStrike' if id.startswith('orbital') else 'StratagemType_CallInSuperDestroyer'})
(R/'art/icons/orbital/sources.json').write_text(json.dumps(evidence,indent=2)+'\n')
print('Native mission and orbital icons staged')

# Keep native mask exports consistent with the defensive card frame.
import subprocess
subprocess.run(["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                str(Path(__file__).with_name("normalize_red_stratagem_frames.ps1"))], check=True)
