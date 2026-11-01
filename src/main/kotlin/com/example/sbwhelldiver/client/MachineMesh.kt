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
    private val triangleQuad=intArrayOf(0,8,16,16)
    private data class Node(val parent:Int,val mesh:Int,val t:Vector3f,val r:Quaternionf,val s:Vector3f)
    private data class Primitive(val material:Int,val vertices:FloatArray)
    private data class Channel(val node:Int,val path:String,val times:FloatArray,val values:List<FloatArray>,val step:Boolean)
    private class Model(val nodes:List<Node>,val meshes:List<List<Primitive>>,val clips:Map<String,List<Channel>>,val textures:List<ResourceLocation>,val nozzles:List<net.minecraft.world.phys.Vec3>) {
        var frame=Int.MIN_VALUE;var clip="";var matrices=emptyList<Matrix4f>()
        fun pose(animation:String,time:Float):List<Matrix4f> {
            val next=(time*30).toInt()
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
        models=listOf("bioprocessor","refinery","bioprocessor_container","refinery_container").associateWith{name->
            val json=manager.open(ResourceLocation.fromNamespaceAndPath(MOD_ID,"models/mesh/$name.mesh.json.gz")).use{GZIPInputStream(it).reader().use(JsonParser::parseReader)}.asJsonObject
            fun floats(a:JsonArray)=a.map{it.asFloat}.toFloatArray()
            fun vector(a:JsonArray)=floats(a).let{Vector3f(it[0],it[1],it[2])}
            val nodes=json.getAsJsonArray("nodes").map{raw->val n=raw.asJsonObject;val r=floats(n.getAsJsonArray("r"));Node(n["parent"].asInt,n["mesh"].asInt,vector(n.getAsJsonArray("t")),Quaternionf(r[0],r[1],r[2],r[3]),vector(n.getAsJsonArray("s")))}
            val meshes=json.getAsJsonArray("meshes").map{raw->raw.asJsonArray.map{value->
                val p=value.asJsonObject;val positions=p.getAsJsonArray("p").map{vector(it.asJsonArray)};val uv=p.getAsJsonArray("uv").map{floats(it.asJsonArray)};val ids=p.getAsJsonArray("indices").map{it.asInt}
                val data=FloatArray(ids.size*8);var cursor=0
                for(k in ids.indices step 3) {
                    val a=positions[ids[k]];val b=positions[ids[k+1]];val c=positions[ids[k+2]]
                    val normal=Vector3f(b).sub(a).cross(Vector3f(c).sub(a));if(normal.lengthSquared()>1e-15f)normal.normalize()else normal.set(0f,1f,0f)
                    for(j in 0..2){val index=ids[k+j];val v=positions[index];for(f in floatArrayOf(v.x,v.y,v.z,uv[index][0],uv[index][1],normal.x,normal.y,normal.z))data[cursor++]=f}
                }
                Primitive(p["material"].asInt,data)
            }}
            val clips=json.getAsJsonObject("clips").entrySet().associate{(key,value)->key to value.asJsonArray.map{raw->val c=raw.asJsonObject;Channel(c["node"].asInt,c["path"].asString,floats(c.getAsJsonArray("times")),c.getAsJsonArray("values").map{floats(it.asJsonArray)},c["interpolation"].asString=="STEP")}}
            val count=meshes.flatten().maxOfOrNull{it.material}?:0
            Model(nodes,meshes,clips,List(count+1){ResourceLocation.fromNamespaceAndPath(MOD_ID,"textures/mesh/${name}_$it.png")},json.getAsJsonArray("nozzles").map{raw->val a=raw.asJsonArray;net.minecraft.world.phys.Vec3(a[0].asDouble,a[1].asDouble,a[2].asDouble)})
        }
    }
    fun nozzles(machine:com.example.sbwhelldiver.survival.MachineDeployment)=models[machine.id+"_container"]?.nozzles.orEmpty()
    fun draw(name:String,animation:String,time:Float,p:PoseStack,b:MultiBufferSource,light:Int) {
        val model=models[name]?:return;val matrices=model.pose(animation,time)
        val position=Vector3f();val normal=Vector3f();val pose=p.last()
        for((i,node)in model.nodes.withIndex())if(node.mesh>=0) {
            val matrix=Matrix4f(pose.pose()).mul(matrices[i]);val normals=Matrix3f(pose.normal()).mul(Matrix3f(matrices[i]).invert().transpose())
            for(primitive in model.meshes[node.mesh]) {
                val vc=b.getBuffer(RenderType.entityCutoutNoCull(model.textures[primitive.material]));val bulk=PackedEntityVertices.create(vc);val data=primitive.vertices
                var k=0
                while(k<data.size) {
                    // Entity render types use quads: duplicate the third triangle vertex.
                    for(j in triangleQuad) {
                        val n=k+j;position.set(data[n],data[n+1],data[n+2]).mulPosition(matrix);normal.set(data[n+5],data[n+6],data[n+7]).mul(normals).normalize()
                        if(bulk!=null)bulk.vertex(position.x,position.y,position.z,-1,data[n+3],data[n+4],OverlayTexture.NO_OVERLAY,light,normal.x,normal.y,normal.z)
                        else vc.vertex(position.x,position.y,position.z,1f,1f,1f,1f,data[n+3],data[n+4],OverlayTexture.NO_OVERLAY,light,normal.x,normal.y,normal.z)
                    };k+=24
                };bulk?.flush()
            }
        }
    }
}
