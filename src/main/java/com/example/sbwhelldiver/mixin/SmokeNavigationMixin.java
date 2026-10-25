package com.example.sbwhelldiver.mixin;
import com.example.sbwhelldiver.effect.SmokeOcclusion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import java.util.Set;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PathNavigation.class)
public abstract class SmokeNavigationMixin {
    @ModifyVariable(method="createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private Set<BlockPos> smokePathDestination(Set<BlockPos> destinations) {
        Mob mob=((SmokeNavigationAccess)this).getSmokeMob();
        Entity target=mob.getTarget();
        if(target==null||!destinations.contains(target.blockPosition()))return destinations;
        Vec3 known=SmokeOcclusion.knownPosition(mob,target);
        return known==null?destinations:Set.of(BlockPos.containing(known));
    }
    @Inject(method="createPath(Lnet/minecraft/world/entity/Entity;I)Lnet/minecraft/world/level/pathfinder/Path;",at=@At("HEAD"),cancellable=true)
    private void smokeKnownTarget(Entity target,int accuracy,CallbackInfoReturnable<Path> cir) {
        Mob mob=((SmokeNavigationAccess)this).getSmokeMob();
        Vec3 at=SmokeOcclusion.knownPosition(mob,target);
        if(at!=null)cir.setReturnValue(((PathNavigation)(Object)this).createPath(BlockPos.containing(at),accuracy));
    }
    @Inject(method="createPath(DDDI)Lnet/minecraft/world/level/pathfinder/Path;",at=@At("HEAD"),cancellable=true)
    private void smokeKnownCoordinates(double x,double y,double z,int accuracy,CallbackInfoReturnable<Path> cir) {
        Mob mob=((SmokeNavigationAccess)this).getSmokeMob();
        Vec3 wanted=new Vec3(x,y,z),at=SmokeOcclusion.maskDestination(mob,wanted);
        if(!at.equals(wanted))cir.setReturnValue(((PathNavigation)(Object)this).createPath(BlockPos.containing(at),accuracy));
    }
}
