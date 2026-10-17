package com.example.sbwhelldiver.compat;

import com.example.sbwhelldiver.entity.HmgEmplacementEntity;
import com.example.sbwhelldiver.entity.PlayerHellpodEntity;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class TaczGunEvents {
    private TaczGunEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void shoot(GunShootEvent event) {
        if ((event.getShooter().getVehicle() instanceof HmgEmplacementEntity || event.getShooter().getVehicle() instanceof PlayerHellpodEntity)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void fire(GunFireEvent event) {
        if ((event.getShooter().getVehicle() instanceof HmgEmplacementEntity || event.getShooter().getVehicle() instanceof PlayerHellpodEntity)) event.setCanceled(true);
    }
}
