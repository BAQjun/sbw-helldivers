package com.example.sbwhelldiver.mixin;

import com.example.sbwhelldiver.survival.EquipmentBeaconCollisionPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Lithium's section cache omits NeoForge multipart children because they have no own section. */
@Pseudo
@Mixin(targets="net.caffeinemc.mods.lithium.common.world.WorldHelper", remap=false)
public abstract class EquipmentBeaconLithiumCollisionMixin {
    @Inject(method="getEntitiesForCollision", at=@At("RETURN"), cancellable=true, require=0, remap=false)
    private static void beaconMovementParts(EntityGetter world, AABB box, Entity except, CallbackInfoReturnable<List<Entity>> cir) {
        append(world, box, except, null, cir);
    }
    @Inject(method="getOtherEntitiesForCollision", at=@At("RETURN"), cancellable=true, require=0, remap=false)
    private static void beaconQueryParts(EntityGetter world, AABB box, Entity except, Predicate<? super Entity> predicate, CallbackInfoReturnable<List<Entity>> cir) {
        append(world, box, except, predicate, cir);
    }
    private static void append(EntityGetter world, AABB box, Entity except, Predicate<? super Entity> predicate, CallbackInfoReturnable<List<Entity>> cir) {
        if (!(world instanceof Level level)) return;
        List<Entity> result=null;
        for (var part : level.getPartEntities()) {
            if (!(part instanceof EquipmentBeaconCollisionPart) || part.getParent()==except || part==except ||
                !part.canBeCollidedWith() || !part.getBoundingBox().intersects(box) ||
                (predicate!=null && !predicate.test(part)) || cir.getReturnValue().contains(part)) continue;
            if (result==null) result=new ArrayList<>(cir.getReturnValue());
            result.add(part);
        }
        if (result!=null) cir.setReturnValue(result);
    }
}
