"""Check the exported pack's dependency links and animation/audio contracts."""
from pathlib import Path
import json, struct, math

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
A = RES / 'assets/sbw_helldiver'
def load(p): return json.loads(p.read_text(encoding='utf-8'))
def values(v):
    if isinstance(v, list): return [v]
    if isinstance(v, dict) and ('post' in v or 'pre' in v): return values(v.get('post', v.get('pre')))
    return [value for child in v.values() for value in values(child)]
def asset(ref):
    ns, path = ref.split(':', 1)
    return RES / 'assets' / ns / path
profiles = load(A / 'helldivers/profiles.json')
sounds = load(A / 'sounds.json')
for name in ['machine_gun_casing','gatling_casing','cannon_casing']:
    refs = load(A / f'particles/{name}.json')['textures']
    assert refs == [f'sbw_helldiver:{name}'], (name, 'Particle atlas paths must omit the particle directory')
    assert (A / f'textures/particle/{name}.png').is_file()
audio = load(A / 'helldivers/audio.json')
assert len(profiles) == 5 and len(sounds) == 53 and len(audio['effects']) == 92
models = list((A / 'models/bedrock/vehicle').glob('*.geo.json'))
cap = load(A / 'models/bedrock/misc/hellpod_cap.geo.json')['minecraft:geometry'][0]
source_cap = next(b for b in load(A / 'models/bedrock/vehicle/hellpod_v3.geo.json')['minecraft:geometry'][0]['bones'] if b['name'] == 'pod_eject_cap')
assert len(cap['bones']) == 1 and not cap['bones'][0].get('parent')
assert len(cap['bones'][0]['cubes']) == len(source_cap['cubes']) == 84
for actual, original in zip(cap['bones'][0]['cubes'], source_cap['cubes']):
    assert actual['uv'] == original['uv'] and actual['size'] == original['size']
    assert abs(actual['origin'][1] + 3.3225 - original['origin'][1]) < 1e-8
for file in models:
    g = load(file)['minecraft:geometry'][0]
    names = [b['name'] for b in g['bones']]
    assert len(names) == len(set(names)), (file, 'duplicate bones')
    for bone in g['bones']:
        assert not bone.get('parent') or bone['parent'] in names, (file, bone['name'])
    png = A / 'textures/bedrock/vehicle' / file.name.replace('.geo.json', '.png')
    data = png.read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n'
    width, height = struct.unpack('>II', data[16:24])
    assert (width, height) == (g['description']['texture_width'], g['description']['texture_height']), file
for effect, entry in sounds.items():
    if not effect.startswith('beacon_'):
        assert audio['effects'][effect]['audible']
    for variant in entry['sounds']:
        assert asset('sbw_helldiver:sounds/' + variant['name'].split(':')[1] + '.ogg').is_file(), (effect, variant)
        if not effect.startswith('beacon_'):
            assert variant['attenuation_distance'] >= 32
for effect, definition in audio['effects'].items():
    for target in definition['stop_effects']: assert target in sounds, (effect, target)
    if definition['kind'] == 'marker_only': assert not definition['audible'], effect
for entity, p in profiles.items():
    c = load(A / f'sbw/vehicles/{entity}.json')
    for entry in c['Models']:
        assert asset(entry['Model']).is_file() and asset(entry['Texture']).is_file()
    animations = load(asset(c['Animation']))['animations']
    assert len(p['nozzles']) == 6 and 5.5 < p['pod_height'] < 5.6
    assert len(p['nozzle_bones']) == len(p['nozzle_model_positions']) == len(p['nozzle_directions']) == 6
    for position, direction in zip(p['nozzles'], p['nozzle_directions']):
        assert abs(sum(v*v for v in direction)-1) < 1e-6, 'Nozzle direction must be a unit vector'
        assert abs(direction[1]+math.sqrt(3)/2) < 1e-6, 'Nozzle exhaust must point 30 degrees outward from downward'
        assert position[0]*direction[0]+position[2]*direction[2] > 0, 'Exhaust points into the pod'
    assert bool(p['shell_offset']) == (entity in ['machine_gun_sentry', 'gatling_sentry', 'autocannon_sentry'])
    for name, anim in animations.items():
        assert 'sound_effects' not in anim, (entity, name, 'duplicate native playback')
    for stage in ['deploy', 'retract']:
        anim = animations[f'animation.hellpod.{stage}']
        assert p[f'{stage}_ticks'] == math.ceil(anim['animation_length'] * 20)
        assert all(all(v == [-75, 0, 0] for v in values(anim['bones'][f'pod_flap_{i}']['rotation'])) for i in range(3))
        scale = anim['bones']['pod_eject_cap']['scale']
        assert scale == 0 or all(v == [0, 0, 0] for v in values(scale)), 'Animated duplicate cap'
    assert 'animation.helldiver.deployed' in animations and 'animation.helldiver.retracted' in animations
    if entity != 'supply_hellpod':
        gun = load(RES / f'data/sbw_helldiver/sbw/vehicles/{entity}.json')['Weapons']['Main']
        assert gun['SoundInfo'] == {} and gun['NormalReloadTime'] == gun['EmptyReloadTime'] == 0
        assert gun['ShootPos']['Positions'] == p['muzzle_offsets']
        data=load(RES / f'data/sbw_helldiver/sbw/vehicles/{entity}.json')
        assert data['MaxHealth']==80 and data['ApplyDefaultDamageModifiers'] is False
        assert data['SelfHurtAmount']==20 and data['SelfHurtPercent']==.2
        assert data['SeekInfo']['MaxSeekRange']==(100 if entity in ['rocket_sentry','autocannon_sentry'] else 75)
        assert data['SeekInfo']['MinSeekRange']==(10 if entity=='rocket_sentry' else 0)
        assert all(a['bones'].get('pod_root')=={'position':[0,0,0]} for a in animations.values()), 'Pod material group can be optimized away'
        destroyed=animations['animation.helldiver.destroyed'];assert destroyed['animation_length']==.4
        assert destroyed['bones']['deployment_lift']['position']==[0,0,0], 'Destroyed turret must not retract'
        assert all(last['rotation']['0.4']==[0,0,0] and last['scale']['0.4']==[1,1,1] for bone,last in destroyed['bones'].items() if bone.startswith('pod_iris_'))
        assert data['TurretTurnSpeed'] == ([2.4,2.4] if entity in ['rocket_sentry','autocannon_sentry'] else [8.0,8.0])
        assert p['total_ammo'] == {'machine_gun_sentry':263,'gatling_sentry':750,'rocket_sentry':48,'autocannon_sentry':36}[entity]
        assert gun['Magazine'] == (16 if entity=='rocket_sentry' else p['total_ammo'])
        if entity in ['machine_gun_sentry','gatling_sentry']: assert gun['Projectile'] == 'sbw_helldiver:sentry_bullet'
        if entity in ['rocket_sentry', 'autocannon_sentry']:
            cannon = entity == 'autocannon_sentry'
            assert gun['Projectile'] == ('sbw_helldiver:sentry_cannon_shell' if cannon else 'sbw_helldiver:sentry_rocket')
            assert gun['Damage'] == (70 if cannon else 80) and gun['ExplosionDamage'] == 20
            assert gun['ExplosionRadius'] == (5 if cannon else 4) and gun['Headshot'] == 1
            assert gun['RPM'] == 90
        if entity == 'machine_gun_sentry':
            geo=load(A / f'models/bedrock/vehicle/{p["model"]}.geo.json')['minecraft:geometry'][0]
            bones={b['name']:b for b in geo['bones']}
            bolt=bones['bolt']['cubes'][0];port=bones['recoil']['cubes'][-1]
            marker=bones['recoil']['locators']['shell_exit']
            assert abs(marker[1]-(port['origin'][1]+port['size'][1]/2))<1e-7
            assert port['origin'][2] < -3 and marker[0] < 1.3, 'MG port must be in the front breech, not rear receiver'
            assert abs(marker[2]-(port['origin'][2]+port['size'][2]/2))<1e-7
            assert marker[0]-(bolt['origin'][0]+bolt['size'][0]) > .013*16, 'Case begins inside the bolt'
            expected=[(marker[0]-bones['barrel']['pivot'][0])/16,(marker[1]-bones['barrel']['pivot'][1])/16,-(marker[2]-bones['barrel']['pivot'][2])/16]
            assert all(abs(x-y)<1e-8 for x,y in zip(expected,p['shell_offset'])), 'Runtime exit is detached from model marker'
            assert p['shell_bone']=='recoil'
        if entity == 'autocannon_sentry':
            assert (p['burst_size'],p['burst_interval_ticks'],p['burst_pause_ticks']) == (3,10,20.0)

            geo=load(A / f'models/bedrock/vehicle/{p["model"]}.geo.json')['minecraft:geometry'][0]
            ejector=next(b for b in geo['bones'] if b['name']=='ejector')
            marker=ejector['locators']['shell_exit']['offset']
            assert marker[1] > max(c['origin'][1]+c['size'][1] for c in ejector['cubes']), 'Case spawned below the top hatch'
            assert p['shell_direction'][1] > .8 and p['shell_direction'][2] < 0, 'Cannon cases must clear the receiver upwards/rearwards'
        if entity == 'gatling_sentry':
            geo=load(A / f'models/bedrock/vehicle/{p["model"]}.geo.json')['minecraft:geometry'][0]
            rotor=next(b for b in geo['bones'] if b['name']=='rotor');axis=rotor['pivot']
            radius=rotor['locators']['muzzle_0'][1]-axis[1]
            for i in range(3):
                point=rotor['locators'][f'muzzle_{i}']
                angle=math.radians(i*120)
                x,y=point[0]-axis[0],point[1]-axis[1]
                assert abs(x*math.cos(angle)-y*math.sin(angle))<1e-6
                assert abs(x*math.sin(angle)+y*math.cos(angle)-radius)<1e-6
            assert all('rotation' not in a.get('bones',{}).get('rotor',{}) for a in animations.values())
        reloads = [name for name in animations if name.endswith('.reload')]
        assert bool(reloads) == (entity == 'rocket_sentry'), (entity, reloads)
        if entity == 'rocket_sentry':
            reload = animations['animation.rocket.reload']
            assert p['reload_ticks'] >= 41
            assert 'barrel' in reload['bones'] and 'gunhousing' in reload['bones']
            assert not any('tray' in name and any(v != [0, 0, 0] for v in values(channels.get('position', [0, 0, 0])))
                           for name, channels in reload['bones'].items()), 'Moving reload tray'
for file in (A / 'models/item').glob('*_stratagem.json'):
    tex = load(file)['textures']['layer0']
    assert asset('sbw_helldiver:textures/' + tex.split(':')[1] + '.png').is_file(), file
assert len(list((ROOT / '_asset_pack/当前模型').rglob('*.bbmodel'))) == 9
beacon = load(A / 'models/mesh/stratagem_beacon.mesh.json')
assert len(beacon['vertices']) == len(beacon['joint']) == len(beacon['uv'])
assert len(beacon['faces']) > 7000
assert set(beacon['clips']) == {'closed', 'deploy', 'active'}
assert beacon['clips']['deploy']['length'] == 1
for name in ['stratagem_beacon.png', 'stratagem_beacon_emissive.png', 'white.png']:
    assert (A / 'textures/mesh' / name).is_file()
for name in ['beacon_ready', 'beacon_deploy']:
    assert all(asset('sbw_helldiver:sounds/' + v['name'].split(':')[1] + '.ogg').is_file() for v in sounds[name]['sounds'])
print(f'PASS: {len(models)} models plus native beacon mesh, 5 deployables, 53 audible events, 92 turret recipes, all OGG/texture references and rocket-only reload checked.')
