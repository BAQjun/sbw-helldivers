package com.example.sbwhelldiver.compat;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;

/** Do not load TACZ event signatures on installations without the optional mod. */
public final class TaczBootstrap {
    private TaczBootstrap() {}
    public static void register() {
        if (ModList.get().isLoaded("tacz")) MinecraftForge.EVENT_BUS.register(TaczGunEvents.class);
    }
}
