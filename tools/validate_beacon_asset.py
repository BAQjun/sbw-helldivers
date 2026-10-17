"""Compare actual Blockbench rendering transforms with the extracted native rig."""
import json,sys
from pathlib import Path
import numpy as np
sys.path.insert(0,str(Path(__file__).parent))
import inspect_beacon_source as src
root=Path('D:/Codex/projects/2026-09-19/new-chat/outputs/当前交付/信标球')
model=json.loads((root/'模型/信标球.bbmodel').read_text(encoding='utf8'))
runtime=json.loads((root/'模型/信标球.mesh.json').read_text(encoding='utf8'))
frames=json.loads((src.ROOT/'bb_motion_matrices.json').read_text(encoding='utf8'))
names={n['name']:i for i,n in enumerate(src.doc['nodes'])}
clips={'closed':src.doc['animations'][2],'deploy':src.doc['animations'][0],'active':src.doc['animations'][1]}
G0i=np.linalg.inv(src.pose(clips['closed'],0));maximum=0;worst=None;errors=[]
models={e['name']:e for e in model['elements']}
for state in frames:
 D=src.pose(clips[state['name']],state['time'])@G0i
 for part in state['meshes']:
  e=models[part['name']];v=np.array(list(e['vertices'].values()),dtype=float)
  if part['name']=='closed_light_ring':
   factor=1 if state['name']=='closed' else 1e-6 if state['name']=='active' else max(1e-6,1-state['time']*30)
   center=np.array(runtime['locators']['closed_ring_origin'])/16
   T=np.eye(4);T[:3,3]=center;Ti=np.eye(4);Ti[:3,3]=-center
   expected_matrix=D[11]@T@np.diag([factor,factor,factor,1])@Ti
  else:expected_matrix=D[names[part['name']]]
  expected=(expected_matrix@np.column_stack((v/16,np.ones(len(v)))).T).T[:,:3]*16
  M=np.array(part['matrix']).reshape(4,4).T
  actual=(M@np.column_stack((v-np.array(part['origin']),np.ones(len(v)))).T).T[:,:3]
  error=float(np.linalg.norm(expected-actual,axis=1).max())
  errors.append((error,state['name'],state['time'],part['name']))
  if error>maximum:maximum=error;worst=[state['name'],state['time'],part['name']]
if '--diagnostic' in sys.argv:
 print('Worst:',sorted(errors,reverse=True)[:20]);print('Static:',[x for x in errors if x[1]!='deploy']);
 state=next(f for f in frames if f['name']=='deploy' and f['time']==.9)
 D=src.pose(clips['deploy'],.9)@G0i
 for part in state['meshes']:
  if part['name'] in ['boss','front_lid_1','front_lid_2','front_lid_3']:
   print(part['name'],'\nEXPECTED',D[names[part['name']]],'\nACTUAL',np.array(part['matrix']).reshape(4,4).T)
 sys.exit(0)
assert maximum<.00005,(maximum,worst)
audit=json.loads((root/'参考/核对记录.json').read_text(encoding='utf8'))
assert len(runtime['vertices'])==5853+audit['recreated_closed_ring_vertices']
assert len(runtime['faces'])==audit['runtime_total_triangles']
assert len(runtime['clips']['deploy']['frames'])==31
assert all(np.isfinite(np.array(f['matrices'][str(b['index'])])).all() for f in runtime['clips']['deploy']['frames'] for b in runtime['bones'])
audit['blockbench_version']='5.2.1'
audit['blockbench_sampled_states']=len(frames)
audit['blockbench_max_rigid_transform_error_units']=maximum
audit['blockbench_max_error_sample']=worst
(root/'参考/核对记录.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps({'passed':True,'sampled_states':len(frames),'max_rigid_transform_error_units':maximum,'worst':worst},ensure_ascii=False))
