/** Offline, attribute-aware runtime reduction. Authored Blockbench sources stay intact. */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {MeshoptSimplifier as Simplifier} from 'meshoptimizer';
import {PNG} from 'pngjs';

const opts=Object.fromEntries(process.argv.slice(2).reduce((a,v,i,all)=>{if(v.startsWith('--'))a.push([v.slice(2),all[i+1]]);return a},[]));
if(!opts.input||!opts.output)throw new Error('Use --input <original asset directory> --output <runtime asset directory> [--report <json>]');
const source=path.resolve(opts.input),destination=path.resolve(opts.output);
if(source===destination)throw new Error('Keep the original exports in a separate directory; do not simplify an already reduced model.');
const preserveSurface=Object.hasOwn(opts,'preserve-surface');
const ratio=Number(opts.ratio??.55),maxError=Number(opts.error??.006);
if(!(ratio>=.35&&ratio<=1&&maxError>0&&maxError<=.01))throw new Error('Unsafe reduction settings');
await Simplifier.ready;
const report={settings:{ratio,maxError,preserveSurface,meshoptimizer:'1.3.0'},models:[]};
const cache=new Map(),images=new Map();
const digest=x=>crypto.createHash('sha256').update(x).digest('hex');
function imageFor(rel){
 const name=path.basename(rel,'.geo.json');const folder=rel.includes('/vehicle/')?'vehicle':rel.includes('/misc/')?'misc':'projectile';
 const file=path.join(path.resolve(opts.textures??destination),'textures/bedrock',folder,name+'.png');
 if(!images.has(file))images.set(file,fs.existsSync(file)?PNG.sync.read(fs.readFileSync(file)):null);
 return images.get(file);
}
function pixel(image,u,v){
 if(!image)return null;const x=Math.max(0,Math.min(image.width-1,Math.floor(u))),y=Math.max(0,Math.min(image.height-1,Math.floor(v)));
 const i=(y*image.width+x)*4;return image.data.subarray(i,i+4);
}
function glowing(c){return c&&((c[0]>155&&c[0]>c[2]*1.7&&c[1]<c[0]*.8)||(c[1]>150&&c[1]>c[0]*1.6&&c[1]>c[2]*1.3))}
function cross2(a,b,c){const u=b.map((v,i)=>v-a[i]),v=c.map((w,i)=>w-a[i]);const c0=u[1]*v[2]-u[2]*v[1],c1=u[2]*v[0]-u[0]*v[2],c2=u[0]*v[1]-u[1]*v[0];return c0*c0+c1*c1+c2*c2}
function reduceMesh(m,width,height,{mine=false,skull=false,image=null,protectedBone=false}={}){
 const textureKey=mine&&image?(image.optimizerHash??=digest(image.data)):null;
 const key=digest(JSON.stringify([m,width,height,mine,skull,protectedBone,textureKey]));if(cache.has(key))return structuredClone(cache.get(key));
 const refs=[],positions=[],attributes=[],indices=[],unique=new Map();
 const uvScale=m.normalized_uvs?[1,1]:[width,height];
 for(const face of m.polys)for(let j=1;j<face.length-1;j++){
  const tri=[face[0],face[j],face[j+1]];const p=tri.map(c=>m.positions[c[0]]);
  if(cross2(...p)<=1e-16)continue;
  for(const corner of tri){
   const p=m.positions[corner[0]],n=m.normals[corner[1]]??[0,0,0],uv=m.uvs[corner[2]];
   // Weld exactly equal corner values; no coordinate quantization or texture baking.
   const key=JSON.stringify([p,n,uv]);let id=unique.get(key);
   if(id===undefined){id=refs.length;unique.set(key,id);refs.push(corner);positions.push(...p);attributes.push(...n,uv[0]/uvScale[0],uv[1]/uvScale[1]);}
   indices.push(id);
  }
 }
 const original=indices.length/3;let selected=Uint32Array.from(indices),error=0;
 if(original>=96&&!protectedBone){
  const locks=new Uint8Array(refs.length);
  for(let i=0;i<refs.length;i++){
   const uv=m.uvs[refs[i][2]],u=uv[0]/uvScale[0]*width,v=(1-uv[1]/uvScale[1])*height;
   // Retain the user-edited central skull and the mines' emissive inserts exactly.
   if(mine&&(glowing(pixel(image,u,v))||(skull&&u>=76&&u<=180&&v>=56&&v<=140)))locks[i]=1;
  }
  [selected,error]=Simplifier.simplifyWithAttributes(selected,Float32Array.from(positions),3,Float32Array.from(attributes),5,[.35,.35,.35,1.5,1.5],locks,Math.floor(original*ratio)*3,maxError,['Permissive','LockBorder']);
 }
 // Reuse original attributes verbatim and retain the original normal, UV and winding.
 const out={...m,positions:[],normals:[],uvs:[],polys:[]};const maps=[new Map(),new Map(),new Map()];const names=['positions','normals','uvs'];
 for(let i=0;i<selected.length;i+=3){const tri=[];
  for(let k=0;k<3;k++){const old=refs[selected[i+k]],corner=[];for(let j=0;j<3;j++){if(old[j]<0){corner.push(old[j]);continue}let id=maps[j].get(old[j]);if(id===undefined){id=out[names[j]].length;maps[j].set(old[j],id);out[names[j]].push(m[names[j]][old[j]])}corner.push(id)}tri.push(corner)}
  out.polys.push(tri);
 }
 const result={mesh:out,before:original,after:selected.length/3,error};cache.set(key,structuredClone(result));return result;
}
const FACE={west:[0,-1],east:[0,1],down:[1,-1],up:[1,1],north:[2,-1],south:[2,1]};
function cubeBox(c){if(!Array.isArray(c.origin)||!Array.isArray(c.size)||c.size.some(v=>v<=1e-6)||c.rotation?.some(v=>v!==0)||c.inflate)return null;return [c.origin,c.origin.map((v,i)=>v+c.size[i])];}
function opaqueCube(c,image){
 if(!image||!c.uv||Array.isArray(c.uv)||Object.keys(c.uv).length!==6)return false;
 for(const f of Object.values(c.uv)){
  if(!f.uv_size)return false;const x1=Math.min(f.uv[0],f.uv[0]+f.uv_size[0]),x2=Math.max(f.uv[0],f.uv[0]+f.uv_size[0]);const y1=Math.min(f.uv[1],f.uv[1]+f.uv_size[1]),y2=Math.max(f.uv[1],f.uv[1]+f.uv_size[1]);
  if(x1<0||y1<0||x2>image.width||y2>image.height)return false;
  for(let y=Math.floor(y1);y<Math.ceil(y2);y++)for(let x=Math.floor(x1);x<Math.ceil(x2);x++)if(image.data[(y*image.width+x)*4+3]!==255)return false;
 }return true;
}
// Full-detail mode never moves, collapses or rounds a surface vertex. Only
// identical attribute records are shared. Keep every mesh surface triangle.
function preserveMesh(m){
 const out={...m,positions:[],normals:[],uvs:[],polys:[]};
 const names=['positions','normals','uvs'],maps=names.map(()=>new Map());let before=0;
 for(const face of m.polys)for(let j=1;j<face.length-1;j++){
  before++;const tri=[face[0],face[j],face[j+1]];
  out.polys.push(tri.map(c=>c.map((old,a)=>{
   if(old<0)return old;const value=m[names[a]][old],key=JSON.stringify(value);
   let id=maps[a].get(key);if(id===undefined){id=out[names[a]].length;maps[a].set(key,id);out[names[a]].push(value)}return id;
  })));
 }
 return {mesh:out,before,after:out.polys.length,error:0};
}
function subtract(r,cover){const [a,b,c,d]=r,[x,y,z,w]=cover;const l=Math.max(a,x),t=Math.max(b,y),rr=Math.min(c,z),bb=Math.min(d,w);if(l>=rr-1e-7||t>=bb-1e-7)return [r];const rows=[];if(a<l-1e-7)rows.push([a,b,l,d]);if(rr<c-1e-7)rows.push([rr,b,c,d]);if(b<t-1e-7)rows.push([l,b,rr,t]);if(bb<d-1e-7)rows.push([l,bb,rr,d]);return rows;}
function cullInternalFaces(bone,image){
 const cubes=bone.cubes??[],boxes=cubes.map(cubeBox),opaque=cubes.map((c,i)=>boxes[i]&&opaqueCube(c,image));let removed=0;
 for(let i=0;i<cubes.length;i++){const cube=cubes[i],box=boxes[i];if(!box||!cube.uv||Array.isArray(cube.uv))continue;
  for(const face of Object.keys(cube.uv)){
   if(!FACE[face])continue;const [axis,side]=FACE[face],other=[0,1,2].filter(a=>a!==axis),plane=box[side<0?0:1][axis];
   let remains=[[box[0][other[0]],box[0][other[1]],box[1][other[0]],box[1][other[1]]]];
   for(let j=0;j<cubes.length&&remains.length;j++){
    const b=boxes[j];if(i===j||!b||!opaque[j])continue;
    // Require solid volume immediately outside the face; coplanar outer skins stay.
    if(side>0?b[0][axis]>plane+1e-7||b[1][axis]<=plane+1e-7:b[1][axis]<plane-1e-7||b[0][axis]>=plane-1e-7)continue;
    const cover=[b[0][other[0]],b[0][other[1]],b[1][other[0]],b[1][other[1]]];remains=remains.flatMap(r=>subtract(r,cover));
   }
   if(!remains.length){delete cube.uv[face];removed++;}
  }
 }
 return removed;
}
function filesIn(root){return fs.readdirSync(root,{withFileTypes:true}).flatMap(e=>e.isDirectory()?filesIn(path.join(root,e.name)):[path.join(root,e.name)]);}
for(const file of filesIn(path.join(source,'models')).filter(f=>f.endsWith('.geo.json')&&!/_lod[12]\.geo\.json$/.test(f))){
 const rel=path.relative(source,file).split(path.sep).join('/'),original=JSON.parse(fs.readFileSync(file,'utf8')),out=structuredClone(original);
 const image=imageFor(rel);const row={model:rel,meshBefore:0,meshAfter:0,cubeFacesBefore:0,cubeFacesRemoved:0,maxError:0,bones:[]};
 for(const g of out['minecraft:geometry'])for(const b of g.bones){
  row.cubeFacesBefore+=(b.cubes??[]).reduce((n,c)=>n+(Array.isArray(c.uv)?6:Object.keys(c.uv??{}).length),0);row.cubeFacesRemoved+=cullInternalFaces(b,image);
  if(!b.poly_mesh)continue;
  const m=preserveSurface?preserveMesh(b.poly_mesh):reduceMesh(b.poly_mesh,g.description.texture_width,g.description.texture_height,{image,mine:rel.includes('/misc/')&&rel.includes('mine'),skull:rel.includes('anti_tank_mine.geo'),protectedBone:/ls_heat_|lens|optic_lens/.test(b.name)});
  b.poly_mesh=m.mesh;row.meshBefore+=m.before;row.meshAfter+=m.after;row.maxError=Math.max(row.maxError,m.error);row.bones.push({name:b.name,before:m.before,after:m.after,error:m.error});
 }
 const target=path.join(destination,rel);fs.mkdirSync(path.dirname(target),{recursive:true});fs.writeFileSync(target,JSON.stringify(out));report.models.push(row);
 console.log(path.basename(file),`${row.meshBefore}->${row.meshAfter} mesh triangles; ${row.cubeFacesRemoved}/${row.cubeFacesBefore} enclosed cube faces removed`);
}
// Eagle has a separate material-indexed triangle format. Never join different materials.
const eagle=path.join(source,'models/mesh/eagle.mesh.json');if(fs.existsSync(eagle)){
 const original=JSON.parse(fs.readFileSync(eagle,'utf8'));const out={...original,triangles:[]};const row={model:'models/mesh/eagle.mesh.json',meshBefore:original.triangles.length,meshAfter:0};
 if(preserveSurface)out.triangles=original.triangles;
 else for(const material of [...new Set(original.triangles.map(t=>t[0]))]){
  const triangles=original.triangles.filter(t=>t[0]===material),m={positions:[],normals:[],uvs:[],polys:[],normalized_uvs:true};
  for(const [,points] of triangles){const a=points[0],b=points[1],c=points[2],u=b.slice(0,3).map((v,i)=>v-a[i]),v=c.slice(0,3).map((v,i)=>v-a[i]);const n=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]],len=Math.hypot(...n);if(len<1e-12)continue;const ni=m.normals.length;m.normals.push(n.map(v=>v/len));const face=[];for(const p of points){const i=m.positions.length;m.positions.push(p.slice(0,3));m.uvs.push(p.slice(3,5));face.push([i,ni,i])}m.polys.push(face);}
  const {mesh}=reduceMesh(m,1,1);
  for(const tri of mesh.polys)out.triangles.push([material,tri.slice(0,3).map(v=>[...mesh.positions[v[0]],...mesh.uvs[v[2]]])]);
 }
 row.meshAfter=out.triangles.length;fs.mkdirSync(path.dirname(path.join(destination,row.model)),{recursive:true});fs.writeFileSync(path.join(destination,row.model),JSON.stringify(out));report.models.push(row);console.log('eagle',row.meshBefore,'->',row.meshAfter);
}
if(opts.report){fs.mkdirSync(path.dirname(opts.report),{recursive:true});fs.writeFileSync(opts.report,JSON.stringify(report,null,2)+'\n')}


