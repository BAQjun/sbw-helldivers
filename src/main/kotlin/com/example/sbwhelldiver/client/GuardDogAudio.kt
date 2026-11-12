package com.example.sbwhelldiver.client



import com.example.sbwhelldiver.MOD_ID

import com.example.sbwhelldiver.entity.GuardDogEntity

import com.example.sbwhelldiver.entity.GuardDogEntity.Flight

import com.example.sbwhelldiver.entity.GuardDogMotion

import com.example.sbwhelldiver.init.ModSounds

import net.minecraft.client.Minecraft

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance

import net.minecraft.sounds.SoundSource

import net.minecraft.util.RandomSource

import net.minecraftforge.api.distmarker.Dist

import net.minecraftforge.eventbus.api.SubscribeEvent

import net.minecraftforge.fml.common.Mod.EventBusSubscriber

import net.minecraftforge.event.TickEvent

import java.lang.ref.WeakReference

import java.util.WeakHashMap



@EventBusSubscriber(modid = MOD_ID, value = [Dist.CLIENT])

object GuardDogAudio {

    private val active = WeakHashMap<GuardDogEntity, List<Engine>>()

    internal var engineStarts = 0; private set

    internal var engineStops = 0; private set

    internal fun engineSounds(entity: GuardDogEntity): List<AbstractTickableSoundInstance> = active[entity].orEmpty()

    private val firing = WeakHashMap<GuardDogEntity, List<WeaponSound>>()

    internal fun firingSounds(entity: GuardDogEntity): List<AbstractTickableSoundInstance> = firing[entity].orEmpty()



    @SubscribeEvent

    fun tick(event: TickEvent.ClientTickEvent) {
        if(event.phase != TickEvent.Phase.END) return

        val mc = Minecraft.getInstance()

        val level = mc.level

        for ((entity, sounds) in firing.toMap()) {

            if (entity.level() == level && !entity.isRemoved && shooting(entity)) continue

            firing.remove(entity)

            sounds.forEach(mc.soundManager::stop)

            if (entity.isLaser && entity.level() == level && !entity.isRemoved)

                mc.soundManager.play(WeaponSound(entity, "rover_fire_stop", false))

        }

        for ((entity, sounds) in active.toMap()) {

            if (entity.level() == level && !entity.isRemoved && audible(entity)) continue

            active.remove(entity); sounds.forEach(mc.soundManager::stop); engineStops++

        }

        if (level == null) return

        for (entity in level.entitiesForRendering()) {

            if (entity !is GuardDogEntity) continue

            if (entity.isFlame) FlameSentryClient.tick(entity)

            if (entity.isGas) GasGuardDogVisual.tick(entity)

            if (shooting(entity) && entity !in firing) {

                val loops = if (entity.isGas) {

                    listOf(WeaponSound(entity, "gas_dog_spray", true))

                } else {

                    // The Scythe has four simultaneous loops with independent lengths.

                    mc.soundManager.play(WeaponSound(entity, "rover_fire_start", false))

                    (1..4).map { WeaponSound(entity, "rover_fire_loop_$it", true) }

                }

                firing[entity] = loops

                loops.forEach(mc.soundManager::play)

            }

            // The server emits guard_dog_ignition/shutdown once at the animation cues.

            // Native event 3935554741 pairs that ignition with just this motor loop.

            // Do not layer unrelated beam/weapon containers over flight audio.

            if (!audible(entity) || entity in active) continue

            val loops = listOf(Engine(entity))

            active[entity] = loops

            engineStarts++

            loops.forEach(mc.soundManager::play)

        }

    }



    private fun shooting(entity: GuardDogEntity) =

        entity.flight == Flight.PATROL && (entity.gasSpraying || entity.isLaser && entity.beamLength > 0f)



    private fun audible(entity: GuardDogEntity) = when (entity.flight) {

        Flight.LAUNCH -> entity.phaseAge >= GuardDogMotion.liftTick(entity.isEnergy) - 2

        Flight.DOCK -> entity.phaseAge < GuardDogMotion.contactTick(entity.isEnergy)

        else -> true

    }



    private class WeaponSound(entity: GuardDogEntity, key: String, repeat: Boolean) : AbstractTickableSoundInstance(

        ModSounds.EVENTS.getValue(key).get(), SoundSource.PLAYERS, RandomSource.create()) {

        private val drone = WeakReference(entity)

        init { looping = repeat; volume = .8f; updatePosition(entity) }

        private fun updatePosition(entity: GuardDogEntity) {

            val muzzle = if (entity.isGas) GuardDogVisual.firingMuzzle(entity, 1f) else entity.laserOrigin()

            x = muzzle.x; y = muzzle.y; z = muzzle.z

        }

        override fun tick() {

            val entity = drone.get()

            if (entity == null || entity.isRemoved || entity.level() != Minecraft.getInstance().level ||

                (looping && !shooting(entity))) { stop(); return }

            updatePosition(entity)

        }

    }



    private class Engine(entity: GuardDogEntity) : AbstractTickableSoundInstance(

        ModSounds.EVENTS.getValue("guard_dog_engine").get(), SoundSource.PLAYERS, RandomSource.create()) {

        private val drone = WeakReference(entity)

        init {

            looping = true

            volume = .06f

            x = entity.x

            y = entity.y + GuardDogEntity.MODEL_HEIGHT

            z = entity.z

        }



        override fun tick() {

            val entity = drone.get()

            if (entity == null || entity.isRemoved || entity.level() != Minecraft.getInstance().level) {

                stop()

                return

            }

            if (!audible(entity)) {

                stop()

                return

            }

            val thrust = kotlin.math.abs(entity.thrustAlong(entity.yRot, 1f)).coerceAtMost(1f)

            val gain = when (entity.flight) {

                Flight.LAUNCH -> ((entity.phaseAge - (GuardDogMotion.liftTick(entity.isEnergy) - 2f)) / 10f).coerceIn(0f, 1f)

                Flight.DOCK -> ((GuardDogMotion.contactTick(entity.isEnergy) - entity.phaseAge.toFloat()) / 12f).coerceIn(0f, 1f)

                else -> 1f

            }

            volume = (.28f + .06f * thrust) * gain

            val spool = if (entity.flight == Flight.DOCK) .78f + .22f * gain else .92f + .08f * gain

            pitch = spool + .10f * thrust

            x = entity.x

            y = entity.y + GuardDogEntity.MODEL_HEIGHT

            z = entity.z

        }

    }

}
