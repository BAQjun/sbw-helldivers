package com.example.sbwhelldiver.survival

import com.example.sbwhelldiver.entity.*
import net.minecraft.core.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.*
import net.minecraft.world.*
import net.minecraft.world.entity.*
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.server.level.ServerLevel

class MachineContainerEntity(type:EntityType<out MachineContainerEntity>,level:Level):Entity(type,level) {
    // Deployment deadlines must keep advancing while the delivery is above a player's activation range.
    // Use the vanilla contract also respected by entity-tick optimizers, without a mod-specific dependency.
    override fun isAlwaysTicking()=true
    val machine get()=MachineDeployment.entries[entityData.get(KIND).coerceIn(0,1)]
    val facing get()=Direction.from2DDataValue(entityData.get(FACING))
    val phase get()=entityData.get(PHASE)
    val phaseStart get()=entityData.get(START)
    val arrival get()=entityData.get(ARRIVAL)
    val launch get()=entityData.get(LAUNCH)
    val root get()=entityData.get(ROOT)
    val braking get()=phase==FALLING && level().gameTime>=HellpodFlight.brakeStart(launch,arrival)
    private var payload=CompoundTag()
    init{isNoGravity=true;noPhysics=true}
    override fun defineSynchedData(){entityData.define(KIND,0);entityData.define(FACING,Direction.NORTH.get2DDataValue());entityData.define(PHASE,CLOSED);entityData.define(START,0L);entityData.define(ROOT,BlockPos.ZERO);entityData.define(LAUNCH,0L);entityData.define(ARRIVAL,0L)}
    fun configure(m:MachineDeployment,pos:BlockPos,f:Direction,data:CompoundTag,airdrop:Boolean,deadline:Long=level().gameTime+120) {
        entityData.set(KIND,m.ordinal);entityData.set(FACING,f.get2DDataValue());entityData.set(ROOT,pos);entityData.set(START,level().gameTime)
        entityData.set(PHASE,if(airdrop)FALLING else CLOSED);entityData.set(LAUNCH,level().gameTime);entityData.set(ARRIVAL,deadline)
        payload=data.copy();setYRot(f.toYRot());setPos(pos.x+.5,if(airdrop)height(level().gameTime.toDouble())else pos.y.toDouble(),pos.z+.5)
    }
    fun height(now:Double)=HellpodFlight.height(now,launch,arrival,root.y.toDouble()+AbstractHellpodEntity.descentSpawnHeight(),root.y.toDouble(),AbstractHellpodEntity.descentBrakeHeight(),brake={r,d,_,_->d*r*r})
    fun sampleClientFlight() {
        if(phase==FALLING){xo=x;yo=y;zo=z;setPos(root.x+.5,height(level().gameTime.toDouble()),root.z+.5)}
        setBoundingBox(MachineLayout.rotate(MachineLayout.containerBounds(machine),facing).move(x,y,z))
    }
    override fun tick() {
        super.tick()
        if(level().isClientSide)return
        val beforeY=y
        if(phase==FALLING){setPos(root.x+.5,height(level().gameTime.toDouble()),root.z+.5);yo=beforeY;deltaMovement=net.minecraft.world.phys.Vec3.ZERO}
        setBoundingBox(MachineLayout.rotate(MachineLayout.containerBounds(machine),facing).move(x,y,z))
        if(phase==FALLING&&level().gameTime>=arrival){
            setPos(root.x+.5,root.y.toDouble(),root.z+.5)
            if(!MachineStructure.space(level() as ServerLevel,root,machine,facing,this)){spawnAtLocation(MachineContainerItem.stack(machine,payload));discard();return}
            entityData.set(PHASE,CLOSED);entityData.set(START,level().gameTime)
        }
        if(phase==OPENING&&level().gameTime-phaseStart>=OPENING_TICKS)discard()
    }
    override fun isPickable()=phase!=OPENING
    override fun isPushable()=false
    override fun canBeCollidedWith()=phase==CLOSED
    override fun shouldRenderAtSqrDistance(distance:Double)=distance<1024.0*1024.0
    override fun interact(player:Player,hand:InteractionHand):InteractionResult {
        if(phase!=CLOSED||!player.getItemInHand(hand).`is`(com.atsuishio.superbwarfare.init.ModItems.CROWBAR.get()))return InteractionResult.PASS
        if(level() is ServerLevel) {
            if(player.isShiftKeyDown){spawnAtLocation(MachineContainerItem.stack(machine,payload));discard()}
            else {
                val be=MachineStructure.place(level() as ServerLevel,root,machine,facing,payload,this)
                if(be==null){player.displayClientMessage(net.minecraft.network.chat.Component.translatable("terminal.sbw_helldiver.no_space"),true);return InteractionResult.FAIL}
                payload=CompoundTag();entityData.set(PHASE,OPENING);entityData.set(START,level().gameTime)
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide)
    }
    override fun readAdditionalSaveData(n:CompoundTag){entityData.set(KIND,n.getInt("Kind").coerceIn(0,1));entityData.set(FACING,n.getInt("Facing"));entityData.set(PHASE,n.getInt("Phase").coerceIn(0,2));entityData.set(ROOT,BlockPos.of(n.getLong("Root")));entityData.set(START,n.getLong("Start"));entityData.set(LAUNCH,n.getLong("Launch"));entityData.set(ARRIVAL,n.getLong("Arrival"));payload=n.getCompound("Contents").copy()}
    override fun addAdditionalSaveData(n:CompoundTag){n.putInt("Kind",machine.ordinal);n.putInt("Facing",facing.get2DDataValue());n.putInt("Phase",phase);n.putLong("Root",root.asLong());n.putLong("Start",phaseStart);n.putLong("Launch",launch);n.putLong("Arrival",arrival);n.put("Contents",payload.copy())}
    companion object {
        const val OPENING_TICKS=40
        const val OPENING_CLIP_SECONDS=2.4f
        const val FALLING=0;const val CLOSED=1;const val OPENING=2
        private val KIND=SynchedEntityData.defineId(MachineContainerEntity::class.java,EntityDataSerializers.INT)
        private val FACING=SynchedEntityData.defineId(MachineContainerEntity::class.java,EntityDataSerializers.INT)
        private val PHASE=SynchedEntityData.defineId(MachineContainerEntity::class.java,EntityDataSerializers.INT)
        private val ROOT=SynchedEntityData.defineId(MachineContainerEntity::class.java,EntityDataSerializers.BLOCK_POS)
        private val START=SynchedEntityData.defineId(MachineContainerEntity::class.java,EntityDataSerializers.LONG)
        private val LAUNCH=SynchedEntityData.defineId(MachineContainerEntity::class.java,EntityDataSerializers.LONG)
        private val ARRIVAL=SynchedEntityData.defineId(MachineContainerEntity::class.java,EntityDataSerializers.LONG)
    }
}
