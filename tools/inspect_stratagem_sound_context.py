from pathlib import Path
import json, xml.etree.ElementTree as ET, subprocess
W=Path('D:/Codex/projects/2026-09-19/new-chat/work')
for name in ['entity_hash_names.json','thin_hash_names.json']:
    data=json.loads((W/'audio_tools'/name).read_text(encoding='utf-8'))
    print(name,[(k,v) for k,v in data.items() if 'stratagem' in str(v).lower() and not str(v).startswith('content/')][:70])
index=json.loads((W/'trigger_investigation/archive_index.json').read_text(encoding='utf-8'))
print('UI_STRAT_PATHS',[(x['path'],x['type_name']) for x in index if 'stratagem' in x.get('path','').lower() and '/ui/' in x.get('path','')][:90])
root=Path('D:/Project/sbw_helldiver/.codex/beacon/audio')
short=json.loads((root/'ui_short_candidates.json').read_text())
ffmpeg=W/'audio_tools/v0.7.55/filediver-cli/ffmpeg.exe'
# Audition only, never used as runtime sound. Labels remain in the JSON index.
for start in [0,30]:
    lines=[]
    for n,item in enumerate(short[start:start+30]):
        lines.extend(["file '"+item['path'].replace('\\','/')+"'","file '"+str(root/'silence.wav').replace('\\','/')+"'"])
    listing=root/f'ui_audition_{start}.txt';listing.write_text('\n'.join(lines)+'\n')
    import wave
    with wave.open(str(root/'silence.wav'),'wb') as out: out.setnchannels(1);out.setsampwidth(2);out.setframerate(48000);out.writeframes(b'\0'*24000)
    subprocess.run([str(ffmpeg),'-hide_banner','-loglevel','error','-y','-f','concat','-safe','0','-i',str(listing),'-ac','1',str(root/f'ui_audition_{start}.wav')],check=True)
print('AUDITION_LIST',[(i,x['media']) for i,x in enumerate(short)])
