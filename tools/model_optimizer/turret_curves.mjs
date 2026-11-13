/** Conservative near-model reduction. Original positions and corner UVs are reused. */
import fs from 'node:fs';
import path from 'node:path';
import {MeshoptSimplifier as S} from 'meshoptimizer';
import {PNG} from 'pngjs';
const dir=process.argv[2]; if(!dir)throw Error('Pass candidate model directory');
await S.ready;
const sub=(a,b)=>a.map((v,i)=>v-b[i]);
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
const dot=(a,b)=>a.reduce((n,v,i)=>n+v*b[i],0);
const reports=[];
for(const filename of fs.readdirSync(dir).filter(x=>x.endsWith('.bbmodel'))){
 const file=path.join(dir,filename),d=JSON.parse(fs.readFileSync(file,'utf8'));
 if(!/火焰|特斯拉|迫击/.test(filename))continue;
 const images=d.textures.map(t=>PNG.sync.read(Buffer.from(t.source.split(',')[1],'base64')));
 const changes=[];
 for(const e of d.elements){
  if(e.type!=='mesh'||e.name.startsWith('共用_')||e.name.startsWith('结构_')||/标志|标识|镜片|指示|发光|光学|lens|heat_|optic/.test(e.name))continue;
  const F=Object.values(e.faces);if(F.length<32)continue;
  const before=F.reduce((n,f)=>n+f.vertices.length-2,0);
  const palette=new Map(),canonical=[];
  for(const f of F){
   let uv=f.vertices.map(k=>f.uv[k]),im=images[Number(f.texture)];
   if(!im){canonical.push(uv);continue;}
   const l=Math.floor(Math.min(...uv.map(p=>p[0]))),r=Math.floor(Math.max(...uv.map(p=>p[0]))),t=Math.floor(Math.min(...uv.map(p=>p[1]))),b=Math.floor(Math.max(...uv.map(p=>p[1])));
   if(l>=0&&t>=0&&r<im.width&&b<im.height&&Math.floor(l/100)===Math.floor(r/100)&&Math.floor(t/100)===Math.floor(b/100)){
    const px=(x,y)=>im.data.subarray((y*im.width+x)*4,(y*im.width+x)*4+4),c=px(l,t);let solid=true;
    for(let y=t;y<=b&&solid;y++)for(let x=l;x<=r;x++)if(!px(x,y).equals(c)){solid=false;break;}
    if(solid){const key=[f.texture,Math.floor(l/100),Math.floor(t/100),...c].join(',');if(!palette.has(key))palette.set(key,[(l+r)/2+.5,(t+b)/2+.5]);uv=uv.map(()=>palette.get(key));}
   }
   canonical.push(uv);
  }
  const refs=[],positions=[],attrs=[],indices=[],unique=new Map(),edges=new Map(),locations=new Map();
  F.forEach((f,fi)=>{
   const vs=f.vertices;
   for(let j=1;j<vs.length-1;j++){
    const keys=[0,j,j+1],p=keys.map(k=>e.vertices[vs[k]]),n=cross(sub(p[1],p[0]),sub(p[2],p[0])),len=Math.hypot(...n);
    if(len<1e-12)continue;
    const ids=[];
    for(const k of keys){
     const p=e.vertices[vs[k]],uv=canonical[fi][k],key=JSON.stringify([p,uv,f.texture]);let id=unique.get(key);
     if(id===undefined){id=refs.length;unique.set(key,id);refs.push({p,uv,texture:f.texture});positions.push(...p);attrs.push(...uv);const loc=JSON.stringify(p);if(!locations.has(loc))locations.set(loc,[]);locations.get(loc).push(id);}
     ids.push(id);indices.push(id);
    }
    for(let k=0;k<3;k++){const a=JSON.stringify(p[k]),b=JSON.stringify(p[(k+1)%3]),key=[a,b].sort().join('|');if(!edges.has(key))edges.set(key,[]);edges.get(key).push({ids:[ids[k],ids[(k+1)%3]],normal:n.map(v=>v/len)});}
   }
  });
  // Do not silently delete pre-existing degenerate triangles.
  if(indices.length/3!==before)continue;
  const locks=new Uint8Array(refs.length);
  for(const ids of locations.values())if(ids.length>1)for(const id of ids)locks[id]=1;
  for(const rows of edges.values())if(rows.length!==2||dot(rows[0].normal,rows[1].normal)<Math.cos(Math.PI*35/180))for(const row of rows)for(const id of row.ids)locks[id]=1;
  const [selected,error]=S.simplifyWithAttributes(Uint32Array.from(indices),Float32Array.from(positions),3,Float32Array.from(attrs),2,[1,1],locks,Math.floor(before*.60)*3,.08,['LockBorder','ErrorAbsolute','PreserveFolds']);
  if(selected.length>=indices.length)continue;
  const vertices={},faces={};
  for(let i=0;i<selected.length;i+=3){const tri=[selected[i],selected[i+1],selected[i+2]],tex=refs[tri[0]].texture;if(tri.some(v=>refs[v].texture!==tex))throw Error('Material boundary crossed');
   const keys=tri.map(v=>'v'+v);for(const v of tri)vertices['v'+v]=refs[v].p;
   faces['f'+i/3]={vertices:keys,uv:Object.fromEntries(tri.map(v=>['v'+v,refs[v].uv])),texture:tex};
  }
  changes.push({part:e.name,before,after:selected.length/3,error});e.vertices=vertices;e.faces=faces;
 }
 fs.writeFileSync(file,JSON.stringify(d));reports.push({model:filename,parts:changes});console.log(filename,changes.reduce((n,r)=>n+r.before-r.after,0),'triangles removed');
}
fs.writeFileSync(path.join(dir,'curve-report.json'),JSON.stringify(reports,null,2));
