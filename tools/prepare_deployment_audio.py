"""Fit existing processed mechanical samples to the authored deployment motion spans.

These are mod-side adaptations, not newly confirmed native terminal/container events.
Original marker-only lift/unlock events intentionally remain silent.
"""
import argparse,json,subprocess,hashlib
from pathlib import Path

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--ffmpeg',required=True,type=Path)
    args=p.parse_args()
    assets=Path(__file__).resolve().parents[1]/'src/main/resources/assets/sbw_helldiver'
    audio_path=assets/'helldivers/audio.json';sounds_path=assets/'sounds.json'
    audio=json.loads(audio_path.read_text(encoding='utf-8'));sounds=json.loads(sounds_path.read_text(encoding='utf-8'))
    deploy='sfx_native_machine_gun_sentry_deploy_81997979_0';retract='sfx_emplacement_retract_mechanism'
    specs=[('sfx_equipment_beacon_lift',deploy,.60),
           ('sfx_equipment_beacon_unfold',deploy,1.45),
           ('sfx_equipment_beacon_fold',retract,1.10),
           ('sfx_equipment_beacon_lower',retract,1.14),
           ('sfx_equipment_beacon_iris',retract,.50)]
    probe=args.ffmpeg.with_name('ffprobe.exe' if args.ffmpeg.suffix=='.exe' else 'ffprobe')
    for name,source,length in specs:
        variants=[];provenance=[]
        for i,v in enumerate(sounds[source]['sounds']):
            src=assets/'sounds'/Path(v['name'].split(':',1)[1]+'.ogg')
            duration=float(subprocess.check_output([str(probe),'-v','error','-show_entries','format=duration','-of','default=nw=1:nk=1',str(src)],text=True).strip())
            factor=duration/length;filters=[]
            while factor>2:filters.append('atempo=2');factor/=2
            while factor<.5:filters.append('atempo=0.5');factor/=.5
            filters.extend([f'atempo={factor:.12f}','apad',f'atrim=duration={length}',f'afade=t=out:st={length-.04}:d=0.04'])
            target=f'machines/deployment/{name.removeprefix("sfx_")}_{i}'
            out=assets/'sounds'/Path(target+'.ogg');out.parent.mkdir(parents=True,exist_ok=True)
            subprocess.run([str(args.ffmpeg),'-v','error','-nostdin','-y','-i',str(src),'-af',','.join(filters),'-c:a','libvorbis','-q:a','5','-map_metadata','-1',str(out)],check=True)
            variants.append({'name':'sbw_helldiver:'+target,'volume':v.get('volume',1),'attenuation_distance':96})
            provenance.append({'source':v['name'],'sha256':hashlib.sha256(src.read_bytes()).hexdigest()})
        sounds[name]={'sounds':variants}
        audio['effects'][name]={'kind':'one_shot','audible':True,'fade_in':0,'fade_out':0,'duration':length,'stop_effects':[],
            'adaptation':{'source_effect':source,'method':'Pitch-preserving tempo fit and short release fade to authored mod animation; native trigger unconfirmed.','variants':provenance}}
    def cue(time,effect):return {'time':time,'effect':effect,'locator':'equipment_motion'}
    audio['models']['equipment_delivery']={
        'container.deploy':{'length':2.0,'events':[cue(.44/1.2,retract)]},
        'beacon.deploy':{'length':2.65,'events':[cue(0,'sfx_hellpod_cap_release'),cue(.45,'sfx_equipment_beacon_lift'),cue(1.10,'sfx_equipment_beacon_unfold')]},
        # Renderer compresses the authored 3.95-second retract clip to three seconds.
        'beacon.retract':{'length':3,'events':[cue(.05,'sfx_equipment_beacon_fold'),cue(1.20,'sfx_equipment_beacon_lower'),cue(2.50,'sfx_equipment_beacon_iris')]}}
    audio_path.write_text(json.dumps(audio,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    sounds_path.write_text(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Prepared 20 beacon mechanical variants; container retains the original unmodified sound.')

if __name__=='__main__':main()
