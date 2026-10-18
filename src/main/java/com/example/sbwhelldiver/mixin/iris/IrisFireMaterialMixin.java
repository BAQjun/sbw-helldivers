package com.example.sbwhelldiver.mixin.iris;

import com.example.sbwhelldiver.client.IrisFireMaterials;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional integration, in a separate non-required config. Populate materials
 * before chunk meshing, and again on shader-pack reload/switch. */
@Pseudo
@Mixin(targets="net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings",remap=false)
public abstract class IrisFireMaterialMixin {
    @Inject(method="setBlockStateIds",at=@At("HEAD"),require=0)
    private void helldivers$fireMaterial(Object2IntMap<BlockState> ids,CallbackInfo ci){IrisFireMaterials.extend(ids);}
}
