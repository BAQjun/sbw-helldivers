package com.example.sbwhelldiver.client
import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RenderGuiEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.math.*
/** Opt-in, isolated flat testing scene. Disabled in delivered gameplay. */
@Mod.EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object ElementalMineClientScene {
    private val enabled=java.lang.Boolean.getBoolean("sbw_helldiver.elementalSmokeTest")
    private val out by lazy{Path.of(System.getProperty("sbw_helldiver.elementalCapture"))}
    private val firePod=Vec3(1610.5,257.0,1610.5)
    private val gasPod=Vec3(1660.5,257.0,1610.5)
    private val fireFx=Vec3(1610.5,257.0,1660.5)
    private val gasFx=Vec3(1640.5,257.0,1660.5)
    private var launched=false
    @Volatile private var ready=false
    @Volatile private var stage=0
    @Volatile private var started=0L
    @Volatile private var focus=firePod.add(0.0,1.2,0.0)
    @Volatile private var podId:UUID?=null
    @Volatile private var finished=false
    private var ticks=0
    private var flameId:UUID?=null
    private var flameTargetId:UUID?=null
    private val captures=mutableSetOf<String>()
    private val counts=mutableListOf<Int>()
    private fun camera(l:ServerLevel,at:Vec3,target:Vec3) {
        focus=target;val p=l.server.playerList.players.first();val d=target.subtract(at.add(0.0,p.eyeHeight.toDouble(),0.0))
        p.teleportTo(l,at.x,at.y,at.z,Math.toDegrees(atan2(-d.x,d.z)).toFloat(),-Math.toDegrees(atan2(d.y,d.horizontalDistance())).toFloat())
    }
    private fun drop(l:ServerLevel,id:String,at:Vec3) {
        podId=null;val m=ModEntities.STRATAGEM_MARKER.get().create(l)!!;m.moveTo(at.add(0.0,.01,0.0));m.configure(ResourceLocation(MOD_ID,id),60,l.server.playerList.players.first().uuid,Direction.UP,257.0);l.addFreshEntity(m)
    }
    private fun state(l:ServerLevel,n:Int){stage=n;started=l.gameTime}
    @SubscribeEvent fun client(e:TickEvent.ClientTickEvent) {
        if(!enabled||e.phase!=TickEvent.Phase.END)return
        val mc=Minecraft.getInstance();val p=mc.player?:return;val server=mc.singleplayerServer?:return
        mc.options.pauseOnLostFocus=false;if(mc.overlay!=null||mc.isPaused)return
        if(!launched) {
            launched=true;Files.createDirectories(out.resolve("screenshots"));mc.options.hideGui=false;mc.options.gamma().set(1.0);mc.options.fov().set(62);mc.options.guiScale().set(2);mc.options.framerateLimit().set(60);mc.resizeDisplay()
            server.execute {
                val l=server.overworld();for(x in 98..106)for(z in 98..106){l.setChunkForced(x,z,true);l.getChunk(x,z)}
                l.getEntities(null,AABB(1570.0,250.0,1570.0,1690.0,320.0,1690.0)){it !is Player}.forEach{it.discard()}
                l.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);l.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,server);l.dayTime=6000;l.setWeatherParameters(100000,0,false,false)
                for(c in listOf(firePod,gasPod,fireFx,gasFx))for(x in -22..22)for(z in -22..22) {
                    val block=if(x%5==0||z%5==0)Blocks.GRAY_CONCRETE else Blocks.LIGHT_GRAY_CONCRETE
                    l.setBlock(BlockPos.containing(c.add(x.toDouble(),-1.0,z.toDouble())),block.defaultBlockState(),2)
                }
                val observer=server.playerList.getPlayer(p.uuid)!!;observer.setGameMode(GameType.SPECTATOR);camera(l,firePod.add(4.0,2.0,-5.0),firePod.add(0.0,1.3,0.0));if(java.lang.Boolean.getBoolean("sbw_helldiver.elementalEffectsOnly")) {
                    val mine=ModEntities.INCENDIARY_MINE.get().create(l)!!;mine.setPos(fireFx);l.addFreshEntity(mine);mine.detonate();camera(l,fireFx.add(6.0,1.2,-7.0),fireFx.add(0.0,.5,0.0));state(l,3)
                } else {drop(l,"incendiary_minefield",firePod);state(l,1)}
                ready=true
                LOGGER.info("ELEMENTAL_SCENE_READY four separated boards, spectator, grid=5m")
            }
        }
        if(!ready)return;ticks++
        val d=focus.subtract(p.eyePosition);p.yRot=Math.toDegrees(atan2(-d.x,d.z)).toFloat();p.yRotO=p.yRot;p.xRot=-Math.toDegrees(atan2(d.y,d.horizontalDistance())).toFloat();p.xRotO=p.xRot
        if(finished) {
            check(AreaEffectRenderTypes.isReady()&&captures.containsAll(if(java.lang.Boolean.getBoolean("sbw_helldiver.elementalEffectsOnly")) listOf("ground_fire","gas_cloud","gas_inside","status_icons") else listOf("fire_open","gas_open","ground_fire","gas_cloud","gas_inside","status_icons"))) { "Missing shader or capture: $captures" }
            if(java.lang.Boolean.getBoolean("sbw_helldiver.effects0336"))check(captures.containsAll(listOf("ems_field","flame_jet"))) { "Updated effects did not render: $captures" }
            Files.writeString(out.resolve("client_passed.txt"),"payload_counts=$counts\nshaders_ready=${AreaEffectRenderTypes.isReady()}\nimages=$captures\n")
            LOGGER.info("ELEMENTAL_SCENE_CLIENT_PASSED counts={} shaders=true images={}",counts,captures);mc.stop()
        }
        if(ticks>1900)error("Elemental scene timed out stage=$stage")
    }
    @SubscribeEvent fun server(e:TickEvent.ServerTickEvent) {
        if(!enabled||!ready||finished||e.phase!=TickEvent.Phase.END)return
        val l=e.server.overworld();val age=l.gameTime-started
        if(stage<=2) {
            val center=if(stage==1)firePod else gasPod
            val pod=l.getEntitiesOfClass(MineDispenserEntity::class.java,AABB(center,center).inflate(5.0)).firstOrNull()
            podId=pod?.uuid
            if(pod!=null&&pod.phase==AbstractHellpodEntity.Phase.ACTIVE&&pod.spreadAge>=60&&age>=300) {
                val mines=l.getEntitiesOfClass(AntiPersonnelMineEntity::class.java,AABB(center,center).inflate(22.0)){it.dispenserId==pod.uuid}
                if(mines.size!=48||mines.any{!it.settled||!it.armed})return
                counts.add(mines.size)
                if(stage==1){camera(l,gasPod.add(4.0,2.0,-5.0),gasPod.add(0.0,1.3,0.0));drop(l,"gas_minefield",gasPod);state(l,2)}
                else {
                    val target=EntityType.COW.create(l)!!;target.isNoAi=true;target.setPos(fireFx.add(.5,0.0,0.0));target.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=500.0;target.health=500f;l.addFreshEntity(target)
                    val m=ModEntities.INCENDIARY_MINE.get().create(l)!!;m.setPos(fireFx);l.addFreshEntity(m);m.hurt(l.damageSources().generic(),1f)
                    camera(l,fireFx.add(6.0,1.2,-7.0),fireFx.add(0.0,.5,0.0));state(l,3)
                }
            }
        } else if(stage==3&&age>150) {
            val target=EntityType.COW.create(l)!!;target.isNoAi=true;target.setPos(gasFx.add(1.0,0.0,0.0));target.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=500.0;target.health=500f;l.addFreshEntity(target)
            val m=ModEntities.GAS_MINE.get().create(l)!!;m.setPos(gasFx);l.addFreshEntity(m);m.detonate();camera(l,gasFx.add(7.0,1.5,-8.0),gasFx.add(0.0,.9,0.0));state(l,4)
        } else if(stage==4&&age>100){camera(l,gasFx.add(.0,.0,-1.0),gasFx.add(0.0,1.4,5.0));state(l,5)}
        else if(stage==5&&age>70) {
            val player=e.server.playerList.players.first();player.setGameMode(GameType.CREATIVE);camera(l,gasFx.add(10.0,2.0,-9.0),gasFx.add(0.0,1.0,0.0))
            for(effect in listOf(ModEffects.GAS,ModEffects.CONFUSION,ModEffects.BURNING,ModEffects.PANIC,ModEffects.STUN))player.addEffect(MobEffectInstance(effect.get(),200,0,false,true,true));state(l,6)
        } else if(stage==6&&age>80) {
            e.server.playerList.players.first().removeAllEffects()
            if(java.lang.Boolean.getBoolean("sbw_helldiver.effects0336")) {
                e.server.playerList.players.first().setGameMode(GameType.SPECTATOR);MortarFieldEntity.create(l,firePod,MortarFieldEntity.Kind.EMS);camera(l,firePod.add(10.0,2.0,-11.0),firePod.add(0.0,1.1,0.0));state(l,7)
            }else finished=true
        } else if(stage==7&&age>90) {
            val sentry=ModEntities.FLAME_SENTRY.get().create(l)!!
            val at=gasPod.add(0.0,-com.example.sbwhelldiver.config.HelldiversConfig.effectiveBurialDepth()+sentry.profile.podHeight,0.0);sentry.setPos(at)
            val tag=CompoundTag();sentry.saveWithoutId(tag);tag.putInt("HellpodPhase",AbstractHellpodEntity.Phase.DEPLOYING.ordinal);tag.putInt("HellpodPhaseTicks",sentry.deployTicks-1);tag.putDouble("HellpodRestY",at.y);tag.putBoolean("HellpodCapReleased",true);sentry.load(tag);l.addFreshEntity(sentry);flameId=sentry.uuid
            val target=EntityType.ZOMBIE.create(l)!!;target.isNoAi=true;target.setPos(gasPod.add(0.0,0.0,18.0));target.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=2000.0;target.health=2000f;l.addFreshEntity(target);flameTargetId=target.uuid
            camera(l,gasPod.add(20.0,3.5,13.0),gasPod.add(0.0,2.0,13.0));state(l,8)
        }else if(stage==8&&age>345)finished=true
        if(stage==8 && age==170L)camera(l,gasPod.add(-20.0,3.5,13.0),gasPod.add(0.0,2.0,13.0))
        if(stage==8 && age==230L)camera(l,gasPod.add(4.0,11.0,-3.0),gasPod.add(8.0,1.5,12.0))
        if(stage==8 && age==290L)camera(l,gasPod.add(-3.0,3.0,-5.0),gasPod.add(12.0,1.5,18.0))
        if(stage==8 && age==90L)l.getEntity(flameTargetId?:UUID.randomUUID())?.teleportTo(gasPod.x+12.0,gasPod.y,gasPod.z+18.0)
        if(stage==8 && age==90L) LOGGER.info("FLAME_EFFECT_SCENE phase={} state={} ammo={}",(l.getEntity(flameId?:UUID.randomUUID()) as? SentryHellpodEntity)?.phase,(l.getEntity(flameId?:UUID.randomUUID()) as? SentryHellpodEntity)?.flameState,(l.getEntity(flameId?:UUID.randomUUID()) as? SentryHellpodEntity)?.getGunData("Main")?.ammo?.get())
    }
    private fun capture(name:String){if(captures.add(name))Screenshot.grab(out.toFile(),name+".png",Minecraft.getInstance().mainRenderTarget){}}
    @SubscribeEvent fun render(e:RenderGuiEvent.Post) {
        if(!enabled||!ready||finished)return
        val mc=Minecraft.getInstance();val age=(mc.level?.gameTime?:started)-started
        val pod=mc.level?.entitiesForRendering()?.filterIsInstance<MineDispenserEntity>()?.firstOrNull{it.uuid==podId}
        if(stage<=2&&pod!=null) {
            val prefix=if(stage==1)"fire" else "gas"
            if(pod.phase==AbstractHellpodEntity.Phase.DEPLOYING&&pod.phaseAge in 28..35)capture(prefix+"_open")
            if(pod.spinning&&pod.spreadAge in 5..15)capture(prefix+"_spread")
            if(pod.phase==AbstractHellpodEntity.Phase.ACTIVE&&pod.spreadAge>=55)capture(prefix+"_deployed")
        }
        if(stage==3&&age>75)capture("ground_fire")
        if(stage==4&&age>60)capture("gas_cloud")
        if(stage==5&&age>25)capture("gas_inside")
        if(stage==6) {mc.options.hideGui=false;if(age>25)capture("status_icons")}
        if(stage==7&&age>30)capture("ems_field")
        if(stage==8) {
            val s=mc.level?.getEntity(mc.level?.entitiesForRendering()?.firstOrNull{it.uuid==flameId}?.id?:-1) as? SentryHellpodEntity
            if(s?.flameState==FlameClock.State.SPRAYING && age>50) {
                capture("flame_jet")
                if(age in 196..225)capture("flame_jet_reverse")
                if(age in 250..275)capture("flame_jet_above")
                if(age in 315..340)capture("flame_jet_axial")
                if(age in 104..110)capture("flame_jet_sweep")
                if(age in 60..139)capture("jet_motion_"+(age-60).toString().padStart(3,'0'))
            }
        }
    }
}
