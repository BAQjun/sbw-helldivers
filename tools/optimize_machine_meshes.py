"""Reconstruct convex planar quads without moving vertices or changing UVs.

The quad's 0--2 diagonal is exactly the original shared triangle edge. This
preserves the original triangulation, texture mapping, silhouette and animation.
Authored Blockbench sources are deliberately untouched.
"""
import argparse, gzip, json, math
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def sub(a,b): return tuple(x-y for x,y in zip(a,b))
def cross(a,b): return (a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])
def dot(a,b): return sum(x*y for x,y in zip(a,b))

def optimize(p):
    ids=p.get('indices')
    if ids is None:return
    triangles=[ids[i:i+3] for i in range(0,len(ids),3)]
    # Weld only identical position AND UV. Texture seams remain separate.
    canonical={}; remap=[]
    for xyz,uv in zip(p['p'],p['uv']):
        key=tuple(xyz+uv);remap.append(canonical.setdefault(key,len(canonical)))
    edges=defaultdict(list)
    for i,t in enumerate(triangles):
        for k in range(3):edges[(remap[t[k]],remap[t[(k+1)%3]])].append((i,k))
    used=set();faces=[]
    for i,t in enumerate(triangles):
        if i in used:continue
        q=None
        for k in range(3):
            a,b,c=t[k],t[(k+1)%3],t[(k+2)%3]
            for j,l in edges.get((remap[b],remap[a]),[]):
                if j==i or j in used:continue
                d=triangles[j][(l+2)%3]
                # [b,c,a,d] is split along b--a, the original shared edge.
                candidate=[b,c,a,d];v=[p['p'][x] for x in candidate]
                n=cross(sub(v[1],v[0]),sub(v[2],v[0]));length=math.sqrt(dot(n,n))
                if length<1e-12:continue
                if abs(dot(n,sub(v[3],v[0])))>length*1e-7:continue
                if any(dot(cross(sub(v[(x+1)%4],v[x]),sub(v[(x+2)%4],v[(x+1)%4])),n)<=length*length*1e-9 for x in range(4)):continue
                q=candidate;used.add(j);break
            if q:break
        faces.append(q or t);used.add(i)
    # Retain indices for re-export and independent surface equivalence checks.
    p['faces']=faces

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--report',type=Path,required=True);args=parser.parse_args()
    report={}
    for name in ['bioprocessor','refinery','bioprocessor_container','refinery_container']:
        path=ROOT/f'src/main/resources/assets/sbw_helldiver/models/mesh/{name}.mesh.json.gz'
        data=json.loads(gzip.decompress(path.read_bytes()));before=after=0
        for mesh in data['meshes']:
            for p in mesh:
                before+=len(p['indices'])//3;optimize(p);after+=len(p['faces'])
                # Every reconstructed quad expands to the same two triangles.
                def signature(t):return tuple(sorted(tuple(p['p'][v]+p['uv'][v]) for v in t))
                expected=sorted(signature(p['indices'][i:i+3]) for i in range(0,len(p['indices']),3))
                actual=[]
                for f in p['faces']:
                    actual.append(signature(f[:3]))
                    if len(f)==4:actual.append(signature([f[0],f[2],f[3]]))
                assert sorted(actual)==expected,(name,'Surface or UV changed')
        path.write_bytes(gzip.compress(json.dumps(data,separators=(',',':'),ensure_ascii=False).encode(),mtime=0))
        report[name]={'original_triangle_quads':before,'optimized_faces':after,'vertex_submission_reduction':round(1-after/before,4)}
    from PIL import Image
    for name,color in [('bioprocessor',(60,175,200,255)),('refinery',(140,225,245,255))]:
        # Only _window / _light geometry is rendered with this layer. Metal and
        # interface icons never participate, even though they share an atlas.
        atlas=ROOT/f'src/main/resources/assets/sbw_helldiver/textures/mesh/{name}_0.png'
        Image.new('RGBA',Image.open(atlas).size,color).save(atlas.with_stem(atlas.stem+'_glow'))
    args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2))

if __name__=='__main__':main()
