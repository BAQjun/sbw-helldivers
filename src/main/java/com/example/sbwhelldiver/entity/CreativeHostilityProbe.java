package com.example.sbwhelldiver.entity;

import java.util.function.BooleanSupplier;
import net.minecraft.world.entity.player.Player;

public final class CreativeHostilityProbe {
    private static final ThreadLocal<Player> DEPLOYER = new ThreadLocal<>();

    private CreativeHostilityProbe() {}

    public static boolean test(Player deployer, BooleanSupplier eligibility) {
        Player previous = DEPLOYER.get();
        DEPLOYER.set(deployer);
        try {
            return eligibility.getAsBoolean();
        } finally {
            if (previous == null) {
                DEPLOYER.remove();
            } else {
                DEPLOYER.set(previous);
            }
        }
    }

    public static boolean isProbing(Player player) {
        return DEPLOYER.get() == player;
    }
}
