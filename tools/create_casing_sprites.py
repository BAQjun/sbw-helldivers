"""Tiny clean brass/steel atlas swatches for the octagonal 3D particle renderer."""
from pathlib import Path
import struct, zlib, json

root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/sbw_helldiver'
def chunk(kind,data):
    return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
for name,base in [('machine_gun_casing',(176,151,91)),('gatling_casing',(170,154,108)),('cannon_casing',(153,145,116))]:
    pixels=[]
    for y in range(16):
        row=bytearray([0])
        for x in range(16):
            # Flat new metal, seam and case-head bands. No artificial wear/noise.
            gain=0.72 if y in [13,15] else 1.12 if x in [4,5] else 0.9 if x>12 else 1
            row.extend([min(255,round(c*gain)) for c in base]+[255])
        pixels.append(row)
    path=root/f'textures/particle/{name}.png';path.parent.mkdir(parents=True,exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(pixels)))+chunk(b'IEND',b''))
    path=root/f'particles/{name}.json';path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps({'textures':[f'sbw_helldiver:{name}']})+'\n',encoding='utf8')
print('Created 3 clean metal casing sprites.')
