"""Regenerate skinned belt and display UVs after the final runtime atlas is packed."""
from pathlib import Path
import json

def export(assets):
 g=json.loads((assets/'models/bedrock/vehicle/hmg_hellpod_v1.geo.json').read_bytes())['minecraft:geometry'][0]
 names={'l_ammo_01','r_ammo_01','l_ammo_02','r_ammo_02'}
 previous=assets/'helldivers/hmg_feed.json'
 old={m['bone']:m for m in json.loads(previous.read_bytes())['meshes']} if previous.exists() else {}
 meshes=[{'bone':b['name'],'poly_mesh':b['poly_mesh']} if b.get('poly_mesh') else old[b['name']] for b in g['bones']if b['name']in names]
 assert len(meshes)==4
 assert all(not m['poly_mesh'].get('normalized_uvs',False) for m in meshes),'HMG renderer expects texel UVs'
 assert all(len(p)==3 or p[2]==p[3] for m in meshes for p in m['poly_mesh']['polys']),'Export triangulated attachment meshes'
 out={'texture_width':g['description']['texture_width'],'texture_height':g['description']['texture_height'],'meshes':meshes}
 (assets/'helldivers/hmg_feed.json').write_text(json.dumps(out,separators=(',',':'))+'\n','utf8')
 g=json.loads((assets/'models/bedrock/vehicle/laser_hellpod_v1.geo.json').read_bytes())['minecraft:geometry'][0]
 width=g['description']['texture_width'];height=g['description']['texture_height'];meshes=[]
 for b in g['bones']:
  n=b['name']
  if not(n.startswith('ls_displays_')or n.startswith('ls_heat_')and n[8:].isdigit()):continue
  p=b['poly_mesh'];norm=p.get('normalized_uvs',False)
  meshes.append({'segment':-1 if n.startswith('ls_displays_')else int(n[8:]),'positions':p['positions'],'uvs':[[v[0]if norm else v[0]/width,1-(v[1]if norm else v[1]/height)]for v in p['uvs']],'faces':[[[v[0],v[2]]for v in f[:3]]for f in p['polys']]})
 assert len(meshes)==21
 (assets/'helldivers/laser_displays.json').write_text(json.dumps({'meshes':meshes},separators=(',',':'))+'\n','utf8')
if __name__=='__main__':export(Path(__file__).resolve().parents[1]/'src/main/resources/assets/sbw_helldiver')
