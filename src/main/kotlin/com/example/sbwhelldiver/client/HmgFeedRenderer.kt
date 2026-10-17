package com.example.sbwhelldiver.client

import com.atsuishio.superbwarfare.client.model.entity.VehicleModelInstance
import com.example.sbwhelldiver.MOD_ID
import com.google.gson.JsonParser
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import org.joml.Vector3f

/** Both source halves use one continuous skin field; their seam cannot pull apart. */
object HmgFeedRenderer: ResourceManagerReloadListener {
    private data class Corner(val position: Int,val uv: Int)
    private data class Mesh(val positions: Array<Vector3f>,val weights: FloatArray,
        val uvs: Array<FloatArray>,val faces: List<Array<Corner>>)
    private var meshes=emptyList<Mesh>()
    private val texture=ResourceLocation(MOD_ID,"textures/bedrock/vehicle/hmg_hellpod_v1.png")
    override fun onResourceManagerReload(resources: ResourceManager) {
        val parsed=resources.openAsReader(ResourceLocation(MOD_ID,"helldivers/hmg_feed.json")).use {JsonParser.parseReader(it).asJsonObject}
        val width=parsed["texture_width"].asFloat;val height=parsed["texture_height"].asFloat
        meshes=parsed["meshes"].asJsonArray.map { item ->
            val raw=item.asJsonObject["poly_mesh"].asJsonObject
            val points=raw["positions"].asJsonArray.map { a ->
                val p=a.asJsonArray;Vector3f(-p[0].asFloat/16f,p[1].asFloat/16f,p[2].asFloat/16f)
            }.toTypedArray()
            val weights=FloatArray(points.size) {i -> skinWeight(points[i].y*16f)}
            val uv=raw["uvs"].asJsonArray.map { a -> val p=a.asJsonArray;floatArrayOf(p[0].asFloat/width,1f-p[1].asFloat/height)}.toTypedArray()
            val faces=raw["polys"].asJsonArray.map { a ->
                val p=a.asJsonArray;Array(3) {i -> val ids=p[i].asJsonArray;Corner(ids[0].asInt,ids[2].asInt)}
            }
            require(faces.all {f->f.all {it.position in points.indices && it.uv in uv.indices}})
            Mesh(points,weights,uv,faces)
        }
        require(meshes.size==4)
    }
    internal fun skinWeight(modelY: Float): Float {
        val t=((modelY-18f)/11f).coerceIn(0f,1f)
        return t*t*(3f-2f*t)
    }
    fun render(instance: VehicleModelInstance,pose: PoseStack,buffers: MultiBufferSource,light: Int,wreck: Boolean) {
        val bottom=instance.getIndex("turret");val top=instance.getIndex("barrel")
        if(bottom<0 || top<0)return
        // Read the same evaluated matrices as the gun: lift, fold, live pitch and yaw
        // stay synchronized even when a phase packet arrives between render frames.
        val lower=instance.getGlobalTransform(bottom);val upper=instance.getGlobalTransform(top)
        val buffer=buffers.getBuffer(RenderType.entityCutoutNoCull(texture))
        val matrix=pose.last().pose();val normals=pose.last().normal();val color=if(wreck)77 else 255
        val a=Vector3f();val b=Vector3f();val edge=Vector3f();val normal=Vector3f()
        for(mesh in meshes) {
            val deformed=Array(mesh.positions.size) {i ->
                lower.transformPosition(mesh.positions[i],a)
                upper.transformPosition(mesh.positions[i],b)
                Vector3f(a).lerp(b,mesh.weights[i])
            }
            for(face in mesh.faces) {
                val p0=deformed[face[0].position];val p1=deformed[face[1].position];val p2=deformed[face[2].position]
                edge.set(p1).sub(p0);normal.set(p2).sub(p0);edge.cross(normal,normal)
                if(normal.lengthSquared()<1e-12f)continue
                normal.normalize()
                // The entity render type consumes quads; repeat the triangle's last corner.
                for(i in 0..3) {
                    val corner=face[minOf(i,2)];val p=deformed[corner.position];val uv=mesh.uvs[corner.uv]
                    buffer.vertex(matrix,p.x,p.y,p.z).color(color,color,color,255).uv(uv[0],uv[1])
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                        .normal(normals,normal.x,normal.y,normal.z).endVertex()
                }
            }
        }
    }
}
