"""Reuse processed mechanical sounds at their original speed; animation follows audio."""
import argparse,copy,json
from pathlib import Path

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--ffmpeg',type=Path,help='Retained for compatibility; no audio re-encoding is performed.')
    p.parse_args()
    assets=Path(__file__).resolve().parents[1]/'src/main/resources/assets/sbw_helldiver'
    audio_path=assets/'helldivers/audio.json';sounds_path=assets/'sounds.json'
    audio=json.loads(audio_path.read_text(encoding='utf-8'));sounds=json.loads(sounds_path.read_text(encoding='utf-8'))
    deploy='sfx_native_machine_gun_sentry_deploy_81997979_0';retract='sfx_emplacement_retract_mechanism'
    for alias,source in [('sfx_equipment_beacon_unfold',deploy),('sfx_equipment_beacon_fold',retract)]:
        sounds[alias]=copy.deepcopy(sounds[source])
        audio['effects'][alias]=copy.deepcopy(audio['effects'][source])
        audio['effects'][alias]['adaptation']={'source_effect':source,'method':'Unmodified processed sound variants at original playback speed; the mod animation is timed to the sound. Native beacon trigger unconfirmed.'}
    def cue(time,effect):return {'time':time,'effect':effect,'locator':'equipment_motion'}
    audio['models']['equipment_delivery']={
        'container.deploy':{'length':2.0,'events':[cue(.44/1.2,retract)]},
        'beacon.deploy':{'length':2.2,'events':audio['models']['hellpod_v3']['animation.hellpod.deploy']['events']+[cue(1.10,'sfx_equipment_beacon_unfold')]},
        'beacon.retract':{'length':3,'events':[cue(.05,'sfx_equipment_beacon_fold')]+[dict(e,time=(e['time']+.75)*3/3.95)for e in audio['models']['hellpod_v3']['animation.hellpod.retract']['events']]}}
    audio_path.write_text(json.dumps(audio,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    sounds_path.write_text(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Beacon motion reuses original-speed samples without stretching or re-encoding.')

if __name__=='__main__':main()
