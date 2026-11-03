"""Prepare machine loops from locally decoded native streams, never publish inputs."""
import argparse,json,subprocess
from pathlib import Path

parser=argparse.ArgumentParser();parser.add_argument('--source',type=Path,required=True);parser.add_argument('--ffmpeg',default='ffmpeg');args=parser.parse_args()
out=Path(__file__).resolve().parents[1]/'src/main/resources/assets/sbw_helldiver'
(out/'sounds/machines').mkdir(parents=True,exist_ok=True)
sounds=json.loads((out/'sounds.json').read_text(encoding='utf-8'))
sources={}
for name,bank,event,stop,node,media,gain in [
    ('bioprocessor','obj_cy_shutdown_grinder',343445948,2610515472,640549576,217124450,-4),
    ('refinery','obj_cy_lava_hack_refinery',3974959357,1832582421,145983370,806688412,0),
]:
    effect=f'machine_{name}_loop'
    subprocess.run([args.ffmpeg,'-hide_banner','-loglevel','error','-y','-i',str(args.source/f'{media}.wav'),'-ac','1','-af',f'volume={gain}dB','-c:a','libvorbis','-q:a','5',str(out/f'sounds/machines/{name}_loop.ogg')],check=True)
    sounds[effect]={'subtitle':f'subtitles.sbw_helldiver.{effect}','sounds':[{'name':f'sbw_helldiver:machines/{name}_loop','attenuation_distance':64}]}
    sources[name]={'bank':f'content/audio/{bank}','play_event':event,'stop_event':stop,'loop_node':node,'stream_media':media,'gain_db':gain,
        'verified':'Bank Play/Seek actions reference this continuous loop; the listed Stop action targets the same node. The original models reference the grinder/refinery entities.',
        'adaptation':'Loop follows sample processing, including power pauses; original objective state transitions and RTPC curves are not reproduced. No invented startup/shutdown clips.'}
(out/'sounds.json').write_text(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(out/'helldivers/machine_audio_sources.json').write_text(json.dumps(sources,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
for lang,labels in [('zh_cn',['生物处理器运转','精炼厂运转']),('en_us',['Bioprocessor running','Refinery running'])]:
    p=out/f'lang/{lang}.json';j=json.loads(p.read_text(encoding='utf-8'))
    for name,label in zip(sources,labels):j[f'subtitles.sbw_helldiver.machine_{name}_loop']=label
    p.write_text(json.dumps(j,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
