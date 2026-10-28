package com.example.sbwhelldiver.config

import net.minecraft.nbt.CompoundTag
import net.minecraftforge.common.ForgeConfigSpec
import kotlin.math.roundToInt

/** Immutable call snapshot. Round pause starts after the preceding round's last shell. */
data class BarrageSettings(val kind:String="orbital_120mm_he_barrage",val rounds:Int=5,val shots:Int=3,
 val shotGap:Int=15,val roundPause:Int=40,val radius:Double=27.0,val advanceSpeed:Double=0.0,
 val callTicks:Int=100,val flightTicks:Int=30,val direct:Float=560f,val blast:Float=200f,
 val inner:Double=3.3,val outer:Float=10f,val fireRadius:Double=0.0,val fireTicks:Int=0){
 val napalm get()=kind=="orbital_napalm_barrage"
 val total get()=rounds*shots
 fun shotTime(index:Int)=(index/shots)*(shotGap*(shots-1)+roundPause)+(index%shots)*shotGap
 val lastShotTime get()=shotTime(total-1)
 val walking get()=kind=="orbital_walking_barrage"
 fun save()=CompoundTag().also{
  it.putString("Kind",kind);it.putInt("Rounds",rounds);it.putInt("Shots",shots);it.putInt("ShotGap",shotGap);it.putInt("RoundPause",roundPause)
  it.putDouble("Radius",radius);it.putDouble("Speed",advanceSpeed);it.putInt("Call",callTicks);it.putInt("Flight",flightTicks)
  it.putFloat("Direct",direct);it.putFloat("Blast",blast);it.putDouble("Inner",inner);it.putFloat("Outer",outer);it.putDouble("FireRadius",fireRadius);it.putInt("FireTicks",fireTicks)
 }
 companion object{
  val ids=listOf("orbital_120mm_he_barrage","orbital_380mm_he_barrage","orbital_walking_barrage","orbital_napalm_barrage")
  fun defaults(id:String)=when(id){
   ids[1]->BarrageSettings(kind=id,shotGap=30,roundPause=60,radius=36.0,callTicks=120,direct=640f,blast=240f,inner=4.0,outer=12f)
   ids[2]->BarrageSettings(kind=id,shotGap=30,roundPause=60,radius=25.0,advanceSpeed=3.0,callTicks=60,direct=640f,blast=240f,inner=4.0,outer=12f)
   ids[3]->BarrageSettings(kind=id,rounds=6,shots=5,shotGap=10,roundPause=40,radius=25.0,direct=80f,blast=60f,fireRadius=10.0,fireTicks=com.example.sbwhelldiver.entity.GroundFireEntity.NAPALM_LIFETIME)
   else->BarrageSettings()
  }
  fun load(t:CompoundTag):BarrageSettings{
   val id=t.getString("Kind");val d=if(id in OrbitalFieldSettings.ids)OrbitalFieldSettings.defaults(id).delivery()else defaults(id);if(!t.contains("Rounds"))return d
   return BarrageSettings(d.kind,t.getInt("Rounds").coerceIn(1,20),t.getInt("Shots").coerceIn(1,20),t.getInt("ShotGap").coerceIn(if(d.kind in OrbitalFieldSettings.ids)0 else 1,1200),t.getInt("RoundPause").coerceIn(0,1200),
    t.getDouble("Radius").coerceIn(0.0,128.0),t.getDouble("Speed").coerceIn(0.0,20.0),t.getInt("Call").coerceIn(10,1200),t.getInt("Flight").coerceIn(10,200),
    t.getFloat("Direct").coerceIn(0f,100000f),t.getFloat("Blast").coerceIn(0f,100000f),t.getDouble("Inner").coerceIn(0.0,128.0),t.getFloat("Outer").coerceIn(if(d.kind in OrbitalFieldSettings.ids)0f else .1f,256f),
    if(t.contains("FireRadius"))t.getDouble("FireRadius").coerceIn(0.0,16.0)else d.fireRadius,if(t.contains("FireTicks"))t.getInt("FireTicks").coerceIn(0,72000)else d.fireTicks)
  }
 }
}
object OrbitalBarrageConfig{
 private class Entry(b:ForgeConfigSpec.Builder,val d:BarrageSettings){
  val rounds=b.defineInRange("rounds",d.rounds,1,20)
  val shots=b.defineInRange("shotsPerRound",d.shots,1,20)
  val gap=b.defineInRange("shotIntervalSeconds",d.shotGap/20.0,.05,60.0)
  val pause=b.comment("上一轮末发至下一轮首发的停顿。Pause from the last shell of a salvo to the first of the next.").defineInRange("roundPauseSeconds",d.roundPause/20.0,0.0,60.0)
  val radius=b.defineInRange("spreadRadius",d.radius,0.0,128.0)
  val speed=if(d.walking)b.comment("原版 BombardmentComponentData.speed=3；沿投掷点至落点方向。Native speed=3, along the throw-to-beacon direction.").defineInRange("advanceSpeed",d.advanceSpeed,0.0,20.0)else null
  val call=b.defineInRange("firingDelaySeconds",d.callTicks/20.0,.5,60.0)
  val flight=b.defineInRange("flightSeconds",d.flightTicks/20.0,.5,10.0)
  val direct=if(d.kind==BarrageSettings.ids[0]||d.napalm)b.defineInRange("directDamage",d.direct.toDouble(),0.0,100000.0)else null
  val blast=if(direct!=null)b.defineInRange("explosionDamage",d.blast.toDouble(),0.0,100000.0)else null
  val inner=if(direct!=null)b.defineInRange("innerRadius",d.inner,0.0,128.0)else null
  val outer=if(direct!=null)b.defineInRange("outerRadius",d.outer.toDouble(),.1,256.0)else null
  val fireRadius=if(d.napalm)b.comment("贴地火焰外半径，内外圈分批熄灭。Ground-fire outer radius; rings extinguish from outside inward.").defineInRange("groundFireRadius",d.fireRadius,.1,16.0)else null
  val fireLife=if(d.napalm)b.comment("内圈最长燃烧时间；外圈更短。Maximum inner-ring lifetime; outer rings burn for less time.").defineInRange("groundFireSeconds",d.fireTicks/20.0,.05,3600.0)else null
  fun snapshot()=BarrageSettings(d.kind,rounds.get(),shots.get(),(gap.get()*20).roundToInt(),(pause.get()*20).roundToInt(),radius.get(),speed?.get()?:0.0,
   (call.get()*20).roundToInt(),(flight.get()*20).roundToInt(),(direct?.get()?:OrbitalConfig.direct.get()).toFloat(),(blast?.get()?:OrbitalConfig.blast.get()).toFloat(),inner?.get()?:OrbitalConfig.inner.get(),(outer?.get()?:OrbitalConfig.outer.get()).toFloat(),fireRadius?.get()?:0.0,fireLife?.get()?.let{(it*20).roundToInt()}?:0)
 }
 private val entries=linkedMapOf<String,Entry>()
 fun register(b:ForgeConfigSpec.Builder){
  for((id,section)in BarrageSettings.ids.zip(listOf("he120mmBarrage","he380mmBarrage","walkingBarrage","napalmBarrage"))){
   b.comment("轨道炮击；380mm与徐进共用precisionStrike弹药伤害。380mm and Walking Barrage share precisionStrike ammunition damage.").push(section)
   entries[id]=Entry(b,BarrageSettings.defaults(id));b.pop()
  }
 }
 fun snapshot(id:String)=entries.getValue(id).snapshot()
}
