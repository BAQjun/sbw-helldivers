package com.example.sbwhelldiver.client

import com.atsuishio.superbwarfare.client.renderer.entity.BasicAutoAimableRenderer
import com.atsuishio.superbwarfare.entity.vehicle.base.AutoAimableEntity
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.init.ModParticles
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.EntityRenderersEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Mod.EventBusSubscriber

/**
 * 客户端渲染注册。
 *
 * 三个空投仓实体都直接复用卓越前线的 [BasicAutoAimableRenderer] —— 它内部会按实体的
 * 注册名去 `assets/<命名空间>/sbw/vehicles/<id>.json` 取模型与贴图，并驱动
 * `turret` / `barrel` / `base` 这几个骨骼的旋转。我们的模型骨骼名字正好和它对得上，
 * 所以一行模型代码都不用自己写。
 */
@EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object ClientSetup {

    @SubscribeEvent
    fun shaders(event: net.minecraftforge.client.event.RegisterShadersEvent) {
        TeslaRenderTypes.registerShaders(event)
        ShieldRenderTypes.registerShaders(event)
        AreaEffectRenderTypes.registerShaders(event)
    }

    @SubscribeEvent
    fun setup(event: net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent) {
        event.enqueueWork {
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(com.example.sbwhelldiver.init.ModBlocks.SENTRY_FIRE.get(),net.minecraft.client.renderer.RenderType.cutout())
            net.minecraft.client.Minecraft.getInstance().mainRenderTarget.enableStencil()
            net.minecraft.client.gui.screens.MenuScreens.register(com.example.sbwhelldiver.init.ModMenus.BEACON_LOADOUT.get(), ::BeaconLoadoutScreen)
        }
    }

    @SubscribeEvent
    fun reloadListeners(event: net.minecraftforge.client.event.RegisterClientReloadListenersEvent) {
        event.registerReloadListener(net.minecraft.server.packs.resources.ResourceManagerReloadListener { FieldVolumeRenderer.requestReload() })
        event.registerReloadListener(SoundTimeline)
        event.registerReloadListener(MortarFieldAudio)
        event.registerReloadListener(WreckTextureCache)
        event.registerReloadListener(HellpodCapRenderer.Model)
        event.registerReloadListener(AntiPersonnelMineRenderer.Model)
        event.registerReloadListener(EagleRenderer.Model)
        event.registerReloadListener(BeaconModel)
        event.registerReloadListener(MortarShellRenderer.Model)
        event.registerReloadListener(HmgFeedRenderer)
        event.registerReloadListener(LaserPostVisual)
        event.registerReloadListener(BeaconCallHud)
    }

    @SubscribeEvent
    fun particles(event: net.minecraftforge.client.event.RegisterParticleProvidersEvent) {
        event.registerSpriteSet(ModParticles.MACHINE_GUN_CASING.get()) { CasingParticle.Provider(it, CasingParticle.Kind.MACHINE_GUN) }
        event.registerSpriteSet(ModParticles.GATLING_CASING.get()) { CasingParticle.Provider(it, CasingParticle.Kind.GATLING) }
        event.registerSpriteSet(ModParticles.CANNON_CASING.get()) { CasingParticle.Provider(it, CasingParticle.Kind.CANNON) }
        event.registerSpriteSet(ModParticles.HELLPOD_JET.get()) { HellpodJetParticle.Provider(it) }
        event.registerSpriteSet(ModParticles.GAS_CLOUD.get()) { FieldMistParticle.Provider(it,false) }
        event.registerSpriteSet(ModParticles.EMS_CLOUD.get()) { FieldMistParticle.Provider(it,true) }
        event.registerSpriteSet(ModParticles.FLAME_STREAM.get()) { FlameStreamParticle.Provider(it,false) }
        event.registerSpriteSet(ModParticles.IGNITION_SMOKE.get()) { FlameStreamParticle.Provider(it,true) }
        event.registerSpriteSet(ModParticles.FLAME_SOOT.get()) { FlameStreamParticle.Provider(it,true,true) }
    }

    @SubscribeEvent
    fun registerRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerEntityRenderer<AutoAimableEntity>(ModEntities.PLAYER_HELLPOD.get(),::HellpodRenderer)
        event.registerEntityRenderer(ModEntities.EAGLE.get(),::EagleRenderer)
        event.registerEntityRenderer(ModEntities.EAGLE_BOMB.get(),::EagleBombRenderer)
        event.registerEntityRenderer<com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity>(ModEntities.EAGLE_CANNON_ROUND.get(),::SentryCannonShellRenderer)
        event.registerEntityRenderer<AutoAimableEntity>(ModEntities.SHIELD_GENERATOR.get(),::HellpodRenderer)
        event.registerEntityRenderer<AutoAimableEntity>(ModEntities.GRENADIER_BATTLEMENT.get(),::HellpodRenderer)
        event.registerEntityRenderer<com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity>(ModEntities.GRENADIER_GRENADE.get(),::GrenadierGrenadeRenderer)
        event.registerEntityRenderer(ModEntities.GRENADIER_WALL_PANEL.get(),::GrenadierWallPanelRenderer)
        event.registerEntityRenderer<AutoAimableEntity>(ModEntities.ANTI_TANK_EMPLACEMENT.get(),::HellpodRenderer)
        event.registerEntityRenderer<com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity>(ModEntities.ANTI_TANK_EMPLACEMENT_SHELL.get(),::SentryCannonShellRenderer)
        event.registerEntityRenderer<AutoAimableEntity>(ModEntities.HMG_EMPLACEMENT.get(),::HellpodRenderer)
        // 显式写出类型参数 `AutoAimableEntity`：渲染器的泛型参数是
        // `GeoVehicleRenderer<AutoAimableEntity>`，不写的话 Kotlin 会从渲染器那边
        // 反推 T，再拿它去卡实体类型，报「EntityType<SentryHellpodEntity> 与
        // EntityType<out T> 不匹配」。
        event.registerEntityRenderer<AutoAimableEntity>(
            ModEntities.MACHINE_GUN_SENTRY.get(),
            ::HellpodRenderer,
        )
        event.registerEntityRenderer<AutoAimableEntity>(
            ModEntities.GATLING_SENTRY.get(),
            ::HellpodRenderer,
        )
        // 补给仓同样继承自 AutoAimableEntity，只是数据里没有武器，索敌自然什么都不做。
        event.registerEntityRenderer<AutoAimableEntity>(
            ModEntities.SUPPLY_HELLPOD.get(),
            ::HellpodRenderer,
        )

        event.registerEntityRenderer<AutoAimableEntity>(ModEntities.ROCKET_SENTRY.get(), ::HellpodRenderer)
        event.registerEntityRenderer<AutoAimableEntity>(ModEntities.AUTOCANNON_SENTRY.get(), ::HellpodRenderer)
        event.registerEntityRenderer<AutoAimableEntity>(ModEntities.MORTAR_SENTRY.get(), ::HellpodRenderer)
        event.registerEntityRenderer(ModEntities.MORTAR_SHELL.get(), ::MortarShellRenderer)
        event.registerEntityRenderer<AutoAimableEntity>(ModEntities.GAS_MORTAR_SENTRY.get(), ::HellpodRenderer)
        event.registerEntityRenderer<AutoAimableEntity>(ModEntities.EMS_MORTAR_SENTRY.get(), ::HellpodRenderer)
        event.registerEntityRenderer<AutoAimableEntity>(ModEntities.FLAME_SENTRY.get(), ::HellpodRenderer)
        event.registerEntityRenderer<AutoAimableEntity>(ModEntities.LASER_SENTRY.get(), ::HellpodRenderer)
        event.registerEntityRenderer<AutoAimableEntity>(ModEntities.TESLA_TOWER.get(), ::HellpodRenderer)
        event.registerEntityRenderer(ModEntities.GROUND_FIRE.get(),::GroundFireRenderer)
        event.registerEntityRenderer<com.example.sbwhelldiver.entity.MortarShellEntity>(ModEntities.GAS_MORTAR_SHELL.get(), ::MortarShellRenderer)
        event.registerEntityRenderer<com.example.sbwhelldiver.entity.MortarShellEntity>(ModEntities.EMS_MORTAR_SHELL.get(), ::MortarShellRenderer)
        event.registerEntityRenderer(ModEntities.MORTAR_FIELD.get(),::MortarFieldRenderer)
        event.registerEntityRenderer<AutoAimableEntity>(ModEntities.ANTI_PERSONNEL_MINEFIELD.get(),::HellpodRenderer)
        event.registerEntityRenderer(ModEntities.ANTI_PERSONNEL_MINE.get(),::AntiPersonnelMineRenderer)
        event.registerEntityRenderer(ModEntities.INCENDIARY_MINE.get(),::AntiPersonnelMineRenderer)
        event.registerEntityRenderer(ModEntities.GAS_MINE.get(),::AntiPersonnelMineRenderer)
        event.registerEntityRenderer(ModEntities.ANTI_TANK_MINE.get(),::AntiPersonnelMineRenderer)
        event.registerEntityRenderer<AutoAimableEntity>(ModEntities.ANTI_TANK_MINEFIELD.get(),::HellpodRenderer)
        event.registerEntityRenderer<AutoAimableEntity>(ModEntities.INCENDIARY_MINEFIELD.get(),::HellpodRenderer)
        event.registerEntityRenderer<AutoAimableEntity>(ModEntities.GAS_MINEFIELD.get(),::HellpodRenderer)
        event.registerEntityRenderer(ModEntities.HELLPOD_CAP.get(), ::HellpodCapRenderer)
        event.registerEntityRenderer(ModEntities.SENTRY_BULLET.get(), ::SentryBulletRenderer)
        event.registerEntityRenderer(ModEntities.SENTRY_ROCKET.get(), ::SentryRocketRenderer)
        event.registerEntityRenderer<com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity>(
            ModEntities.SENTRY_CANNON_SHELL.get(), ::SentryCannonShellRenderer)


        // 飞行中的信标使用闭合三维球体。
        event.registerEntityRenderer(
            ModEntities.STRATAGEM_BEACON.get(),
            ::BeaconProjectileRenderer,
        )

        // 落点同时绘制信标展开、颜色光束和抵达倒计时。
        event.registerEntityRenderer(
            ModEntities.STRATAGEM_MARKER.get(),
            ::BeaconMarkerRenderer,
        )
    }
}
