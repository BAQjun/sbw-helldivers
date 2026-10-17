from pathlib import Path
import shutil,zipfile,time
ROOT=Path('D:/Project/sbw_helldiver')
OLD=Path('C:/Users/USER/.gradle')
NEW=ROOT/'.codex/gradle-user-home'
NEW.mkdir(parents=True,exist_ok=True)
started=time.monotonic();count=0
for rel in ['caches/modules-2','caches/forge_gradle','caches/8.14.2','wrapper/dists/gradle-8.14.2-bin']:
 for src in (OLD/rel).rglob('*'):
  if not src.is_file() or src.suffix in ['.lock','.lck'] or '_mapped_parchment_' in str(src):continue
  dest=NEW/src.relative_to(OLD)
  if dest.exists() and dest.stat().st_size==src.stat().st_size:continue
  dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(src,dest);count+=1
shutil.copy2(OLD/'init.gradle',NEW/'init.gradle')
jar=next((NEW/'caches/forge_gradle/deobf_dependencies/curse/maven/superb-warfare-1218165').rglob('*.jar'))
out=ROOT/'.codex/contracts';out.mkdir(parents=True,exist_ok=True)
prefixes=['com/atsuishio/superbwarfare/client/animation/', 'com/atsuishio/superbwarfare/entity/vehicle/base/', 'com/atsuishio/superbwarfare/client/renderer/entity/BasicAutoAimableRenderer', 'com/atsuishio/superbwarfare/client/renderer/entity/GeoVehicleRenderer','com/atsuishio/superbwarfare/client/model/','com/atsuishio/superbwarfare/data/gun/','com/atsuishio/superbwarfare/data/vehicle/','com/atsuishio/superbwarfare/entity/projectile/SmallRocketEntity','com/atsuishio/superbwarfare/entity/projectile/RocketEntity']
with zipfile.ZipFile(jar) as z,zipfile.ZipFile(out/'sbw-contracts.jar','w') as target:
 (out/'actual-mods.toml').write_bytes(z.read('META-INF/mods.toml'))
 for n in z.namelist():
  if n.endswith('.class') and any(n.startswith(p) for p in prefixes):target.writestr(n,z.read(n))
  if n.startswith('data/superbwarfare/sbw/vehicles/') and n.endswith('.json'):
   targetpath=out/n;targetpath.parent.mkdir(parents=True,exist_ok=True);targetpath.write_bytes(z.read(n))
print(f'Prepared D: Gradle cache: {count} files in {time.monotonic()-started:.1f}s; source API jar: {jar}')
