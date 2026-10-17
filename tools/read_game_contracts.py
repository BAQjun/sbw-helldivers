from pathlib import Path
import json, zipfile

root = Path(__file__).resolve().parents[1]
cache = root / '.codex/gradle-user-home/caches'
jar = next(cache.glob('forge_gradle/minecraft_user_repo/net/minecraftforge/forge/*/*-sources.jar'))
names = ['net/minecraft/gametest/framework/GameTestHelper.java',
         'net/minecraftforge/gametest/GameTestHolder.java',
         'net/minecraftforge/gametest/PrefixGameTestTemplate.java',
         'net/minecraftforge/gametest/ForgeGameTestHooks.java',
         'net/minecraft/client/renderer/entity/EntityRenderer.java']
with zipfile.ZipFile(jar) as z:
    for name in names:
        if name in z.namelist():
            out = root / '.codex/contracts' / name
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_bytes(z.read(name))
            print(name)
userdev = next(cache.rglob('forge-1.20.1-47.2.0-userdev.jar'), None)
if userdev:
    with zipfile.ZipFile(userdev) as z:
        cfg = json.loads(z.read('config.json'))
        print(json.dumps(cfg.get('runs'), indent=2))
sbw = next(cache.glob('forge_gradle/deobf_dependencies/curse/maven/superb-warfare-*/*/*-mapped*.jar'), None)
if not sbw:
    sbw = next(cache.glob('forge_gradle/deobf_dependencies/curse/maven/superb-warfare-*/*/*_mapped*.jar'))
with zipfile.ZipFile(sbw) as z:
    name = 'com/atsuishio/superbwarfare/item/gun/GunItem.class'
    out = root / '.codex/contracts' / name
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(z.read(name))
