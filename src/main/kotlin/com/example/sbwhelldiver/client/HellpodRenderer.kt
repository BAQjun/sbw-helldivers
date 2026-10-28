package com.example.sbwhelldiver.client

import com.atsuishio.superbwarfare.client.model.entity.VehicleModelInstance
import com.atsuishio.superbwarfare.client.renderer.entity.BasicAutoAimableRenderer
import com.atsuishio.superbwarfare.entity.vehicle.base.AutoAimableEntity
import com.example.sbwhelldiver.entity.AbstractHellpodEntity
import com.example.sbwhelldiver.entity.SentryHellpodEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.entity.EntityRendererProvider

/** Preserve authored lift/fold/reload transforms; the native renderer replaces barrel rotation. */
class HellpodRenderer(context: EntityRendererProvider.Context) : BasicAutoAimableRenderer(context) {
    override fun shouldRender(entity: AutoAimableEntity, frustum: net.minecraft.client.renderer.culling.Frustum,
        x: Double, y: Double, z: Double): Boolean {
        if(super.shouldRender(entity,frustum,x,y,z))return true
        val sentry=entity as? SentryHellpodEntity ?: return false
        if(sentry.profile.isFlame && entity.shouldRender(x,y,z))return FlameJetVisual.bounds(sentry)?.let(frustum::isVisible) ?: false
        if(sentry.profile.isTesla && entity.shouldRender(x,y,z))return TeslaTowerClient.effectBounds(sentry)?.let(frustum::isVisible) ?: false
        if(!sentry.profile.isLaser || !entity.shouldRender(x,y,z))return false
        return LaserSentryClient.beamBounds(sentry,1f)?.let(frustum::isVisible) ?: false
    }
    private val sampled = mutableSetOf<String>()
    private val checkedShellShots = mutableSetOf<Int>()
    private data class TurnFrame(val time: Double, val direction: net.minecraft.world.phys.Vec3, val phase: AbstractHellpodEntity.Phase)
    private val turnFrames = java.util.WeakHashMap<AbstractHellpodEntity, TurnFrame>()
    private val loadedMineBones=Array(64){"loaded_mine_${it.toString().padStart(2,'0')}"}
    private val laserHeatBones=Array(20){"ls_heat_${it.toString().padStart(2,'0')}"}
    private val rocketRoundBones=Array(17){"rocket_round_${it.toString().padStart(2,'0')}"}
    override fun render(entity: AutoAimableEntity, yaw: Float, partial: Float, pose: PoseStack,
        buffers: net.minecraft.client.renderer.MultiBufferSource, light: Int) {
        (entity as? AbstractHellpodEntity)?.let(ModelQualityResources::refresh)
        if(entity is com.example.sbwhelldiver.entity.PlayerHellpodEntity && entity.phase==AbstractHellpodEntity.Phase.FALLING){
            val actual=entity.visualOrigin(partial)
            val rendered=net.minecraft.world.phys.Vec3(net.minecraft.util.Mth.lerp(partial.toDouble(),entity.xOld,entity.x),net.minecraft.util.Mth.lerp(partial.toDouble(),entity.yOld,entity.y),net.minecraft.util.Mth.lerp(partial.toDouble(),entity.zOld,entity.z))
            pose.translate(actual.x-rendered.x,actual.y-rendered.y,actual.z-rendered.z)
        }
        (entity as? AbstractHellpodEntity)?.let { PodInterior.render(it, partial, pose, buffers, light) }
        if (java.lang.Boolean.getBoolean("sbw_helldiver.clientSmokeTest") && entity.isWreck && !checkedWreckTextureCache) {
            val original = requireNotNull(getCurrentModelEntry(pose,entity)).texture
            check(WreckTextureCache.preparedTextures.containsKey(original)) { "Wreck texture was not prepared during resource loading" }
            val began = System.nanoTime()
            val actual = com.atsuishio.superbwarfare.client.renderer.TextureBrightnessHandler.getBrightenedTexture(original,.3f)
            check(actual == WreckTextureCache.preparedTextures[original])
            checkedWreckTextureCache = true
            com.example.sbwhelldiver.LOGGER.info("WRECK_TEXTURE_CACHE_PASSED: native wreck rendering uses its prepared texture; lookupMs={}",(System.nanoTime()-began)/1_000_000.0)
        }
        // Cache repeated mesh vertices in both qualities; preserve Iris triangle boundaries.
        val needsShaderMeshAdapter=entity is AbstractHellpodEntity
        val meshBuffers=if(needsShaderMeshAdapter)
            getCurrentModelEntry(pose,entity)?.let{entry->
                var texture=entry.texture
                if(!entry.isLOD())com.atsuishio.superbwarfare.data.vehicle_skin.VehicleSkin.getSkin(entity)?.texture
                    ?.let(net.minecraft.resources.ResourceLocation::tryParse)?.let{texture=it}
                texture=when{
                    com.atsuishio.superbwarfare.event.ClientEventHandler.activeThermalImaging ->
                        com.atsuishio.superbwarfare.client.renderer.SmartTextureBrightener.getSmartBrightenedTexture(texture,3f)
                    entity.isWreck -> com.atsuishio.superbwarfare.client.renderer.TextureBrightnessHandler.getBrightenedTexture(texture,.3f)
                    else -> texture
                }
                ShaderMeshBuffers.wrap(buffers,texture,(entity as? AbstractHellpodEntity)?.let(TerrainMeshOcclusion::volume))
            } else null
        try {super.render(entity,yaw,partial,pose,meshBuffers?:buffers,light)}
        finally {meshBuffers?.finish()}
        val pod = entity as? AbstractHellpodEntity ?: return
        if(pod is com.example.sbwhelldiver.entity.MineDispenserEntity){
            getCurrentModelEntry(pose,entity)?.let{entry->
                pose.pushPose()
                try{rotateVehicleAxis(entity,pose,yaw,partial);pose.scale(renderScale(),renderScale(),renderScale())
                    MountedMineRenderer.render(pod,entry.instance,pose,buffers,light)
                }finally{pose.popPose()}
            }
        }
        if(pod is com.example.sbwhelldiver.entity.HmgEmplacementEntity && pod.phase !in listOf(AbstractHellpodEntity.Phase.SPENT,AbstractHellpodEntity.Phase.DONE)) {
            getCurrentModelEntry(pose,entity)?.let {entry ->
                pose.pushPose()
                try {
                    rotateVehicleAxis(entity,pose,yaw,partial)
                    pose.scale(renderScale(),renderScale(),renderScale())
                    if(pod.profile.id == "hmg_emplacement")
                        HmgFeedRenderer.render(entry.instance,pose,buffers,light,pod.isWreck)


                } finally {pose.popPose()}
            }
        }
        if(pod is SentryHellpodEntity && pod.profile.isTesla && !ShaderPackCompat.active() && !ShaderPackCompat.shadowPass()) TeslaTowerClient.render(pod,partial,pose,buffers)
        if(pod is SentryHellpodEntity && pod.profile.isLaser && !ShaderPackCompat.shadowPass()) {
            getCurrentModelEntry(pose,entity)?.let { entry ->
                pose.pushPose()
                try {
                    rotateVehicleAxis(entity,pose,yaw,partial)
                    pose.scale(renderScale(),renderScale(),renderScale())
                    LaserPostVisual.capture(pod,entry.instance,pose)
                } finally { pose.popPose() }
            }
        }
        if (!pod.isWreck) return
        val entry = getCurrentModelEntry(pose, entity) ?: return
        val instance = entry.instance
        val hull = instance.getBone("pod_root") ?: return
        val turret = instance.getBone("deployment_lift") ?: return
        val oldHull = hull.visible
        val oldTurret = turret.visible
        // The native main pass darkens wrecks. Draw the intact pod in a separate pass
        // using its original texture, without changing the synced wreck flag.
        hull.visible = true
        turret.visible = false
        pose.pushPose()
        try {
            rotateVehicleAxis(entity, pose, yaw, partial)
            pose.scale(renderScale(), renderScale(), renderScale())
            val texture = entry.texture
            val intactBuffers=if(needsShaderMeshAdapter)ShaderMeshBuffers.wrap(buffers,texture,(entity as? AbstractHellpodEntity)?.let(TerrainMeshOcclusion::volume)) else null
            try {instance.renderToBuffer(pose, intactBuffers?:buffers,
                net.minecraft.client.renderer.RenderType.entityTranslucent(texture),
                com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.BedrockModelRenderTypes.polyMeshCutout(texture),
                light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)}
            finally {intactBuffers?.finish()}
            if (java.lang.Boolean.getBoolean("sbw_helldiver.clientSmokeTest")) {
                check(pod.isWreck && hull.visible && !turret.visible)
                checkedIntactPodPass = true
            }
        } finally {
            pose.popPose()
            hull.visible = oldHull
            turret.visible = oldTurret
        }
    }

    override fun transformCustomModelPart(entity: AutoAimableEntity, instance: VehicleModelInstance,
        poseStack: PoseStack, entityYaw: Float, partialTicks: Float) {
        val pod = entity as? AbstractHellpodEntity ?: return
        instance.getBone("pod_eject_cap")?.visible = !pod.capReleased
        if(pod is com.example.sbwhelldiver.entity.GrenadierBattlementEntity) {
            val lens=instance.getBone("optic_lens")
            lens?.let {
                val open=pod.throughSight()
                it.visible=!open
                // Poly-mesh passes also need their geometry collapsed while sighting.
                val scale=if(open)0f else 1f
                it.xScale=scale;it.yScale=scale;it.zScale=scale
            }
        }
        instance.getBone("pod_root")?.visible = !pod.isWreck
        instance.getBone("deployment_lift")?.visible = pod.phase != AbstractHellpodEntity.Phase.SPENT
        if (java.lang.Boolean.getBoolean("sbw_helldiver.beaconSmokeTest") &&
            pod.phase == AbstractHellpodEntity.Phase.FALLING && pod.braking && pod.brakeAge >= 12 &&
            !pod.profile.isSupply && sampled.add("packed_${pod.profile.id}")) {
            val index = instance.getIndex("deployment_lift")
            check(index >= 0) { "Packed sentry has no lift bone" }
            val offset = instance.getGlobalTransform(index).getTranslation(org.joml.Vector3f()).y
            com.example.sbwhelldiver.LOGGER.info("PACKED_LIFT_RENDER_CHECK {}: shiftY={}", pod.profile.id, offset)
            check(kotlin.math.abs(offset) < pod.profile.podHeight) { "Packed turret was displaced below the pod: $offset" }
        }
        if (java.lang.Boolean.getBoolean("sbw_helldiver.beaconSmokeTest") &&
            pod.phase == AbstractHellpodEntity.Phase.FALLING && pod.braking &&
            sampled.add("nozzles_${pod.profile.id}")) {
            var maxError = 0.0
            pod.profile.nozzleBones.forEachIndexed { index, bone ->
                val boneIndex = instance.getIndex(bone)
                val matrix = if (boneIndex >= 0) instance.getGlobalTransform(boneIndex)
                    else requireNotNull(instance.getQueryTransform(bone))
                val outlet = pod.profile.nozzleModelPositions[index]
                val point = org.joml.Vector3f(outlet.x.toFloat(), outlet.y.toFloat(), outlet.z.toFloat())
                matrix.transformPosition(point)
                point.rotateY((180f - entityYaw) * (Math.PI / 180).toFloat())
                val actual = net.minecraft.world.phys.Vec3(point.x.toDouble(), point.y.toDouble(), point.z.toDouble())
                val expected = pod.profile.nozzles[index].yRot(-entityYaw * (Math.PI / 180).toFloat())
                maxError = maxOf(maxError, actual.distanceTo(expected))
            }
            com.example.sbwhelldiver.LOGGER.info("NOZZLE_RENDER_CHECK {}: all six outlets, max error={}", pod.profile.id, maxError)
            check(maxError < 0.002) { "Fire roots missed rendered nozzle outlets by $maxError blocks" }
        }
        if(pod is com.example.sbwhelldiver.entity.MineDispenserEntity) {
            instance.getBone("spinner")?.rotation?.rotationY(Math.toRadians(pod.interpolatedSpin(partialTicks).toDouble()).toFloat())
            // Mounted geometry is drawn from an immutable shared cache only
            // after opening; never traverse or submit its meshes in this pass.
            for(i in 0 until pod.mineCount) instance.getBone(loadedMineBones[i])?.let{
                it.visible=false;it.xScale=0f;it.yScale=0f;it.zScale=0f
            }

        }
        if (pod is SentryHellpodEntity && pod.profile.isLaser) {
            // Progress is drawn once in an isolated emissive pass. Poly-mesh
            // bones need zero scale as well as visibility to hide their main pass.
            for (i in 0 until 20) instance.getBone(laserHeatBones[i])?.let {
                it.visible=false;it.xScale=0f;it.yScale=0f;it.zScale=0f
            }
        }
        if (pod is SentryHellpodEntity && pod.profile.isGatling) instance.getBone("rotor")?.rotation?.rotationZ(
            pod.interpolatedRotorAngle(partialTicks) * (Math.PI / 180).toFloat())
        if (pod.phase == AbstractHellpodEntity.Phase.ACTIVE || pod.phase == AbstractHellpodEntity.Phase.DESTROYED) {
            // A phase packet can arrive before the animation controller ticks.
            // Discard the completed unfolding hinge before applying live aim,
            // otherwise its held 70-degree mortar pose is added a second time.
            instance.getBone("stow_pivot")?.rotation?.identity()
            val hmg=pod as? com.example.sbwhelldiver.entity.HmgEmplacementEntity
            val renderedYaw=if(hmg!=null)entityYaw-hmg.interpolatedYaw(partialTicks) else turretYRot
            val renderedPitch=hmg?.interpolatedPitch(partialTicks) ?: turretXRot
            val firstMountPose=hmg!=null&&(hmg.awaitingFirstMount||hmg.isFirstMountLevelling)
            if(hmg!=null && !firstMountPose) {
                instance.getBone("turret")?.rotation?.identity()
                instance.getBone("barrel")?.rotation?.identity()
            }
            if(pod is com.example.sbwhelldiver.entity.AntiTankEmplacementEntity) {
                if (pod.phase == AbstractHellpodEntity.Phase.ACTIVE) {
                    // Apply after authored idle/feed tracks so reloading cannot stand the optic back up.
                    instance.getBone("scope_arm_03")?.rotation?.rotationXYZ(
                        -Math.toRadians(pod.opticPitchDegrees(partialTicks).toDouble()).toFloat(), 0f, 0f)
                }
                // The entire installed assembly kicks around the current traverse axis.
                // Its animation rig is above the turret, so conjugate by the live yaw.
                instance.getBone("at_recoil_rig")?.let { rig ->
                    val angle=renderedYaw * (Math.PI / 180).toFloat()
                    rig.rotation.premul(org.joml.Quaternionf().rotationY(angle)).rotateY(-angle)
                }
            }
            instance.getBone("turret")?.rotation?.rotateY(renderedYaw * (Math.PI / 180).toFloat())
            if (!firstMountPose && (pod !is SentryHellpodEntity || !pod.reloading)) {
                instance.getBone("barrel")?.rotation?.rotateX(
                    (-renderedPitch).coerceIn(entity.turretMinPitch, entity.turretMaxPitch) * (Math.PI / 180).toFloat())
            }
        }
        if ((java.lang.Boolean.getBoolean("sbw_helldiver.clientSmokeTest") || java.lang.Boolean.getBoolean("sbw_helldiver.mortarSmokeTest") || java.lang.Boolean.getBoolean("sbw_helldiver.flameSmokeTest")) &&
            pod is SentryHellpodEntity && ((pod.phase == AbstractHellpodEntity.Phase.DEPLOYING && pod.phaseAge >= pod.deployTicks - 6) ||
            (pod.phase == AbstractHellpodEntity.Phase.ACTIVE && pod.phaseAge <= 35))) {
            val boreName = pod.profile.muzzleBone ?: "barrel"
            val boreIndex = instance.getIndex(boreName)
            val boreTransform = if (boreIndex >= 0) instance.getGlobalTransform(boreIndex)
                else requireNotNull(instance.getQueryTransform(boreName)) { "Missing bore transform: " + boreName }
            val axis = boreTransform.transformDirection(org.joml.Vector3f(0f, 0f, -1f)).normalize()
            axis.rotateY((180f - entityYaw) * (Math.PI / 180).toFloat())
            val direction = net.minecraft.world.phys.Vec3(axis.x.toDouble(), axis.y.toDouble(), axis.z.toDouble())
            val now = pod.tickCount + partialTicks.toDouble()
            val previous = turnFrames[pod]
            if (previous != null && now >= previous.time) {
                val angle = Math.toDegrees(kotlin.math.acos(previous.direction.dot(direction).coerceIn(-1.0, 1.0)))
                val allowed = (pod.turretTurnXSpeed + pod.turretTurnYSpeed) * (now - previous.time + .15) + .3
                check(angle <= allowed) { "Deployed barrel snaps instead of continuing its turn: " + pod.profile.id + " step=" + angle + " allowed=" + allowed + " phase=" + previous.phase + "->" + pod.phase + " pitch=" + pod.turretXRotO + "/" + pod.turretXRot + " rendered=" + turretXRot }
                if (previous.phase == AbstractHellpodEntity.Phase.DEPLOYING && pod.phase == AbstractHellpodEntity.Phase.ACTIVE) {
                    checkedDeploymentTurns.add(pod.profile.id)
                    com.example.sbwhelldiver.LOGGER.info("DEPLOYMENT_TURN_RENDER_PASSED {}: first active frame angleStep={} degrees, allowed={}", pod.profile.id, angle, allowed)
                }
            }
            turnFrames[pod] = TurnFrame(now, direction, pod.phase)
        }
        if (java.lang.Boolean.getBoolean("sbw_helldiver.clientSmokeTest") &&
            pod.phase == AbstractHellpodEntity.Phase.SPENT && pod.phaseAge >= 16) {
            check(!pod.isWreck && pod.health == 120f)
            check(instance.getBone("deployment_lift")?.visible == false && instance.getBone("pod_root")?.visible == true) {
                "Exploded turret is still visible, or surviving pod was hidden"
            }
            for (i in 0..7) {
                val iris = requireNotNull(instance.getBone("pod_iris_$i"))
                check(kotlin.math.abs(iris.xScale-1f)<.001 && kotlin.math.abs(iris.zScale-1f)<.001 &&
                    kotlin.math.abs(iris.rotation.y)<.001) { "Destroyed pod's iris is not closed" }
            }
            checkedDestroyedPose = true
        }
        if (pod is SentryHellpodEntity && pod.profile.isRocket) {
            for (round in 1..16) instance.getBone(rocketRoundBones[round])?.visible =
                pod.reloading || pod.phase != AbstractHellpodEntity.Phase.ACTIVE || round > 16 - pod.clipRemaining
        }
        if (java.lang.Boolean.getBoolean("sbw_helldiver.clientSmokeTest") && pod is SentryHellpodEntity &&
            pod.profile.id == "machine_gun_sentry" && pod.phase == AbstractHellpodEntity.Phase.ACTIVE &&
            pod.shotSerial > 5 && pod.shotSerial <= 30 && checkedShellShots.add(pod.shotSerial)) {
            val index = instance.getIndex(requireNotNull(pod.profile.shellBone))
            check(index >= 0)
            val marker = requireNotNull(pod.profile.shellModelPosition)
            val point = org.joml.Vector3f(marker.x.toFloat(),marker.y.toFloat(),marker.z.toFloat())
            instance.getGlobalTransform(index).transformPosition(point)
            point.rotateY((180f-entityYaw)*(Math.PI/180).toFloat())
            val rendered = pod.getPosition(partialTicks).add(point.x.toDouble(),point.y.toDouble(),point.z.toDouble())
            val native = requireNotNull(pod.shellExitPosition(partialTicks))
            val delta = rendered.subtract(native)
            val sideways = delta.cross(pod.getShootVec("Main",partialTicks).normalize()).length()
            // The port moves at most .1872/16 blocks along the bore during recoil.
            check(sideways < .002 && delta.length() < .014) {
                "MG shell exit misses its rendered port: sideways=$sideways distance=${delta.length()}"
            }
            checkedMachineGunShellShots++
            com.example.sbwhelldiver.LOGGER.info("MG_SHELL_RENDER_CHECK shot={}: rendered={} native={} sideways={} error={}",pod.shotSerial,rendered,native,sideways,delta.length())
        }
        if ((java.lang.Boolean.getBoolean("sbw_helldiver.clientSmokeTest") || java.lang.Boolean.getBoolean("sbw_helldiver.mortarSmokeTest") || java.lang.Boolean.getBoolean("sbw_helldiver.flameSmokeTest")) && pod is SentryHellpodEntity &&
            pod.phase == AbstractHellpodEntity.Phase.ACTIVE && pod.shotSerial > 5 && !pod.profile.isRocket && pod.profile.muzzleBone != null && sampled.add(pod.profile.id)) {
            // Geometry chunks use absolute model coordinates; locator APIs subtract the bind pivot.
            val bone = pod.profile.muzzleBone!!
            val rotor = instance.getBone("rotor")
            val rotation = rotor?.rotation?.let { org.joml.Quaternionf(it) }
            if (pod.profile.isGatling) rotor?.rotation?.identity() // Check the fixed upper firing station.
            val index = instance.getIndex(bone)
            val transform = if (index >= 0) instance.getGlobalTransform(index) else requireNotNull(instance.getQueryTransform(bone))
            if (rotation != null) rotor.rotation.set(rotation)
            val marker = requireNotNull(pod.profile.muzzleModelPosition)
            val point = org.joml.Vector3f(marker.x.toFloat(),marker.y.toFloat(),marker.z.toFloat())
            transform.transformPosition(point)
            point.rotateY((180f - entityYaw) * (Math.PI / 180).toFloat())
            val rendered = pod.getPosition(partialTicks).add(point.x.toDouble(), point.y.toDouble(), point.z.toDouble())
            (net.minecraft.client.Minecraft.getInstance().singleplayerServer?.overworld()?.getEntity(pod.uuid) as? SentryHellpodEntity)?.let {
                com.example.sbwhelldiver.LOGGER.info("LIVE_MUZZLE_COMPARE {}: serverEntity={} clientEntity={} serverMuzzle={} clientMuzzle={} serverPitch={} clientPitch={} serverBodyPitch={} clientBodyPitch={}",
                    pod.profile.id,it.position(),pod.position(),it.getShootPos("Main",1f),pod.getShootPos("Main",1f),it.turretXRot,pod.turretXRot,it.xRot,pod.xRot)
                check(kotlin.math.abs(it.y - pod.y) < 0.0001) { "Server and client pod heights disagree: ${pod.profile.id}" }
                // The two worlds are on different ticks while aiming. The render-bore
                // assertion below compares one coherent local pose, rather than
                // requiring zero network latency between moving server/client barrels.
            }
            val sidewaysError = rendered.subtract(pod.getShootPos("Main",partialTicks)).cross(pod.getShootVec("Main",partialTicks).normalize()).length()
            com.example.sbwhelldiver.LOGGER.info("MUZZLE_RENDER_CHECK {}: rendered={} native={} distance={} turretPitch={} barrel={}",
                pod.profile.id, rendered, pod.getShootPos("Main", partialTicks), rendered.distanceTo(pod.getShootPos("Main", partialTicks)), turretXRot, instance.getBone("barrel")?.rotation)
            check(rendered.distanceTo(pod.getShootPos("Main",partialTicks)) < if (pod.profile.isMortar) .16 else .04) { "Gun muzzle differs from rendered bore: ${pod.profile.id}" }
            check(sidewaysError < 0.002) { "Gun muzzle is off the barrel centreline: ${pod.profile.id}, $sidewaysError blocks" }
        }
    }
    companion object {
        val checkedDeploymentTurns = mutableSetOf<String>()
        var checkedWreckTextureCache = false
            private set
        var checkedIntactPodPass = false
            private set
        var checkedDestroyedPose = false
            private set
        var checkedMachineGunShellShots = 0
            private set
    }
}
