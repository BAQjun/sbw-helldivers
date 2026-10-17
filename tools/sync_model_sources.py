"""Synchronize accepted Blockbench sources with Git; generated runtime geometry stays in src/."""
from pathlib import Path
import argparse,hashlib,shutil
p=argparse.ArgumentParser();p.add_argument('--delivery',type=Path,required=True);p.add_argument('--check',action='store_true');args=p.parse_args()
source=args.delivery/'模型';dest=Path(__file__).resolve().parents[1]/'art/models'
if not source.is_dir():raise SystemExit('The delivery directory must contain 模型')
count=0;different=[]
for f in source.rglob('*'):
 if not f.is_file():continue
 rel=f.relative_to(source)
 if f.suffix.lower() in {'.glb','.blend1','.zip'} or f.name.endswith('.geo.json') or '游戏资源' in rel.parts:continue
 target=dest/rel;count+=1
 if target.exists() and hashlib.sha256(f.read_bytes()).digest()==hashlib.sha256(target.read_bytes()).digest():continue
 different.append(rel.as_posix())
 if not args.check:target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(f,target)
if args.check and different:raise SystemExit('Sources out of sync: '+', '.join(different))
print(f'{count} model source files checked; {len(different)} '+('differ' if args.check else 'copied'))
