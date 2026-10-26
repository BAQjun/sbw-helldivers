package com.example.sbwhelldiver.init

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.asset.AssetCatalog
import net.minecraft.sounds.SoundEvent
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries

object ModSounds {
    val SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MOD_ID)
    val EVENTS = AssetCatalog.audibleEffects.associateWith { effect ->
        SOUNDS.register(effect) {
            // Never broadcast less far than the selected sound can be heard on the client.
            SoundEvent.createFixedRangeEvent(AssetCatalog.sound(effect),AssetCatalog.soundRanges.getValue(effect))
        }
    }
}
