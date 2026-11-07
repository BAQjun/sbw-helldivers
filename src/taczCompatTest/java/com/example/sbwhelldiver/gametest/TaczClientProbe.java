package com.example.sbwhelldiver.gametest;

import com.example.sbwhelldiver.entity.AbstractHellpodEntity;
import com.example.sbwhelldiver.init.ModEntities;
import com.tacz.guns.util.InputExtraCheck;
import com.tacz.guns.client.event.CameraSetupEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.slf4j.LoggerFactory;

/** Runs in the prepared, self-cleaning port client scene, with the real client player. */
@EventBusSubscriber(modid="sbw_helldiver", value=Dist.CLIENT)
public final class TaczClientProbe {
    private static int ticks;
    private static boolean done;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("sbw_helldiver.taczCompatTests") || done) return;
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null || mc.screen!=null || ++ticks<120)return;
        for (String name : new String[]{"InputExtraCheck", "CameraSetupEvent", "RenderCrosshairEvent"}) {
            String pkg=name.equals("InputExtraCheck") ? "com.tacz.guns.util." : "com.tacz.guns.client.event.";
            if(java.util.Arrays.stream(Class.forName(pkg+name).getDeclaredMethods()).noneMatch(m->m.getName().contains("helldivers$")))
                throw new AssertionError("TaCZ client hook missing: "+name);
        }
        var player=mc.player;var old=player.position();
        var turret=ModEntities.INSTANCE.getHMG_EMPLACEMENT().get().create(mc.level);
        turret.setPos(old);
        CompoundTag data=new CompoundTag();turret.saveWithoutId(data);
        data.putInt("HellpodPhase",AbstractHellpodEntity.Phase.ACTIVE.ordinal());turret.load(data);
        try {
            if(!player.startRiding(turret,true))throw new AssertionError("Could not mount client fixture");
            if(InputExtraCheck.isInGame())throw new AssertionError("Mounted TaCZ inputs remain active");
            var fov=new ViewportEvent.ComputeFov(mc.gameRenderer,mc.gameRenderer.getMainCamera(),0,17,true);
            CameraSetupEvent.applyScopeMagnification(fov);
            if(fov.getFOV()!=17)throw new AssertionError("TaCZ modified mounted FOV");
            LoggerFactory.getLogger("sbw_helldiver").info("TACZ_120_CLIENT_PASSED input_camera_crosshair_hooks=true mounted_input_blocked=true mounted_fov_unchanged=true");
            done=true;
        } finally {player.stopRiding();player.setPos(old);turret.discard();}
    }
}
