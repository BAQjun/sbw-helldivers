import copy,unittest
from rebuild_equipment_panels import rebuild_element,count,triangulate,mapping
from other_turrets import affine_star_consistent,weld_exact_positions

def fan(fold=False,warp=False):
 p=[[0,0,0],[2,0,0],[2,2,0],[0,2,0],[1,1,.1 if warp else 0]]
 e={'type':'mesh','name':'panel','shading':'smooth','vertices':{str(i):v for i,v in enumerate(p)},'faces':{}}
 for i in range(4):
  vs=list(map(str,[i,(i+1)%4,4]));e['faces'][str(i)]={'texture':0,'vertices':vs,'uv':{v:[p[int(v)][0],p[int(v)][1]]for v in vs}}
 if fold:
  for f in list(e['faces'].values())[2:]:
   for v in f['vertices']:f['uv'][v][0]=2-f['uv'][v][0]
 return e
class PanelRegression(unittest.TestCase):
 def test_planar_panel_reconstructed(self):
  e=fan();r,s=rebuild_element(e);self.assertEqual(count(r),2);self.assertEqual(s['patches'],1);self.assertTrue(all(v.startswith('panel_')for v in r['vertices']))
 def test_uv_fold_not_crossed(self):
  e=fan(True);r,s=rebuild_element(e);slopes={round(mapping([r['vertices'][v]for v in f['vertices']],[f['uv'][v]for v in f['vertices']],(0,1))[0][0])for f in r['faces'].values()};self.assertEqual(slopes,{-1,1})
 def test_previous_collapse_rejects_mirrored_mapping(self):
  e=fan(True);self.assertFalse(affine_star_consistent(e['vertices'],e['faces'].values()))
 def test_previous_collapse_accepts_single_mapping(self):
  e=fan();self.assertTrue(affine_star_consistent(e['vertices'],e['faces'].values()))
 def test_warped_panel_keeps_geometry(self):
  e=fan(warp=True);r,s=rebuild_element(e);self.assertEqual(count(r),4);self.assertEqual(r['vertices'],e['vertices'])
 def test_untouched_native_quad_remains_quad(self):
  e=fan();e['faces']={'quad':{'texture':0,'vertices':['0','1','2','3'],'uv':{str(i):e['vertices'][str(i)][:2]for i in range(4)}}};r,s=rebuild_element(e);self.assertEqual(r['faces'],e['faces'])
 def test_smooth_vertex_seams_not_welded(self):
  e=fan();e['vertices']['duplicate']=e['vertices']['0'][:];old=copy.deepcopy(e);weld_exact_positions(e);self.assertEqual(e,old)
 def test_concave_boundary(self):
  self.assertEqual(len(triangulate([[0,0],[2,0],[2,2],[1,1],[0,2]])),3)
 def test_rig_metadata_preserved(self):
  e=fan();e.update(uuid='fixed',origin=[1,2,3],rotation=[0,30,0]);r,_=rebuild_element(e);self.assertEqual({k:v for k,v in r.items()if k not in ('vertices','faces')},{k:v for k,v in e.items()if k not in ('vertices','faces')})
if __name__=='__main__':unittest.main()
