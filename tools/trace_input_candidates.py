from pathlib import Path
import json, struct, itertools, xml.etree.ElementTree as ET, wave
ROOT=Path('D:/Project/sbw_helldiver/.codex/beacon/audio')
BANKS=Path('D:/Codex/projects/2026-09-19/new-chat/work/audio_tools/v0755_all_banks_raw/content/audio')
events={}
for path in BANKS.glob('*.bnk'):
    blob=path.read_bytes();p=0
    while p+8<=len(blob):
        kind,length=struct.unpack_from('<4sI',blob,p);p+=8
        if kind==b'HIRC':
            n=struct.unpack_from('<I',blob,p)[0];q=p+4
            for _ in range(n):
                typ,size=struct.unpack_from('<BI',blob,q);q+=5
                if typ==4 and size>=4: events.setdefault(struct.unpack_from('<I',blob,q)[0],[]).append(path.name)
                q+=size
        p+=length
def fnv(word):
    n=2166136261
    for byte in word.lower().encode(): n=((n*16777619)^byte)&0xffffffff
    return n
words=['stratagem','stratagems','strategem','hud','ui','input','dial','dialing','code','key','button','arrow','terminal','direction','up','down','left','right','north','south','east','west','press','pressed','click','correct','failed','success','fail','step','on','off','start','stop','enter','entry','sequence','navigate','01','02','03','04','event','sound','select','menu']
found=[]
for count in [1,2,3,4]:
    for parts in itertools.product(words,repeat=count):
        if not any(x in parts for x in ['stratagem','stratagems','strategem']): continue
        name='_'.join(parts);ident=fnv(name)
        if ident in events: found.append({'name':name,'id':ident,'banks':events[ident]})
print('ALL_BANK_MATCHES',found,flush=True)
(ROOT/'all_bank_input_hashes.json').write_text(json.dumps(found,indent=2))
index=json.loads(Path('D:/Codex/projects/2026-09-19/new-chat/work/trigger_investigation/archive_index.json').read_text(encoding='utf-8'))
print('STRATAGEM_UI_FILES',[(x.get('path'),x.get('type_name')) for x in index if 'stratagem' in x.get('path','').lower() and any(k in x.get('type_name','') for k in ['lua','data','config','setting','script'])],flush=True)
short=[]
for p in (ROOT/'ui_extracted/content/audio/ui_mission.bnk.dir').glob('*.wav'):
    with wave.open(str(p),'rb') as audio:
        duration=audio.getnframes()/audio.getframerate()
        if duration<0.65:short.append({'media':p.stem,'seconds':duration,'path':str(p)})
(ROOT/'ui_short_candidates.json').write_text(json.dumps(short,indent=2))
print('SHORT_SAMPLES',short,flush=True)
