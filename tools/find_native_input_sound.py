from pathlib import Path
import json, xml.etree.ElementTree as ET
import itertools
root=Path('D:/Project/sbw_helldiver/.codex/beacon/audio')
tree=json.loads(Path('D:/Codex/projects/2026-09-19/new-chat/work/trigger_investigation/audio_db_live_tree.json').read_text(encoding='utf-8'))
print('DATABASE_LABELS',[(x['path'],x.get('size')) for x in tree['tree'] if x['path'].startswith('label/') and any(k in x['path'].lower() for k in ['ui','hud','menu','stratagem'])])
print('AUDIO_FILES',[(x.name,x.stat().st_size) for x in root.iterdir() if x.is_file()])
print('INPUT_HASH_MATCHES', (root/'input_name_matches.json').read_text(encoding='utf-8'))
for filename in ['ui_mission.xml','ui_ship.xml']:
    t=ET.parse(root/filename).getroot()
    nodes=[]
    for node in t.iter('object'):
        if node.get('name')=='CAkEvent':
            ident=next((x.get('value') for x in node.iter('field') if x.get('name')=='ulID'),None)
            nodes.append(ident)
    print(filename,'EVENTS',len(nodes),'XML_HEAD',ET.tostring(t,encoding='unicode')[:250])
    wanted=set(map(int,nodes))
    def fnv(word):
        n=2166136261
        for byte in word.lower().encode(): n=((n*16777619)^byte)&0xffffffff
        return n
    prefixes=['hud_stratagem','ui_stratagem','stratagem','hud_stratagem_input','hud_stratagem_dial','hud_stratagem_dialing','hud_stratagem_code','ui_stratagem_code','ui_hud_stratagem','hud_stratagems']
    words=['up','down','left','right','north','south','east','west','key','button','direction','arrow','dial','dialing','input','code','sequence','press','pressed','click','clicked','enter','entered','correct','incorrect','success','failed','fail','error','valid','invalid','start','stop','select','selection','selecting','activate','activation','available','unavailable','menu','open','close','confirm','cancel','step','navigate','nav','entry','list','on','off','01','02','03','04']
    found=[]
    for prefix in prefixes:
        for count in [1,2,3]:
            for parts in itertools.product(words,repeat=count):
                name=prefix+'_'+'_'.join(parts);ident=fnv(name)
                if ident in wanted: found.append({'name':name,'id':ident,'bank':filename})
    print('HASH_MATCHES',found)
    (root/f'{filename}_expanded_hash_matches.json').write_text(json.dumps(found,indent=2))
archive=json.loads(Path('D:/Codex/projects/2026-09-19/new-chat/work/trigger_investigation/archive_index.json').read_text(encoding='utf-8'))
print('INDEX_TYPE',type(archive).__name__)
if isinstance(archive,dict): print('INDEX_KEYS',list(archive)[:8])
