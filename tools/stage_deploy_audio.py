"""Apply audited original deploy/retract events without changing any geometry."""
import argparse,copy,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];A=ROOT/'src/main/resources/assets/sbw_helldiver'
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def save(p,v):p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def apply(only=None):
 b=load(ROOT/'tools/verified_deploy_audio.json');a=load(A/'helldivers/audio.json');s=load(A/'sounds.json')
 a['effects'].update(b['effects']);s.update(b['registry']);changed=[]
 for ident,p in b['profiles'].items():
  if only and ident!=only:continue
  standalone={'machine_gun_sentry':'amg43_sentry_v4_audio','gatling_sentry':'gatling_sentry_v1_audio','autocannon_sentry':'autocannon_sentry_v1','rocket_sentry':'rocket_sentry_v1','mortar_sentry':'mortar_sentry_v1','gas_mortar_sentry':'gas_mortar_sentry_v1','ems_mortar_sentry':'ems_mortar_sentry_v1'}.get(ident)
  for model_key in [p['model']]+([standalone] if standalone else []):
   for name,clip in a['models'][model_key].items():
    role='deploy' if name.endswith('.deploy') or name.endswith('.retro_landing_preview') else 'retract' if name.endswith('.retract') else None
    if role is None:continue
    base=6.45 if name.endswith('.retro_landing_preview') else (1.05 if model_key==p['model'] else .55) if role=='deploy' else 0.
    clip['events']=[e for e in clip['events'] if e['effect'].startswith(('sfx_hellpod_','sfx_original_stop_'))]
    clip['events'] += [dict(e,time=round(base+e['time'],6)) for e in p['cues'][role]]
    clip['events'].sort(key=lambda e:e['time']);changed.append([ident,model_key,name])
 save(A/'helldivers/audio.json',a);save(A/'sounds.json',s)
 return changed
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--only');args=p.parse_args();print(json.dumps({'changed':apply(args.only)},ensure_ascii=False))
