import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { MeshoptSimplifier as Simplifier } from 'meshoptimizer';

await Simplifier.ready;
const folder = path.resolve('src/main/resources/assets/sbw_helldiver/models/mesh');
for (const name of fs.readdirSync(folder).filter(name => /^(?:guard_dog|(?:laser|stun|flame|gas)_guard_dog)(?:_backpack)?\.mesh\.json\.gz$/.test(name))) {
    const model = JSON.parse(zlib.gunzipSync(fs.readFileSync(path.join(folder, name))));
    let before = 0;
    let after = 0;
    for (const mesh of model.meshes) {
        for (const part of mesh) {
            const { p, uv } = part;
            const indices = part.faces?.flatMap(face => face.length === 4
                ? [face[0], face[1], face[2], face[0], face[2], face[3]] : face) ?? part.indices;
            const originalFaces = part.faces?.length ?? indices.length / 3;
            before += originalFaces;
            if (indices.length < 288) {
                after += originalFaces;
                continue;
            }
            // Work on each node/material separately. Lock open boundaries and
            // weigh texture coordinates when collapsing interior edges.
            const [triangles] = Simplifier.simplifyWithAttributes(
                Uint32Array.from(indices), Float32Array.from(p.flat()), 3,
                Float32Array.from(uv.flat()), 2, [1.5, 1.5], null,
                Math.floor(originalFaces * 0.65) * 3, 0.008, ['Permissive', 'LockBorder'],
            );
            if (triangles.length / 3 >= originalFaces) {
                after += originalFaces;
                continue;
            }
            const remap = new Map();
            const positions = [];
            const coordinates = [];
            part.indices = Array.from(triangles, index => {
                if (!remap.has(index)) {
                    remap.set(index, positions.length);
                    positions.push(p[index]);
                    coordinates.push(uv[index]);
                }
                return remap.get(index);
            });
            part.p = positions;
            part.uv = coordinates;
            delete part.faces;
            after += triangles.length / 3;
        }
    }
    const output = name.replace('.mesh.json.gz', '_lod.mesh.json.gz');
    fs.writeFileSync(path.join(folder, output), zlib.gzipSync(JSON.stringify(model), { level: 9 }));
    console.log(`${name}: ${before} -> ${after} rendered faces`);
}
