package com.example.sbwhelldiver.init

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.HellpodCapEntity
import com.example.sbwhelldiver.entity.SentryHellpodEntity
import com.example.sbwhelldiver.entity.StratagemBeacon
import com.example.sbwhelldiver.entity.StratagemMarker
import com.example.sbwhelldiver.entity.SupplyHellpodEntity
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.level.Level
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object ModEntities {

    val ENTITIES: DeferredRegister<EntityType<*>> =
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID)

    val PLAYER_HELLPOD=ENTITIES.register("player_hellpod"){vehicle{t,l->com.example.sbwhelldiver.entity.PlayerHellpodEntity(t,l)}.sized(2.65f,.2f).build("player_hellpod")}

    val ORBITAL_AIRBURST_SHELL=ENTITIES.register("orbital_airburst_shell"){EntityType.Builder.of({t,l -> com.example.sbwhelldiver.entity.OrbitalAirburstShellEntity(t,l)},MobCategory.MISC).sized(.25f,.25f).clientTrackingRange(32).updateInterval(1).build("orbital_airburst_shell")}
    val ORBITAL_AIRBURST_BOMBLET=ENTITIES.register("orbital_airburst_bomblet"){EntityType.Builder.of({t,l -> com.example.sbwhelldiver.entity.OrbitalAirburstBombletEntity(t,l)},MobCategory.MISC).sized(.12f,.12f).clientTrackingRange(32).updateInterval(1).build("orbital_airburst_bomblet")}
    val ORBITAL_GATLING_ROUND=ENTITIES.register("orbital_gatling_round"){EntityType.Builder.of({t,l -> com.example.sbwhelldiver.entity.OrbitalGatlingRoundEntity(t,l)},MobCategory.MISC).sized(.12f,.12f).clientTrackingRange(32).updateInterval(1).build("orbital_gatling_round")}
    val ORBITAL_SHELL=ENTITIES.register("orbital_precision_shell"){EntityType.Builder.of({t,l -> com.example.sbwhelldiver.entity.OrbitalPrecisionShellEntity(t,l)},MobCategory.MISC).sized(.3f,.3f).clientTrackingRange(32).updateInterval(1).build("orbital_precision_shell")}
    val EAGLE=ENTITIES.register("eagle") { EntityType.Builder.of({t,l -> com.example.sbwhelldiver.entity.EagleEntity(t,l)},MobCategory.MISC).sized(17.8f,8.3f).clientTrackingRange(64).updateInterval(1).fireImmune().build("eagle") }
    val EAGLE_CLUSTER_BOMB=ENTITIES.register("eagle_cluster_bomb") { EntityType.Builder.of({t,l -> com.example.sbwhelldiver.entity.EagleClusterBombEntity(t,l)},MobCategory.MISC).sized(0.35f,0.9f).clientTrackingRange(32).updateInterval(1).build("eagle_cluster_bomb") }
    val EAGLE_110MM_ROCKET=ENTITIES.register("eagle_110mm_rocket") { EntityType.Builder.of({t,l -> com.example.sbwhelldiver.entity.Eagle110mmRocketEntity(t,l)},MobCategory.MISC).sized(0.2f,1.2f).clientTrackingRange(32).updateInterval(1).build("eagle_110mm_rocket") }
    val EAGLE_500KG_BOMB=ENTITIES.register("eagle_500kg_bomb") { EntityType.Builder.of({t,l -> com.example.sbwhelldiver.entity.Eagle500kgBombEntity(t,l)},MobCategory.MISC).sized(0.8f,1.8f).clientTrackingRange(32).updateInterval(1).build("eagle_500kg_bomb") }
    val EAGLE_CLUSTER_BOMBLET=ENTITIES.register("eagle_cluster_bomblet") { EntityType.Builder.of({t,l -> com.example.sbwhelldiver.entity.EagleClusterBombletEntity(t,l)},MobCategory.MISC).sized(0.12f,0.2f).clientTrackingRange(32).updateInterval(1).build("eagle_cluster_bomblet") }
    val EAGLE_NAPALM_BOMB=ENTITIES.register("eagle_napalm_airstrike_bomb") { EntityType.Builder.of({t,l -> com.example.sbwhelldiver.entity.EagleNapalmBombEntity(t,l)},MobCategory.MISC).sized(.3f,.8f).clientTrackingRange(32).updateInterval(1).build("eagle_napalm_airstrike_bomb") }
    val EAGLE_GAS_BOMB=ENTITIES.register("eagle_gas_strike_bomb") { EntityType.Builder.of({t,l -> com.example.sbwhelldiver.entity.EagleGasBombEntity(t,l)},MobCategory.MISC).sized(.3f,.8f).clientTrackingRange(32).updateInterval(1).build("eagle_gas_strike_bomb") }
    val EAGLE_SMOKE_BOMB=ENTITIES.register("eagle_smoke_strike_bomb") { EntityType.Builder.of({t,l -> com.example.sbwhelldiver.entity.EagleSmokeBombEntity(t,l)},MobCategory.MISC).sized(.3f,.8f).clientTrackingRange(32).updateInterval(1).build("eagle_smoke_strike_bomb") }
    val EAGLE_BOMB=ENTITIES.register("eagle_bomb") { EntityType.Builder.of({t,l -> com.example.sbwhelldiver.entity.EagleBombEntity(t,l)},MobCategory.MISC).sized(.3f,.8f).clientTrackingRange(32).updateInterval(1).build("eagle_bomb") }
    val EAGLE_CANNON_ROUND=ENTITIES.register("eagle_cannon_round") { EntityType.Builder.of({t,l -> com.example.sbwhelldiver.entity.EagleCannonRoundEntity(t,l)},MobCategory.MISC).sized(.12f,.12f).clientTrackingRange(32).updateInterval(1).build("eagle_cannon_round") }

    /**
     * 复刻卓越前线的载具 builder 配方。
     *
     * 本体自己用的是一个**私有** helper `ModEntities.vehicle()`，外部模组调不到，
     * 所以这里照它的字节码逐项还原：
     * - `MobCategory.MISC` + 追踪距离 **512** + 更新间隔 **1**
     * - `fireImmune()` —— 载具本体防火
     * - **不调用 `.sized()`**：沿用默认 0.6×1.8 的 AABB。
     *   真正的体积由本体的 **OBB 系统**决定，碰撞盒写在数据 JSON 的 `obb` 字段里，
     *   这里给默认值即可，多给反而会和 OBB 打架。
     */
    private fun <T : Entity> vehicle(factory: (EntityType<T>, Level) -> T): EntityType.Builder<T> =
        EntityType.Builder.of({ type, level -> factory(type, level) }, MobCategory.MISC)
            .clientTrackingRange(512)
            .updateInterval(1)
            .fireImmune()

    /**
     * 机枪哨戒炮台。
     *
     * 与 [GATLING_SENTRY] **共用同一个实体类**，靠各自的数据 JSON 区分 ——
     * 这与卓越前线自己的做法一致：它连 LAV_150 / BMP_2 / AH_6 这些也只是注册
     * `EntityType<VehicleEntity>` 而不写独立类，差异全在数据里。
     * 两者动画名完全相同（`hellpod.deploy` / `Main.idle` / `Main.fire`），
     * 只有模型文件与武器参数不一样。
     */
    val ANTI_TANK_EMPLACEMENT = ENTITIES.register("anti_tank_emplacement") {
        vehicle { type,level -> com.example.sbwhelldiver.entity.AntiTankEmplacementEntity(type,level) }
            .sized(2.3f,2.3f).build("anti_tank_emplacement")
    }
    val ANTI_TANK_EMPLACEMENT_SHELL = ENTITIES.register("anti_tank_emplacement_shell") {
        EntityType.Builder.of({ type,level -> com.example.sbwhelldiver.entity.AntiTankEmplacementShellEntity(type,level) },MobCategory.MISC)
            .sized(.12f,.12f).clientTrackingRange(12).updateInterval(1).build("anti_tank_emplacement_shell")
    }

    val SHIELD_GENERATOR = ENTITIES.register("shield_generator") {
        vehicle {type,level -> com.example.sbwhelldiver.entity.ShieldGeneratorEntity(type,level)}.sized(1.45f,1.6f).build("shield_generator")
    }

    val GRENADIER_BATTLEMENT = ENTITIES.register("grenadier_battlement") {
        vehicle {type,level -> com.example.sbwhelldiver.entity.GrenadierBattlementEntity(type,level)}
            .sized(2.3f,2.3f).build("grenadier_battlement")
    }
    val GRENADIER_GRENADE = ENTITIES.register("grenadier_grenade") {
        EntityType.Builder.of({type,level -> com.example.sbwhelldiver.entity.GrenadierGrenadeEntity(type,level)},MobCategory.MISC)
            .sized(.12f,.12f).clientTrackingRange(12).updateInterval(1).build("grenadier_grenade")
    }
    val GRENADIER_WALL_PANEL = ENTITIES.register("grenadier_wall_panel") {
        EntityType.Builder.of({type,level -> com.example.sbwhelldiver.entity.GrenadierWallPanelEntity(type,level)},MobCategory.MISC)
            .sized(.21f,1.6f).clientTrackingRange(12).updateInterval(1).noSave().fireImmune().build("grenadier_wall_panel")
    }
    val HMG_EMPLACEMENT = ENTITIES.register("hmg_emplacement") {
        vehicle { type,level -> com.example.sbwhelldiver.entity.HmgEmplacementEntity(type,level) }
            .sized(2.3f,2.3f).build("hmg_emplacement")
    }

    val MACHINE_GUN_SENTRY: RegistryObject<EntityType<SentryHellpodEntity>> =
        ENTITIES.register("machine_gun_sentry") {
            vehicle { type, level -> SentryHellpodEntity(type, level) }
                .sized(SENTRY_WIDTH, SENTRY_HEIGHT)
                .build("machine_gun_sentry")
        }

    /** 加特林哨戒炮台。 */
    val GATLING_SENTRY: RegistryObject<EntityType<SentryHellpodEntity>> =
        ENTITIES.register("gatling_sentry") {
            vehicle { type, level -> SentryHellpodEntity(type, level) }
                .sized(SENTRY_WIDTH, SENTRY_HEIGHT)
                .build("gatling_sentry")
        }

    val ROCKET_SENTRY: RegistryObject<EntityType<SentryHellpodEntity>> =
        ENTITIES.register("rocket_sentry") {
            vehicle { type, level -> SentryHellpodEntity(type, level) }
                .sized(SENTRY_WIDTH, SENTRY_HEIGHT).build("rocket_sentry")
        }

    val AUTOCANNON_SENTRY: RegistryObject<EntityType<SentryHellpodEntity>> =
        ENTITIES.register("autocannon_sentry") {
            vehicle { type, level -> SentryHellpodEntity(type, level) }
                .sized(SENTRY_WIDTH, SENTRY_HEIGHT).build("autocannon_sentry")
        }

    val MORTAR_SENTRY: RegistryObject<EntityType<SentryHellpodEntity>> = ENTITIES.register("mortar_sentry") {
        vehicle { type, level -> SentryHellpodEntity(type, level) }.sized(SENTRY_WIDTH, SENTRY_HEIGHT).build("mortar_sentry")
    }
    val MORTAR_SHELL: RegistryObject<EntityType<com.example.sbwhelldiver.entity.MortarShellEntity>> = ENTITIES.register("mortar_shell") {
        EntityType.Builder.of({ type, level -> com.example.sbwhelldiver.entity.MortarShellEntity(type, level) }, MobCategory.MISC)
            .sized(.12f,.12f).clientTrackingRange(8).updateInterval(1).build("mortar_shell")
    }

    val GAS_MORTAR_SENTRY = ENTITIES.register("gas_mortar_sentry") {
        vehicle { type, level -> SentryHellpodEntity(type, level) }.sized(SENTRY_WIDTH,SENTRY_HEIGHT).build("gas_mortar_sentry")
    }
    val EMS_MORTAR_SENTRY = ENTITIES.register("ems_mortar_sentry") {
        vehicle { type, level -> SentryHellpodEntity(type, level) }.sized(SENTRY_WIDTH,SENTRY_HEIGHT).build("ems_mortar_sentry")
    }
    val FLAME_SENTRY = ENTITIES.register("flame_sentry") {
        vehicle { type, level -> SentryHellpodEntity(type,level) }.sized(SENTRY_WIDTH,SENTRY_HEIGHT).build("flame_sentry")
    }
    val LASER_SENTRY = ENTITIES.register("laser_sentry") {
        vehicle { type, level -> SentryHellpodEntity(type,level) }.sized(SENTRY_WIDTH,SENTRY_HEIGHT).build("laser_sentry")
    }
    val TESLA_TOWER = ENTITIES.register("tesla_tower") {
        vehicle { type, level -> SentryHellpodEntity(type,level) }.sized(2.65f,5.7f).build("tesla_tower")
    }
    val ANTI_PERSONNEL_MINEFIELD = ENTITIES.register("anti_personnel_minefield") {
        vehicle { type,level -> com.example.sbwhelldiver.entity.MineDispenserEntity(type,level) }
            .sized(1.4f,2.4f).build("anti_personnel_minefield")
    }
    val ANTI_PERSONNEL_MINE = ENTITIES.register("anti_personnel_mine") {
        EntityType.Builder.of({ type,level -> com.example.sbwhelldiver.entity.AntiPersonnelMineEntity(type,level) },MobCategory.MISC)
            .sized(.28f,.18f).clientTrackingRange(12).updateInterval(1).fireImmune().build("anti_personnel_mine")
    }
    val INCENDIARY_MINEFIELD = ENTITIES.register("incendiary_minefield") {
        vehicle { type,level -> com.example.sbwhelldiver.entity.MineDispenserEntity(type,level) }
            .sized(1.4f,2.4f).build("incendiary_minefield")
    }
    val GAS_MINEFIELD = ENTITIES.register("gas_minefield") {
        vehicle { type,level -> com.example.sbwhelldiver.entity.MineDispenserEntity(type,level) }
            .sized(1.4f,2.4f).build("gas_minefield")
    }
    val INCENDIARY_MINE = ENTITIES.register("incendiary_mine") {
        EntityType.Builder.of({ type,level -> com.example.sbwhelldiver.entity.AntiPersonnelMineEntity(type,level) },MobCategory.MISC)
            .sized(.28f,.18f).clientTrackingRange(12).updateInterval(1).fireImmune().build("incendiary_mine")
    }
    val GAS_MINE = ENTITIES.register("gas_mine") {
        EntityType.Builder.of({ type,level -> com.example.sbwhelldiver.entity.AntiPersonnelMineEntity(type,level) },MobCategory.MISC)
            .sized(.28f,.18f).clientTrackingRange(12).updateInterval(1).fireImmune().build("gas_mine")
    }
    val ANTI_TANK_MINEFIELD = ENTITIES.register("anti_tank_minefield") {
        vehicle { type,level -> com.example.sbwhelldiver.entity.MineDispenserEntity(type,level) }
            .sized(1.4f,2.4f).build("anti_tank_minefield")
    }
    val ANTI_TANK_MINE = ENTITIES.register("anti_tank_mine") {
        EntityType.Builder.of({ type,level -> com.example.sbwhelldiver.entity.AntiPersonnelMineEntity(type,level) },MobCategory.MISC)
            .sized(.34f,.18f).clientTrackingRange(12).updateInterval(1).fireImmune().build("anti_tank_mine")
    }
    val GROUND_FIRE = ENTITIES.register("ground_fire") {
        EntityType.Builder.of({ type,level -> com.example.sbwhelldiver.entity.GroundFireEntity(type,level) },MobCategory.MISC)
            .sized(.1f,.1f).clientTrackingRange(12).updateInterval(20).fireImmune().build("ground_fire")
    }
    val GAS_MORTAR_SHELL = ENTITIES.register("gas_mortar_shell") {
        EntityType.Builder.of({ type, level -> com.example.sbwhelldiver.entity.GasMortarShellEntity(type,level) },MobCategory.MISC)
            .sized(.12f,.12f).clientTrackingRange(8).updateInterval(1).build("gas_mortar_shell")
    }
    val EMS_MORTAR_SHELL = ENTITIES.register("ems_mortar_shell") {
        EntityType.Builder.of({ type, level -> com.example.sbwhelldiver.entity.EmsMortarShellEntity(type,level) },MobCategory.MISC)
            .sized(.12f,.12f).clientTrackingRange(8).updateInterval(1).build("ems_mortar_shell")
    }
    val MORTAR_FIELD = ENTITIES.register("mortar_field") {
        EntityType.Builder.of({ type, level -> com.example.sbwhelldiver.entity.MortarFieldEntity(type,level) },MobCategory.MISC)
            .sized(.1f,.1f).clientTrackingRange(12).updateInterval(20).build("mortar_field")
    }

    /** 独立空投仓，落地开舱后弹出补给容器。 */
    val SUPPLY_HELLPOD: RegistryObject<EntityType<SupplyHellpodEntity>> =
        ENTITIES.register("supply_hellpod") {
            vehicle { type, level -> SupplyHellpodEntity(type, level) }
                .sized(SENTRY_WIDTH, SENTRY_HEIGHT)
                .build("supply_hellpod")
        }

    /**
     * 哨戒炮台的**原版 AABB** 尺寸。
     *
     * 本体的载具默认走它自己的 OBB 碰撞（见上面 [vehicle] 的说明），但 OBB 那套是给
     * **会移动**的载具设计的（`supportEntities` 里算的是载具自身的位移），对我们这种
     * 落地就静止的炮台并不挡住生物。而 `VehicleEntity.canBeCollidedWith()` 是返回真的，
     * 也就是说**原版实体碰撞对它生效** —— 所以这里把 AABB 放大到炮塔的实际占地，
     * 生物就会被原版碰撞挡在外面，不依赖 OBB。
     *
     * 尺寸取炮塔的可视占地（约 1.4 格宽、1.8 格高），盒子锚在实体位置往上长，
     * 正好覆盖露出地面的炮塔。
     */
    private const val SENTRY_WIDTH = 1.4f
    private const val SENTRY_HEIGHT = 1.8f

    val HELLPOD_CAP: RegistryObject<EntityType<HellpodCapEntity>> =
        ENTITIES.register("hellpod_cap") {
            EntityType.Builder.of(::HellpodCapEntity, MobCategory.MISC)
                .sized(1.4f, HellpodCapEntity.MODEL_THICKNESS.toFloat())
                .clientTrackingRange(8).updateInterval(1).fireImmune()
                .build("hellpod_cap")
        }

    val SENTRY_ROCKET: RegistryObject<EntityType<com.example.sbwhelldiver.entity.SentryRocketEntity>> =
        ENTITIES.register("sentry_rocket") {
            EntityType.Builder.of({ type, level -> com.example.sbwhelldiver.entity.SentryRocketEntity(type, level) }, MobCategory.MISC)
                .sized(0.25f, 0.25f).clientTrackingRange(8).updateInterval(1).build("sentry_rocket")
        }

    val SENTRY_CANNON_SHELL: RegistryObject<EntityType<com.example.sbwhelldiver.entity.SentryCannonShellEntity>> =
        ENTITIES.register("sentry_cannon_shell") {
            EntityType.Builder.of({ type, level -> com.example.sbwhelldiver.entity.SentryCannonShellEntity(type, level) }, MobCategory.MISC)
                .sized(0.1f, 0.1f).clientTrackingRange(8).updateInterval(1).build("sentry_cannon_shell")
        }

    val SENTRY_BULLET: RegistryObject<EntityType<com.atsuishio.superbwarfare.entity.projectile.ProjectileEntity>> =
        ENTITIES.register("sentry_bullet") {
            EntityType.Builder.of<com.atsuishio.superbwarfare.entity.projectile.ProjectileEntity>({ type, level -> com.example.sbwhelldiver.entity.SentryBulletEntity(type,level) }, MobCategory.MISC)
                .sized(0.1f,0.1f).clientTrackingRange(8).updateInterval(1)
                .build("sentry_bullet")
        }

    /**
     * 战备落点标记：信标落地后留下的倒计时实体，到点召来空投仓。
     *
     * 服务端负责倒计时；客户端绘制展开信标、光束和倒计时。
     * 尺寸给得很小，它不参与任何碰撞，只是「一件事的载体」。
     */
    val STRATAGEM_MARKER: RegistryObject<EntityType<StratagemMarker>> =
        ENTITIES.register("stratagem_marker") {
            EntityType.Builder.of({ type, level -> StratagemMarker(type, level) }, MobCategory.MISC)
                .sized(0.1f, 0.1f)
                .clientTrackingRange(512)
                .updateInterval(1)
                .build("stratagem_marker")
        }

    /**
     * 投出去的战备信标（雪球 / 末影珍珠那一套）。
     *
     * 尺寸 0.25 见方，与雪球一致：要能被正常抛出、被方块挡住、被弹开，
     * 碰撞箱给大了容易卡在墙边飞不出去。
     */
    val STRATAGEM_BEACON: RegistryObject<EntityType<StratagemBeacon>> =
        ENTITIES.register("stratagem_beacon") {
            EntityType.Builder.of({ type, level -> StratagemBeacon(type, level) }, MobCategory.MISC)
                .sized(StratagemBeacon.DIAMETER, StratagemBeacon.DIAMETER)
                .clientTrackingRange(8)
                .updateInterval(1)
                .build("stratagem_beacon")
        }
}
