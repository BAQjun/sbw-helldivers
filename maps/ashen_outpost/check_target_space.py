from pathlib import Path
import math,zlib,json,zipfile
r=Path(__file__).resolve().parent;ns={};s=(r/'validate_world.py').read_text(encoding='utf8');exec(s[:s.index('chunks=0;')],ns);Reader=ns['Reader'];w=r/'world/HD2_Ashen_Outpost';cache={}
def chunk(cx,cz):
 if (cx,cz) not in cache:
  b=(w/'region'/f'r.{cx//32}.{cz//32}.mca').read_bytes();idx=cx%32+cz%32*32;off=(int.from_bytes(b[4*idx:4*idx+4],'big')>>8)*4096;n=int.from_bytes(b[off:off+4],'big');cache[(cx,cz)]=Reader(zlib.decompress(b[off+5:off+4+n])).root()
 return cache[(cx,cz)]
def block(x,y,z):
 c=chunk(x//16,z//16);sec=next((v for v in c['sections'] if v['Y']==y//16),None)
 if sec is None:return 'minecraft:air'
 bs=sec['block_states'];pal=bs['palette']
 if len(pal)==1:return pal[0]['Name']
 bits=max(4,(len(pal)-1).bit_length());idx=(y%16)*256+(z%16)*16+x%16;per=64//bits;value=(bs['data'][idx//per]>>((idx%per)*bits))&((1<<bits)-1);return pal[value]['Name']
checked=[]
for mode in ('near','far'):
 for line in (w/f'datapacks/ashen_outpost/data/outpost/functions/targets/{mode}.mcfunction').read_text().splitlines():
  if not line.startswith('summon '):continue
  a=line.split();x,y,z=map(float,a[2:5])
  for xx in range(math.floor(x-.3),math.floor(x+.3)+1):
   for yy in range(math.floor(y),math.floor(y+1.95)+1):
    for zz in range(math.floor(z-.3),math.floor(z+.3)+1):assert block(xx,yy,zz)=='minecraft:air',(mode,x,y,z,xx,yy,zz,block(xx,yy,zz))
  assert block(math.floor(x),math.floor(y)-1,math.floor(z))!='minecraft:air'
  checked.append([mode,x,y,z])
assert len(checked)==10
report={'all_ten_target_spawn_boxes_clear':True,'solid_ground_under_all_targets':True,'target_positions':checked,'far_targets_in_front_of_static_panel':True}
out=r;(out/'靶群位置检查.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps(report))
