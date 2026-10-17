package com.example.sbwhelldiver.client
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.asset.AssetCatalog
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvent
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

/** Original ui_mission event variants; UI feedback is relative, with no distance attenuation. */
@Mod.EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object MarkAudio {
 private val effects=listOf("sfx_mark_enemy","sfx_mark_generic","sfx_mark_location","sfx_mark_cancel")
 private data class Probe(val kind:Int,val sound:SimpleSoundInstance,var ticks:Int=0)
 private val probes=mutableListOf<Probe>()
 val started=IntArray(4)
 val allocated=mutableSetOf<Int>()
 fun play(kind:Int){
  if(kind !in effects.indices)return
  val mc=Minecraft.getInstance();val sound=SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(AssetCatalog.sound(effects[kind])),1f)
  mc.soundManager.play(sound)
  if(java.lang.Boolean.getBoolean("sbw_helldiver.markSmokeTest")){started[kind]++;probes.add(Probe(kind,sound))}
 }
 @SubscribeEvent fun tick(e:TickEvent.ClientTickEvent){
  if(e.phase!=TickEvent.Phase.END||probes.isEmpty())return
  val manager=Minecraft.getInstance().soundManager
  probes.forEach {it.ticks++;if(it.ticks in 2..8&&manager.isActive(it.sound))allocated.add(it.kind)}
  probes.removeAll {it.ticks>10}
 }
}
