import unittest
from repair_drone_surfaces import same_surface

def mesh(points,triangles):
 return {'vertices':{str(i):p for i,p in enumerate(points)},'faces':{str(i):{'vertices':list(map(str,t)),'texture':0}for i,t in enumerate(triangles)}}
class SurfaceRegression(unittest.TestCase):
 def test_identical(self):
  a=mesh([[0,0,0],[1,0,0],[1,1,0],[0,1,0]],[[0,1,2],[0,2,3]])
  self.assertTrue(same_surface(a,a))
 def test_exact_planar_retriangulation(self):
  p=[[0,0,0],[1,0,0],[1,1,0],[0,1,0]]
  self.assertTrue(same_surface(mesh(p,[[0,1,2],[0,2,3]]),mesh(p,[[0,1,3],[1,2,3]])))
 def test_twisted_quad_with_same_vertices_and_boundary(self):
  p=[[0,0,0],[1,0,0],[1,1,.02],[0,1,0]]
  self.assertFalse(same_surface(mesh(p,[[0,1,2],[0,2,3]]),mesh(p,[[0,1,3],[1,2,3]])))
 def test_flipped_normal(self):
  p=[[0,0,0],[1,0,0],[1,1,0]]
  self.assertFalse(same_surface(mesh(p,[[0,1,2]]),mesh(p,[[0,2,1]])))
 def test_same_area_different_location(self):
  p=[[0,0,0],[1,0,0],[1,1,0]]
  self.assertFalse(same_surface(mesh(p,[[0,1,2]]),mesh([[x+1,y,z]for x,y,z in p],[[0,1,2]])))
 def test_duplicate_area(self):
  p=[[0,0,0],[1,0,0],[1,1,0]]
  self.assertFalse(same_surface(mesh(p,[[0,1,2]]),mesh(p,[[0,1,2],[0,1,2]])))
if __name__=='__main__':unittest.main()
