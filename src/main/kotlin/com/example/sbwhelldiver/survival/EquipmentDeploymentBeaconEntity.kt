package com.example.sbwhelldiver.survival

import com.example.sbwhelldiver.init.ModSounds
import com.example.sbwhelldiver.network.*
import com.example.sbwhelldiver.stratagem.Stratagems
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.*
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.abs

/** Terminal session, payment and the saved ten-second equipment delivery reservation. */
class EquipmentDeploymentBeaconEntity(type:EntityType<out EquipmentDeploymentBeaconEntity>,level:Level):Entity(type,level) {
    // Deployment deadlines must keep advancing while the delivery is above a player's activation range.
    // Use the vanilla contract also respected by entity-tick optimizers, without a mod-specific dependency.
    override fun isAlwaysTicking()=true
    val phase get()=entityData.get(PHASE)
    val phaseStart get()=entityData.get(START)
    val selectedMachine get()=MachineDeployment.entries.getOrNull(entityData.get(MACHINE))
    val prefix get()=entityData.get(PREFIX).mapNotNull { it.digitToIntOrNull() }
    val error get()=entityData.get(ERROR)
    val inputErrorAt get()=entityData.get(INPUT_ERROR_AT)
    val paid get()=phase==COUNTDOWN||phase==RETRACTING||phase==FINISHED
    val capReleased get()=entityData.get(CAP_RELEASED)
    val deploymentEnd get()=entityData.get(DEPLOYMENT_END)
    val deploymentAnchor get()=Vec3(x,entityData.get(GROUND).toDouble(),z)
    val screenFacingYaw get()=yRot+EquipmentBeaconGeometry.screenYaw
    var owner:UUID?=null;private set
    private var operator:UUID?=null
    private var nonce:UUID?=null
    private var sessionUntil=0L
    private var lastInput=-1L
    private var groundY=0.0
    private var arrival=0L
    private var born=0L
    private var reservedFacing=net.minecraft.core.Direction.NORTH
    private var containerSent=false
    private lateinit var collisionParts:Array<EquipmentBeaconCollisionPart>
    init {
        isNoGravity=true;noPhysics=true
        collisionParts=Array(2){EquipmentBeaconCollisionPart(this)}
        setId(ENTITY_COUNTER.getAndAdd(collisionParts.size+1)+1)
    }
    override fun isMultipartEntity()=true
    override fun getParts()=collisionParts
    override fun setId(value:Int){super.setId(value);if(this::collisionParts.isInitialized)collisionParts.forEachIndexed{i,p->p.id=value+i+1}}
    override fun setPos(x:Double,y:Double,z:Double){super.setPos(x,y,z);if(this::collisionParts.isInitialized)updateCollisionParts()}
    fun payloadTravelOffset():Double {
        val age=(level().gameTime-phaseStart).coerceAtLeast(0)/20.0
        val remaining=when(phase){
            PACKED,IMPACT->1.0
            DEPLOYING->1-((age-.45)/.60).coerceIn(0.0,1.0)
            RETRACTING->((age*3.95/3-1.55)/1.5).coerceIn(0.0,1.0)
            FINISHED->1.0
            else->0.0
        }
        return -EquipmentBeaconGeometry.liftTravel*remaining
    }
    fun updateCollisionParts() {
        val origin=position();val moving=payloadTravelOffset()
        // Two stable axis-aligned volumes replace overlapping rotated fragments.
        // The low pod base remains separate from the raised, retractable beacon body.
        val authored=EquipmentBeaconGeometry.collision
        val base=authored[0]
        val body=authored[3]
        val top=authored[5]
        val radius=maxOf(kotlin.math.abs(body.minX),kotlin.math.abs(body.maxX),kotlin.math.abs(body.minZ),kotlin.math.abs(body.maxZ))
        val boxes=listOf(
            net.minecraft.world.phys.AABB(base.minX,base.minY,base.minZ,base.maxX,base.maxY,base.maxZ),
            net.minecraft.world.phys.AABB(-radius,body.minY+moving,-radius,radius,top.maxY+moving,radius)
        )
        for((i,part)in collisionParts.withIndex()){
            part.setPos(origin)
            // Multipart debug rendering interpolates against the parent's previous position.
            part.xOld=xOld;part.yOld=yOld;part.zOld=zOld
            part.boundingBox=boxes[i].move(origin)
        }
    }
    override fun defineSynchedData(b:SynchedEntityData.Builder) {
        b.define(PHASE,PACKED);b.define(START,-1L);b.define(MACHINE,-1);b.define(PREFIX,"");b.define(ERROR,0);b.define(INPUT_ERROR_AT,-1L)
        b.define(GROUND,0f);b.define(ARRIVAL,-1L);b.define(BORN,-1L)
        b.define(CAP_RELEASED,false)
        b.define(DEPLOYMENT_END,-1L)
    }
    fun configure(surface:Double,arrivalTime:Long,caller:UUID?) {
        groundY=surface;arrival=arrivalTime;born=level().gameTime;owner=caller
        entityData.set(GROUND,surface.toFloat());entityData.set(ARRIVAL,arrival);entityData.set(BORN,born)
        transition(PACKED,born)
        setPos(x,heightAt(born.toDouble()),z)
    }
    private fun heightAt(now:Double):Double {
        val landing=entityData.get(GROUND)+.0956
        return com.example.sbwhelldiver.entity.HellpodFlight.height(now,entityData.get(BORN),entityData.get(ARRIVAL),landing+com.example.sbwhelldiver.entity.AbstractHellpodEntity.descentSpawnHeight()+5.45,landing,com.example.sbwhelldiver.entity.AbstractHellpodEntity.descentBrakeHeight())
    }
    val flightLaunch get()=entityData.get(BORN)
    val flightArrival get()=entityData.get(ARRIVAL)
    val flightBraking get()=level().gameTime>=com.example.sbwhelldiver.entity.HellpodFlight.brakeStart(entityData.get(BORN),flightArrival)
    fun sampleClientFlight(){if(phase==PACKED){xo=x;yo=y;zo=z;setPos(x,heightAt(level().gameTime.toDouble()),z)};updateCollisionParts()}
    fun localToWorld(v:Vec3)=v.yRot(-yRot*(Math.PI/180).toFloat()).add(position())
    fun screenCenter()=localToWorld(EquipmentBeaconGeometry.center)
    fun screenNormal()=EquipmentBeaconGeometry.normal.yRot(-yRot*(Math.PI/180).toFloat())
    fun screenHit(player:Player):Boolean {
        if(phase!=ACTIVE)return false
        val eye=player.eyePosition;val ray=player.lookAngle;val n=screenNormal();val center=screenCenter()
        val denominator=ray.dot(n)
        if(denominator>=-.0001)return false
        val t=center.subtract(eye).dot(n)/denominator
        if(t<0 || t>player.entityInteractionRange()+.2)return false
        val point=eye.add(ray.scale(t));val offset=point.subtract(center).yRot(yRot*(Math.PI/180).toFloat())
        if(abs(offset.dot(EquipmentBeaconGeometry.right))>EquipmentBeaconGeometry.width/2 || abs(offset.dot(EquipmentBeaconGeometry.up))>EquipmentBeaconGeometry.height/2)return false
        val clip=level().clip(ClipContext(eye,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player))
        return clip.type==net.minecraft.world.phys.HitResult.Type.MISS || clip.location.distanceToSqr(point)<.01
    }
    override fun isPickable()=true
    override fun isPushable()=false
    override fun canBeCollidedWith()=false
    override fun interactAt(player:Player,hit:Vec3,hand:InteractionHand):InteractionResult {
        if(hand!=InteractionHand.MAIN_HAND || !screenHit(player))return InteractionResult.PASS
        if(player is ServerPlayer)openTerminal(player)
        return InteractionResult.sidedSuccess(level().isClientSide)
    }
    fun openTerminal(player:ServerPlayer):Boolean {
        if(!screenHit(player)||player.isSpectator||player.isPassenger||player.containerMenu!==player.inventoryMenu)return false
        if(operator!=null && operator!=player.uuid) { player.displayClientMessage(Component.translatable("terminal.sbw_helldiver.busy"),true);return false }
        operator=player.uuid;nonce=UUID.randomUUID();sessionUntil=level().gameTime+600;lastInput=-1
        entityData.set(MACHINE,-1);entityData.set(PREFIX,"");entityData.set(ERROR,0);entityData.set(INPUT_ERROR_AT,-1L)
        sendState(player,true);return true
    }
    private fun valid(player:ServerPlayer,token:UUID)=phase==ACTIVE && operator==player.uuid && nonce==token && player.level()===level() && player.isAlive && !player.isSpectator && !player.isPassenger && player.containerMenu===player.inventoryMenu && distanceToSqr(player)<36 && level().gameTime<=sessionUntil
    fun acceptInput(player:ServerPlayer,token:UUID,direction:Int):Boolean {
        if(!valid(player,token))return false
        if(direction==-1){closeTerminal();return true}
        if(direction !in 0..3 || lastInput==level().gameTime)return false
        lastInput=level().gameTime;sessionUntil=lastInput+600;entityData.set(ERROR,0)
        if(selectedMachine!=null && direction==Stratagems.LEFT) {
            entityData.set(MACHINE,-1);entityData.set(PREFIX,"");sound(player,"terminal_input");sendState(player,true);return true
        }
        val input=prefix+direction
        val machine=selectedMachine
        if(machine==null) {
            if(input.all{it==Stratagems.LEFT}) {
                entityData.set(PREFIX,input.joinToString(""));sound(player,"terminal_input")
                if(input.size==6) {
                    entityData.set(PREFIX,"");containerSent=true
                    val now=level().gameTime;entityData.set(DEPLOYMENT_END,now+60)
                    transition(RETRACTING,now);closeTerminal()
                }else sendState(player,true)
                return true
            }
            val candidates=MachineDeployment.entries.filter{it.code.take(input.size)==input}
            if(candidates.isEmpty())return rejectInput(player)
            entityData.set(PREFIX,input.joinToString(""));sound(player,"terminal_input")
            candidates.firstOrNull{it.code.size==input.size}?.let{
                entityData.set(PREFIX,"")
                if(chooseFacing(it)==null)entityData.set(ERROR,1)
                else entityData.set(MACHINE,it.ordinal)
            }
        }else {
            if(direction!=Stratagems.RIGHT)return rejectInput(player)
            entityData.set(PREFIX,input.joinToString(""));sound(player,"terminal_input")
            if(input.size==6) {
                entityData.set(PREFIX,"")
                val facing=chooseFacing(machine)
                if(facing==null)entityData.set(ERROR,1)
                else {
                    if(!player.isCreative&&!machine.hasMaterials(player.inventory))entityData.set(ERROR,2)
                    else if(!com.example.sbwhelldiver.stratagem.StratagemCooldowns.reserve(player,Stratagems.equipmentDeployment.id,uuid))entityData.set(ERROR,3)
                    else {
                        // Server thread, phase transition and debit are atomic. Replayed inputs cannot pay twice.
                        if(!player.isCreative)check(machine.consumeMaterials(player.inventory))
                        player.containerMenu.broadcastChanges()
                        owner=player.uuid
                        reservedFacing=facing;containerSent=false
                        val now=level().gameTime;entityData.set(DEPLOYMENT_END,now+200);transition(COUNTDOWN,now)
                        com.example.sbwhelldiver.stratagem.StratagemCooldowns.change((level() as ServerLevel).server,player.uuid){state,time->state.beginEquipmentDeployment(uuid,time,time+200,machine.id)}
                        closeTerminal();return true
                    }
                }
            }
        }
        if(error>0)sound(player,"terminal_error")
        sendState(player,true);return true
    }
    private fun rejectInput(player:ServerPlayer):Boolean {
        entityData.set(PREFIX,"")
        entityData.set(INPUT_ERROR_AT,level().gameTime)
        sound(player,"terminal_error");sendState(player,true)
        return false
    }
    private fun deploymentPoint()=Vec3(x,groundY,z)
    private fun deploymentRoot()=net.minecraft.core.BlockPos.containing(deploymentPoint())
    private fun chooseFacing(m:MachineDeployment)=MachineStructure.choose(level() as ServerLevel,deploymentRoot(),m,net.minecraft.core.Direction.fromYRot(screenFacingYaw.toDouble()),this)
    private fun sendContainer() {
        val m=selectedMachine?:return
        val l=level() as ServerLevel
        val box=com.example.sbwhelldiver.init.ModEntities.MACHINE_CONTAINER.get().create(l)?:return
        box.configure(m,deploymentRoot(),reservedFacing,CompoundTag(),true,deploymentEnd)
        if(l.addFreshEntity(box))containerSent=true
    }
    private fun sendState(p:ServerPlayer,open:Boolean) {
        if(p is net.neoforged.neoforge.common.util.FakePlayer)return
        BeaconNetwork.CHANNEL.send(PacketTargets.PLAYER.with{p},EquipmentTerminalStatePacket(id,uuid,nonce?:UUID(0,0),open,entityData.get(MACHINE),entityData.get(PREFIX),error))
    }
    private fun sound(p:ServerPlayer,id:String) { ModSounds.EVENTS[id]?.get()?.let{p.playNotifySound(it,SoundSource.PLAYERS,1f,1f)} }
    private fun closeTerminal() {
        operator?.let{(level() as? ServerLevel)?.server?.playerList?.getPlayer(it)}?.let{sendState(it,false)}
        operator=null;nonce=null
    }
    private fun transition(value:Int,start:Long){entityData.set(PHASE,value);entityData.set(START,start)}
    override fun tick() {
        super.tick()
        if(phaseStart<0)return
        if(level().isClientSide)return
        if(phase==PACKED)setPos(x,heightAt(level().gameTime.toDouble()),z)
        if(!Stratagems.dimensionAllowed(level().dimension().location().toString())){closeTerminal();discard();return}
        val now=level().gameTime
        if((phase==COUNTDOWN||phase==RETRACTING)&&!containerSent)sendContainer()
        if(operator!=null) {
            val p=(level() as ServerLevel).server.playerList.getPlayer(operator!!)
            if(p==null||nonce==null||!valid(p,nonce!!))closeTerminal()
        }
        when(phase) {
            PACKED->if(now>=arrival){setPos(x,groundY+.0956,z);transition(IMPACT,arrival)}
            IMPACT->if(now-phaseStart>=10){transition(DEPLOYING,arrival+10);releaseCap()}
            DEPLOYING->if(now-phaseStart>=44)transition(ACTIVE,now)
            COUNTDOWN->if(now>=deploymentEnd-60)transition(RETRACTING,deploymentEnd-60)
            RETRACTING->if(now>=deploymentEnd){transition(FINISHED,now);discard();return}
            FINISHED->{discard();return}
        }
        updateCollisionParts()
    }

    private fun releaseCap(){
        if(capReleased)return
        val cap=com.example.sbwhelldiver.init.ModEntities.HELLPOD_CAP.get().create(level())?:return
        cap.ejectFrom(this)
        if(level().addFreshEntity(cap))entityData.set(CAP_RELEASED,true)
    }
    override fun readAdditionalSaveData(n:CompoundTag) {
        groundY=n.getDouble("Ground");arrival=n.getLong("Arrival");born=n.getLong("Born")
        reservedFacing=net.minecraft.core.Direction.from2DDataValue(n.getInt("ReservedFacing"));containerSent=n.getBoolean("ContainerSent")
        owner=if(n.hasUUID("Owner"))n.getUUID("Owner")else null
        entityData.set(GROUND,groundY.toFloat());entityData.set(ARRIVAL,arrival);entityData.set(BORN,born)
        entityData.set(PHASE,n.getInt("Phase").coerceIn(PACKED,IMPACT));entityData.set(START,n.getLong("Start"));entityData.set(MACHINE,n.getInt("Machine").coerceIn(-1,1))
        entityData.set(PREFIX,"");entityData.set(ERROR,0);entityData.set(INPUT_ERROR_AT,-1L)
        entityData.set(CAP_RELEASED,if(n.contains("CapReleased"))n.getBoolean("CapReleased")else phase!=PACKED&&phase!=IMPACT)
        entityData.set(DEPLOYMENT_END,if(n.contains("DeploymentEnd"))n.getLong("DeploymentEnd") else phaseStart+if(phase==RETRACTING)60 else 200)
    }
    override fun addAdditionalSaveData(n:CompoundTag) {
        n.putDouble("Ground",groundY);n.putLong("Arrival",arrival);n.putLong("Born",born);owner?.let{n.putUUID("Owner",it)}
        n.putInt("Phase",phase);n.putLong("Start",phaseStart);n.putInt("Machine",entityData.get(MACHINE))
        n.putBoolean("CapReleased",capReleased)
        n.putLong("DeploymentEnd",deploymentEnd)
        n.putInt("ReservedFacing",reservedFacing.get2DDataValue());n.putBoolean("ContainerSent",containerSent)
    }
    companion object {
        const val PACKED=0;const val DEPLOYING=1;const val ACTIVE=2;const val RETRACTING=3;const val COUNTDOWN=4;const val FINISHED=5;const val IMPACT=6
        private val PHASE=SynchedEntityData.defineId(EquipmentDeploymentBeaconEntity::class.java,EntityDataSerializers.INT)
        private val START=SynchedEntityData.defineId(EquipmentDeploymentBeaconEntity::class.java,EntityDataSerializers.LONG)
        private val MACHINE=SynchedEntityData.defineId(EquipmentDeploymentBeaconEntity::class.java,EntityDataSerializers.INT)
        private val PREFIX=SynchedEntityData.defineId(EquipmentDeploymentBeaconEntity::class.java,EntityDataSerializers.STRING)
        private val ERROR=SynchedEntityData.defineId(EquipmentDeploymentBeaconEntity::class.java,EntityDataSerializers.INT)
        private val INPUT_ERROR_AT=SynchedEntityData.defineId(EquipmentDeploymentBeaconEntity::class.java,EntityDataSerializers.LONG)
        private val GROUND=SynchedEntityData.defineId(EquipmentDeploymentBeaconEntity::class.java,EntityDataSerializers.FLOAT)
        private val ARRIVAL=SynchedEntityData.defineId(EquipmentDeploymentBeaconEntity::class.java,EntityDataSerializers.LONG)
        private val BORN=SynchedEntityData.defineId(EquipmentDeploymentBeaconEntity::class.java,EntityDataSerializers.LONG)
        private val CAP_RELEASED=SynchedEntityData.defineId(EquipmentDeploymentBeaconEntity::class.java,EntityDataSerializers.BOOLEAN)
        private val DEPLOYMENT_END=SynchedEntityData.defineId(EquipmentDeploymentBeaconEntity::class.java,EntityDataSerializers.LONG)
    }
}
