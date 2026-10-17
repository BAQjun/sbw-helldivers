"""Read existing extracted game settings and the shared bank for beacon events."""
import json,sys,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path('D:/Codex/projects/2026-09-19/new-chat')
WORK=ROOT/'work/audio_tools';OUT=Path('D:/Project/sbw_helldiver/.codex/beacon/audio')
OUT.mkdir(parents=True,exist_ok=True)
names=json.loads((WORK/'entity_hash_names.json').read_text())
thins=json.loads((WORK/'thin_hash_names.json').read_text())
terms=['beacon','stratagem_ball','stratagem_throw','stratagem_ready','stratagem_selected','stratagem_select']
matches={k:v for k,v in thins.items() if any(t in str(v).lower() for t in terms)}
print('THIN_NAMES',json.dumps(matches,ensure_ascii=False))
paths={k:v for k,v in names.items() if 'beacon' in str(v).lower() or 'stratagem_ball' in str(v).lower()}
print('RESOURCE_NAMES',json.dumps(paths,ensure_ascii=False))
units={str(int('16f397ca5f51f271',16)),str(int('c6a5bb8b319217ac',16))}
units.update(paths)
config=json.loads((WORK/'settings_generated_animation_event_trigger_settings.json').read_text())
triggers=[]
for data in config:
 for setting in data.get('AnimationEventTriggerSettings',{}).get('settings',[]):
  if str(setting['unit_resource']) in units:
   triggers.append(setting)
print('UNIT_TRIGGERS',json.dumps(triggers,ensure_ascii=False))
tree=ET.parse(WORK/'v0755_stratagems_shared.xml')
objects={}
for obj in tree.findall('.//list[@name="listLoadedItem"]/object'):
 key=obj.find('field[@name="ulID"]')
 if key is not None:objects[int(key.get('value'))]=obj
def walk(i,seen=None):
 seen=set() if seen is None else seen
 if i not in objects or i in seen:return []
 seen.add(i);o=objects[i]
 source=[int(f.get('value')) for f in o.findall('.//field[@name="sourceID"]')]
 for f in o.findall('.//field'):
  if f.get('name') in ['ulActionID','idExt','ulChildID']:source.extend(walk(int(f.get('value')),seen))
 return sorted(set(source))
events={str(i):{'name':thins.get(str(i)), 'sources':walk(i),
  'actions':[int(f.get('value')) for f in o.findall('.//field[@name="ulActionID"]')]}
  for i,o in objects.items() if o.get('name')=='CAkEvent'}
named={i:e for i,e in events.items() if e['name'] and any(t in str(e['name']).lower() for t in ['beacon','stratagem','ui_'])}
print('NAMED_SHARED_EVENTS',json.dumps(named,ensure_ascii=False))
ball=json.loads((WORK/'entity_snapshots/StratagemBallComponentData.json').read_text())['1653832383613497969']
print('BEACON_EVENTS',json.dumps({k:{'event_id':v,'bank':events.get(str(v))} for k,v in ball.items() if 'audio' in k and isinstance(v,int)},ensure_ascii=False))
(OUT/'original_beacon_component.json').write_text(json.dumps(ball,ensure_ascii=False,indent=2),encoding='utf8')
for setting in triggers:
 for trigger in setting['event_triggers']:
  eid=str(trigger['sound_event']);print('TRIGGER_EVENT',eid,events.get(eid))
(OUT/'shared_events.json').write_text(json.dumps(events,ensure_ascii=False,indent=2),encoding='utf8')
(OUT/'beacon_triggers.json').write_text(json.dumps(triggers,ensure_ascii=False,indent=2),encoding='utf8')
index=json.loads((ROOT/'work/trigger_investigation/archive_index.json').read_text())
selected=[r for r in index if r['name'] in units or 'beacon' in r['path'].lower()]
(OUT/'beacon_resources.json').write_text(json.dumps(selected,ensure_ascii=False,indent=2),encoding='utf8')
print('INDEX_MATCHES',len(selected),json.dumps([{'name':r['name'],'path':r['path'],'type':r['type_name']} for r in selected[:35]],ensure_ascii=False))
