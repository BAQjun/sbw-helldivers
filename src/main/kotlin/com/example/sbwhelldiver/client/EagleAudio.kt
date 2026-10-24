package com.example.sbwhelldiver.client
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.EagleEntity
import com.example.sbwhelldiver.entity.EagleFlight
import com.example.sbwhelldiver.init.ModSounds
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.lang.ref.WeakReference
import java.util.WeakHashMap
@Mod.EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object EagleAudio {
 private val played=WeakHashMap<EagleEntity,MutableSet<String>>()
 var flybys=0;private set
 var strafes=0;private set
 val allocated=mutableSetOf<String>()
 private val probes=mutableListOf<Pair<FlySound,Int>>()
 @SubscribeEvent fun tick(e:TickEvent.ClientTickEvent){
  if(e.phase!=TickEvent.Phase.END)return
  val mc=Minecraft.getInstance();val level=mc.level?:run{played.clear();probes.clear();return}
  for(p in level.entitiesForRendering().filterIsInstance<EagleEntity>()){
   val keys=played.getOrPut(p){mutableSetOf()}
   fun play(key:String){if(keys.add(key)){val sound=FlySound(p,key);mc.soundManager.play(sound);probes.add(sound to 0);if(key=="sfx_eagle_flyby")flybys++ else if(key=="sfx_eagle_strafe")strafes++}}
   if(p.age<EagleFlight.end(p.attack))play("sfx_eagle_engine")
   if(p.age in (EagleFlight.attackStart(p.attack)-25.0)..(EagleFlight.attackEnd(p.attack)+30.0) && (mc.player?.position()?.distanceToSqr(p.positionAt(p.age))?:Double.MAX_VALUE)<140.0*140.0)play("sfx_eagle_flyby")
   if(p.attack==com.example.sbwhelldiver.entity.EagleAttack.STRAFE&&p.age>=EagleFlight.attackStart(p.attack)&&p.age<EagleFlight.attackEnd(p.attack))play("sfx_eagle_strafe")
  }
  val it=probes.listIterator();while(it.hasNext()){val(s,t)=it.next();if(mc.soundManager.isActive(s))allocated.add(s.key);if(t>12)it.remove() else it.set(s to t+1)}
 }
 private class FlySound(entity:EagleEntity,val key:String):AbstractTickableSoundInstance(ModSounds.EVENTS.getValue(key).get(),SoundSource.PLAYERS,RandomSource.create()) {
  private val ref=WeakReference(entity)
  init {volume=when(key){"sfx_eagle_flyby"->3f;"sfx_eagle_engine"->1.5f;else->4f};looping=false;relative=false;x=entity.x;y=entity.y;z=entity.z}
  override fun tick(){
   val e=ref.get();if(e==null||e.isRemoved){stop();return}
   val at=e.positionAt(e.age);x=at.x;y=at.y;z=at.z
   val listener=Minecraft.getInstance().player?.eyePosition?:return
   val v=e.positionAt(e.age+1).subtract(at).scale(20.0);val radial=at.subtract(listener).normalize().dot(v)
   pitch=(343.0/(343.0+radial)).coerceIn(.8,1.2).toFloat()
  }
 }
}
