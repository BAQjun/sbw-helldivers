"""Convert standalone reviewed sentry geometry into the existing runtime rest space.

The legacy rigs deliberately use different mesh and pivot offsets. Never infer a
mesh transform from the bone pivots alone. This transform applies to positions
and normals; it does not alter the runtime hierarchy, pivots or animation data.
"""

MORTAR_MOVING_PARTS = frozenset({
    "barrel", "receiver", "feed_round", "recoil_tube", "muzzle_tube",
    "feed_cradle", "feed_cup", "feed_slider", "feed_arm", "ammunition_column",
})


def runtime_vertex(model, bone, value, *, source_pivot, runtime_pivot,
                   elevation_pivot=None, normal=False):
    x, y, z = value
    if "mortar" in model:
        if bone in MORTAR_MOVING_PARTS:
            if normal:
                return [x, z, -y]
            if elevation_pivot is None:
                raise ValueError("Mortar export requires the runtime elevation pivot")
            _, py, pz = elevation_pivot
            # Undo the standalone lift, then rotate upright geometry -90 deg
            # about the runtime elevation axis. This is build_mortar_assets.py's
            # rest-pose transform, applied to the authored standalone meshes.
            return [x, z + py - pz, -y + pz + py + 3.85]
        return [x, y, z] if normal else [x, y - 3.85, z]
    if model == "autocannon_hellpod_v1":
        # The standalone assembly is 3.85 units above the pod's bind space.
        # Apply the same displacement to the complete assembly: retaining the
        # old gun vertices separated its trunnions from the fixed axle housing.
        return [x, y, z] if normal else [x, y - 3.85, z]
    if normal:
        return [x, y, z]
    return [v + b - a for v, a, b in zip(value, source_pivot, runtime_pivot)]
