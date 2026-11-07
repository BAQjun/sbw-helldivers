package com.example.sbwhelldiver.compat;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.ModList;

/** Do not load TACZ event signatures on installations without the optional mod. */
public final class TaczBootstrap {
    private TaczBootstrap() {}
    public static void register() {
        if (ModList.get().isLoaded("tacz")) NeoForge.EVENT_BUS.register(TaczGunEvents.class);
    }
}
