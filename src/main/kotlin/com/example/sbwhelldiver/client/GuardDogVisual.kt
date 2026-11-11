package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.entity.GuardDogEntity
import com.example.sbwhelldiver.entity.GuardDogMotion
import com.example.sbwhelldiver.entity.GuardDogEntity.Flight
import com.example.sbwhelldiver.item.GuardDogBackpack
import com.example.sbwhelldiver.network.BeaconNetwork
import com.example.sbwhelldiver.network.GuardDogTogglePacket
import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.sin
import net.neoforged.neoforge.client.settings.KeyConflictContext
import org.lwjgl.glfw.GLFW

@net.neoforged.fml.common.EventBusSubscriber(modid = "sbw_helldiver", value = [net.neoforged.api.distmarker.Dist.CLIENT])
object GuardDogVisual {
    @net.neoforged.bus.api.SubscribeEvent
    fun tick(event: net.neoforged.neoforge.client.event.ClientTickEvent.Post) = pollKey()
    const val PACK = "guard_dog_backpack"
    const val DRONE = "guard_dog"
    val key = KeyMapping("key.sbw_helldiver.guard_dog", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_G, "key.categories.sbw_helldiver")

    fun pollKey() {
        val mc = Minecraft.getInstance()
        if (key.consumeClick() && mc.screen == null && mc.player?.let(GuardDogBackpack::equipped) != null)
            BeaconNetwork.CHANNEL.sendToServer(GuardDogTogglePacket)
        while (key.consumeClick()) { /* coalesce clicks within one client tick */ }
    }

    // Observes the actual rendered transform in isolated client checks, including both handoffs.
    internal var poseObserver: ((GuardDogEntity?, Matrix4f) -> Unit)? = null
    internal var magazineObserver: ((String, Float, Matrix4f, Matrix4f, Matrix4f, Matrix4f) -> Unit)? = null

    private fun blendPose(from: Matrix4f, to: Matrix4f, amount: Float): Matrix4f {
        val position = from.getTranslation(Vector3f()).lerp(to.getTranslation(Vector3f()), amount)
        val rotation = from.getUnnormalizedRotation(Quaternionf()).normalize()
            .slerp(to.getUnnormalizedRotation(Quaternionf()).normalize(), amount)
        val scale = from.getScale(Vector3f()).lerp(to.getScale(Vector3f()), amount)
        return Matrix4f().translationRotateScale(position, rotation, scale)
    }

    internal fun magazineTransferPose(source: Matrix4f, carried: Matrix4f, socket: Matrix4f, time: Float): Matrix4f {
        val grip = ease((time - .30f) / .25f)
        val seat = ease((time - .55f) / (GuardDogEntity.INSERT_TICK / 20f - .55f))
        return blendPose(blendPose(source, carried, grip), socket, seat)
    }


    private fun flightRotation(yaw: Float, pitch: Float) =
        Matrix4f().rotationY((180f - yaw) * Mth.DEG_TO_RAD).rotateX(-pitch * Mth.DEG_TO_RAD)

    private fun freeFlightPose(entity: GuardDogEntity, partial: Float, camera: Vec3): Matrix4f {
        // LevelRenderer uses an identity PoseStack; its view matrix is applied separately.
        // Use the same previous position and angle interpolation as its entity renderer.
        val t = partial.toDouble()
        val owner = entity.clientOwner()?.takeIf { entity.usesPackPose }
        val position = owner?.let { entity.transitionPosition(it, entity.phaseAge + t, partial) }
            ?: Vec3(Mth.lerp(t, entity.xOld, entity.x), Mth.lerp(t, entity.yOld, entity.y), Mth.lerp(t, entity.zOld, entity.z))
        val yaw = owner?.let { Mth.rotLerp(partial, it.yBodyRotO, it.yBodyRot) }
            ?: Mth.rotLerp(partial, entity.yRotO, entity.yRot)
        return Matrix4f().translation(
            (position.x - camera.x).toFloat(),
            (position.y + GuardDogEntity.MODEL_HEIGHT - camera.y).toFloat(),
            (position.z - camera.z).toFloat())
            .mul(flightRotation(yaw, if (owner != null) 0f else Mth.lerp(partial, entity.xRotO, entity.xRot)))
    }

    private data class ReturnPose(
        val position: Vec3,
        val renderedAt: Float,
        var dockStartedAt: Float = Float.NaN,
        var correction: Vec3 = Vec3.ZERO
    )

    private val returnPoses = java.util.WeakHashMap<GuardDogEntity, ReturnPose>()

    private fun smoothDockHandoff(entity: GuardDogEntity, partial: Float, modelPose: Matrix4f) {
        val flight = entity.flight
        if ((flight != Flight.RETURN && flight != Flight.DOCK) || ShaderPackCompat.shadowPass()) return
        val now = entity.tickCount + partial
        val camera = Minecraft.getInstance().gameRenderer.mainCamera.position
        val world = camera.add(modelPose.m30().toDouble(), modelPose.m31().toDouble(), modelPose.m32().toDouble())
        if (flight == Flight.RETURN) {
            returnPoses[entity] = ReturnPose(world, now)
            return
        }
        val approach = returnPoses[entity] ?: return
        if (approach.dockStartedAt.isNaN()) {
            if (now - approach.renderedAt > 3f) {
                returnPoses.remove(entity)
                return
            }
            // The dock packet may arrive ahead of the interpolated free-flight pose.
            approach.dockStartedAt = now
            approach.correction = approach.position.subtract(world)
        }
        val weight = 1.0 - ease((now - approach.dockStartedAt) / 5f)
        if (weight <= 0.0) {
            returnPoses.remove(entity)
            return
        }
        val corrected = world.add(approach.correction.scale(weight)).subtract(camera)
        modelPose.setTranslation(corrected.x.toFloat(), corrected.y.toFloat(), corrected.z.toFloat())
    }

    internal fun firingMuzzle(entity: GuardDogEntity, partial: Float): Vec3 {
        val local = MachineMesh.guardDogMuzzle(entity.isLaser, (entity.tickCount + partial) / 20f, entity.isStun, entity.isFlame, entity.isGas)
            ?: return entity.muzzleOrigin(partial)
        flightRotation(Mth.rotLerp(partial, entity.yRotO, entity.yRot),
            Mth.lerp(partial, entity.xRotO, entity.xRot)).transformPosition(local)
        return entity.getPosition(partial).add(0.0, GuardDogEntity.MODEL_HEIGHT, 0.0)
            .add(local.x.toDouble(), local.y.toDouble(), local.z.toDouble())
    }

    private fun ease(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun jet(pose: PoseStack, buffers: MultiBufferSource, transform: Matrix4f, socket: Vector3f,
        axis: Vector3f, flame: Float, age: Float) {
        if (flame <= .02f) return
        val origin = transform.transformPosition(Vector3f(socket))
        val direction = transform.transformDirection(Vector3f(axis)).normalize()
        GuardDogThrusters.capture(pose, origin, direction, flame, age)
    }

    fun drawPack(pose: PoseStack, buffers: MultiBufferSource, light: Int, docked: Boolean,
        drone: GuardDogEntity? = null, partial: Float = 0f, spares: Int = GuardDogEntity.SPARE_MAGAZINES,
        emptyFold: Boolean = false, flightPose: Matrix4f? = null, observe: Boolean = false, laser: Boolean = drone?.isLaser == true, stun: Boolean = drone?.isStun == true,
        flameDog: Boolean = drone?.isFlame == true, gasDog: Boolean = drone?.isGas == true, reducedModel: Boolean = false) {
        val energy = laser || stun
        val age = drone?.let { it.phaseAge + partial } ?: 0f
        val launch = drone?.flight == Flight.LAUNCH
        val docking = drone?.flight == Flight.DOCK
        val reload = drone?.reload?.takeIf { docking }
        val reloadAge = (age - GuardDogEntity.TRANSITION_TICKS).coerceAtLeast(0f)
        val loading = reload?.changesMagazine == true && age >= GuardDogEntity.TRANSITION_TICKS
        val loadTime = reloadAge / 20f
        val transferFrom = .30f
        val insertion = GuardDogEntity.INSERT_TICK / 20f
        val inTransit = loading && loadTime >= transferFrom && loadTime < insertion
        val displayedSpares = if (docking && reload?.changesMagazine == true) drone!!.dockingSpares else spares
        val animation = when {
            launch -> "deploy"
            docking -> "retract"
            docked -> "docked"
            else -> "idle"
        }
        val contact = GuardDogEntity.LIFT_TICK / 20f
        val catch = GuardDogEntity.CONTACT_TICK / 20f
        val carrierAge = GuardDogMotion.carrierAge(energy, launch, age)
        val clock = carrierAge / 20f
        val socketTime = when {
            launch -> clock.coerceAtMost(contact)
            docking -> clock.coerceAtLeast(catch)
            else -> clock
        }
        val pack = MachineMesh.drawGuardDogPack(animation, clock, socketTime, pose, buffers, light,
            displayedSpares, if (loading) reload?.clip else null, loadTime, inTransit, laser, stun, flameDog, gasDog, reducedModel) ?: return
        if (!docked && !launch && !docking) return

        // Player model, backpack, arm and drone share this transform all the way through contact.
        val travel = when {
            launch -> ease((age - GuardDogMotion.liftTick(energy)) / (GuardDogEntity.TRANSITION_TICKS - GuardDogMotion.liftTick(energy)))
            docking -> 1f - ease(age / GuardDogMotion.contactTick(energy))
            else -> 0f
        }
        val attached = Matrix4f(pack.socket).rotateY(Mth.PI).rotateX(Mth.HALF_PI)
        val mount = if (travel == 0f) attached else {
            // The native socket's tracks include an off-body preview trajectory.
            // Once released, blend from the end of the rod into the actual flight pose.
            val target = flightPose ?: attached
            val position = attached.getTranslation(Vector3f()).lerp(target.getTranslation(Vector3f()), travel)
            val orientation = attached.getUnnormalizedRotation(Quaternionf()).normalize()
                .slerp(target.getUnnormalizedRotation(Quaternionf()).normalize(), travel)
            // The player renderer also scales its model. Preserve the inverse scale
            // at release so switching to the entity renderer does not change size.
            val scale = attached.getScale(Vector3f()).lerp(target.getScale(Vector3f()), travel)
            Matrix4f().translationRotateScale(position, orientation, scale)
        }
        val droneClip = when {
            launch -> "deploy"
            docking -> "retract"
            else -> "deploy"
        }
        val overlay = when {
            energy && docked -> "empty"
            energy && (launch || docking) -> "empty_fold"
            loading && !flameDog && !gasDog -> reload?.clip
            docking && reload == GuardDogEntity.Reload.EMPTY && age >= GuardDogEntity.TRANSITION_TICKS -> reload.clip
            docked && emptyFold -> "empty"
            launch && emptyFold && age < 10f -> "empty_fold"
            else -> null
        }
        val overlayTime = if (energy) GuardDogMotion.foldTime(launch, age)
            else if (launch && emptyFold) (10f - age) / 20f else loadTime
        val flame = when {
            launch -> ease((carrierAge - 31f) / 15f)
            docking -> 1f - ease((carrierAge - 7f) / 15f)
            else -> 0f
        }
        val ejected = docking && reload?.changesMagazine == true && age >= GuardDogEntity.EJECT_TICK && loadTime < insertion
        pose.pushPose()
        pose.mulPose(mount)
        if (docking) smoothDockHandoff(drone!!, partial, pose.last().pose())
        if (observe) poseObserver?.invoke(drone, Matrix4f(pose.last().pose()))
        val magazine = MachineMesh.drawGuardDog(droneClip, clock, pose, buffers, light, { 0f },
            overlay, overlayTime, hideMagazine = ejected, laser = laser, stun = stun, flame = flameDog, gas = gasDog, reducedModel = reducedModel) { matrix, origin, axis ->
            jet(pose, buffers, matrix, origin, axis, flame, age)
        }
        pose.popPose()
        if (magazine == null) return
        val gunSocket = Matrix4f(mount).mul(magazine)
        val transfer = pack.magazine
        if (transfer != null && pack.loader != null) {
            val carried = magazineTransferPose(transfer, pack.loader, gunSocket, loadTime)
            if (observe) magazineObserver?.invoke(reload!!.clip!!, loadTime, transfer, pack.loader, gunSocket, carried)
            MachineMesh.drawGuardDogMagazine(carried, pose, buffers, light)
        }
        val falling = (age - GuardDogEntity.EJECT_TICK) / 20f
        if (ejected && falling < .55f) {
            val offset = if (reload == GuardDogEntity.Reload.RIGHT) -falling * .65f else falling * .65f
            val dropped = Matrix4f().translation(offset, .15f * falling - 4.9f * falling * falling, -.65f * falling)
                .mul(gunSocket).rotateX(falling * 10f).rotateZ(falling * 5f)
            MachineMesh.drawGuardDogMagazine(dropped, pose, buffers, light)
        }
    }

    private fun matchesPack(drone: GuardDogEntity, owner: Player, active: UUID?) =
        drone.belongsTo(owner) && (active == null || drone.uuid == active)

    private val packDrones = java.util.WeakHashMap<Player, java.lang.ref.WeakReference<GuardDogEntity>>()

    private fun packDrone(owner: Player, active: UUID?): GuardDogEntity? {
        packDrones[owner]?.get()?.takeIf { !it.isRemoved && matchesPack(it, owner, active) }?.let { return it }
        // Fast movement can separate the last tracked entity position from its owner
        // by more than eight blocks. Identity, not spatial proximity, owns the pack.
        val drone = (owner.level() as? net.minecraft.client.multiplayer.ClientLevel)?.entitiesForRendering()
            ?.filterIsInstance<GuardDogEntity>()
            ?.firstOrNull { !it.isRemoved && matchesPack(it, owner, active) }
        if (drone != null) packDrones[owner] = java.lang.ref.WeakReference(drone) else packDrones.remove(owner)
        return drone
    }

    class PackLayer(parent: RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) :
        RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>(parent) {
        override fun render(pose: PoseStack, buffers: MultiBufferSource, light: Int, player: AbstractClientPlayer,
            swing: Float, amount: Float, partial: Float, age: Float, yaw: Float, pitch: Float) {
            val pack = GuardDogBackpack.equipped(player) ?: return
            val active = GuardDogBackpack.active(pack)
            val drone = packDrone(player, active)
            pose.pushPose()
            parentModel.body.translateAndRotate(pose)
            pose.translate(0.0, GuardDogEntity.PACK_LAYER_Y, GuardDogEntity.PACK_LAYER_Z)
            pose.mulPose(Axis.XP.rotationDegrees(180f))
            val spares = GuardDogBackpack.spareMags(pack)
            // Match the free-flying entity renderer exactly at either handoff.
            // Both matrices are camera-relative, so the inverse includes the
            // player's body rotation, walking pose, and crouching transform.
            val flightPose = drone?.takeIf { it.usesPackPose }?.let {
                val camera = Minecraft.getInstance().gameRenderer.mainCamera
                Matrix4f(pose.last().pose()).invert().mul(freeFlightPose(it, partial, camera.position))
            }
            drawPack(pose, buffers, light, drone == null && active == null, drone, partial, spares,
                GuardDogBackpack.emptyFold(pack), flightPose, observe = true, laser = GuardDogBackpack.isLaser(pack), stun = GuardDogBackpack.isStun(pack), flameDog = GuardDogBackpack.isFlame(pack), gasDog = GuardDogBackpack.isGas(pack),
                reducedModel = ModelLod.reduced(player.distanceToSqr(Minecraft.getInstance().gameRenderer.mainCamera.position)))
            pose.popPose()
        }
    }

    class DroneRenderer(context: EntityRendererProvider.Context) : EntityRenderer<GuardDogEntity>(context) {
        override fun getTextureLocation(entity: GuardDogEntity) =
            ResourceLocation.fromNamespaceAndPath("sbw_helldiver", "textures/mesh/${MachineMesh.guardDogModel(entity.isLaser, entity.isStun, entity.isFlame, entity.isGas)}_0.png")

        override fun shouldRender(entity: GuardDogEntity, frustum: Frustum, x: Double, y: Double, z: Double): Boolean {
            val mc = Minecraft.getInstance()
            if (!entity.usesPackPose) return super.shouldRender(entity, frustum, x, y, z)
            val owner = entity.clientOwner() ?: return super.shouldRender(entity, frustum, x, y, z)
            val pack = GuardDogBackpack.equipped(owner) ?: return false
            if (!matchesPack(entity, owner, GuardDogBackpack.active(pack))) return false

            // Use the backpack layer only when the owning player's model is visible in this view.
            val camera = mc.gameRenderer.mainCamera
            if (camera.entity == owner && !camera.isDetached && !owner.isSleeping)
                return frustum.isVisible(owner.boundingBox.inflate(3.0))
            return !entityRenderDispatcher.shouldRender(owner, frustum, x, y, z) &&
                frustum.isVisible(owner.boundingBox.inflate(3.0))
        }

        // Engine-only poses from the native forward (0x52f98f0ec340b1d4), idle and reverse (0x4458b2513f3723dc) clips.
        private fun engineOffset(thrust: Float): Float =
            if (thrust < 0f) thrust * (90.2f - 43.1f) else thrust * (142f - 90.2f)

        override fun render(entity: GuardDogEntity, yaw: Float, partial: Float, pose: PoseStack,
            buffers: MultiBufferSource, light: Int) {
            val reducedModel = ModelLod.reduced(entity.distanceToSqr(Minecraft.getInstance().gameRenderer.mainCamera.position))
            val pitch = Mth.lerp(partial, entity.xRotO, entity.xRot)
            pose.pushPose()
            val owner = entity.clientOwner()?.takeIf { entity.usesPackPose }
            if (owner != null) {
                val actual = entity.transitionPosition(owner, entity.phaseAge + partial.toDouble(), partial)
                pose.translate(actual.x - Mth.lerp(partial.toDouble(), entity.xOld, entity.x),
                    actual.y - Mth.lerp(partial.toDouble(), entity.yOld, entity.y),
                    actual.z - Mth.lerp(partial.toDouble(), entity.zOld, entity.z))
            }
            pose.translate(0.0, GuardDogEntity.MODEL_HEIGHT, 0.0)
            pose.mulPose(flightRotation(owner?.let { Mth.rotLerp(partial, it.yBodyRotO, it.yBodyRot) } ?: yaw,
                if (owner != null) 0f else pitch))
            smoothDockHandoff(entity, partial, pose.last().pose())
            poseObserver?.invoke(entity, Matrix4f(pose.last().pose()))
            if (entity.usesPackPose) {
                val age = entity.phaseAge + partial
                val launch = entity.flight == Flight.LAUNCH
                val travel = if (launch)
                    ease((age - GuardDogMotion.liftTick(entity.isEnergy)) / (GuardDogEntity.TRANSITION_TICKS - GuardDogMotion.liftTick(entity.isEnergy)))
                else 1f - ease(age / GuardDogMotion.contactTick(entity.isEnergy))
                pose.mulPose(Axis.XP.rotation((1f - travel) * Mth.HALF_PI))
                val reload = entity.reload.takeUnless { launch }
                val reloadAge = age - GuardDogEntity.TRANSITION_TICKS
                val ejected = reload?.changesMagazine == true && age >= GuardDogEntity.EJECT_TICK &&
                    reloadAge < GuardDogEntity.INSERT_TICK
                val carrierAge = GuardDogMotion.carrierAge(entity.isEnergy, launch, age)
                val flame = if (launch) ease((carrierAge - 31f) / 15f) else 1f - ease((carrierAge - 7f) / 15f)
                MachineMesh.drawGuardDog(if (launch) "deploy" else "retract", carrierAge / 20f, pose, buffers, light,
                    { 0f }, if (entity.isEnergy) "empty_fold" else reload?.clip?.takeIf { reloadAge >= 0f },
                    if (entity.isEnergy) GuardDogMotion.foldTime(launch, age) else reloadAge.coerceAtLeast(0f) / 20f, ejected, laser = entity.isLaser, stun = entity.isStun, flame = entity.isFlame, gas = entity.isGas, reducedModel = reducedModel
                ) { matrix, origin, axis -> jet(pose, buffers, matrix, origin, axis, flame, age) }
            } else {
                val age = entity.tickCount + partial
                val drive = -entity.thrustAlong(yaw, partial)
                val turn = entity.thrustTurn(partial)
                MachineMesh.drawGuardDog("flight", (age / 20f) % (if (entity.isEnergy || entity.isSprayer) .15f else .4f), pose, buffers, light, { left ->
                    val input = (drive + if (left) turn else -turn).coerceIn(-1f, 1f)
                    (engineOffset(input) + pitch).coerceIn(-47.1f, 51.8f) * Mth.DEG_TO_RAD
                }, laser = entity.isLaser, stun = entity.isStun, flame = entity.isFlame, gas = entity.isGas,
                    motion = Vector3f(entity.thrustAlong(yaw, partial), entity.thrustAcross(yaw, partial) + turn, pitch * Mth.DEG_TO_RAD),
                    spin = (age / 20f) % .3f, reducedModel = reducedModel
                ) { matrix, origin, axis -> jet(pose, buffers, matrix, origin, axis, 1f, age) }
            }
            pose.popPose()
            if (entity.isGas) GasGuardDogVisual.render(entity, partial, pose, buffers, light)
            super.render(entity, yaw, partial, pose, buffers, light)
        }
    }

    var heldItemObserver: ((ItemDisplayContext, Boolean) -> Unit)? = null

    class PackItemRenderer : BlockEntityWithoutLevelRenderer(Minecraft.getInstance().blockEntityRenderDispatcher,
        Minecraft.getInstance().entityModels) {
        override fun renderByItem(stack: ItemStack, context: ItemDisplayContext, pose: PoseStack,
            buffers: MultiBufferSource, light: Int, overlay: Int) {
            pose.pushPose()
            pose.translate(.5, .5, .6)
            pose.scale(1.25f, 1.25f, 1.25f)
            val held = when (context) {
                ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                ItemDisplayContext.THIRD_PERSON_LEFT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND -> true
                else -> false
            }
            heldItemObserver?.invoke(context, held)
            val spares = GuardDogBackpack.spareMags(stack)
            drawPack(pose, buffers, light, GuardDogBackpack.active(stack) == null, spares = spares,
                emptyFold = GuardDogBackpack.emptyFold(stack), laser = GuardDogBackpack.isLaser(stack), stun = GuardDogBackpack.isStun(stack),
                flameDog = GuardDogBackpack.isFlame(stack), gasDog = GuardDogBackpack.isGas(stack), reducedModel = held)
            pose.popPose()
        }
    }

    val itemRenderer by lazy { PackItemRenderer() }
}
