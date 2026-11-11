import com.example.sbwhelldiver.client.PlanarMeshQuads;
import java.util.*;
public final class NearMeshCheck {
 static float[] p={0,0,0,1,0,0,1,1,0,0,0,0,1,1,0,0,1,0};
 static float[] n={0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1};
 static float[] uv={0,0,1,0,1,1,0,0,1,1,0,1};
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static String corner(int at){return p[at*3]+","+p[at*3+1]+","+p[at*3+2]+","+uv[at*2]+","+uv[at*2+1];}
 static String tri(int a,int b,int c){String[] s={corner(a),corner(b),corner(c)};String best=null;for(int i=0;i<3;i++){String t=s[i]+"/"+s[(i+1)%3]+"/"+s[(i+2)%3];if(best==null||t.compareTo(best)<0)best=t;}return best;}
 public static void main(String[] args){
  int[] q=PlanarMeshQuads.compile(p,n,uv).corners();check(q.length==4,"flat square was not merged");
  var before=new TreeSet<>(List.of(tri(0,1,2),tri(3,4,5)));
  var after=new TreeSet<>(List.of(tri(q[0],q[1],q[2]),tri(q[0],q[2],q[3])));
  check(before.equals(after),"diagonal, winding or UV changed");
  float[] curved=p.clone();curved[17]=.01f;check(PlanarMeshQuads.compile(curved,n,uv).corners().length==8,"curved surface merged");
  float[] seam=uv.clone();seam[6]=.1f;check(PlanarMeshQuads.compile(p,n,seam).corners().length==8,"UV seam merged");
  float[] bent=n.clone();bent[15]=.001f;check(PlanarMeshQuads.compile(p,bent,uv).corners().length==8,"normal boundary merged");
  float[] nonAffine=uv.clone();nonAffine[10]=.1f;check(PlanarMeshQuads.compile(p,n,nonAffine).corners().length==8,"tangent seam merged");
  float[] source=new float[64];int[] ids={0,1,2,2,3,4,5,5};for(int i=0;i<8;i++){int v=ids[i];System.arraycopy(p,v*3,source,i*8,3);System.arraycopy(uv,v*2,source,i*8+3,2);System.arraycopy(n,v*3,source,i*8+5,3);}
  check(PlanarMeshQuads.packQuads(source).length==32,"machine submission not reduced");
  float[] trueQuad=Arrays.copyOf(PlanarMeshQuads.packQuads(source),32);check(PlanarMeshQuads.packQuads(trueQuad)==trueQuad,"authored quad changed");
  // Both animation-collapsed and unmatched triangles remain valid four-corner primitives.
  check(PlanarMeshQuads.compile(new float[9],new float[9],new float[6]).corners().length==4,"degenerate topology broken");
  System.out.println("NEAR_MESH_CHECK_PASSED diagonal winding UV seams normals tangents curved surfaces original-quads");
 }
}
