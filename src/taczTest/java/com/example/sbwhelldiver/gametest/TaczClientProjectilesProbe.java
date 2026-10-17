package com.example.sbwhelldiver.gametest;

import com.example.sbwhelldiver.compat.TaczBulletAccess;
import com.example.sbwhelldiver.entity.ShieldGeneratorEntity;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage.DistanceDamagePair;
import com.tacz.guns.util.InputExtraCheck;
import net.minecraft.client.Minecraft;
import com.example.sbwhelldiver.init.ModEntities;
import com.example.sbwhelldiver.entity.AbstractHellpodEntity;
import net.minecraft.nbt.CompoundTag;
import com.tacz.guns.client.event.CameraSetupEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

/** Adds real TACZ network-spawned bullets to the existing self-cleaning shield scene. */
public final class TaczClientProjectilesProbe {
    private static final boolean ENABLED = Boolean.getBoolean("sbw_helldiver.shieldSmokeTest");
    private static EntityKineticBullet pending;
    private static int absorbed;
    private static int clientBullets;
    private static boolean reported;

    @SubscribeEvent public static void server(TickEvent.ServerTickEvent event) {
        if(!ENABLED || event.phase!=TickEvent.Phase.END) return;
        var server=ServerLifecycleHooks.getCurrentServer();
        if(server==null || server.getPlayerList().getPlayers().isEmpty()) return;
        if(pending!=null) {
            if(!pending.isRemoved()) throw new AssertionError("Network-spawned TACZ bullet crossed shield");
            absorbed++;pending=null;
        }
        if(server.getTickCount()%20!=0) return;
        var level=server.overworld();
        for(var entity:level.getAllEntities()) {
            if(!(entity instanceof ShieldGeneratorEntity shield) || !shield.getShieldOnline()) continue;
            Vec3 center=shield.shieldCenter(1f).add(0,2,0);
            for(boolean incoming:List.of(true,false)) {
                Vec3 from=center.add(incoming?-20:2,0,0);
                EntityKineticBullet bullet=new EntityKineticBullet(EntityKineticBullet.TYPE,level);
                bullet.setOwner(server.getPlayerList().getPlayers().get(0));
                bullet.setPos(from);bullet.setDeltaMovement(incoming?new Vec3(40,0,0):new Vec3(2,0,0));
                TaczGameTests.field(bullet,"startPos",from);
                TaczGameTests.field(bullet,"gunId",new ResourceLocation("tacz","ak47"));
                TaczGameTests.field(bullet,"gunDisplayId",new ResourceLocation("tacz","ak47_display"));
                TaczGameTests.field(bullet,"ammoId",new ResourceLocation("tacz","762x39"));
                TaczGameTests.field(bullet,"damageAmount",new LinkedList<>(List.of(new DistanceDamagePair(10000,1))));
                TaczGameTests.field(bullet,"distanceAmount",10000f);
                TaczGameTests.field(bullet,"life",20);
                level.addFreshEntity(bullet);
                if(incoming)pending=bullet;
            }
            break;
        }
    }

    @SubscribeEvent public static void client(TickEvent.ClientTickEvent event) {
        if(!ENABLED || event.phase!=TickEvent.Phase.END || reported) return;
        var level=Minecraft.getInstance().level;if(level==null)return;
        for(var entity:level.entitiesForRendering()) if(entity instanceof EntityKineticBullet) {
            if(!(entity instanceof TaczBulletAccess))throw new AssertionError("Client bullet compatibility was not applied");
            clientBullets++;
        }
        if(absorbed>=5 && clientBullets>=5) {
            boolean inputHook=java.util.Arrays.stream(InputExtraCheck.class.getDeclaredMethods()).anyMatch(m->m.getName().contains("helldivers$mountedInput"));
            if(!inputHook) throw new AssertionError("TACZ client input hook missing");
            var mc=Minecraft.getInstance();
            var player=mc.player;
            if(player==null || player.getVehicle()!=null) return;
            Vec3 oldPosition=player.position();
            var turret=ModEntities.INSTANCE.getHMG_EMPLACEMENT().get().create(level);
            turret.setPos(oldPosition);
            CompoundTag data=new CompoundTag();turret.saveWithoutId(data);
            data.putInt("HellpodPhase",AbstractHellpodEntity.Phase.ACTIVE.ordinal());turret.load(data);
            try {
                if(!player.startRiding(turret,true))throw new AssertionError("Could not mount local camera fixture");
                if(InputExtraCheck.isInGame())throw new AssertionError("TACZ still receives mounted player inputs");
                var fov=new ViewportEvent.ComputeFov(mc.gameRenderer,mc.gameRenderer.getMainCamera(),0,17,true);
                CameraSetupEvent.applyScopeMagnification(fov);
                if(fov.getFOV()!=17)throw new AssertionError("TACZ FOV smoothing altered the mounted sight: "+fov.getFOV());
            } finally {
                player.stopRiding();player.setPos(oldPosition);turret.discard();
            }
            reported=true;
            LoggerFactory.getLogger("sbw_helldiver").info("TACZ_CLIENT_PROJECTILES_PASSED absorbed={} client_projectile_frames={} input_hook={} mounted_fov_unchanged=true",absorbed,clientBullets,inputHook);
        }
    }
}
