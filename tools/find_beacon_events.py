"""Locate the two original StratagemBall event objects across extracted banks."""
from pathlib import Path
import struct,json,collections
root=Path('D:/Codex/projects/2026-09-19/new-chat/work/audio_tools/v0755_all_banks_raw')
out=Path('D:/Project/sbw_helldiver/.codex/beacon/audio')
targets={4086158401,2512009224};found=[];count=collections.Counter()
for p in root.rglob('*.bnk'):
 data=p.read_bytes();offset=0
 while offset+8<=len(data):
  tag=data[offset:offset+4];size=struct.unpack_from('<I',data,offset+4)[0];body=data[offset+8:offset+8+size];offset+=8+size
  if tag!=b'HIRC':continue
  count['banks']+=1
  n=struct.unpack_from('<I',body)[0];pos=4
  for _ in range(n):
   typ=body[pos];sz=struct.unpack_from('<I',body,pos+1)[0];ident=struct.unpack_from('<I',body,pos+5)[0]
   if typ==4:count['events']+=1
   if typ==4 and ident in targets:
    record={'bank':str(p),'event_id':ident,'offset':offset-size+pos,'object_hex':body[pos:pos+5+sz].hex()};found.append(record);print('FOUND',json.dumps(record))
   pos+=5+sz
(out/'event_banks.json').write_text(json.dumps(found,indent=2),encoding='utf8')
print('SCANNED',dict(count),'MATCHES',len(found))
