package com.example.sbwhelldiver.client;

import com.example.sbwhelldiver.entity.MortarFieldEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.WeakHashMap;

/** Per-block floors for the downward part of a cloud, shared by all its pixels.
 * No chunks are loaded and no terrain geometry is generated. Refresh at most
 * once per client tick. The main depth buffer still clips foreground geometry. */
public final class FieldTerrainMask {
    private FieldTerrainMask() {}
    private static final WeakHashMap<MortarFieldEntity, Entry> CACHE = new WeakHashMap<>();
    private record Entry(long frameTick, int x, int z, double y, double radius, Data data) {}
    public record Data(int x, int z, int width, int height, float depth, FloatBuffer floors) {}

    public static double maximumDepth(MortarFieldEntity field) {
        return Math.min(4.0, field.getFieldHeight() * .5);
    }
    public static void clear() { CACHE.clear(); }

    public static Data get(MortarFieldEntity field) {
        int tick = net.minecraft.client.Minecraft.getInstance().player == null ? field.tickCount :
            net.minecraft.client.Minecraft.getInstance().player.tickCount;
        int x = Mth.floor(field.getX() - field.getRadius());
        int z = Mth.floor(field.getZ() - field.getRadius());
        Entry previous = CACHE.get(field);
        if (previous != null && previous.frameTick == tick && previous.x == x && previous.z == z &&
            previous.y == field.getY() && previous.radius == field.getRadius()) return previous.data;
        int w = Mth.floor(field.getX() + field.getRadius()) - x + 1;
        int h = Mth.floor(field.getZ() + field.getRadius()) - z + 1;
        FloatBuffer floors = previous != null && previous.data.width == w && previous.data.height == h ?
            previous.data.floors : BufferUtils.createFloatBuffer(w * h);
        floors.clear();
        float limit = (float)maximumDepth(field), depth = 0;
        int top = Mth.floor(field.getY()), bottom = Mth.floor(field.getY() - limit);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dz = 0; dz < h; dz++) for (int dx = 0; dx < w; dx++) {
            float floor = 0;
            double nearX = Math.max(x + dx, Math.min(field.getX(), x + dx + 1.0)) - field.getX();
            double nearZ = Math.max(z + dz, Math.min(field.getZ(), z + dz + 1.0)) - field.getZ();
            // Cells outside the footprint cannot contribute any fog.
            if (nearX * nearX + nearZ * nearZ < field.getRadius() * field.getRadius() &&
                field.level().hasChunk((x + dx) >> 4, (z + dz) >> 4)) {
                floor = -limit;
                for (int y = top; y >= bottom; y--) {
                    at.set(x + dx, y, z + dz);
                    var state = field.level().getBlockState(at);
                    if (state.canOcclude() && Block.isShapeFullBlock(state.getOcclusionShape(field.level(), at))) {
                        floor = Math.max(-limit, (float)(y + 1 - field.getY()));
                        break;
                    }
                }
            }
            floors.put(floor);
            depth = Math.max(depth, -floor);
        }
        floors.flip();
        Data data = new Data(x, z, w, h, depth, floors);
        CACHE.put(field, new Entry(tick, x, z, field.getY(), field.getRadius(), data));
        return data;
    }
}
