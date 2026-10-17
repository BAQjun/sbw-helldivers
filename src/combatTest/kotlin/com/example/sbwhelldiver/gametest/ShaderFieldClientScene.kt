package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.client.ShaderPackCompat
import com.example.sbwhelldiver.entity.MortarFieldEntity
import net.minecraft.client.*
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.*
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.*
import net.minecraftforge.fml.common.Mod
import java.nio.file.*
import kotlin.math.*

/** Live GPU regression: shader pack on, off and on again, outside and inside
 * both areas. Real gas/EMS shell impacts create continuous volumes for all camera views.
 */
@Mod.EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object ShaderFieldClientScene {
 private val enabled=java.lang.Boolean.getBoolean("sbw_helldiver.shaderFieldSmokeTest")
 private val out=Path.of(System.getProperty("sbw_helldiver.shaderFieldOutput","run-client-test/screenshots/shader-fields"))
 private var setup=false
 @Volatile private var ready=false
 @Volatile private var cleaned=false
 private var warmup=100
 private var step=0
 private var age=0
 private var wasDebug=false
 private var ending=false
 private val views=listOf("gas_outside","gas_side","gas_above","gas_inside","ems_outside","ems_inside")
 private val shots=mutableListOf<String>()
 private val gas=Vec3(244.5,65.0,224.5)
 private val ems=Vec3(268.5,65.0,224.5)
 private val fieldIds=java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
 private val restore=mutableMapOf<BlockPos,net.minecraft.world.level.block.state.BlockState>()
 private val cameraPosition by lazy{net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(Camera::class.java,"m_90581_",Vec3::class.java)}
 private fun command(l:ServerLevel,s:String)=l.server.commands.performPrefixedCommand(l.server.createCommandSourceStack().withPermission(4),s)
 private fun fields(l:ServerLevel){
  l.allEntities.filterIsInstance<MortarFieldEntity>().toList().forEach{it.discard()}
  for((center,type) in listOf(gas to com.example.sbwhelldiver.init.ModEntities.GAS_MORTAR_SHELL.get(),ems to com.example.sbwhelldiver.init.ModEntities.EMS_MORTAR_SHELL.get())) {
   val shell=type.create(l)!!;shell.setPos(center.add(0.0,12.0,0.0));shell.deltaMovement=Vec3(0.0,-1.0,0.0)
   shell.addTag("shader_field_qa");l.addFreshEntity(shell)
  }
 }
 private fun cleanup(l:ServerLevel){
  l.allEntities.filter{it is MortarFieldEntity||it.tags.contains("shader_field_qa")}.toList().forEach{it.discard()}
  for((p,state) in restore)l.setBlock(p,state,2)
  l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity::class.java,net.minecraft.world.phys.AABB(224.0,60.0,192.0,288.0,90.0,256.0)).forEach{it.discard()}
  command(l,"forceload remove 224 192 288 256");cleaned=true
 }
 @SubscribeEvent fun server(e:TickEvent.ServerTickEvent){
  if(!enabled||e.phase!=TickEvent.Phase.END)return
  val l=e.server.overworld()
  if(!setup&&l.server.playerList.players.isNotEmpty()){
   setup=true;Files.createDirectories(out)
   l.allEntities.filter{net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(it.type).namespace==MOD_ID||it.tags.contains("siege_film")}.toList().forEach{it.discard()}
   command(l,"forceload add 224 192 288 256");command(l,"gamerule doDaylightCycle false");command(l,"gamerule doMobSpawning false");command(l,"time set 18000");command(l,"weather clear")
   val p=l.server.playerList.players.first();p.setGameMode(GameType.SPECTATOR);p.teleportTo(l,256.0,67.0,208.0,0f,0f)
   for(center in listOf(gas,ems))for(i in -1..1){val mob=EntityType.ZOMBIE.create(l)!!;mob.moveTo(center.x+i*2,center.y,center.z+1,180f,0f);mob.isNoAi=true;mob.getAttribute(Attributes.MAX_HEALTH)?.baseValue=500.0;mob.health=500f;mob.addTag("shader_field_qa");l.addFreshEntity(mob)}
   // Remove the previous temporary wall; keep the real impact zones unobstructed.
   for(x in 247..249)for(y in 65..67)l.setBlock(BlockPos(x,y,220),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2)
   fields(l);ready=true
  }
  if(ready&&!ending)l.allEntities.filterIsInstance<MortarFieldEntity>().forEach{fieldIds.add(it.id)}
  if(ready&&!ending&&l.gameTime%80L==0L){
   l.allEntities.filterIsInstance<MortarFieldEntity>().forEach{field->
    val tag=net.minecraft.nbt.CompoundTag();field.saveWithoutId(tag);tag.putInt("Remaining",field.kind.lifetime);field.load(tag)
   }
   l.allEntities.filterIsInstance<net.minecraft.world.entity.LivingEntity>().filter{it.tags.contains("shader_field_qa")}.forEach{it.health=it.maxHealth}
  }
  if(ending&&!cleaned)cleanup(l)
 }
 private fun toggle(on:Boolean){
  val t=Class.forName("net.irisshaders.iris.api.v0.IrisApi");val api=t.getMethod("getInstance").invoke(null);val config=t.getMethod("getConfig").invoke(api)
  Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig").getMethod("setShadersEnabledAndApply",Boolean::class.javaPrimitiveType).invoke(config,on)
 }
 @SubscribeEvent fun client(e:TickEvent.ClientTickEvent){
  if(!enabled||e.phase!=TickEvent.Phase.END||!ready)return
  val mc=Minecraft.getInstance();mc.player?:return
  if(mc.screen!=null){mc.setScreen(null);mc.mouseHandler.grabMouse()}
  mc.options.hideGui=true;mc.options.cameraType=CameraType.FIRST_PERSON;mc.options.pauseOnLostFocus=false
  if(ending){if(cleaned){mc.options.hideGui=false;mc.stop()};return}
  if(warmup>0){warmup--;return}
  val debug=out.parent.resolve("volume-debug.txt")
  if(Files.exists(debug)){
   wasDebug=true
   val args=Files.readString(debug).trim().split(" ")
   step=args[0].toInt();System.setProperty("sbw_helldiver.volumeDebug",args.getOrElse(1){"0"})
   if(args.size>2){com.example.sbwhelldiver.client.FieldVolumeRenderer.requestReload();Files.writeString(debug,args.take(2).joinToString(" "))}
   age++;if(age%20==0){
    Files.writeString(out.resolve("debug-state.txt"),com.example.sbwhelldiver.client.FieldVolumeRenderer.diagnostics()+" step="+step)
    Screenshot.takeScreenshot(mc.mainRenderTarget).use{it.writeToFile(out.resolve("debug.png"))}
   }
   return
  }
  if(wasDebug){wasDebug=false;step=0;age=0;warmup=20;return}
  System.setProperty("sbw_helldiver.volumeDebug","0")
  val pack=step<views.size||step>=views.size*2
  check(ShaderPackCompat.active()==pack){"Wrong shader-pack detection at step $step"}
  age++
  if(age==40){
   check(com.example.sbwhelldiver.client.FieldVolumeRenderer.isReady()&&com.example.sbwhelldiver.client.FieldVolumeRenderer.renderedFrames()>0){"Volume pass failed"}
   val label=(if(pack)if(step<views.size)"shader" else "reenabled" else "vanilla")+"_"+views[step%views.size]
   Screenshot.takeScreenshot(mc.mainRenderTarget).use{it.writeToFile(out.resolve(label+".png"))};shots.add(label)
  }
  if(age>=60){step++;age=0
   if(step==views.size){toggle(false);warmup=70}
   if(step==views.size*2){toggle(true);warmup=70}
   if(step==views.size*2+1){
    check(shots.size==views.size*2+1&&ShaderPackCompat.active()&&fieldIds.size==2)
    Files.writeString(out.resolve("result.json"),"{\"shader_on_off_on\":true,\"captures\":13,\"pack_active_at_end\":true,\"native_impacts\":2,\"continuous_field_entities\":true}");ending=true
   }
  }
 }
 @SubscribeEvent(priority=EventPriority.LOWEST) fun camera(e:ViewportEvent.ComputeCameraAngles){
  if(!enabled||!ready)return
  val view=step%views.size
  val center=if(view<4)gas else ems
  val eye=when(view){
   0,4->center.add(-9.0,2.0,-11.0)
   1->center.add(-11.0,1.4,1.0)
   2->center.add(1.0,13.0,-1.0)
   else->center.add(-2.5,1.2,-2.0)
  }
  val look=if(view==3||view==5)eye.add(.2,.02,7.0) else center.add(0.0,1.1,0.0)
  cameraPosition.invoke(e.camera,eye);val d=look.subtract(eye);e.yaw=Math.toDegrees(atan2(-d.x,d.z)).toFloat();e.pitch=-Math.toDegrees(atan2(d.y,d.horizontalDistance())).toFloat();e.roll=0f
 }
 @SubscribeEvent fun stopping(e:net.minecraftforge.event.server.ServerStoppingEvent){if(enabled&&setup)cleanup(e.server.overworld())}
 @SubscribeEvent fun fov(e:ViewportEvent.ComputeFov){if(enabled&&ready)e.fov=65.0}
 @SubscribeEvent fun hand(e:RenderHandEvent){if(enabled)e.isCanceled=true}
}
