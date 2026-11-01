"""Export native terminal media and mission glyphs from the locally decoded game data."""
import argparse, base64, json, shutil, subprocess, sys, wave
from pathlib import Path

p=argparse.ArgumentParser();p.add_argument('--artwork',type=Path,required=True);a=p.parse_args()
work=a.artwork/'work/survival06'
out=Path(__file__).resolve().parents[1]/'src/main/resources/assets/sbw_helldiver'
icons=json.loads((work/'native_icons.json').read_text())
subprocess.run(['powershell.exe','-NoProfile','-STA','-File',str(Path(__file__).with_name('export_machine_icons.ps1')),'-Source',str(work/'ui_native/content/ui/shared/resources/generated_icons/mission_icons.xaml'),'-OutputDirectory',str(out/'textures/gui/machines')],check=True)
shutil.copyfile(a.artwork/'outputs/当前交付/assets/图标/机器集装箱呼叫/machine_container_call.png',out/'textures/item/machine_deployment_beacon_stratagem.png')

# InteractableMinigameComponentData.audio uses these events for ordinary terminals.
# The seven key mechanisms are the random container shared by correct/incorrect input.
# Proprietary procedural Wwise plugin nodes have no embedded media and are not synthesised.
keys=[394284415,720879876,491416012,166576578,623733073,793799307,305174497]
source=work/'terminal_audio/content/audio/obj_generic_terminal.bnk.dir'
audio=out/'sounds/terminal';audio.mkdir(parents=True,exist_ok=True)
for i,id in enumerate(keys):
    subprocess.run(['D:/ffrp/ffmpeg.exe','-hide_banner','-loglevel','error','-y','-i',str(source/f'{id}.wav'),'-af','volume='+('0.794328' if id in [394284415,166576578] else '1'),'-c:a','libvorbis','-q:a','5',str(audio/f'key_{i}.ogg')],check=True)
subprocess.run(['D:/ffrp/ffmpeg.exe','-hide_banner','-loglevel','error','-y','-i',str(source/'230678024.wav'),'-c:a','libvorbis','-q:a','5',str(audio/'error.ogg')],check=True)
sounds=json.loads((out/'sounds.json').read_text(encoding='utf-8'))
for name,files in [('terminal_input',[f'key_{i}' for i in range(7)]),('terminal_error',['error'])]:
    sounds[name]={'subtitle':f'subtitles.sbw_helldiver.{name}','sounds':[{'name':f'sbw_helldiver:terminal/{f}','attenuation_distance':64} for f in files]}
(out/'sounds.json').write_text(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
provenance={'bank':'content/audio/obj_generic_terminal','component':'InteractableMinigameComponentData.audio','correct_event':4105722653,'incorrect_event':304927172,'key_random_container':102604740,'key_media':keys,'incorrect_media':230678024,'procedural_nodes':'No embedded media; omitted rather than replaced with beacon-ball audio.','mission_icons':{'bioprocessor':'MissionProcessors','refinery':'MissionIconIndustrialcomplex'}}
(out/'helldivers/equipment_terminal_sources.json').write_text(json.dumps(provenance,indent=2)+'\n')
print('Exported native terminal keys, error tone, mission glyphs and accepted call card.')
