package com.example.sbwhelldiver.init

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.asset.AssetCatalog
import net.minecraft.sounds.SoundEvent
import net.neoforged.neoforge.registries.DeferredRegister
import net.minecraft.core.registries.BuiltInRegistries

object ModSounds {
    val SOUNDS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, MOD_ID)
    val EVENTS = AssetCatalog.audibleEffects.associateWith { effect ->
        SOUNDS.register(effect) { _ ->
            // Never broadcast less far than the selected sound can be heard on the client.
            SoundEvent.createFixedRangeEvent(AssetCatalog.sound(effect),AssetCatalog.soundRanges.getValue(effect))
        }
    }
}
