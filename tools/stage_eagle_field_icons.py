"""Stage Eagle field cards from native R/G masks. Run with Blender's Python."""
from pathlib import Path
import argparse,hashlib,json,struct,zlib
import bpy,numpy as np
ROOT=Path(__file__).resolve().parents[1]
CARDS={'eagle_napalm_airstrike':('napalm','3ec6a98542325181'),'eagle_gas_strike':('gas','0d3c8f39571eaea9'),'eagle_smoke_strike':('smoke','e768c8e161674303')}
def png(path,a):
 h,w=a.shape[:2]
 def chunk(t,d):return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
 raw=b''.join(b'\0'+row.tobytes() for row in a)
 path.parent.mkdir(parents=True,exist_ok=True)
 path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
def main():
 evidence=[]
 for id,(name,hash) in CARDS.items():
  source=ROOT/'art/icons/eagle_fields'/f'{id}.png'
  im=bpy.data.images.load(str(source));im.colorspace_settings.name='Non-Color'
  w,h=im.size;assert w%64==0 and h%64==0
  pixels=np.array(im.pixels[:]).reshape(h,w,4)[::-1]
  masks=pixels[:,:,:3].reshape(64,h//64,64,w//64,3).mean(axis=(1,3))
  bg=np.array([25.,3.,1.]);plane=np.array([220.,100.,85.]);payload=np.array([255.,255.,238.])
  rgb=np.broadcast_to(bg,(64,64,3)).copy()+masks[:,:,0,None]*(plane-bg)+masks[:,:,1,None]*(payload-bg)
  out=np.full((64,64,4),255,dtype=np.uint8);out[:,:,:3]=np.rint(rgb.clip(0,255))
  out[1:3,1:63,:3]=plane;out[61:63,1:63,:3]=plane;out[1:63,1:3,:3]=plane;out[1:63,61:63,:3]=plane
  png(ROOT/f'src/main/resources/assets/sbw_helldiver/textures/item/{id}_stratagem.png',out)
  evidence.append({'id':id,'nativeHash':'0x'+hash,'nativeSha256':hashlib.sha256(source.read_bytes()).hexdigest()})
 (ROOT/'art/icons/eagle_fields/sources.json').write_text(json.dumps(evidence,indent=2)+'\n')
 print('Eagle field cards staged from native red/green masks')
if __name__=='__main__':main()
