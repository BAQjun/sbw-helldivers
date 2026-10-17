"""Export only the two requested beacon action cues from verified native events.

Continuous held/beam loops are recorded as event semantics, not baked into
finite action sounds. No mastering, trimming, pitch edits, or added effects.
"""
from __future__ import annotations

import hashlib
import json
import re
import wave
import xml.etree.ElementTree as ET
from pathlib import Path

import numpy as np

PROJECT = Path('D:/Codex/projects/2026-09-19/new-chat')
WORK = Path('D:/Project/sbw_helldiver/.codex/beacon/audio')
SOURCES = WORK / 'extracted/content/audio/foley_player.bnk.dir'
OUTPUT = PROJECT / 'outputs/当前交付/信标球/音效'
OUTPUT.mkdir(parents=True, exist_ok=True)

component = json.loads((PROJECT / 'work/audio_tools/entity_snapshots/StratagemBallComponentData.json').read_text(encoding='utf-8'))['1653832383613497969']
assert component['armed_audio_event'] == 4086158401
assert component['deployed_audio_event'] == 2512009224
assert component['stratagem_blue_audio_start'] == 1032351623

tree = ET.parse(WORK / 'foley_player.xml')
objects = {int(o.find('field[@name="ulID"]').get('value')): o
           for o in tree.findall('.//list[@name="listLoadedItem"]/object')
           if o.find('field[@name="ulID"]') is not None}

def actions(event: int) -> list[dict]:
    result = []
    for f in objects[event].findall('.//field[@name="ulActionID"]'):
        ident = int(f.get('value'))
        o = objects[ident]
        kind = o.find('field[@name="ulActionType"]')
        target = o.find('.//field[@name="idExt"]')
        props = {}
        for p in o.findall('.//object[@name="AkPropBundle"]'):
            key, value = p.find('field[@name="pID"]'), p.find('field[@name="pValue"]')
            if key is not None and value is not None:
                props[key.get('valuefmt')] = value.get('value')
        result.append({'id': ident, 'kind': o.get('name'), 'type': kind.get('valuefmt'),
                       'target': int(target.get('value')), 'properties': props})
    return result

assert all(a['kind'] == 'CAkActionStop' for a in actions(2512009224))

def native_gain(event_index: int, media: int) -> float:
    paths = list((WORK / 'txtp').glob(f'*-{event_index}-event*.txtp'))
    assert len(paths) == 1, paths
    text = paths[0].read_text(encoding='utf-8')
    master = re.search(r'\* master volume: auto \((-?[\d.]+)dB\)', text)
    assert master, paths[0]
    rows = [line for line in text.splitlines() if f'##{media}.wem' in line]
    assert len(rows) == 1, (media, rows)
    match = re.search(r'#v\s+(-?[\d.]+)dB', rows[0])
    gain = float(match.group(1)) if match else 0.0
    # wwiser's optional preview headroom is not an original game gain.
    return gain - float(master.group(1))

def export(name: str, event: int, event_index: int, parts: list[tuple[int, float]]) -> dict:
    arrays = []
    records = []
    for media, delay in parts:
        path = SOURCES / f'{media}.wav'
        with wave.open(str(path), 'rb') as reader:
            assert (reader.getframerate(), reader.getnchannels(), reader.getsampwidth()) == (48000, 2, 2)
            samples = np.frombuffer(reader.readframes(reader.getnframes()), dtype='<i2').reshape(-1, 2).astype(np.float64) / 32768
        gain = native_gain(event_index, media)
        start = round(delay * 48000)
        arrays.append((start, samples * 10 ** (gain / 20)))
        records.append({'source_media_id': media, 'source_file': str(path),
                        'source_sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                        'source_frames': len(samples), 'delay_seconds': delay, 'native_gain_db': gain})
    length = max(start + len(samples) for start, samples in arrays)
    mixed = np.zeros((length, 2), dtype=np.float64)
    for start, samples in arrays:
        mixed[start:start+len(samples)] += samples
    peak = float(np.max(np.abs(mixed)))
    assert 0 < peak < 1, ('native gain would clip; do not silently normalize', peak)
    # Preserve decoded 16-bit layers and native gains in a 24-bit stereo WAV.
    pcm = np.rint(mixed * 8388608).astype('<i4')
    packed = pcm.view(np.uint8).reshape(-1, 4)[:, :3].tobytes()
    destination = OUTPUT / name
    with wave.open(str(destination), 'wb') as writer:
        writer.setnchannels(2)
        writer.setsampwidth(3)
        writer.setframerate(48000)
        writer.writeframes(packed)
    with wave.open(str(destination), 'rb') as reader:
        assert (reader.getnframes(), reader.getframerate(), reader.getnchannels(), reader.getsampwidth()) == (length, 48000, 2, 3)
        assert len(reader.readframes(length)) == length * 6
    return {'file': str(destination), 'event_id': event, 'event_actions': actions(event),
            'seconds': length / 48000, 'frames': length, 'peak': peak, 'sources': records,
            'sha256': hashlib.sha256(destination.read_bytes()).hexdigest()}

# One permitted original variant per random container, never all variants at once.
# Armed: immediate ready click plus one random mechanical handling layer.
ready = export('01_战备选好_信标准备.wav', 4086158401, 12426,
               [(965247493, 0.0), (840653805, 0.0)])
# Blue deploy: original three mechanical containers plus the startup tone.
# Random delay ranges: 0..0.2 and 0..0.3 seconds; use their midpoints.
# Parent delay 0.2, nested delay 0.05. Startup tone action delay is 0.6.
deploy = export('02_落地展开并启动光束.wav', 1032351623, 11979,
                [(308857965, 0.3), (263083201, 0.4), (497066532, 0.2), (509487668, 0.6)])

report = {
    'source': 'User-installed Helldivers 2; StratagemBallComponentData and foley_player.bnk HIRC',
    'bank': str(PROJECT / 'work/audio_tools/v0755_all_banks_raw/content/audio/foley_player.bnk'),
    'original_beacon_unit': '0x16f397ca5f51f271',
    'scope': 'Exactly two finite action cues: ready, and landing/unfolding/blue-beam activation.',
    'rendering': 'Native decoded media, static event-relative gains, one valid variant per random container, original delay ranges at midpoint. No normalization, respeeding, trimming, added reverb, synthesis, or recording-derived audio.',
    'limitation': 'Offline action cues do not reproduce live distance/occlusion/reverb/RTPC changes or bake persistent held/beam loops into one-shots.',
    'actions': {'ready': ready, 'land_unfold_activate': deploy},
    'original_runtime_semantics': {
        'armed_audio_event': {'id': 4086158401, 'held_loop_media': [316881483, 191643849], 'held_loop_delay_seconds': 0.3, 'held_loop_fade_in_seconds': 0.45},
        'deployed_audio_event': {'id': 2512009224, 'kind': 'Stop-only; not a deployment sound', 'actions': actions(2512009224)},
        'stratagem_blue_audio_start': {'id': 1032351623, 'beam_loop_media': 464085076, 'beam_loop_delay_seconds': 0.7, 'beam_loop_fade_in_seconds': 0.2},
        'stratagem_blue_audio_stop': {'id': 3766827821, 'actions': actions(3766827821)},
        'media_less_plugin': '0x00830402 source plugin actions have no WAV media and are not replaced with invented audio.',
    },
}
(OUTPUT / '原版事件对应.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
(WORK / 'original_beacon_component.json').write_text(json.dumps(component, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
(OUTPUT / '说明.md').write_text('''# 信标球的两个动作声音

1. **01_战备选好_信标准备.wav**：选好战备、信标球准备就绪。原事件 `4086158401`，对应原版信标组件的 `armed_audio_event`。
2. **02_落地展开并启动光束.wav**：落地后展开并启动蓝色光束，作为一个完整动作播放。原事件 `1032351623`，对应 `stratagem_blue_audio_start`。

这两个文件使用本机游戏中解包的原始音频，按原事件的相对音量和延迟叠层；每个随机组只选择一个允许的原版变体。保留音色、速度与完整尾音，没有添加新音效、磨音或使用录屏音轨。第二个文件的尾音较长，不代表信标展开动作必须持续同样长的时间。

原版还会持续播放手持与光束循环声；它们不属于这两个短促动作文件，未额外交付第三个声音。实际接入时，应分别按原版的开始和停止事件控制循环，不能把两个动作文件反复循环。

注意：`deployed_audio_event = 2512009224` 的实际动作全部是 Stop，只负责停止手持声音，不能把它误标为“展开声”。这里已沿实际蓝色信标启动事件找到第二个动作的音频。

详细来源、媒体编号、时序和文件校验见同目录 `原版事件对应.json`。这些是离线动作试听，未复现游戏中的动态距离衰减、遮挡、环境混响和 RTPC，也尚未改动模组的信标部署逻辑。
''', encoding='utf-8')
assert len(list(OUTPUT.glob('*.wav'))) == 2
print(json.dumps({k: {x: v[x] for x in ['file', 'event_id', 'seconds', 'peak', 'sha256']} for k, v in report['actions'].items()}, ensure_ascii=False, indent=2))
