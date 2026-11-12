package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.google.gson.*
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.*
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.*
import org.joml.*
import java.util.zip.GZIPInputStream

/** Authored meshes stay intact; animation changes only their node transforms. */
object MachineMesh:ResourceManagerReloadListener {
    private data class Node(val parent:Int,val mesh:Int,val t:Vector3f,val r:Quaternionf,val s:Vector3f,val name:String)
    private data class Primitive(val material:Int,val vertices:FloatArray)
    private class Batch(val node:Int,val material:Int,val glow:Boolean,val vertices:FloatArray) {
        val geometry=MachineVertexBatch(vertices)
    }
    private data class Channel(val node:Int,val path:String,val times:FloatArray,val values:List<FloatArray>,val step:Boolean)
    private data class Exhaust(val node: Int, val origin: Vector3f, val axis: Vector3f)

    private class Model(val nodes:List<Node>,meshes:List<List<Primitive>>,val clips:Map<String,List<Channel>>,val textures:List<ResourceLocation>,val nozzles:List<net.minecraft.world.phys.Vec3>, val exhausts: List<Exhaust>) {
        val sourceFaceCount:Int
        val batches:List<Batch>
        init {
            val bind=arrayOfNulls<Matrix4f>(nodes.size)
            fun world(i:Int):Matrix4f {bind[i]?.let{return it};val n=nodes[i];val m=Matrix4f().translationRotateScale(n.t,n.r,n.s);if(n.parent>=0)m.set(Matrix4f(world(n.parent)).mul(m));bind[i]=m;return m}
            val moving=clips.values.flatten().map{it.node}.toMutableSet()
            nodes.indices.filterTo(moving) { nodes[it].name == "gun_magazine" || nodes[it].name.matches(spareNode) ||
                nodes[it].name.startsWith("fuel_gauge_") || nodes[it].name in setOf("rack_lift","front_shield","rear_shield") || nodes[it].name.matches(Regex("case_0[1-4]")) }
            val grouped=linkedMapOf<Triple<Int,Int,Boolean>,MutableList<Float>>()
            for((i,node)in nodes.withIndex())if(node.mesh>=0) {
                var anchor=i;while(anchor>=0 && anchor !in moving)anchor=nodes[anchor].parent
                val relative=if(anchor<0)Matrix4f(world(i))else Matrix4f(world(anchor)).invert().mul(world(i))
                val normals=Matrix3f(relative).invert().transpose();val position=Vector3f();val normal=Vector3f()
                val emissive=nodeNameGlow(i)
                for(primitive in meshes[node.mesh]) {
                    val data=primitive.vertices
                    for(glow in if(emissive)listOf(false,true)else listOf(false)) {
                        val target=grouped.getOrPut(Triple(anchor,primitive.material,glow)){ArrayList()}
                        for(k in data.indices step 8) {
                            position.set(data[k],data[k+1],data[k+2]).mulPosition(relative)
                            normal.set(data[k+5],data[k+6],data[k+7]).mul(normals).normalize()
                            target.add(position.x);target.add(position.y);target.add(position.z);target.add(data[k+3]);target.add(data[k+4]);target.add(normal.x);target.add(normal.y);target.add(normal.z)
                        }
                    }
                }
            }
            // These meshes use cutout surfaces. Draw each material together and the
            // emissive overlay afterwards instead of flushing once per animated part.
            sourceFaceCount=grouped.values.sumOf { it.size/32 }
            batches=grouped.map{(key,vertices)->Batch(key.first,key.second,key.third,PlanarMeshQuads.packQuads(vertices.toFloatArray()))}
                .sortedWith(compareBy<Batch> { it.glow }.thenBy { it.material })
        }
        private val meshCenters = meshes.map { mesh ->
            val min = Vector3f(Float.POSITIVE_INFINITY)
            val max = Vector3f(Float.NEGATIVE_INFINITY)
            val point = Vector3f()
            for (primitive in mesh) {
                val vertices = primitive.vertices
                for (i in vertices.indices step 8) {
                    point.set(vertices[i], vertices[i + 1], vertices[i + 2])
                    min.min(point); max.max(point)
                }
            }
            if (mesh.isEmpty()) Vector3f() else Vector3f(min).add(max).mul(.5f)
        }
        fun meshCenter(node: Int): Vector3f = meshCenters[nodes[node].mesh]
        private fun nodeNameGlow(i:Int)=nodes[i].name.endsWith("_window") || nodes[i].name.endsWith("_light")
        private data class PoseKey(val clip: String, val time: Float)
        private val poseCache = object : LinkedHashMap<PoseKey, List<Matrix4f>>(16, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PoseKey, List<Matrix4f>>) = size > 8
        }
        private val stationaryClips = clips.filterValues { channels -> channels.all { it.times.size == 1 } }.keys
        val surfaceTypes = textures.map { RenderType.entityCutoutNoCull(it) }
        val glowTypes = textures.map { RenderType.eyes(ResourceLocation(it.namespace,
            it.path.removeSuffix(".png")+"_glow.png")) }
        val itemFit:Pair<Vector3f,Float> by lazy {
            val transforms=pose("deploy",0f)
            val min=Vector3f(Float.POSITIVE_INFINITY);val max=Vector3f(Float.NEGATIVE_INFINITY)
            val point=Vector3f()
            for(batch in batches)if(!batch.glow)for(k in batch.vertices.indices step 8) {
                point.set(batch.vertices[k],batch.vertices[k+1],batch.vertices[k+2])
                if(batch.node>=0)point.mulPosition(transforms[batch.node])
                min.min(point);max.max(point)
            }
            val span=Vector3f(max).sub(min)
            Vector3f(min).add(max).mul(.5f) to (.9f/maxOf(span.x,span.y,span.z,.01f))
        }
        fun pose(animation:String,time:Float, engineOffsets: Map<Int, Float> = emptyMap(),
            overlay: String? = null, overlayTime: Float = 0f, motion: Vector3f? = null, spin: Float? = null, overlayNodes: (String) -> Boolean = { false }):List<Matrix4f> {
            val key = PoseKey(animation, if (animation in stationaryClips || clips[animation].isNullOrEmpty()) 0f else time)
            val cacheable = overlay == null && engineOffsets.isEmpty() && motion == null && spin == null
            if (cacheable) poseCache[key]?.let { return it }
            val translations=nodes.map{Vector3f(it.t)};val rotations=nodes.map{Quaternionf(it.r)};val scales=nodes.map{Vector3f(it.s)}
            fun apply(c: Channel, tt: Float) {
                var i=0;while(i<c.times.lastIndex&&c.times[i+1]<=tt)i++
                val j=minOf(i+1,c.times.lastIndex);val mix=if(i==j||c.step)0f else ((tt-c.times[i])/(c.times[j]-c.times[i])).coerceIn(0f,1f)
                val a=c.values[i];val b=c.values[j]
                when(c.path){"translation"->translations[c.node].set(a[0],a[1],a[2]).lerp(Vector3f(b[0],b[1],b[2]),mix);"rotation"->rotations[c.node].set(a[0],a[1],a[2],a[3]).slerp(Quaternionf(b[0],b[1],b[2],b[3]),mix);"scale"->scales[c.node].set(a[0],a[1],a[2]).lerp(Vector3f(b[0],b[1],b[2]),mix)}
            }
            clips[animation].orEmpty().forEach { apply(it, time) }
            overlay?.let { clips[it].orEmpty().filter { c -> overlayNodes(nodes[c.node].name) }.forEach { c -> apply(c, overlayTime) } }
            // Native generic drone has separate forward/back tilt and asymmetric lateral folds.
            // Apply only those engine joints, leaving the weapon and release/return motion intact.
            motion?.let { input ->
                val neutral = clips["flight"].orEmpty().filter { it.path == "rotation" }.associateBy { it.node }
                fun thrust(clip: String, weight: Float) {
                    if (weight <= .0001f) return
                    for (c in clips[clip].orEmpty()) {
                        if (c.path != "rotation" || !nodes[c.node].name.startsWith("engine_") || nodes[c.node].name.endsWith("_rotor")) continue
                        val base = neutral[c.node]?.values?.first() ?: continue
                        val target = c.values.first()
                        val delta = Quaternionf(base[0], base[1], base[2], base[3]).invert()
                            .mul(Quaternionf(target[0], target[1], target[2], target[3]))
                        rotations[c.node].mul(Quaternionf().slerp(delta, weight.coerceAtMost(1f)))
                    }
                }
                thrust(if (input.x >= 0f) "thrust_forward" else "thrust_back", kotlin.math.abs(input.x))
                thrust(if (input.y >= 0f) "thrust_left" else "thrust_right", kotlin.math.abs(input.y))
                for (exhaust in exhausts) rotations[exhaust.node].rotateX(input.z)
            }
            spin?.let { tt -> clips["spin"].orEmpty().filter {
                nodes[it.node].name.endsWith("_rotor")
            }.forEach { apply(it, tt) } }
            for ((node, radians) in engineOffsets) rotations[node].rotateX(radians)
            val result=arrayOfNulls<Matrix4f>(nodes.size)
            fun world(i:Int):Matrix4f {result[i]?.let{return it};val n=nodes[i];val m=Matrix4f().translationRotateScale(translations[i],rotations[i],scales[i]);if(n.parent>=0)m.set(Matrix4f(world(n.parent)).mul(m));result[i]=m;return m}
            val sampled = nodes.indices.map { world(it) }
            if (cacheable) {
                poseCache[key] = sampled
            }
            return sampled
        }
    }
    private var models=emptyMap<String,Model>()
    override fun onResourceManagerReload(manager:ResourceManager) {
        val dogs = listOf("guard_dog", "laser_guard_dog", "stun_guard_dog", "flame_guard_dog", "gas_guard_dog")
            .flatMap { listOf(it, it + "_backpack") }
        val withLod = listOf("bioprocessor", "refinery", "bioprocessor_container", "refinery_container") + dogs
        models=(withLod.flatMap { listOf(it, it + "_lod") } + listOf("tactical_terminal", "sample_container", "item_delivery_rack", "resupply_rack", "supply_case")).associateWith { name ->
            val json=manager.open(ResourceLocation(MOD_ID,"models/mesh/$name.mesh.json.gz")).use{GZIPInputStream(it).reader().use(JsonParser::parseReader)}.asJsonObject
            fun floats(a:JsonArray)=a.map{it.asFloat}.toFloatArray()
            fun vector(a:JsonArray)=floats(a).let{Vector3f(it[0],it[1],it[2])}
            val nodes=json.getAsJsonArray("nodes").map{raw->val n=raw.asJsonObject;val r=floats(n.getAsJsonArray("r"));Node(n["parent"].asInt,n["mesh"].asInt,vector(n.getAsJsonArray("t")),Quaternionf(r[0],r[1],r[2],r[3]),vector(n.getAsJsonArray("s")),n["name"].asString)}
            val meshes=json.getAsJsonArray("meshes").map{raw->raw.asJsonArray.map{value->
                val p=value.asJsonObject;val positions=p.getAsJsonArray("p").map{vector(it.asJsonArray)};val uv=p.getAsJsonArray("uv").map{floats(it.asJsonArray)};val ids=p.getAsJsonArray("indices").map{it.asInt}
                val faces=p.getAsJsonArray("faces")?.map{it.asJsonArray.map{v->v.asInt}}?:ids.chunked(3)
                val data=FloatArray(faces.size*32);var cursor=0
                for(face in faces) {
                    val a=positions[face[0]];val b=positions[face[1]];val c=positions[face[2]]
                    val normal=Vector3f(b).sub(a).cross(Vector3f(c).sub(a));if(normal.lengthSquared()>1e-15f)normal.normalize()else normal.set(0f,1f,0f)
                    for(j in 0..3){val index=face[minOf(j,face.lastIndex)];val v=positions[index];for(f in floatArrayOf(v.x,v.y,v.z,uv[index][0],uv[index][1],normal.x,normal.y,normal.z))data[cursor++]=f}
                }
                Primitive(p["material"].asInt,data)
            }}
            val clips=json.getAsJsonObject("clips").entrySet().associate{(key,value)->key to value.asJsonArray.map{raw->
                val c=raw.asJsonObject
                val times=floats(c.getAsJsonArray("times"))
                val values=c.getAsJsonArray("values").map{floats(it.asJsonArray)}
                // Constant native tracks need neither key searching nor interpolation.
                val constant=values.all { it.contentEquals(values.first()) }
                Channel(c["node"].asInt,c["path"].asString,if(constant)floatArrayOf(times.first())else times,
                    if(constant)listOf(values.first())else values,c["interpolation"].asString=="STEP")
            }}
            val count=meshes.flatten().maxOfOrNull{it.material}?:0
            val exhausts = json.getAsJsonArray("exhausts")?.map { raw ->
                val exhaust = raw.asJsonObject
                Exhaust(exhaust["node"].asInt, vector(exhaust.getAsJsonArray("origin")),
                    exhaust.getAsJsonArray("axis")?.let(::vector) ?: Vector3f(0f, 0f, 1f))
            }.orEmpty()
            Model(nodes,meshes,clips,List(count+1){ResourceLocation(MOD_ID,"textures/mesh/${name.removeSuffix("_lod")}_$it.png")},json.getAsJsonArray("nozzles").map{raw->val a=raw.asJsonArray;net.minecraft.world.phys.Vec3(a[0].asDouble,a[1].asDouble,a[2].asDouble)},exhausts)
        }
    }
    fun nozzles(machine:com.example.sbwhelldiver.survival.MachineDeployment)=models[machine.id+"_container"]?.nozzles.orEmpty()
    fun draw(name:String,animation:String,time:Float,p:PoseStack,b:MultiBufferSource,light:Int,distanceSquared:Double=0.0, nodeYOffsets:Map<String,Float> = emptyMap(), hiddenNodeNames:Set<String> = emptySet()) {
        val selected=if(ModelLod.reduced(distanceSquared))name+"_lod" else name
        val model=models[selected]?:models[name]?:return
        draw(model,animation,time,p,b,light,nodeYOffsets, hiddenNodes = model.nodes.indices.filter { model.nodes[it].name in hiddenNodeNames }.toSet())
    }

    private val spareNode = Regex("mags_[lr]_0[1-4]_scale")

    data class GuardDogPackPose(val socket: Matrix4f, val magazine: Matrix4f?, val loader: Matrix4f?)
    internal var guardDogEngineObserver: ((String, Matrix4f) -> Unit)? = null
    internal var guardDogMuzzleObserver: ((Boolean, Vector3f) -> Unit)? = null

    internal fun guardDogModel(laser: Boolean, stun: Boolean, flame: Boolean, gas: Boolean = false) = when {
        gas -> "gas_guard_dog"
        flame -> "flame_guard_dog"
        stun -> "stun_guard_dog"
        laser -> "laser_guard_dog"
        else -> "guard_dog"
    }

    /** Muzzle in the gun node's authored local space, including its animated parents. */
    internal fun guardDogMuzzle(laser: Boolean, time: Float, stun: Boolean = false, flame: Boolean = false, gas: Boolean = false): Vector3f? {
        val model = models[guardDogModel(laser, stun, flame, gas)] ?: return null
        val gun = model.nodes.indexOfFirst { it.name == "gun_mount" }
        if (gun < 0) return null
        val bind = model.pose("", 0f)[gun]
        val marker = if (gas) com.example.sbwhelldiver.entity.GuardDogEntity.GAS_MUZZLE.toVector3f() else if (flame) com.example.sbwhelldiver.entity.GuardDogEntity.FLAME_MUZZLE.toVector3f() else if (stun) Vector3f(-0.0042053589005740626f / 16, -3.877977775969399f / 16, -9.98510232604056f / 16) else if (laser) Vector3f(-.003942f / 16, -4.602679f / 16, -4.998472f / 16)
            else Vector3f(0f, -3.399575f / 16, -5.55529f / 16)
        Matrix4f(bind).invert().transformPosition(marker)
        return model.pose("flight", time % (if (laser || stun || flame || gas) .15f else .4f))[gun].transformPosition(marker)
    }

    private fun guardDogMesh(name: String, reducedModel: Boolean): Model? =
        if (reducedModel) models[name + "_lod"] ?: models[name]
        else models[name]

    fun drawGuardDogPack(animation: String, time: Float, socketTime: Float, p: PoseStack, b: MultiBufferSource, light: Int,
        spares: Int, reload: String?, reloadTime: Float, magazineInTransit: Boolean, laser: Boolean = false, stun: Boolean = false, flame: Boolean = false, gas: Boolean = false, reducedModel: Boolean = false): GuardDogPackPose? {
        val model = guardDogMesh(guardDogModel(laser, stun, flame, gas) + "_backpack", reducedModel) ?: return null
        if (flame || gas) {
            val transforms = model.pose(animation, time, overlay = reload, overlayTime = reloadTime) {
                it.startsWith("feed_arm_") || it.startsWith("piston_") || it.startsWith("sleeve_")
            }.toMutableList()
            val hidden = mutableSetOf<Int>()
            val displayed = (spares - if (reload != null && reloadTime >= com.example.sbwhelldiver.entity.GuardDogEntity.FLAME_REFILL_TICK / 20f) 1 else 0)
                .coerceIn(0, com.example.sbwhelldiver.entity.GuardDogEntity.FLAME_RESERVES)
            val spent = com.example.sbwhelldiver.entity.GuardDogEntity.FLAME_RESERVES - displayed
            for ((side, used) in listOf("r" to (spent + 1) / 2, "l" to spent / 2)) {
                val index = model.nodes.indexOfFirst { it.name == "fuel_gauge_$side" }
                if (index < 0) continue
                if (used >= 3) hidden.add(index)
                else transforms[index] = Matrix4f(transforms[index]).scale(1f, (3 - used) / 3f, 1f)
            }
            draw(model, animation, time, p, b, light, matrices = transforms, hiddenNodes = hidden)
            val socket = model.nodes.indexOfFirst { it.name == "drone_attach" }
            return GuardDogPackPose(model.pose(animation, socketTime)[socket], null, null)
        }
        if (laser || stun) {
            val transforms = model.pose(animation, time)
            draw(model, animation, time, p, b, light, matrices = transforms)
            val socket = model.nodes.indexOfFirst { it.name == "drone_attach" }
            return GuardDogPackPose(model.pose(animation, socketTime)[socket], null, null)
        }
        val side = reload?.lastOrNull()
        // At the bottom of the native carrier stroke, slot 1 coincides with
        // slot 2 at rest. Reindex there so the remaining magazines rise together.
        val reindex = side != null && reloadTime >= .6f
        val transforms = model.pose(animation, time, overlay = reload, overlayTime = reloadTime) {
            it.startsWith("loading_") || reindex && it == "mags_${side}_boss"
        }
        fun index(name: String) = model.nodes.indexOfFirst { it.name == name }
        val spent = 8 - spares
        val hidden = mutableSetOf<Int>()
        for (bank in listOf('r', 'l')) {
            val count = 4 - (if (bank == 'r') (spent + 1) / 2 else spent / 2)
            val consumed = bank == side && reindex
            for (slot in 1..4) {
                if (slot > count - (if (consumed) 1 else 0)
                    || slot == 1 && bank == side && magazineInTransit && !reindex) {
                    hidden += index("mags_${bank}_0${slot}_scale")
                }
            }
        }
        var transfer: Matrix4f? = null
        var carrier: Matrix4f? = null
        if (magazineInTransit && side != null) {
            val drone = models["guard_dog"]!!
            val magazine = drone.nodes.indexOfFirst { it.name == "gun_magazine" }
            val spare = index("mags_${side}_01_scale")
            val arm = index("loading_${side}_arm_03")
            // Both meshes have the same shape but different pivots and local axes.
            // Match their actual geometry before hiding the magazine in the rack.
            transfer = Matrix4f(model.pose(animation, time)[spare])
                .translate(model.meshCenter(spare)).rotateX(-org.joml.Math.PI.toFloat() / 2f)
                .translate(Vector3f(drone.meshCenter(magazine)).negate())
            // The arm's origin is its hinge, not the grip. Retain the pickup offset
            // and follow only the arm's subsequent motion instead of lifting to its origin.
            val gripAtPickup = model.pose(animation, time, overlay = reload, overlayTime = .30f) {
                it.startsWith("loading_")
            }[arm]
            carrier = Matrix4f(transforms[arm]).mul(Matrix4f(gripAtPickup).invert()).mul(transfer)
        }
        draw(model, animation, time, p, b, light, matrices = transforms, hiddenNodes = hidden)
        val socket = index("drone_attach")
        return GuardDogPackPose(if (socketTime == time) transforms[socket] else model.pose(animation, socketTime)[socket],
            transfer, carrier)
    }

    fun drawGuardDog(animation: String, time: Float, p: PoseStack, b: MultiBufferSource, light: Int,
        enginePitch: (left: Boolean) -> Float, overlay: String? = null, overlayTime: Float = 0f,
        hideMagazine: Boolean = false, laser: Boolean = false, stun: Boolean = false, flame: Boolean = false, gas: Boolean = false, motion: Vector3f? = null, spin: Float? = null,
        reducedModel: Boolean = false, jet: (Matrix4f, Vector3f, Vector3f) -> Unit): Matrix4f? {
        val model = guardDogMesh(guardDogModel(laser, stun, flame, gas), reducedModel) ?: return null
        val engineOffsets = (if (laser || stun || flame || gas) emptyList() else model.exhausts).mapNotNull {
            val radians = enginePitch(model.nodes[it.node].t.x < 0f)
            if (radians == 0f) null else it.node to radians
        }.toMap()
        val transforms = model.pose(animation, time, engineOffsets, overlay, overlayTime,
            if (laser || stun || flame || gas) motion else null, if (laser || stun || flame || gas) spin else null) {
            it == "hinge" || it == "swing_arm" || it == "gun_mount"
        }
        val mag = model.nodes.indexOfFirst { it.name == "gun_magazine" }
        val gun = model.nodes.indexOfFirst { it.name == "gun_mount" }
        if (gun >= 0 && guardDogMuzzleObserver != null) {
            val marker = if (gas) com.example.sbwhelldiver.entity.GuardDogEntity.GAS_MUZZLE.toVector3f() else if (flame) com.example.sbwhelldiver.entity.GuardDogEntity.FLAME_MUZZLE.toVector3f() else if (stun) Vector3f(-0.0042053589005740626f / 16, -3.877977775969399f / 16, -9.98510232604056f / 16) else if (laser) Vector3f(-.003942f / 16, -4.602679f / 16, -4.998472f / 16)
                else Vector3f(0f, -3.399575f / 16, -5.55529f / 16)
            Matrix4f(model.pose("", 0f)[gun]).invert().transformPosition(marker)
            transforms[gun].transformPosition(marker)
            p.last().pose().transformPosition(marker)
            guardDogMuzzleObserver?.invoke(laser, marker)
        }
        draw(model, animation, time, p, b, light, matrices = transforms, hiddenNodes = if (hideMagazine) setOf(mag) else emptySet())
        for (exhaust in model.exhausts) {
            if ((laser || stun || flame || gas) && animation == "flight") guardDogEngineObserver?.invoke(model.nodes[exhaust.node].name, transforms[exhaust.node])
            jet(transforms[exhaust.node], exhaust.origin, exhaust.axis)
        }
        return if (mag >= 0) transforms[mag] else null
    }

    fun drawGuardDogMagazine(transform: Matrix4f, p: PoseStack, b: MultiBufferSource, light: Int) {
        val model = models["guard_dog"] ?: return
        val mag = model.nodes.indexOfFirst { it.name == "gun_magazine" }
        val matrices = List(model.nodes.size) { transform }
        draw(model, "", 0f, p, b, light, matrices = matrices, onlyNode = mag)
    }

    fun drawContainerItem(name:String,p:PoseStack,b:MultiBufferSource,light:Int) {
        drawModelItem(name+"_container",p,b,light)
    }
    fun drawModelItem(name:String,p:PoseStack,b:MultiBufferSource,light:Int) {
        val model=models[name]?:return
        val (center,scale)=model.itemFit
        p.pushPose();p.translate(.5,.5,.5);p.scale(scale,scale,scale);p.translate(-center.x,-center.y,-center.z)
        draw(model,"deploy",0f,p,b,light);p.popPose()
    }
    private fun draw(model:Model,animation:String,time:Float,p:PoseStack,b:MultiBufferSource,light:Int,nodeYOffsets:Map<String,Float> = emptyMap(),
        matrices: List<Matrix4f> = if (model.batches.any { it.node >= 0 }) model.pose(animation, time) else emptyList(),
        hiddenNodes: Set<Int> = emptySet(), onlyNode: Int? = null) {
        val position=Vector3f();val normal=Vector3f();val pose=p.last()
        val positions=arrayOfNulls<Matrix4f>(model.nodes.size)
        val normalsByNode=arrayOfNulls<Matrix3f>(model.nodes.size)
        var currentType:RenderType?=null
        var consumer:com.mojang.blaze3d.vertex.VertexConsumer?=null
        var bulk:PackedEntityVertices?=null
        for(batch in model.batches) {
            if (batch.node in hiddenNodes || onlyNode != null && batch.node != onlyNode) continue
            val matrix:Matrix4f
            val normals:Matrix3f
            if(batch.node>=0) {
                if(positions[batch.node]==null) {
                    val transform=Matrix4f(pose.pose())
                    nodeYOffsets[model.nodes[batch.node].name]?.let { transform.translate(0f,it,0f) }
                    positions[batch.node]=transform.mul(matrices[batch.node])
                    normalsByNode[batch.node]=Matrix3f(pose.normal()).mul(Matrix3f(matrices[batch.node]).invert().transpose())
                }
                matrix=positions[batch.node]!!;normals=normalsByNode[batch.node]!!
            } else {matrix=pose.pose();normals=pose.normal()}
                val type=if(batch.glow)model.glowTypes[batch.material]else model.surfaceTypes[batch.material]
                if(type!==currentType) {
                    bulk?.flush()
                    currentType=type
                    consumer=b.getBuffer(type)
                    bulk=PackedEntityVertices.create(consumer)
                }
                val vc=consumer!!;val data=batch.vertices
                val packedLight=if(batch.glow)LightTexture.FULL_BRIGHT else light
                if(PlanarMeshQuads.ENABLED) {
                    batch.geometry.draw(matrix,normals,vc,bulk,packedLight,OverlayTexture.NO_OVERLAY)
                    continue
                }
                var k=0
                while(k<data.size) {
                    normal.set(data[k+5],data[k+6],data[k+7]).mul(normals).normalize()
                    val packedNormal=PackedEntityVertices.packNormal(normal.x,normal.y,normal.z)
                    for(j in 0..3) {
                        val n=k+j*8;position.set(data[n],data[n+1],data[n+2]).mulPosition(matrix)
                        if(bulk!=null)bulk.vertexPackedNormal(position.x,position.y,position.z,-1,data[n+3],data[n+4],OverlayTexture.NO_OVERLAY,packedLight,packedNormal)
                        else vc.vertex(position.x,position.y,position.z,1f,1f,1f,1f,data[n+3],data[n+4],OverlayTexture.NO_OVERLAY,packedLight,normal.x,normal.y,normal.z)
                    };k+=32
                }
        }
        bulk?.flush()
    }
    fun faceCounts(name:String):Pair<Int,Int>?=models[name]?.let{it.sourceFaceCount to it.batches.sumOf{batch->batch.vertices.size/32}}
    fun transformCounts(name:String):Pair<Int,Int>?=models[name]?.let { model ->
        model.batches.sumOf { it.geometry.sourceVertices() } to model.batches.sumOf { it.geometry.uniquePositions() }
    }
    fun batchCount(name:String)=models[name]?.batches?.size?:0
    fun glowFaceCount(name:String)=models[name]?.batches?.filter{it.glow}?.sumOf{it.vertices.size/32}?:0
}
