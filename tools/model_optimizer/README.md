# Runtime model optimization

The editable Blockbench models remain full detail. Both runtime quality levels
receive surface-preserving structural cleanup; only the optional low quality
exports use curve simplification.
Keep an unreduced export directory as the input; never feed reduced output back
into the tool. Full-detail and optional reduced exports are shipped together.

From this directory, install the pinned development dependencies with `npm ci`.
Then run from the repository root:

```sh
node tools/model_optimizer/optimizer.mjs --input <unreduced-assets> --output src/main/resources/resourcepacks/low_quality_models/assets/sbw_helldiver --textures src/main/resources/assets/sbw_helldiver --report <report.json>
python tools/model_optimizer/verify.py --input <unreduced-assets> --output src/main/resources/resourcepacks/low_quality_models/assets/sbw_helldiver
```

Both asset roots contain `models/`. The output root also contains the existing
`textures/` (or specify another asset root with `--textures`); images are read to protect emissive mine inserts and to determine
which cube faces are fully opaque. No texture is resized or rewritten.

- Reduce each bone independently, keeping existing pivots, hierarchy and locators.
- Target 55% of mesh triangles, with a maximum normalized simplification error of
  0.006. Preserve UV and normal attributes and lock open borders.
- Retain the original corner attributes; do not bake, quantize or weld by position
  alone. Protect the anti-tank mine skull and emissive inserts, laser heat segments
  and lenses. Meshes with fewer than 96 triangles remain intact.
- Remove a cube face only if opaque, unrotated solids in the same bone fully cover
  it. Faces belonging to different animated bones are never considered occluders.
- Simplify the Eagle independently per material. Existing distant mine LODs and
  the deforming HMG feed belt are preserved.

After exporting Hellpod sources, apply `tools/fix_hellpod_fittings.py` to the
unreduced exports before reducing them. This keeps the lower lock tabs attached
to the sloped armor and backs the nozzle hinge opening with a fixed header.
Run `tools/create_mine_mounts.py` after updating dispenser exports as usual.

The reducer uses the MIT-licensed [meshoptimizer](https://github.com/zeux/meshoptimizer)
and [pngjs](https://github.com/pngjs/pngjs) as development tools; neither is a runtime
mod dependency. The generated report is a geometry budget, not an FPS guarantee.

The normal asset tree retains the unreduced exports, including the Hellpod fitting
repairs. The client configuration defaults to full detail and enables the hidden
low quality overlay only when selected. Do not overwrite the default exports
with reduced models.

For the default full-detail exports, use `--preserve-surface` with the normal
asset directory as the output. This mode shares only exactly identical position,
normal and UV records, preserving every mesh surface triangle and its order.
The Eagle geometry remains unchanged. It removes cube faces only under the
opaque same-bone coverage rule above. Verify this mode with `verify.py
--preserve-surface`; this checks the ordered triangle attributes against the
unreduced export, rather than just checking that attribute values still exist.

## Structural cleanup for both quality levels

`structural.mjs` reads a separate baseline directory containing Bedrock model
JSON files directly, and writes an optimized copy. Run it independently for
full-detail and already-reduced models; do not feed its output back as input.
The texture root is the normal asset directory and must also contain the
matching animation definitions.

```sh
node tools/model_optimizer/structural.mjs --input <baseline-models> --output <optimized-models> --textures <assets> --report <report.json>
node tools/model_optimizer/structural_test.mjs --work <fixture-directory>
```

This cleanup does not decimate curves. It removes zero-area or exactly duplicate
triangles, compacts identical attributes, removes faces completely enclosed by
closed opaque solids, and merges uniformly colored coplanar cube faces into
rectangles. Hierarchy, names, pivots, locators, and animation definitions remain
unchanged. Coverage by rotated cubes or several neighboring solids is supported;
any remaining gap retains the face. Texture images are read without modification.

Cross-bone coverage is limited to rigid groups. Bones named by animations,
runtime visibility/rotation groups, and locator-bearing bones define protected
group boundaries. Maintain these boundaries when adding runtime bone controls.
Do not merge differently colored or nonuniform textured faces. Merging faces
can split an authored cube into single-face rectangles, so face count rather
than cube-object count measures the geometry reduction.

The fixtures cover hidden and shared surfaces, opacity, animation boundaries,
rotations, partial coverage, unions of opaque occluders, cache metadata, and
mesh degenerates. Accepted Blockbench sources remain unchanged.

Apply the older reducer's `verify.py` checks before structural cleanup. The
structural stage intentionally changes cube partitioning and removes invisible
triangles, so the earlier ordered-triangle and cube-object checks do not apply
to its output. Verify bone metadata, retained mesh attributes, and triangle
subsets against the separate baseline as well as running the structural fixtures.


## Optional pod and laser near-model experiment

`laser_structural.py` runs in Blender and reads the accepted standalone Blockbench source under the supplied artwork workspace. It flattens shallow engravings, reduces parts independently with bounded bidirectional surface error, reprojects reduced faces into source UV charts, and preserves the authored rig. Broad marked armour retains its original chart boundaries. `pod_structural.py` replaces repeated nozzle shell/rim boxes with six continuous 16-sided stepped rings and flattens badge-cap relief. Neither script overwrites the accepted source.

`near_pack.py` stages a reversible resource pack from these candidates and the structurally cleaned cube export of the candidate pod. Export the pod cubes with Blockbench's Bedrock codec into `work/structural-near-test/pod-input/bedrock/vehicle/hellpod_v3.geo.json`, then run `structural.mjs` into the sibling `pod-output` directory. The pack builder restores the new nozzle meshes, retains the runtime heat indicators and bone metadata, and reuses the existing atlas dimensions. Python image processing requires Pillow. `clean_side_markings.py` fixes the four cradle UV-island V outlines with identical mirrored raster legs.

```sh
blender --background --python tools/model_optimizer/laser_structural.py -- --workspace <artwork-workspace> --output <artwork-workspace>/work/structural-near-test
python tools/model_optimizer/clean_side_markings.py --model <artwork-workspace>/work/structural-near-test/laser.bbmodel
python tools/model_optimizer/pod_structural.py --workspace <artwork-workspace> --output <artwork-workspace>/work/structural-near-test
# Export pod cubes with Blockbench, then apply structural.mjs as described above.
python tools/model_optimizer/near_pack.py --workspace <artwork-workspace> --repo .
```

The checked-in optional pack is in `art/experiments/pod-laser-near-model/resourcepack/`. Default mod resources are unchanged. For an isolated client comparison only, `-PnearModelOverride=<unpacked-pack>` overlays processed resources for that Gradle invocation; omit it to restore normal resources. Do not use this flag for a release build.

The second laser pass starts at a 30% per-part ratio and increases retained geometry until the same surface-error limit passes. Marked cradle armour is still protected. Degenerate source UV projections fall back to their source chart centre; non-finite JSON values fail export. Run the marking postprocess with ordinary Python/Pillow, not Blender's separate Python installation.


## Other turret near-model candidates

The separate editable candidates are in `art/models/其它炮台减面/`. They cover
Autocannon, Rocket, Flame, Tesla, and the three mortar sentries. Default runtime
assets are unchanged.

`other_turrets.py` combines coincident cube-built octagonal prisms into closed
meshes, retains their surface colors in a baked atlas, and collapses redundant
planar vertices only when surface and UV interpolation agree. `turret_curves.mjs`
then reduces unmarked curved parts with a 0.08 model-unit error limit, locking UV
seams, open boundaries and creases sharper than 35 degrees. Uniform UVs may be
canonicalized only after every texel in the original face bounds is confirmed
identical within the same material tile. Shared base and cradle parts are excluded.

Run these steps once from the reviewed source, never by repeatedly simplifying
an existing candidate. Python requires NumPy and Pillow; Node dependencies are
pinned in this directory's lockfile.

```sh
python tools/model_optimizer/other_turrets.py --workspace <artwork-workspace> --output <candidate-directory>
node tools/model_optimizer/turret_curves.mjs <candidate-directory>
python tools/model_optimizer/review_turrets.py --workspace <artwork-workspace> --models <candidate-directory> --previews <preview-directory>
```

The reviewer checks preserved animation data, hierarchy, locators and shared
parts, finite geometry and valid face UVs, and unchanged open/non-manifold edge
positions for modified source meshes. New octagonal prisms must be closed. It
renders two matched body views and reports actual triangle counts. These checks
do not establish an in-game frame-rate improvement.

## Editable equipment near-model candidates

`equipment_sources.mjs` produces separate Blockbench review sources for the five
Guard Dog variants, both production machines, deployment beacon, tactical
terminal, Supply Pack and Jump Pack. The two existing deployment containers
are retained at their already small geometry budgets. Reviewed files and matched
views live under `art/models/装备与机器减面/`; default runtime resources are unchanged.

```sh
node tools/model_optimizer/equipment_sources.mjs --input <native-sorted-baseline> --output <candidate-directory> --manifest <relative-model-paths.json>
```

The manifest is a JSON array of relative `.bbmodel` paths. Normalize source mesh
face order first with Blockbench's `MeshFace.getSortedVertices()`. Input and
output must be different directories, and a reviewed candidate must never be
used as its own baseline. The candidate set also includes independently reviewed
exact planar collapses from the same baseline, using the planar pass in
`other_turrets.py`; choose the smaller valid result per element UUID.

The reducer locks textured markings, material/UV seams and open boundaries. It
compares crease-preserving alternatives and selects only results within 0.05
Blockbench model units. It retains original vertex positions, part names,
transforms, rig, animations and texture images. UV coordinates may be collapsed
to a constant color only after checking every texel in the face's original UV
bounds. Every nonuniform textured triangle must survive that pass unchanged.
Open and non-manifold edge positions must match before and after simplification.

Convex planar n-gon caps are triangulated: Blockbench's native mesh renderer
supports triangles and quads, so leaving larger caps in the authored file can
silently hide them. This repaired 28 existing caps on the Bio Processor.

Validation opens all 28 files in Blockbench, checks actual native index counts
against supported source faces, checks successful texture loading, and compares
original/candidate views with identical cameras. Preserve element metadata,
texture data, hierarchy and animation definitions exactly. Source triangle
budgets include cubes; mesh-only native index counts are checked separately.
These model checks do not establish an in-game frame-rate improvement.
