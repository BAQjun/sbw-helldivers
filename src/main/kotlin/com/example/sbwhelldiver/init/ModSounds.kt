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
            if (effect == "beacon_deploy") SoundEvent.createFixedRangeEvent(AssetCatalog.sound(effect), 64f)
            else if (effect == "sfx_ap_mine_explosion_1") SoundEvent.createFixedRangeEvent(AssetCatalog.sound(effect), 64f)
            else if (effect == "sfx_gas_mortar_loop") SoundEvent.createFixedRangeEvent(AssetCatalog.sound(effect), 32f)
            else if (effect.startsWith("sfx_gas_mortar_") || effect.startsWith("sfx_ems_mortar_"))
                SoundEvent.createFixedRangeEvent(AssetCatalog.sound(effect), 96f)
            else SoundEvent.createVariableRangeEvent(AssetCatalog.sound(effect))
        }
    }
}
