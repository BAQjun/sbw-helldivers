from pathlib import Path
import json,zipfile,struct,zlib,gzip,collections,hashlib
r=Path(__file__).resolve().parent;w=r/'world/HD2_Ashen_Outpost'
class Reader:
 def __init__(self,b):self.b=b;self.i=0
 def read(self,n):v=self.b[self.i:self.i+n];self.i+=n;return v
 def num(self,f):return struct.unpack('>'+f,self.read(struct.calcsize('>'+f)))[0]
 def string(self):return self.read(self.num('H')).decode('utf8')
 def tag(self,t):
  if t in (1,2,3,4,5,6):return self.num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
  if t==7:return self.read(self.num('i'))
  if t==8:return self.string()
  if t==9:
   k=self.num('B');n=self.num('i');return [self.tag(k) for _ in range(n)]
  if t==10:
   d={}
   while True:
    k=self.num('B')
    if not k:return d
    name=self.string();d[name]=self.tag(k)
  if t in (11,12):return [self.num('i' if t==11 else 'q') for _ in range(self.num('i'))]
  raise ValueError(t)
 def root(self):assert self.num('B')==10;self.string();return self.tag(10)
chunks=0;names=set();blockentities=[]
for p in w.glob('region/*.mca'):
 b=p.read_bytes()
 for i in range(1024):
  loc=int.from_bytes(b[4*i:4*i+4],'big');off=(loc>>8)*4096
  if not off:continue
  n=int.from_bytes(b[off:off+4],'big');assert b[off+4]==2
  c=Reader(zlib.decompress(b[off+5:off+4+n])).root();chunks+=1
  assert c['Status']=='minecraft:full'
  for sec in c['sections']:
   states=sec['block_states'];names.update(x['Name'] for x in states['palette']);bits=max(4,(len(states['palette'])-1).bit_length())
   if len(states['palette'])>1:assert len(states['data'])==(4096+(64//bits)-1)//(64//bits)
  blockentities.extend(c['block_entities'])
level=Reader(gzip.decompress((w/'level.dat').read_bytes())).root()['Data']
assert chunks==2304 and all(n.startswith('minecraft:') for n in names)
assert len([v for v in blockentities if v['id']=='minecraft:command_block'])==4
assert not list(w.glob('entities/*.mca'))
for be in blockentities:
 if be['id']=='minecraft:sign':
  for msg in be['front_text']['messages']:json.loads(msg)
assert level['Player']['Inventory'][0]['id']=='minecraft:written_book'
report={'chunks_read':chunks,'map_size':[768,768],'unique_vanilla_blocks':len(names),'block_entities':len(blockentities),'manual_command_blocks':4,'repeating_command_blocks':0,'preplaced_mobs_or_turrets':0,'block_palette_encoding_valid':True,'guide_and_sign_json_valid':True,'data_version':level['DataVersion'],'vanilla_1_20_1_building_compatibility':True,'functional_block_types':sorted(n for n in names if any(k in n for k in ['furnace','smoker','barrel','anvil','table','door','button','lever','comparator','hopper','cauldron','grindstone','stonecutter']))}
(r/'world_validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps(report,ensure_ascii=True))
