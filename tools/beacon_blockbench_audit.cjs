const fs=require('fs');
const cdp=require('D:/Codex/projects/2026-09-19/new-chat/work/autocannon_reset/cdp.cjs');
(async()=>{
 const c=await cdp.connect();
 if(process.argv.includes('--inspect')){
  console.log(await c.ev('({projects:ModelProject.all.map(p=>({name:p.name,saved:p.saved,elements:p.elements.length})),setupProject:String(setupProject),load:String(Codecs.project.load)})'));
  c.close();process.exit(0);
 }
 if(process.argv.includes('--inspect-animation')){
  console.log(await c.ev('JSON.stringify({format:Format.euler_order,quat:Format.quaternion_interpolation,parse:String(Codecs.project.parse),extend:String(Animation.prototype.extend),kfs:Object.values(Animation.all.find(a=>a.name==="active").animators).filter(b=>b.name==="r_lid_2"||b.name==="r_lid_1").map(b=>({name:b.name,kfs:b.keyframes.map(k=>({channel:k.channel,time:k.time,point:k.data_points.map(p=>({x:p.x,y:p.y,z:p.z}))}))}))})'));
  c.close();process.exit(0);
 }
 const file='D:/Codex/projects/2026-09-19/new-chat/outputs/当前交付/信标球/模型/信标球.bbmodel';
 const model=JSON.parse(fs.readFileSync(file));
 // This app was launched with an isolated D: user-data directory for this audit.
 // The inspected setupProject implementation always creates a new tab. Existing
 // tabs remain open. Only this task's saved model may already be in this session.
 console.log(await c.ev(`(()=>{if(ModelProject.all.some(p=>!p.saved || p.save_path!==${JSON.stringify(file)}))throw Error('Unrelated or unsaved project present; no project changed');Codecs.project.load(${JSON.stringify(model)},{path:${JSON.stringify(file)}});return {format:Format.id,meshes:Mesh.all.length,groups:Group.all.length,animations:Animation.all.map(a=>a.name)}})()`));
 console.log(await c.ev('({previewMesh:!!Mesh.all[0].mesh,vertices:Mesh.all.reduce((n,m)=>n+Object.keys(m.vertices).length,0),textureErrors:Texture.all.map(t=>t.error),bone:Group.all[0].mesh.rotation.order,preview:String(Animator.preview).slice(0,350)})'));
 if(process.argv.includes('--render')){
  await c.ev(`(async()=>{await new Promise(r=>setTimeout(r,300));Modes.options.animate.select();unselectAllElements();window.beaconAuditRenderer=new THREE.WebGLRenderer({alpha:true,antialias:true,preserveDrawingBuffer:true});beaconAuditRenderer.setSize(1000,1000);beaconAuditRenderer.setClearColor(0,0);beaconAuditRenderer.outputEncoding=Preview.selected.renderer.outputEncoding;return true})()`);
  for(const [name,t] of [['closed',0],['deploy',0.5],['deploy',1],['active',0]]){
   const result=await c.ev(`(()=>{Animation.all.forEach(a=>a.playing=false);const anim=Animation.all.find(a=>a.name===${JSON.stringify(name)});anim.select();anim.playing=true;Timeline.setTime(${t});Animator.preview();scene.updateMatrixWorld(true);const camera=new THREE.OrthographicCamera(-2.8,2.8,2.8,-2.8,.1,100);camera.position.set(7,6,8);camera.lookAt(0,1,0);Canvas.withoutGizmos(()=>beaconAuditRenderer.render(scene,camera));return {image:beaconAuditRenderer.domElement.toDataURL().split(',')[1],vertices:Mesh.all.map(m=>({name:m.name,points:Array.from({length:m.mesh.geometry.attributes.position.count},(_,i)=>{let a=m.mesh.geometry.attributes.position;return new THREE.Vector3(a.getX(i),a.getY(i),a.getZ(i)).applyMatrix4(m.mesh.matrixWorld).toArray()})})),bones:Group.all.map(g=>({name:g.name,position:g.mesh.position.toArray(),rotation:g.mesh.rotation.toArray()}))}})()`);
   const stem=name+'_'+t;
   fs.writeFileSync('D:/Project/sbw_helldiver/.codex/beacon/bb_'+stem+'.png',Buffer.from(result.image,'base64'));delete result.image;
   fs.writeFileSync('D:/Project/sbw_helldiver/.codex/beacon/bb_'+stem+'.json',JSON.stringify(result));
  }
  await c.ev("Animation.all.forEach(a=>a.playing=false);Animation.all.find(a=>a.name==='closed').select();Timeline.setTime(0);Animator.preview();Canvas.updateAll()");
 }
 if(process.argv.includes('--verify')){
  await c.ev('Modes.options.animate.select();Timeline.pause()');
  const states=[];
  const samples=[['closed',0],...Array.from({length:31},(_,i)=>['deploy',i/30]),['active',0]];
  for(const [name,t] of samples){
   states.push(await c.ev(`(()=>{Animation.all.forEach(a=>a.playing=false);const anim=Animation.all.find(a=>a.name===${JSON.stringify(name)});anim.select();anim.playing=true;Timeline.setTime(${t});Animator.preview();scene.updateMatrixWorld(true);return {name:${JSON.stringify(name)},time:${t},actualTime:Timeline.time,animationTime:anim.time,meshes:Mesh.all.map(m=>({name:m.name,origin:m.origin,matrix:m.mesh.matrixWorld.toArray()}))}})()`));
  }
  fs.writeFileSync('D:/Project/sbw_helldiver/.codex/beacon/bb_motion_matrices.json',JSON.stringify(states));
  await c.ev("(()=>{Animation.all.forEach(a=>a.playing=false);const anim=Animation.all.find(a=>a.name==='closed');anim.select();anim.playing=true;Timeline.setTime(0);Animator.preview();Canvas.updateAll()})()");
  console.log({sampledStates:states.length});
 }
 if(process.argv.includes('--animation-preview')){
  const out='D:/Project/sbw_helldiver/.codex/beacon/preview_frames';fs.mkdirSync(out,{recursive:true});
  await c.ev(`(()=>{Modes.options.animate.select();Timeline.pause();unselectAllElements();window.beaconGifRenderer=new THREE.WebGLRenderer({alpha:false,antialias:true,preserveDrawingBuffer:true});beaconGifRenderer.setSize(720,720);beaconGifRenderer.setClearColor(0x303b38,1);beaconGifRenderer.outputEncoding=Preview.selected.renderer.outputEncoding;window.beaconGifCamera=new THREE.OrthographicCamera(-2.8,2.8,2.8,-2.8,.1,100);beaconGifCamera.position.set(7,9,8);beaconGifCamera.lookAt(0,1,0)})()`);
  const samples=[...Array.from({length:15},()=>['closed',0]),...Array.from({length:31},(_,i)=>['deploy',i/30]),...Array.from({length:24},()=>['active',0])];
  for(let i=0;i<samples.length;i++){
   const [name,t]=samples[i];const data=await c.ev(`(()=>{Animation.all.forEach(a=>a.playing=false);const anim=Animation.all.find(a=>a.name===${JSON.stringify(name)});anim.select();anim.playing=true;Timeline.setTime(${t});Animator.preview();scene.updateMatrixWorld(true);Canvas.withoutGizmos(()=>beaconGifRenderer.render(scene,beaconGifCamera));return beaconGifRenderer.domElement.toDataURL().split(',')[1]})()`);
   fs.writeFileSync(out+'/frame_'+String(i).padStart(3,'0')+'.png',Buffer.from(data,'base64'));
  }
  await c.ev(`(()=>{beaconGifRenderer.dispose();Animation.all.forEach(a=>a.playing=false);const anim=Animation.all.find(a=>a.name==='closed');anim.select();anim.playing=true;Timeline.setTime(0);Animator.preview();Canvas.updateAll()})()`);
  console.log({previewFrames:samples.length});
 }
 c.close();process.exit(0);
})().catch(e=>{console.error(e);process.exit(1)});
