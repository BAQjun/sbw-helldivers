package com.example.sbwhelldiver.client;

import com.example.sbwhelldiver.entity.MortarFieldEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.WeakHashMap;

/** Per-block floors for the downward part of a cloud, shared by all its pixels.
 * No chunks are loaded and no terrain geometry is generated. Refresh at most
 * once per client tick. The main depth buffer still clips foreground geometry. */
public final class FieldTerrainMask {
    private FieldTerrainMask() {}
    private static final WeakHashMap<Object, Entry> CACHE = new WeakHashMap<>();
    private record Entry(long frameTick, int x, int z, double y, double radius, Data data) {}
    public record Data(int x, int z, int width, int height, float depth, FloatBuffer floors) {}

    public static double maximumDepth(MortarFieldEntity field) {
        return Math.min(4.0, field.getFieldHeight() * .5);
    }
    public static void clear() { CACHE.clear(); }

    public static Data get(MortarFieldEntity field) {
        int tick = net.minecraft.client.Minecraft.getInstance().player == null ? field.tickCount :
            net.minecraft.client.Minecraft.getInstance().player.tickCount;
        return sample(field, field.level(), field.position(), field.getRadius(), (float)maximumDepth(field), tick, false);
    }

    public static Data getSpray(Object cloud, Level level, Vec3 center, double radius, float limit) {
        return sample(cloud, level, center, radius, limit, level.getGameTime(), true);
    }

    private static Data sample(Object key, Level level, Vec3 center, double radius, float limit, long tick, boolean spray) {
        int x = Mth.floor(center.x - radius);
        int z = Mth.floor(center.z - radius);
        Entry previous = CACHE.get(key);
        if (previous != null && previous.frameTick == tick && previous.x == x && previous.z == z &&
            previous.y == center.y && previous.radius == radius) return previous.data;
        int w = Mth.floor(center.x + radius) - x + 1;
        int h = Mth.floor(center.z + radius) - z + 1;
        FloatBuffer floors = previous != null && previous.data.width == w && previous.data.height == h ?
            previous.data.floors : BufferUtils.createFloatBuffer(w * h);
        floors.clear();
        float depth = 0;
        int top = Mth.floor(center.y), bottom = Mth.floor(center.y - limit);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dz = 0; dz < h; dz++) for (int dx = 0; dx < w; dx++) {
            // Spray fog needs to distinguish a real floor from open air at the scan limit.
            float floor = spray ? -1000 : 0;
            double nearX = Math.max(x + dx, Math.min(center.x, x + dx + 1.0)) - center.x;
            double nearZ = Math.max(z + dz, Math.min(center.z, z + dz + 1.0)) - center.z;
            // Cells outside the footprint cannot contribute any fog.
            if (nearX * nearX + nearZ * nearZ < radius * radius &&
                level.hasChunk((x + dx) >> 4, (z + dz) >> 4)) {
                if (!spray) floor = -limit;
                for (int y = top; y >= bottom; y--) {
                    at.set(x + dx, y, z + dz);
                    var state = level.getBlockState(at);
                    if (spray) {
                        var shape = state.getCollisionShape(level, at);
                        if (shape.isEmpty()) continue;
                        double surface = y + shape.max(Direction.Axis.Y) - center.y;
                        if (surface >= -limit) floor = (float)surface;
                        break;
                    }
                    if (state.canOcclude() && Block.isShapeFullBlock(state.getOcclusionShape(level, at))) {
                        floor = Math.max(-limit, (float)(y + 1 - center.y));
                        break;
                    }
                }
            }
            floors.put(floor);
            if (!spray) depth = Math.max(depth, -floor);
        }
        floors.flip();
        Data data = new Data(x, z, w, h, depth, floors);
        CACHE.put(key, new Entry(tick, x, z, center.y, radius, data));
        return data;
    }
}
