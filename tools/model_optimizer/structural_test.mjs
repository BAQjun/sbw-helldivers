import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {PNG} from 'pngjs';

// Keep generated fixtures under an explicitly selected project directory.
const work = process.argv[process.argv.indexOf('--work') + 1];
if (!process.argv.includes('--work') || !work) throw Error('Use --work <fixture-directory>');
const root = path.resolve(work);
const input = path.join(root, 'input');
const output = path.join(root, 'output');
const assets = path.join(root, 'assets');
fs.mkdirSync(input, {recursive: true});
fs.mkdirSync(path.join(assets, 'textures'), {recursive: true});
fs.mkdirSync(path.join(assets, 'animations'), {recursive: true});
const faces = ['west', 'east', 'down', 'up', 'north', 'south'];
function cube(origin, size, extra = {}) {
  return {origin, size, uv: Object.fromEntries(faces.map(f => [f, {uv:[0,0], uv_size:[1,1]}])), ...extra};
}
function model(name, bones, alpha = 255) {
  const png = new PNG({width: 2, height: 2});
  for (let i=0; i<png.data.length; i+=4) png.data.set([90,100,110,alpha], i);
  fs.writeFileSync(path.join(assets, 'textures', `${name}.png`), PNG.sync.write(png));
  fs.writeFileSync(path.join(input, `${name}.geo.json`), JSON.stringify({
    format_version:'1.12.0', 'minecraft:geometry': [{
      description:{identifier:`geometry.${name}`, texture_width:2, texture_height:2}, bones
    }]
  }));
}
model('adjacent', [{name:'root', cubes:[cube([0,0,0],[1,1,1]), cube([1,0,0],[1,1,1])]}]);
model('enclosed', [{name:'root', cubes:[cube([0,0,0],[4,4,4]), cube([1,1,1],[1,1,1])]}]);
model('transparent', [{name:'root', cubes:[cube([0,0,0],[4,4,4]), cube([1,1,1],[1,1,1])]}], 128);
model('animated', [
  {name:'root', cubes:[cube([0,0,0],[4,4,4])]},
  {name:'moving', parent:'root', pivot:[2,2,2], cubes:[cube([1,1,1],[1,1,1])]}
]);
fs.writeFileSync(path.join(assets,'animations','fixture.json'), JSON.stringify({animations:{test:{bones:{moving:{rotation:[0,90,0]}}}}}));
model('cached-properties', [
  {name:'root', pivot:[0,0,0], cubes:[cube([0,0,0],[1,1,1])]},
  {name:'moving', parent:'root', pivot:[0,9,0], cubes:[cube([0,0,0],[1,1,1])]}
]);
const rotation={rotation:[23,41,17], pivot:[0,0,0]};
model('rotated', [{name:'root', cubes:[cube([0,0,0],[4,4,4],rotation), cube([1,1,1],[1,1,1],rotation)]}]);
for (const gap of [false,true]) {
  const target=cube([0,0,0],[1,1,1]); target.uv.east.marker='target-east';
  model(gap?'gap':'union', [{name:'root', cubes:[target,
    cube([1,0,0],[1,gap?.45:.5,1]), cube([1,gap?.55:.5,0],[1,gap?.45:.5,1])]}]);
}
const valid=[[0,0,0],[1,0,0],[2,0,0]];
model('mesh', [{name:'root', poly_mesh:{normalized_uvs:true,
  positions:[[0,0,0],[1,0,0],[0,1,0]], normals:[[0,0,1]], uvs:[[.25,.25]],
  polys:[valid, valid, [[0,0,0],[0,0,0],[1,0,0]]]
}}]);
const script=path.join(path.dirname(fileURLToPath(import.meta.url)),'structural.mjs');
const run=spawnSync(process.execPath,[script,'--input',input,'--output',output,'--textures',assets,'--report',path.join(root,'report.json')],{encoding:'utf8'});
assert.equal(run.status,0,run.stderr);
const read=name=>JSON.parse(fs.readFileSync(path.join(output,`${name}.geo.json`)))['minecraft:geometry'][0];
const faceCount=name=>read(name).bones.reduce((s,b)=>s+(b.cubes??[]).reduce((s,c)=>s+Object.keys(c.uv).length,0),0);
assert.equal(faceCount('adjacent'),6);
assert.equal(faceCount('enclosed'),6);
assert.equal(faceCount('transparent'),12);
assert.equal(faceCount('animated'),12);
assert.equal(faceCount('rotated'),6);
assert.equal(read('cached-properties').bones[1].name,'moving');
assert.deepEqual(read('cached-properties').bones[1].pivot,[0,9,0]);
const marker=name=>read(name).bones.some(b=>b.cubes?.some(c=>c.uv.east?.marker==='target-east'));
assert.equal(marker('gap'),true,'An uncovered gap must retain the face');
assert.equal(marker('union'),false,'Several opaque blocks together cover the entire face');
assert.equal(read('mesh').bones[0].poly_mesh.polys.length,1);
assert.deepEqual(read('animated').bones.map(({cubes,poly_mesh,...b})=>b),[
  {name:'root'}, {name:'moving',parent:'root',pivot:[2,2,2]}
]);
console.log('Passed: shared planes, enclosed detail, opacity, animation boundaries, rotations, partial coverage, opaque unions, mesh degenerates and hierarchy.');
