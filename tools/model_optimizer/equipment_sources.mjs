/** Editable equipment near-model candidates. Protect markings and animated part boundaries. */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {MeshoptSimplifier as S} from 'meshoptimizer';
import {PNG} from 'pngjs';
await S.ready;
const args=process.argv.slice(2),opt=Object.fromEntries(args.filter((_,i)=>i%2===0).map((k,i)=>[k.slice(2),args[2*i+1]]));
if(!opt.input||!opt.output||!opt.manifest)throw Error('--input --output --manifest required');
if(path.resolve(opt.input)===path.resolve(opt.output))throw Error('Keep reviewed sources separate');
const sub=(a,b)=>a.map((x,i)=>x-b[i]),dot=(a,b)=>a.reduce((n,x,i)=>n+x*b[i],0),cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]],pk=p=>JSON.stringify(p),count=e=>e.type==='mesh'?Object.values(e.faces).reduce((n,f)=>n+f.vertices.length-2,0):e.type==='cube'?Object.values(e.faces).filter(f=>f.texture!==null).length*2:0;
const edgeKey=(a,b)=>[a,b].sort().join('|'),cache=new Map(),report=[];
const boundary=(edges)=>new Map([...edges].filter(([k,a])=>a.length!==2).map(([k,a])=>[k,a.length]));
function optimize(e,images,texWidths,errorLimit,crease=75){
 if(e.type!=='mesh'||Object.keys(e.faces).length<20||/标志|标识|屏幕|接口|skull|badge|heat_|lens|glow/i.test(e.name))return {element:e,reason:'protected or small'};
 const key=crypto.createHash('sha256').update(JSON.stringify([e.vertices,e.faces,errorLimit,crease,images.map(t=>t.hash)])).digest('hex');
 if(cache.has(key))return {...cache.get(key),element:{...e,...structuredClone(cache.get(key).geometry)}};
 const refs=[],pos=[],attrs=[],indices=[],markedTriangles=[],unique=new Map(),locations=new Map(),edges=new Map(),pal=new Map();let marked=0;
 for(const f of Object.values(e.faces)){
  const vs=f.vertices;if(vs.length<3||vs.length>4)throw Error('Non-native polygon '+e.name);
  let uv=vs.map(k=>f.uv[k]),im=images[f.texture],solid=false;
  if(im){
   const sx=im.width/texWidths[f.texture][0],sy=im.height/texWidths[f.texture][1];
   const l=Math.floor(Math.min(...uv.map(p=>p[0]*sx))),r=Math.floor(Math.max(...uv.map(p=>p[0]*sx))),t=Math.floor(Math.min(...uv.map(p=>p[1]*sy))),b=Math.floor(Math.max(...uv.map(p=>p[1]*sy)));
   if(l>=0&&t>=0&&r<im.width&&b<im.height){
    const at=(t*im.width+l)*4,color=im.data.readUInt32LE(at);solid=true;
    for(let y=t;y<=b&&solid;y++)for(let x=l;x<=r;x++)if(im.data.readUInt32LE((y*im.width+x)*4)!==color){solid=false;break}
    if(solid){const k=f.texture+':'+color;if(!pal.has(k))pal.set(k,[(l+.5)/sx,(t+.5)/sy]);uv=uv.map(()=>pal.get(k))}
   }
  }
  if(!solid)marked++;
  for(let j=1;j<vs.length-1;j++){
   const ks=[0,j,j+1],ps=ks.map(i=>e.vertices[vs[i]]),normal=cross(sub(ps[1],ps[0]),sub(ps[2],ps[0])),len=Math.hypot(...normal),ids=[];
   // Existing zero-area triangles have no visible surface.
   if(len<1e-12)continue;
   for(const i of ks){
    const p=e.vertices[vs[i]],k=JSON.stringify([p,uv[i],f.texture]);let id=unique.get(k);
    if(id===undefined){id=refs.length;unique.set(k,id);refs.push({p,uv:uv[i],texture:f.texture,marked:!solid});pos.push(...p);attrs.push(...uv[i]);const loc=pk(p);if(!locations.has(loc))locations.set(loc,[]);locations.get(loc).push(id)}
    else if(!solid)refs[id].marked=true;
    ids.push(id);indices.push(id);
   }
   markedTriangles.push(!solid);
   for(let k=0;k<3;k++){
    const key=edgeKey(pk(ps[k]),pk(ps[(k+1)%3]));if(!edges.has(key))edges.set(key,[]);edges.get(key).push({ids:[ids[k],ids[(k+1)%3]],n:normal.map(x=>x/len)});
   }
  }
 }
 const locks=new Uint8Array(refs.length);
 // Protect every corner touching a nonuniform texture, material seam, hard crease, or open boundary.
 for(let i=0;i<refs.length;i++)if(refs[i].marked)locks[i]=1;
 for(const ids of locations.values())if(ids.length>1)for(const i of ids)locks[i]=1;
 for(const a of edges.values())if(a.length!==2||dot(a[0].n,a[1].n)<Math.cos(crease*Math.PI/180))for(const r of a)for(const i of r.ids)locks[i]=1;
 const [chosen,error]=S.simplifyWithAttributes(Uint32Array.from(indices),Float32Array.from(pos),3,Float32Array.from(attrs),2,[1,1],locks,Math.floor(indices.length*.50/3)*3,errorLimit,['LockBorder','ErrorAbsolute','PreserveFolds']);
 if(chosen.length>=indices.length)return {element:e,reason:'no safe collapse',marked};
 const vertices={},faces={},incidence=new Map();
 for(let j=0;j<chosen.length;j+=3){
  const ids=Array.from(chosen.slice(j,j+3)),ks=ids.map(i=>'v'+i),tex=refs[ids[0]].texture;
  if(ids.some(i=>refs[i].texture!==tex))throw Error('Material crossed');
  for(const i of ids)vertices['v'+i]=refs[i].p;
  faces['f'+j/3]={vertices:ks,uv:Object.fromEntries(ids.map(i=>['v'+i,refs[i].uv])),texture:tex};
  for(let k=0;k<3;k++){const key=edgeKey(pk(refs[ids[k]].p),pk(refs[ids[(k+1)%3]].p));incidence.set(key,(incidence.get(key)||0)+1)}
 }
 const originalBoundary=boundary(edges),newBoundary=new Map([...incidence].filter(([k,n])=>n!==2));
 if(originalBoundary.size!==newBoundary.size||[...originalBoundary].some(([k,n])=>newBoundary.get(k)!==n))return {element:e,reason:'boundary mismatch rejected',marked};
 // Every original marked triangle must remain with the same texture coordinates.
 const sig=(ps,uv,tex)=>JSON.stringify([ps.map((p,i)=>JSON.stringify([p,uv[i]])).sort(),tex]);
 const outputSigs=new Set(Object.values(faces).map(f=>sig(f.vertices.map(k=>vertices[k]),f.vertices.map(k=>f.uv[k]),f.texture)));
 for(let j=0;j<indices.length;j+=3){const ids=indices.slice(j,j+3);if(markedTriangles[j/3]&&!outputSigs.has(sig(ids.map(i=>refs[i].p),ids.map(i=>refs[i].uv),refs[ids[0]].texture)))return {element:e,reason:'marked surface mismatch rejected',marked}}
 const geometry={vertices,faces},row={geometry,element:{...e,...geometry},before:count(e),after:chosen.length/3,error,marked,protectedVertices:locks.reduce((a,n)=>a+(n===1),0)};
 cache.set(key,{...row,element:undefined});return row;
}
for(const rel of JSON.parse(fs.readFileSync(opt.manifest,'utf8'))){
 const d=JSON.parse(fs.readFileSync(path.join(opt.input,rel),'utf8')),original=structuredClone(d);
 const images=d.textures.map(t=>{let p=PNG.sync.read(Buffer.from(t.source.split(',')[1],'base64'));p.hash=crypto.createHash('sha256').update(t.source).digest('hex');return p});
 const dims=d.textures.map((t,i)=>[t.uv_width||d.resolution.width||images[i].width,t.uv_height||d.resolution.height||images[i].height]);
 let caps=0;
 for(const e of d.elements)if(e.type==='mesh'){
  const fs={};
  for(const [key,f]of Object.entries(e.faces)){
   if(f.vertices.length<=4){fs[key]=f;continue}
   const vs=f.vertices,ps=vs.map(k=>e.vertices[k]);let n=cross(sub(ps[1],ps[0]),sub(ps[2],ps[0])),l=Math.hypot(...n);n=n.map(x=>x/l);
   if(ps.some(p=>Math.abs(dot(n,sub(p,ps[0])))>1e-4)||ps.some((p,i)=>dot(cross(sub(ps[(i+1)%ps.length],p),sub(ps[(i+2)%ps.length],ps[(i+1)%ps.length])),n)<-1e-6))throw Error('Cap not planar convex: '+e.name);
   for(let j=1;j<vs.length-1;j++){const keys=[vs[0],vs[j],vs[j+1]];fs[key+'_'+j]={...f,vertices:keys,uv:Object.fromEntries(keys.map(k=>[k,f.uv[k]]))}}
   caps++;
  }e.faces=fs;
 }
 // Drone shells require their authored surface normals; a small positional error
 // alone does not prevent visible dents. Only the exact planar prepass applies.
 let bodyCount=0;
 if(rel.endsWith('_机身.bbmodel'))bodyCount=d.elements.length;
 else if(rel.endsWith('_组合检查.bbmodel')){
  const bodyPath=path.join(opt.input,rel.replace('_组合检查.bbmodel','_机身.bbmodel'));
  if(fs.existsSync(bodyPath)){
   const body=JSON.parse(fs.readFileSync(bodyPath,'utf8'));bodyCount=body.elements.length;
   if(body.elements.some((e,i)=>d.elements[i]?.name!==e.name))throw Error('Drone body ordering changed; identify body parts before simplifying');
  }
 }
 const parts=[];d.elements=d.elements.map((e,i)=>{if(i<bodyCount){parts.push({part:e.name,before:count(e),after:count(e),reason:'authored drone surface protected'});return e}const choices=[35,75,179].map(a=>optimize(e,images,dims,.05,a));const row=choices.reduce((a,b)=>count(a.element)<=count(b.element)?a:b);parts.push({part:e.name,before:count(e),after:count(row.element),error:row.error,reason:row.reason,markedFaces:row.marked});return row.element});
 for(const k of ['groups','outliner','animations','textures'])if(JSON.stringify(d[k])!==JSON.stringify(original[k]))throw Error(k+' changed');
 d.name=d.name.replace(/_近景减面$/,'')+'_近景减面';
 const dest=path.join(opt.output,rel);fs.mkdirSync(path.dirname(dest),{recursive:true});fs.writeFileSync(dest,JSON.stringify(d));
 const before=original.elements.reduce((s,e)=>s+count(e),0),after=d.elements.reduce((s,e)=>s+count(e),0);report.push({model:rel,before,after,reduction:Math.round((1-after/before)*1000)/10,parts});console.log(JSON.stringify({model:rel,before,after,percent:report.at(-1).reduction,caps}));
}
fs.writeFileSync(path.join(opt.output,'减面对照.json'),JSON.stringify(report,null,2));
