package com.example.sbwhelldiver.client

import com.atsuishio.superbwarfare.client.lighting.MuzzleFlashHelper
import com.example.sbwhelldiver.entity.AbstractHellpodEntity
import com.example.sbwhelldiver.entity.FlameClock
import com.example.sbwhelldiver.entity.HmgEmplacementEntity
import com.example.sbwhelldiver.entity.SentryHellpodEntity
import java.util.WeakHashMap

/** Custom firing skips SBW's ClientVehicleFireEvent; publish real world light explicitly. */
object WeaponLighting {
    private val serials=WeakHashMap<AbstractHellpodEntity,Int>()
    private val shot=MuzzleFlashHelper.FlashParams(15,10,3)
    private val flame=MuzzleFlashHelper.FlashParams(15,11,3)
    fun tick(entity:AbstractHellpodEntity) {
        val previous=serials.put(entity,entity.shotSerial)
        if(entity.phase!=AbstractHellpodEntity.Phase.ACTIVE||entity.isWreck||entity.isRemoved)return
        when {
            entity is SentryHellpodEntity && entity.profile.isFlame -> {
                if(entity.flameState==FlameClock.State.SPRAYING && entity.tickCount%2==0) {
                    val tip=entity.getShootPos("Main",1f);val axis=entity.getShootVec("Main",1f).normalize()
                    MuzzleFlashHelper.spawnFlashCone(tip,axis,flame)
                    MuzzleFlashHelper.spawnFlashCone(tip.add(axis.scale(2.5)),axis,flame)
                }
            }
            previous!=null && entity.shotSerial>previous -> {
                if(entity is SentryHellpodEntity && (entity.profile.id=="machine_gun_sentry"||entity.profile.isGatling))
                    MuzzleFlashHelper.spawnFlashCone(entity.getShootPos("Main",1f),entity.getShootVec("Main",1f),shot)
                else if(entity is HmgEmplacementEntity && entity.profile.id=="hmg_emplacement") {
                    repeat((entity.shotSerial-previous).coerceAtMost(4)) {i->
                        val barrel=(previous+i) and 1
                        MuzzleFlashHelper.spawnFlashCone(entity.muzzlePosition(barrel),entity.firingDirection(barrel),shot)
                    }
                }
            }
        }
    }
}
