# Runtime model reduction (performance preview)

The editable Blockbench models remain full detail. Only the optional low quality game exports are reduced.
Keep an unreduced export directory as the input; never feed reduced output back
into the tool. The performance preview is not a production release.

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
