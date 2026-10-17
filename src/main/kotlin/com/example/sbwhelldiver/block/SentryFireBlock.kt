package com.example.sbwhelldiver.block
import com.example.sbwhelldiver.effect.FlameDamage
import com.example.sbwhelldiver.entity.GroundFireEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.*
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.*
import net.minecraft.world.phys.AABB
/** Actual world fire. Vanilla fire geometry, support and extinguishing; custom burn damage only. */
class SentryFireBlock(properties:BlockBehaviour.Properties):FireBlock(properties) {
    override fun canBurn(state:BlockState)=false
    override fun canSurvive(state:BlockState,level:LevelReader,pos:BlockPos)=level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP)
    override fun updateShape(state:BlockState,direction:Direction,other:BlockState,level:LevelAccessor,pos:BlockPos,otherPos:BlockPos)=
        if(canSurvive(state,level,pos))state else Blocks.AIR.defaultBlockState()
    override fun onPlace(state:BlockState,level:Level,pos:BlockPos,old:BlockState,moving:Boolean) {
        // Do not call BaseFireBlock's portal creation or vanilla fire damage/spread.
        if(!state.canSurvive(level,pos))level.removeBlock(pos,false)
        else if(!level.isClientSide)level.scheduleTick(pos,this,1)
    }
    private fun owners(level:Level,pos:BlockPos)=level.getEntitiesOfClass(GroundFireEntity::class.java,AABB(pos).inflate(16.0)) {
        !it.isRemoved && it.remainingTicks>0 && it.coversCell(pos)
    }
    override fun tick(state:BlockState,level:ServerLevel,pos:BlockPos,random:RandomSource) {
        val remaining=owners(level,pos).maxOfOrNull{it.remainingTicks}?:0
        if(remaining<=0 || !canSurvive(state,level,pos))level.removeBlock(pos,false)
        else level.scheduleTick(pos,this,remaining.coerceIn(1,20))
    }
    override fun entityInside(state:BlockState,level:Level,pos:BlockPos,entity:Entity) {
        if(!level.isClientSide && entity is LivingEntity && entity.isAlive && !entity.isSpectator)
            owners(level,pos).forEach{it.igniteInside(entity)}
        // No vanilla inFire damage; no panic; passive ground fire never triggers mines.
    }
    override fun animateTick(state:BlockState,level:Level,pos:BlockPos,random:RandomSource) {
        if(random.nextInt(5)==0)level.addParticle(ParticleTypes.SMOKE,pos.x+random.nextDouble(),pos.y+.85,pos.z+random.nextDouble(),0.0,.025,0.0)
    }
}
