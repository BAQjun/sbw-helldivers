"""Restore Guard Dog curved body surfaces; retain validated exact planar cleanup only."""
import argparse,collections,copy,json,math,pathlib

def count(e):
 return sum(len(f['vertices'])-2 for f in e['faces'].values()) if e['type']=='mesh' else 2*sum(f.get('texture') is not None for f in e.get('faces',{}).values())
def surface(e):
 result=collections.defaultdict(lambda:[0.,0.,0.,0.]);V=e['vertices']
 for f in e['faces'].values():
  vs=f['vertices']
  for i in range(1,len(vs)-1):
   a,b,c=[V[k] for k in [vs[0],vs[i],vs[i+1]]];u=[b[j]-a[j] for j in range(3)];v=[c[j]-a[j] for j in range(3)];n=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]];length=math.sqrt(sum(x*x for x in n))
   if length<1e-10:continue
   n=[x/length for x in n];key=(*[round(x,7)for x in n],round(sum(n[j]*a[j]for j in range(3)),6),f.get('texture'));area=length/2;row=result[key];row[0]+=area
   for j in range(3):row[j+1]+=area*(a[j]+b[j]+c[j])/3
 return result

def same_surface(a,b):
 x,y=surface(a),surface(b)
 return x.keys()==y.keys()and all(all(abs(v-w)<=2e-6*max(1,abs(v),abs(w))for v,w in zip(x[k],y[k]))for k in x)

def main():
 p=argparse.ArgumentParser();p.add_argument('--baseline',required=True,type=pathlib.Path);p.add_argument('--planar',required=True,type=pathlib.Path);p.add_argument('--models',required=True,type=pathlib.Path);p.add_argument('--report',required=True,type=pathlib.Path);a=p.parse_args();report=[]
 for body in sorted(a.baseline.glob('*护卫犬/*_机身.bbmodel')):
  rel=body.relative_to(a.baseline);base=json.loads(body.read_text('utf8'));names=[e['name']for e in base['elements']]
  for r in [rel,pathlib.Path(str(rel).replace('_机身.bbmodel','_组合检查.bbmodel'))]:
   source=json.loads((a.baseline/r).read_text('utf8'));planar=json.loads((a.planar/r).read_text('utf8'));current=json.loads((a.models/r).read_text('utf8'));n=len(names)
   assert [e['name']for e in source['elements'][:n]]==names
   assert [e['name']for e in planar['elements'][:n]]==names
   planarById={e['uuid']:e for e in planar['elements']};before=sum(map(count,current['elements']));parts=[]
   for i,original in enumerate(source['elements'][:n]):
    candidate=planarById[original['uuid']];safe=same_surface(original,candidate);chosen=candidate if safe else original
    assert {k:v for k,v in chosen.items()if k not in ('vertices','faces')}=={k:v for k,v in original.items()if k not in ('vertices','faces')}
    assert current['elements'][i]['uuid']==original['uuid'];current['elements'][i]=copy.deepcopy(chosen)
    parts.append({'part':original['name'],'original':count(original),'corrected':count(chosen),'exactPlanarAccepted':safe})
   for k in ['textures','animations','outliner','groups']:assert current.get(k)==source.get(k)
   (a.models/r).write_text(json.dumps(current,ensure_ascii=False,separators=(',',':')),'utf8');report.append({'model':r.as_posix(),'previous':before,'corrected':sum(map(count,current['elements'])),'bodyParts':parts});print(json.dumps(report[-1],ensure_ascii=True),flush=True)
 assert len(report)==10
 a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,ensure_ascii=False,indent=2),'utf8')
if __name__=='__main__':main()
