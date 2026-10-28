package com.example.sbwhelldiver.config

import com.example.sbwhelldiver.entity.MortarFieldEntity
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.common.ModConfigSpec
import kotlin.math.roundToInt

/** Field and flight parameters are captured once per call and survive saving/loading. */
data class OrbitalFieldSettings(val id:String="orbital_gas_strike",val radius:Double=15.0,
 val lifetime:Int=300,val effectTicks:Int=120,val shells:Int=1,val spread:Double=0.0,
 val callTicks:Int=40,val flightTicks:Int=30,val direct:Float=50f){
 val kind get()=when(id){"orbital_ems_strike"->MortarFieldEntity.Kind.EMS;"orbital_smoke_strike"->MortarFieldEntity.Kind.SMOKE;else->MortarFieldEntity.Kind.GAS}
 fun delivery()=BarrageSettings(kind=id,rounds=1,shots=shells,shotGap=0,roundPause=0,radius=spread,callTicks=callTicks,flightTicks=flightTicks,direct=direct,blast=0f,inner=0.0,outer=0f)
 fun save()=CompoundTag().also{it.putString("Id",id);it.putDouble("Radius",radius);it.putInt("Life",lifetime);it.putInt("Effect",effectTicks);it.putInt("Shells",shells);it.putDouble("Spread",spread);it.putInt("Call",callTicks);it.putInt("Flight",flightTicks);it.putFloat("Direct",direct)}
 companion object{
  val ids=listOf("orbital_gas_strike","orbital_ems_strike","orbital_smoke_strike")
  fun defaults(id:String)=when(id){ids[1]->OrbitalFieldSettings(id=id,radius=13.0,effectTicks=60);ids[2]->OrbitalFieldSettings(id=id,radius=12.0,lifetime=600,effectTicks=0,shells=6,spread=15.0);else->OrbitalFieldSettings()}
  fun load(t:CompoundTag):OrbitalFieldSettings{
   val d=defaults(t.getString("Id"));if(!t.contains("Life"))return d
   return OrbitalFieldSettings(d.id,t.getDouble("Radius").coerceIn(.1,16.0),t.getInt("Life").coerceIn(1,72000),t.getInt("Effect").coerceIn(0,1200),t.getInt("Shells").coerceIn(1,20),t.getDouble("Spread").coerceIn(0.0,128.0),t.getInt("Call").coerceIn(10,1200),t.getInt("Flight").coerceIn(10,200),t.getFloat("Direct").coerceIn(0f,100000f))
  }
 }
}
object OrbitalFieldConfig{
 private class Entry(b:ModConfigSpec.Builder,val d:OrbitalFieldSettings){
  val radius=b.comment("命中后的区域半径。Radius of each impact field.").defineInRange("fieldRadius",d.radius,.1,16.0)
  val life=b.comment("区域持续时间；不会修改其它来源的区域。Field duration, independent of other field sources.").defineInRange("fieldSeconds",d.lifetime/20.0,.05,3600.0)
  val effect=if(d.effectTicks>0)b.comment("持续刷新异常状态的时间。Refreshed status duration.").defineInRange("effectSeconds",d.effectTicks/20.0,.05,60.0)else null
  val shells=b.defineInRange("shells",d.shells,1,20)
  val spread=b.comment("落点围绕信标随机分布的半径。Random impact radius around the beacon.").defineInRange("spreadRadius",d.spread,0.0,128.0)
  val call=b.defineInRange("firingDelaySeconds",d.callTicks/20.0,.5,60.0)
  val flight=b.defineInRange("flightSeconds",d.flightTicks/20.0,.5,10.0)
  val damage=b.defineInRange("directDamage",d.direct.toDouble(),0.0,100000.0)
  fun snapshot()=OrbitalFieldSettings(d.id,radius.get(),(life.get()*20).roundToInt(),effect?.let{(it.get()*20).roundToInt()}?:0,shells.get(),spread.get(),(call.get()*20).roundToInt(),(flight.get()*20).roundToInt(),damage.get().toFloat())
 }
 private val entries=linkedMapOf<String,Entry>()
 fun register(b:ModConfigSpec.Builder){
  for((id,name)in OrbitalFieldSettings.ids.zip(listOf("gasStrike","emsStrike","smokeStrike"))){b.push(name);entries[id]=Entry(b,OrbitalFieldSettings.defaults(id));b.pop()}
 }
 fun snapshot(id:String)=entries.getValue(id).snapshot()
}
