"""Restore exact files from the unpublished workspace archive, without overwriting different files."""
from pathlib import Path
import argparse,json,zipfile,hashlib
p=argparse.ArgumentParser();p.add_argument('--archive-dir',type=Path,required=True);p.add_argument('--destination',type=Path,required=True);p.add_argument('--prefix',default='');args=p.parse_args()
manifest=json.loads((args.archive_dir/'manifest.json').read_text(encoding='utf8'));handles={};restored=0
try:
 for entry in manifest['files']:
  relative=Path(entry['root'])/entry['path']
  if args.prefix and not relative.as_posix().startswith(args.prefix):continue
  dest=(args.destination/relative).resolve();root=args.destination.resolve()
  if not dest.is_relative_to(root):raise ValueError('Unsafe manifest path')
  if dest.exists():
   if hashlib.sha256(dest.read_bytes()).hexdigest()==entry['sha256']:continue
   raise FileExistsError(str(dest))
  dest.parent.mkdir(parents=True,exist_ok=True);temp=dest.with_name(dest.name+'.restoring');h=hashlib.sha256()
  with temp.open('wb') as out:
   for ref in entry['objects']:
    meta=manifest['objects'][ref];part=meta['part']
    if part not in handles:handles[part]=zipfile.ZipFile(args.archive_dir/f'workspace-snapshot-part{part:03d}.zip')
    data=handles[part].read('objects/'+ref)
    if hashlib.sha256(data).hexdigest()!=ref:raise ValueError('Archive object checksum failed')
    out.write(data);h.update(data)
  if h.hexdigest()!=entry['sha256']:temp.unlink();raise ValueError('File checksum failed')
  temp.replace(dest);restored+=1
finally:
 for z in handles.values():z.close()
print(f'Restored {restored} files beneath {args.destination.resolve()}')
