package com.example.sbwhelldiver.entity

import com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity
import com.example.sbwhelldiver.config.OrbitalFieldSettings
import com.example.sbwhelldiver.init.ModSounds
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.*

/** Direct collision is SBW's normal hit; canisters produce fields rather than a damaging blast. */
class OrbitalFieldShellEntity(t:EntityType<out SmallCannonShellEntity>,l:Level):OrbitalPrecisionShellEntity(t,l){
 var fieldSettings=OrbitalFieldSettings()
  private set
 init{configureField(fieldSettings)}
 fun configureField(s:OrbitalFieldSettings){fieldSettings=s;configure(s.direct,0f,0.0,0f)}
 override fun crushProjectile(velocity:Vec3){} // The six simultaneous canisters must not intercept one another.
 override fun causeExplode(vec3:Vec3){
  val server=level() as? ServerLevel?:return
  if(exploded)return
  exploded=true
  val hit=server.clip(ClipContext(vec3.add(0.0,.15,0.0),vec3.add(0.0,-64.0,0.0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this))
  val at=if(hit.type==HitResult.Type.BLOCK)hit.location else vec3
  val s=fieldSettings
  MortarFieldEntity.create(server,at,s.kind,s.radius,effectTicks=s.effectTicks,lifetimeTicks=s.lifetime)
  // EMS/Gas field audio is owned by MortarFieldAudio; smoke uses the shared canister impact.
  if(s.kind==MortarFieldEntity.Kind.SMOKE)server.playSound(null,vec3.x,vec3.y,vec3.z,ModSounds.EVENTS.getValue("sfx_eagle_smoke_impact").get(),SoundSource.HOSTILE,4f,1f)
 }
 override fun afterHitEntity(result:EntityHitResult){causeExplode(result.location);discard()}
 override fun afterHitBlock(result:BlockHitResult){causeExplode(result.location);discard()}
 override fun addAdditionalSaveData(t:CompoundTag){super.addAdditionalSaveData(t);t.put("OrbitalField",fieldSettings.save())}
 override fun readAdditionalSaveData(t:CompoundTag){super.readAdditionalSaveData(t);if(t.contains("OrbitalField"))configureField(OrbitalFieldSettings.load(t.getCompound("OrbitalField")))}
}
