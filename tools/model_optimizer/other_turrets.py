"""Exact-surface Blockbench reduction: UV-safe planar collapse and union of cube-built octagonal prisms.

Reads reviewed models; writes separate candidates. No runtime files are overwritten.
"""
import argparse, base64, collections, copy, io, json, math, pathlib, sys

def count(e):
    if e['type']=='mesh':
        return sum(len(f['vertices'])-2 for f in e['faces'].values() if f.get('texture') is not None)
    return 2*sum(f.get('texture') is not None for f in e.get('faces',{}).values()) if e['type']=='cube' else 0

def sub(a,b):return tuple(x-y for x,y in zip(a,b))
def dot(a,b):return sum(x*y for x,y in zip(a,b))
def cross(a,b):return (a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])

def topology(e):
    # Preserve the exact open boundary and all non-manifold edges of the input.
    V=e['vertices'];edges=collections.Counter()
    for f in e['faces'].values():
        vs=f['vertices']
        for a,b in zip(vs,vs[1:]+vs[:1]):
            if a==b:continue
            edges[tuple(sorted((tuple(V[a]),tuple(V[b]))))]+=1
    return {k:n for k,n in edges.items() if n!=2}

def canonicalize_solid_faces(e, textures):
    # Canonical UVs only inside the same 100px material tile, after checking
    # every texel of the original UV bounding box is exactly the same RGBA.
    # Symbols, gradients, windows and texture seams remain untouched.
    palette={}
    for f in e['faces'].values():
        tex=f.get('texture')
        if tex is None:continue
        im=textures[int(tex)];uv=list(f['uv'].values())
        l,t=[math.floor(min(p[i] for p in uv)) for i in (0,1)]
        r,b=[math.floor(max(p[i] for p in uv)) for i in (0,1)]
        if l<0 or t<0 or r>=im.width or b>=im.height:continue
        if l//100!=r//100 or t//100!=b//100:continue
        patch=im.crop((l,t,r+1,b+1));ext=patch.getextrema()
        if not all(a==b for a,b in ext):continue
        key=(int(tex),l//100,t//100,tuple(a for a,b in ext))
        if key not in palette:palette[key]=[(l+r)/2+.5,(t+b)/2+.5]
        f['uv']={v:list(palette[key]) for v in f['vertices']}

def weld_exact_positions(e):
    seen={};remap={};vertices={}
    for k,p in e['vertices'].items():
        v=seen.setdefault(tuple(p),k);remap[k]=v;vertices[v]=p
    if len(vertices)==len(e['vertices']):return
    for f in e['faces'].values():
        if len(set(remap[v] for v in f['vertices']))!=len(f['vertices']):return
    for f in e['faces'].values():
        f['uv']={remap[v]:uv for v,uv in f['uv'].items()};f['vertices']=[remap[v] for v in f['vertices']]
    e['vertices']=vertices

def planar(e):
    V=e['vertices'];F=e['faces']
    # Fan triangulation is the same triangulation used by the runtime exporter.
    triangles={}
    for k,f in F.items():
        vs=f['vertices']
        for j in range(1,len(vs)-1):
            keys=[vs[0],vs[j],vs[j+1]]
            triangles[k+'_'+str(j)]=dict(f,vertices=keys,uv={v:copy.deepcopy(f['uv'][v]) for v in keys})
    e['faces']=F=triangles
    for iteration in range(10):
        A=collections.defaultdict(set)
        for k,f in F.items():
            for v in f['vertices']:A[v].add(k)
        changes=0
        for v in list(V):
            star=A[v]
            if len(star)<3:continue
            N=collections.Counter(t for k in star for t in F[k]['vertices'] if t!=v)
            if any(z!=2 for z in N.values()):continue
            for w in sorted(N,key=lambda j:math.dist(V[v],V[j])):
                shared=star&A[w]
                if len(shared)!=2:continue
                opposite={x for k in shared for x in F[k]['vertices']}-{v,w}
                if (set(N)&{x for k in A[w] for x in F[k]['vertices'] if x!=w})!=opposite:continue
                work={};valid=True
                for k in sorted(star-shared):
                    f=F[k];i=f['vertices'].index(v);b,c=f['vertices'][(i+1)%3],f['vertices'][(i+2)%3]
                    a=V[v];B=sub(V[b],a);C=sub(V[c],a);W=sub(V[w],a);n=cross(B,C);den=dot(n,n)
                    if den<1e-15 or abs(dot(n,W))>0.000001*math.sqrt(den):valid=False;break
                    s=dot(cross(W,C),n)/den;t=dot(cross(B,W),n)/den
                    if 1-s-t<1e-6:valid=False;break
                    uv=f['uv'];work[k]=tuple(uv[v][j]+s*(uv[b][j]-uv[v][j])+t*(uv[c][j]-uv[v][j]) for j in (0,1))
                if not valid:continue
                for k in sorted(shared):
                    f=F[k];o=next(j for j in f['vertices'] if j not in (v,w));adj=(star&A[o])-{k}
                    if len(adj)!=1:valid=False;break
                    j=next(iter(adj))
                    if j not in work or F[j].get('texture')!=f.get('texture') or any(math.dist(F[j]['uv'][x],f['uv'][x])>0.00001 for x in (v,o)) or math.dist(work[j],f['uv'][w])>0.00001:valid=False;break
                if not valid:continue
                for k in sorted(shared):
                    for j in F[k]['vertices']:A[j].remove(k)
                    del F[k]
                for k,u in work.items():
                    f=F[k];f['vertices'][f['vertices'].index(v)]=w;del f['uv'][v];f['uv'][w]=list(u);A[w].add(k)
                del A[v],V[v];changes+=1;break
        if not changes:break
    used={v for f in F.values() for v in f['vertices']}
    e['vertices']={k:v for k,v in V.items() if k in used}
    return e

def octagonal_union(workspace,names,out):
    from octagonal_prisms import reduce_models
    reduce_models(workspace,names,out)


def main():
    ap=argparse.ArgumentParser();ap.add_argument('--workspace',type=pathlib.Path,required=True);ap.add_argument('--output',type=pathlib.Path,required=True);args=ap.parse_args()
    w=args.workspace;out=args.output;out.mkdir(parents=True,exist_ok=True)
    sys.path.insert(0,str(w/'work/survival06/python_libs'))
    octagonal_union(w,['机炮炮台','火箭炮台'],out)
    from PIL import Image
    report=[]
    for folder,name in [('单独炮台',n) for n in ['机炮炮台','火箭炮台','火焰炮台','特斯拉电塔']]+[('迫击炮',n) for n in ['迫击炮哨戒炮','电磁迫击炮哨戒炮','毒气迫击炮哨戒炮']]:
        source=w/'outputs/当前交付/模型'/folder/(name+'.bbmodel');original=json.loads(source.read_text(encoding='utf8'))
        target=out/(name+'_减面.bbmodel');d=json.loads(target.read_text(encoding='utf8')) if target.exists() and name in ['机炮炮台','火箭炮台'] else copy.deepcopy(original)
        textures=[Image.open(io.BytesIO(base64.b64decode(t['source'].split(',')[1]))).convert('RGBA') for t in d['textures']]
        parts=[]
        for i,e in enumerate(d['elements']):
            # Common bases and side panels were reviewed separately. Do not reduce them again.
            if e['type']!='mesh' or e['name'].startswith(('共用_','结构_')) or e['name'].endswith('_合并重布线'):continue
            before=count(e)
            if before<12:continue
            candidate=copy.deepcopy(e)
            weld_exact_positions(candidate)
            canonicalize_solid_faces(candidate,textures)
            candidate=planar(candidate);after=count(candidate)
            if after<before:
                assert topology(candidate)==topology(e),(name,e['name'],'boundary changed')
                d['elements'][i]=candidate;parts.append(dict(part=e['name'],before=before,after=after))
        assert d.get('groups')==original.get('groups') and d.get('animations')==original.get('animations')
        assert [e for e in d['elements'] if e['type']=='locator']==[e for e in original['elements'] if e['type']=='locator']
        d['name']=name+'_减面'
        if name in ['机炮炮台','火箭炮台']:d['name']=name+'_减面'
        target.write_text(json.dumps(d,ensure_ascii=False,separators=(',',':')),encoding='utf8')
        body=lambda doc:sum(count(e) for e in doc['elements'] if not e['name'].startswith(('共用_','结构_')))
        row=dict(model=name,source_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),before=sum(map(count,original['elements'])),after=sum(map(count,d['elements'])),body_before=body(original),body_after=body(d),planar_parts=parts,output=target.name)
        report.append(row);print(json.dumps({k:v for k,v in row.items() if k!='planar_parts'},ensure_ascii=True),flush=True)
    (out/'减面对照.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')

if __name__=='__main__':main()
