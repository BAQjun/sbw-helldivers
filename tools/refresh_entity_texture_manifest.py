"""Refresh validated binary-alpha texture hashes after texture exports or atlas packing."""
from pathlib import Path
import argparse,hashlib,json
from PIL import Image

def refresh(assets):
    entries={}
    for path in sorted((assets/'textures/bedrock/vehicle').rglob('*.png')):
        with Image.open(path) as image:
            histogram=image.convert('RGBA').getchannel('A').histogram()
        if not sum(histogram[1:255]):
            entries[assets.name+':'+path.relative_to(assets).as_posix()]=hashlib.sha256(path.read_bytes()).hexdigest()
    manifest=assets/'models/entity_cutout_textures.json'
    manifest.write_text(json.dumps(entries,indent=2)+'\n','utf8')
    pack=assets.parent.parent/f'resourcepacks/low_quality_models/assets/{assets.name}'
    if pack.is_dir():
        for name in list(entries):
            path=pack/name.split(':',1)[1]
            if path.exists():
                with Image.open(path) as image:binary=not sum(image.convert('RGBA').getchannel('A').histogram()[1:255])
                if binary:entries[name]=hashlib.sha256(path.read_bytes()).hexdigest()
                else:entries.pop(name)
        target=pack/'models/entity_cutout_textures.json';target.parent.mkdir(parents=True,exist_ok=True)
        target.write_text(json.dumps(entries,indent=2)+'\n','utf8')
    return len(entries)

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--assets',type=Path,required=True);a=p.parse_args();print(refresh(a.assets),'validated texture entries')
