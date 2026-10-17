package com.example.sbwhelldiver.gametest;

import com.example.sbwhelldiver.compat.TaczBulletAccess;
import com.example.sbwhelldiver.entity.*;
import com.example.sbwhelldiver.init.ModEntities;
import com.mojang.authlib.GameProfile;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage.DistanceDamagePair;
import com.tacz.guns.util.EntityUtil;
import com.tacz.guns.util.TacHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

@GameTestHolder("sbw_helldiver_tacz")
@PrefixGameTestTemplate(false)
public final class TaczGameTests {
    static void field(Object object, String name, Object value) {
        try {
            Field field = EntityKineticBullet.class.getDeclaredField(name);
            field.setAccessible(true); field.set(object, value);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static final class Scene implements AutoCloseable {
        final GameTestHelper h;
        final Vec3 origin;
        final List<Entity> entities = new ArrayList<>();
        final FakePlayer owner;
        Scene(GameTestHelper h, int slot) {
            this.h = h;
            BlockPos at = new BlockPos((slot % 4) * 80 - 120, 300, (slot / 4) * 80 - 80);
            h.getLevel().getChunk(at);
            origin = Vec3.atBottomCenterOf(at);
            // Each scene has its own isolated volume, and cleans saved entities first.
            clean();
            owner = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "TaczCompat"));
            owner.setPos(origin.add(0, 30, 0));
        }
        void clean() {
            h.getLevel().getEntities((Entity) null, new AABB(origin, origin).inflate(40), e -> true)
                    .forEach(Entity::discard);
        }
        <T extends Entity> T add(T entity, Vec3 at) {
            entity.setPos(at); entity.xOld = at.x; entity.yOld = at.y; entity.zOld = at.z;
            h.getLevel().addFreshEntity(entity); entities.add(entity); return entity;
        }
        <T extends AbstractHellpodEntity> T active(T entity) {
            entity.setPos(origin);
            CompoundTag tag = new CompoundTag(); entity.saveWithoutId(tag);
            tag.putInt("HellpodPhase", AbstractHellpodEntity.Phase.ACTIVE.ordinal());
            tag.putDouble("HellpodRestY", origin.y); tag.putBoolean("HellpodCapReleased", true);
            entity.load(tag); return add(entity, origin);
        }
        ShieldGeneratorEntity shield() { return active(ModEntities.INSTANCE.getSHIELD_GENERATOR().get().create(h.getLevel())); }
        EntityKineticBullet bullet(Vec3 from, Vec3 velocity, float damage, int pierce) {
            EntityKineticBullet bullet = new EntityKineticBullet(EntityKineticBullet.TYPE, h.getLevel());
            bullet.setOwner(owner); bullet.setDeltaMovement(velocity);
            field(bullet, "startPos", from);
            field(bullet, "gunId", new ResourceLocation("tacz", "ak47"));
            field(bullet, "gunDisplayId", new ResourceLocation("tacz", "ak47_display"));
            field(bullet, "damageAmount", new LinkedList<>(List.of(new DistanceDamagePair(10000f, damage))));
            field(bullet, "distanceAmount", 10000f); field(bullet, "headShot", 8f);
            field(bullet, "armorIgnore", .4f); field(bullet, "pierce", pierce);
            return add(bullet, from);
        }
        Cow cow(Vec3 at) {
            Cow cow = EntityType.COW.create(h.getLevel()); cow.setNoAi(true); cow.setNoGravity(true);
            return add(cow, at);
        }
        @Override public void close() { entities.forEach(Entity::discard); clean(); }
    }

    @GameTest(template="shield_test", timeoutTicks=30)
    public static void firstTickFastBulletUsesRealFalloffAndPelletDamage(GameTestHelper h) {
        try (Scene s = new Scene(h, 1)) {
            ShieldGeneratorEntity g = s.shield(); Vec3 c = g.shieldCenter(1f);
            EntityKineticBullet b = s.bullet(c.add(-20, 0, 0), new Vec3(40, 0, 0), 40, 8);
            field(b, "damageAmount", new LinkedList<>(List.of(new DistanceDamagePair(3, 40), new DistanceDamagePair(10000, 20))));
            field(b, "distanceAmount", 3f); b.applyShotgunDamageSpread(4); b.setShotDamageMultiplier(.75f);
            h.assertTrue(b instanceof TaczBulletAccess, "Optional bullet mixin was not applied");
            b.tick();
            h.assertTrue(b.isRemoved(), "New high-speed bullet crossed the entire shield in its first tick");
            h.assertTrue(Math.abs(g.getShieldHealth() - (640 - 3.75)) < .001, "Falloff/pellet/shot multipliers were lost");
            h.assertTrue(g.getShieldHits().getList("hits", 10).size() == 1, "Shield impact was duplicated or no ripple was sent");
        } h.succeed();
    }

    @GameTest(template="shield_test", timeoutTicks=30)
    public static void outwardBulletAndBrokenShieldDoNotBlock(GameTestHelper h) {
        try (Scene s = new Scene(h, 2)) {
            ShieldGeneratorEntity g = s.shield(); Vec3 c = g.shieldCenter(1f).add(0, 2, 0);
            EntityKineticBullet out = s.bullet(c.add(2, 0, 0), new Vec3(35, 0, 0), 25, 1); out.tick();
            h.assertTrue(!out.isRemoved() && g.getShieldHealth() == 640, "Internal fire was intercepted");
            g.absorb(640, g.shieldCenter(1f).add(15,0,0));
            EntityKineticBullet in = s.bullet(c.add(-20,0,0), new Vec3(40,0,0), 25, 1); in.tick();
            h.assertTrue(!in.isRemoved() && g.getShieldHealth() == 0, "Broken shield intercepted a bullet");
        } h.succeed();
    }

    @GameTest(template="shield_test", timeoutTicks=30)
    public static void nearerEntityAndBlockKeepNativeHitOrder(GameTestHelper h) {
        try (Scene s = new Scene(h, 3)) {
            ShieldGeneratorEntity g = s.shield(); Vec3 c = g.shieldCenter(1f);
            Cow first = s.cow(c.add(-18, -.5, 0)); Cow protectedCow = s.cow(c.add(-12, -.5, 0));
            EntityKineticBullet b = s.bullet(c.add(-22,0,0), new Vec3(40,0,0), 7, 8); b.tick();
            h.assertTrue(first.getHealth() < first.getMaxHealth() && protectedCow.getHealth() == protectedCow.getMaxHealth(), "Piercing hit order bypassed the shield or earlier creature");
            h.assertTrue(g.getShieldHealth() == 633 && b.isRemoved(), "Shield was charged more than once or penetration continued");
            first.setHealth(first.getMaxHealth()); first.invulnerableTime=0;
            EntityKineticBullet single = s.bullet(c.add(-22,0,0),new Vec3(40,0,0),7,1); single.tick();
            h.assertTrue(first.getHealth()<first.getMaxHealth() && g.getShieldHealth()==633 && single.isRemoved(),"Non-penetrating round charged a shield behind its victim");
            first.discard(); protectedCow.discard();
            BlockPos wall = BlockPos.containing(c.add(-17,0,0)); h.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 2);
            try {
                EntityKineticBullet blocked = s.bullet(c.add(-22,0,0), new Vec3(40,0,0), 9, 1); blocked.tick();
                h.assertTrue(g.getShieldHealth() == 633, "Shield absorbed a bullet that struck a closer block");
            } finally { h.getLevel().setBlock(wall, Blocks.AIR.defaultBlockState(), 2); }
        } h.succeed();
    }

    @GameTest(template="shield_test", timeoutTicks=30)
    public static void explosiveIncendiaryBulletIsAbsorbedWithoutDetonation(GameTestHelper h) {
        try (Scene s = new Scene(h, 4)) {
            ShieldGeneratorEntity g = s.shield(); Vec3 c = g.shieldCenter(1f);
            Cow protectedCow = s.cow(c.add(-12, -.5, 0));
            EntityKineticBullet b = s.bullet(c.add(-20,0,0), new Vec3(40,0,0), 17, 1);
            field(b,"explosion",true); field(b,"explosionDamage",70f); field(b,"explosionRadius",8f); field(b,"igniteEntity",true);
            b.tick();
            h.assertTrue(b.isRemoved() && g.getShieldHealth() == 553, "Explosive payload did not debit the shield correctly");
            h.assertTrue(protectedCow.getHealth() == protectedCow.getMaxHealth() && !protectedCow.isOnFire(), "Absorbed payload exploded/ignited behind the shield");
        } h.succeed();
    }

    @GameTest(template="shield_test", timeoutTicks=30)
    public static void overlappingShieldsAbsorbOnlyNearestEntry(GameTestHelper h) {
        try (Scene s = new Scene(h, 5)) {
            ShieldGeneratorEntity first = s.shield();
            ShieldGeneratorEntity second = s.active(ModEntities.INSTANCE.getSHIELD_GENERATOR().get().create(h.getLevel()));
            second.setPos(s.origin.add(5,0,0));
            EntityKineticBullet b = s.bullet(first.shieldCenter(1f).add(-20,0,0),new Vec3(45,0,0),19,8); b.tick();
            h.assertTrue(first.getShieldHealth()==621 && second.getShieldHealth()==640 && b.isRemoved(), "Overlapping shields double-charged or chose the farther entry");
        } h.succeed();
    }

    @GameTest(template="sentry_test", timeoutTicks=30)
    public static void wallStopsApPenetrationAndDoesNotTakeHeadshots(GameTestHelper h) {
        try (Scene s = new Scene(h, 6)) {
            GrenadierBattlementEntity wall = s.active(ModEntities.INSTANCE.getGRENADIER_BATTLEMENT().get().create(h.getLevel())); wall.tick();
            var panels=h.getLevel().getEntitiesOfClass(GrenadierWallPanelEntity.class,new AABB(s.origin,s.origin).inflate(5));
            h.assertTrue(panels.size()==3,"Wall panels inaccessible immediately after spawn: "+panels.size());
            h.assertTrue(panels.stream().allMatch(p->p.getBattlement()==wall && p.isPickable()),"Wall parent lookup/pickability failed: "+panels.stream().map(p->p.getId()+"/"+p.getBattlement()+"/"+p.getBoundingBox()).toList());
            Vec3 ray = s.origin.add(2, 1.04, 0);
            Cow behind = s.cow(ray.add(0,-.2,3));
            EntityKineticBullet b = s.bullet(ray.add(0,0,-4),new Vec3(0,0,10),31,8);
            var hit=EntityUtil.findEntityOnPath(b,b.position(),b.position().add(b.getDeltaMovement()));
            h.assertTrue(hit!=null && !hit.isHeadshot(),"Native wall ray still counted a headshot");
            h.assertTrue(Math.abs(hit.getHitPos().y-hit.getEntity().getY()-hit.getEntity().getEyeHeight())<.25,"Test did not cross TACZ's default headshot band");
            b.tick();
            h.assertTrue(Math.abs(wall.getHealth()-469)<.001, "Wall lost AP split damage or gained a headshot multiplier: " + wall.getHealth());
            h.assertTrue(b.isRemoved() && behind.getHealth()==behind.getMaxHealth(), "AP bullet passed through solid wall cover");
        } h.succeed();
    }

    @GameTest(template="sentry_test", timeoutTicks=30)
    public static void nativeExplosiveWallHitAppliesBlastOnceAcrossPanels(GameTestHelper h) {
        try (Scene s = new Scene(h, 9)) {
            GrenadierBattlementEntity wall = s.active(ModEntities.INSTANCE.getGRENADIER_BATTLEMENT().get().create(h.getLevel())); wall.tick();
            EntityKineticBullet b = s.bullet(s.origin.add(2,.4,-4),new Vec3(0,0,10),5,8);
            field(b,"explosion",true);field(b,"explosionDamage",60f);field(b,"explosionRadius",5f);
            var hit = EntityUtil.findEntityOnPath(b,b.position(),b.position().add(b.getDeltaMovement()));
            h.assertTrue(hit!=null && hit.getEntity() instanceof GrenadierWallPanelEntity,"Native TACZ ray did not hit a wall panel");
            double closest=wall.position().distanceTo(hit.getHitPos());
            for (GrenadierWallPanelEntity panel : h.getLevel().getEntitiesOfClass(GrenadierWallPanelEntity.class,new AABB(s.origin,s.origin).inflate(5)))
                closest=Math.min(closest,panel.position().distanceTo(hit.getHitPos()));
            double expected=500-5-30*(1-closest/5);
            b.tick();
            h.assertTrue(Math.abs(wall.getHealth()-expected)<.01,"TACZ blast multiplied by collision panels or lost 50% resistance: "+wall.getHealth()+" expected "+expected);
        } h.succeed();
    }

    @GameTest(template="sentry_test", timeoutTicks=30)
    public static void allHellpodBodiesAcceptNativeGunAndBlastSources(GameTestHelper h) {
        try (Scene s = new Scene(h, 7)) {
            int count=0;
            for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES.getValues()) {
                ResourceLocation id=ForgeRegistries.ENTITY_TYPES.getKey(type);
                if (id == null || !id.getNamespace().equals("sbw_helldiver")) continue;
                Entity entity=type.create(h.getLevel());
                if (!(entity instanceof AbstractHellpodEntity body)) { if(entity!=null) entity.discard(); continue; }
                // Legacy supply pods are not deployable defences and keep SBW's own armour rules.
                if (body instanceof SupplyHellpodEntity) { body.discard(); continue; }
                s.active(body); float before=body.getHealth();
                EntityKineticBullet b=s.bullet(s.origin.add(-4,.2,0),new Vec3(8,0,0),10,1);
                try {
                    var method=EntityKineticBullet.class.getDeclaredMethod("onHitEntity",TacHitResult.class,Vec3.class,Vec3.class);
                    method.setAccessible(true);
                    method.invoke(b,new TacHitResult(new EntityKineticBullet.EntityResult(body,s.origin.add(0,.2,0),false)),b.position(),s.origin.add(4,.2,0));
                } catch(ReflectiveOperationException e) {throw new AssertionError(e);}
                h.assertTrue(Math.abs(body.getHealth()-(before-10))<.001,"Native TACZ AP/non-AP damage failed for "+id+": "+body.getHealth()+" / "+before);
                float after=body.getHealth(); body.hurt(h.getLevel().damageSources().explosion(b,s.owner),20);
                h.assertTrue(Math.abs(body.getHealth()-(after-10))<.001,"Common 50% blast resistance failed for "+id);
                b.discard();body.discard();count++;
            }
            h.assertTrue(count>=17,"Body coverage silently skipped registered structures: "+count);
        } h.succeed();
    }

    @GameTest(template="sentry_test", timeoutTicks=30)
    public static void nativeGunBulletsTriggerEveryMineKind(GameTestHelper h) {
        try (Scene s=new Scene(h,8)) {
            for (var registry : List.of(ModEntities.INSTANCE.getANTI_PERSONNEL_MINE(),ModEntities.INSTANCE.getANTI_TANK_MINE(),ModEntities.INSTANCE.getINCENDIARY_MINE(),ModEntities.INSTANCE.getGAS_MINE())) {
                AntiPersonnelMineEntity mine=registry.get().create(h.getLevel()); s.add(mine,s.origin);
                EntityKineticBullet b=s.bullet(s.origin.add(0,.05,-3),new Vec3(0,0,6),5,8);b.tick();
                h.assertTrue(mine.isRemoved(),"Native gun bullet did not trigger "+registry.getId());
                h.assertTrue(b.isRemoved(),"Bullet kept penetrating after triggering a mine");
                s.clean();
            }
        } h.succeed();
    }
    @GameTest(template="sentry_test", timeoutTicks=30)
    public static void mountedGunEventsDoNotFireHandheldWeapons(GameTestHelper h) {
        try (Scene s = new Scene(h, 10)) {
            for (var registry : List.of(ModEntities.INSTANCE.getHMG_EMPLACEMENT(),ModEntities.INSTANCE.getANTI_TANK_EMPLACEMENT(),ModEntities.INSTANCE.getGRENADIER_BATTLEMENT())) {
                HmgEmplacementEntity turret = s.active(registry.get().create(h.getLevel()));
                h.assertTrue(s.owner.startRiding(turret,true),"Test operator could not mount "+registry.getId());
                for (var side : net.minecraftforge.fml.LogicalSide.values()) {
                    h.assertTrue(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.tacz.guns.api.event.common.GunShootEvent(s.owner,net.minecraft.world.item.ItemStack.EMPTY,side)),"Mounted hand gun shoot was not blocked on "+side);
                    h.assertTrue(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.tacz.guns.api.event.common.GunFireEvent(s.owner,net.minecraft.world.item.ItemStack.EMPTY,side)),"Mounted hand gun burst was not blocked on "+side);
                }
                s.owner.stopRiding();turret.discard();
            }
            h.assertTrue(!net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.tacz.guns.api.event.common.GunShootEvent(s.owner,net.minecraft.world.item.ItemStack.EMPTY,net.minecraftforge.fml.LogicalSide.SERVER)),"Dismounted player could no longer fire a hand gun");
        } h.succeed();
    }

}
