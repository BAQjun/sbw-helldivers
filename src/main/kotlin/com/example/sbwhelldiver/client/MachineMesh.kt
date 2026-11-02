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
    private data class Batch(val node:Int,val material:Int,val glow:Boolean,val vertices:FloatArray)
    private data class Channel(val node:Int,val path:String,val times:FloatArray,val values:List<FloatArray>,val step:Boolean)
    private class Model(val nodes:List<Node>,meshes:List<List<Primitive>>,val clips:Map<String,List<Channel>>,val textures:List<ResourceLocation>,val nozzles:List<net.minecraft.world.phys.Vec3>) {
        val batches:List<Batch>
        init {
            val bind=arrayOfNulls<Matrix4f>(nodes.size)
            fun world(i:Int):Matrix4f {bind[i]?.let{return it};val n=nodes[i];val m=Matrix4f().translationRotateScale(n.t,n.r,n.s);if(n.parent>=0)m.set(Matrix4f(world(n.parent)).mul(m));bind[i]=m;return m}
            val moving=clips.values.flatten().map{it.node}.toSet()
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
            batches=grouped.map{(key,vertices)->Batch(key.first,key.second,key.third,vertices.toFloatArray())}
        }
        private fun nodeNameGlow(i:Int)=nodes[i].name.endsWith("_window") || nodes[i].name.endsWith("_light")
        var frame=Int.MIN_VALUE;var clip="";var matrices=emptyList<Matrix4f>()
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
        fun pose(animation:String,time:Float):List<Matrix4f> {
            val next=if(clips[animation].isNullOrEmpty())0 else (time*30).toInt()
            if(matrices.isNotEmpty()&&frame==next&&clip==animation)return matrices
            val translations=nodes.map{Vector3f(it.t)};val rotations=nodes.map{Quaternionf(it.r)};val scales=nodes.map{Vector3f(it.s)}
            for(c in clips[animation].orEmpty()) {
                val tt=time;var i=0;while(i<c.times.lastIndex&&c.times[i+1]<=tt)i++
                val j=minOf(i+1,c.times.lastIndex);val mix=if(i==j||c.step)0f else ((tt-c.times[i])/(c.times[j]-c.times[i])).coerceIn(0f,1f)
                val a=c.values[i];val b=c.values[j]
                when(c.path){"translation"->translations[c.node].set(a[0],a[1],a[2]).lerp(Vector3f(b[0],b[1],b[2]),mix);"rotation"->rotations[c.node].set(a[0],a[1],a[2],a[3]).slerp(Quaternionf(b[0],b[1],b[2],b[3]),mix);"scale"->scales[c.node].set(a[0],a[1],a[2]).lerp(Vector3f(b[0],b[1],b[2]),mix)}
            }
            val result=arrayOfNulls<Matrix4f>(nodes.size)
            fun world(i:Int):Matrix4f {result[i]?.let{return it};val n=nodes[i];val m=Matrix4f().translationRotateScale(translations[i],rotations[i],scales[i]);if(n.parent>=0)m.set(Matrix4f(world(n.parent)).mul(m));result[i]=m;return m}
            matrices=nodes.indices.map{world(it)};frame=next;clip=animation;return matrices
        }
    }
    private var models=emptyMap<String,Model>()
    override fun onResourceManagerReload(manager:ResourceManager) {
        models=listOf("bioprocessor","refinery","bioprocessor_container","refinery_container").flatMap{listOf(it,it+"_lod")}.associateWith{name->
            val json=manager.open(ResourceLocation.fromNamespaceAndPath(MOD_ID,"models/mesh/$name.mesh.json.gz")).use{GZIPInputStream(it).reader().use(JsonParser::parseReader)}.asJsonObject
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
            val clips=json.getAsJsonObject("clips").entrySet().associate{(key,value)->key to value.asJsonArray.map{raw->val c=raw.asJsonObject;Channel(c["node"].asInt,c["path"].asString,floats(c.getAsJsonArray("times")),c.getAsJsonArray("values").map{floats(it.asJsonArray)},c["interpolation"].asString=="STEP")}}
            val count=meshes.flatten().maxOfOrNull{it.material}?:0
            Model(nodes,meshes,clips,List(count+1){ResourceLocation.fromNamespaceAndPath(MOD_ID,"textures/mesh/${name.removeSuffix("_lod")}_$it.png")},json.getAsJsonArray("nozzles").map{raw->val a=raw.asJsonArray;net.minecraft.world.phys.Vec3(a[0].asDouble,a[1].asDouble,a[2].asDouble)})
        }
    }
    fun nozzles(machine:com.example.sbwhelldiver.survival.MachineDeployment)=models[machine.id+"_container"]?.nozzles.orEmpty()
    fun draw(name:String,animation:String,time:Float,p:PoseStack,b:MultiBufferSource,light:Int,distanceSquared:Double=0.0) {
        val selected=if(ModelLod.reduced(distanceSquared))name+"_lod" else name
        val model=models[selected]?:models[name]?:return
        draw(model,animation,time,p,b,light)
    }
    fun drawContainerItem(name:String,p:PoseStack,b:MultiBufferSource,light:Int) {
        // Retain complete panels when a packed crate is inspected up close.
        val model=models[name+"_container"]?:return
        val (center,scale)=model.itemFit
        p.pushPose();p.translate(.5,.5,.5);p.scale(scale,scale,scale);p.translate(-center.x,-center.y,-center.z)
        draw(model,"deploy",0f,p,b,light);p.popPose()
    }
    private fun draw(model:Model,animation:String,time:Float,p:PoseStack,b:MultiBufferSource,light:Int) {
        val matrices=if(model.batches.any{it.node>=0})model.pose(animation,time)else emptyList()
        val position=Vector3f();val normal=Vector3f();val pose=p.last()
        for(batch in model.batches) {
            val matrix=Matrix4f(pose.pose());val normals=Matrix3f(pose.normal())
            if(batch.node>=0){matrix.mul(matrices[batch.node]);normals.mul(Matrix3f(matrices[batch.node]).invert().transpose())}
                val texture=model.textures[batch.material]
                val type=if(batch.glow)RenderType.eyes(ResourceLocation.fromNamespaceAndPath(texture.namespace,texture.path.removeSuffix(".png")+"_glow.png"))else RenderType.entityCutoutNoCull(texture)
                val vc=b.getBuffer(type);val bulk=PackedEntityVertices.create(vc);val data=batch.vertices
                val packedLight=if(batch.glow)LightTexture.FULL_BRIGHT else light
                var k=0
                while(k<data.size) {
                    normal.set(data[k+5],data[k+6],data[k+7]).mul(normals).normalize()
                    val packedNormal=PackedEntityVertices.packNormal(normal.x,normal.y,normal.z)
                    for(j in 0..3) {
                        val n=k+j*8;position.set(data[n],data[n+1],data[n+2]).mulPosition(matrix)
                        if(bulk!=null)bulk.vertexPackedNormal(position.x,position.y,position.z,-1,data[n+3],data[n+4],OverlayTexture.NO_OVERLAY,packedLight,packedNormal)
                        else vc.vertex(position.x,position.y,position.z,1f,1f,1f,1f,data[n+3],data[n+4],OverlayTexture.NO_OVERLAY,packedLight,normal.x,normal.y,normal.z)
                    };k+=32
                };bulk?.flush()
        }
    }
    fun batchCount(name:String)=models[name]?.batches?.size?:0
    fun glowFaceCount(name:String)=models[name]?.batches?.filter{it.glow}?.sumOf{it.vertices.size/32}?:0
}
