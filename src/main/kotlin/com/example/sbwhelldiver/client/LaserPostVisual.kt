package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.*
import com.atsuishio.superbwarfare.client.model.entity.VehicleModelInstance
import com.google.gson.JsonParser
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.*
import net.minecraft.world.phys.*
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import org.joml.Vector3f
import java.util.WeakHashMap
import kotlin.math.*

/** World-space laser effects, composed after shader-pack tonemapping. Display
 * points use the same evaluated bone and camera matrices as the native model. */
@EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object LaserPostVisual:ResourceManagerReloadListener {
    private data class Mesh(val segment:Int,val points:List<Vector3f>,val faces:List<List<IntArray>>,val uvs:List<FloatArray>)
    private data class Display(val time:Long,val data:FloatArray)
    private data class Spark(var p:Vec3,var previous:Vec3,var v:Vec3,var age:Int,val life:Int)
    private var meshes=emptyList<Mesh>()
    private val displays=WeakHashMap<SentryHellpodEntity,Display>()
    private val sparks=ArrayList<Spark>()
    private var world:net.minecraft.client.multiplayer.ClientLevel?=null
    var emittedSparks=0L;private set
    private fun world(){val next=Minecraft.getInstance().level;if(next!==world){world=next;sparks.clear();displays.clear()}}
    override fun onResourceManagerReload(resources:ResourceManager){
        val json=resources.openAsReader(ResourceLocation.fromNamespaceAndPath(MOD_ID,"helldivers/laser_displays.json")).use{JsonParser.parseReader(it).asJsonObject}
        meshes=json["meshes"].asJsonArray.map{raw->
            val m=raw.asJsonObject;val index=m["segment"].asInt
            Mesh(index,m["positions"].asJsonArray.map{val p=it.asJsonArray;Vector3f(-p[0].asFloat/16,p[1].asFloat/16,p[2].asFloat/16)},
                m["faces"].asJsonArray.map{face->face.asJsonArray.map{val c=it.asJsonArray;intArrayOf(c[0].asInt,c[1].asInt)}},
                m["uvs"].asJsonArray.map{val uv=it.asJsonArray;floatArrayOf(uv[0].asFloat,uv[1].asFloat)})
        }
        check(meshes.size==21);displays.clear()
    }
    fun capture(s:SentryHellpodEntity,instance:VehicleModelInstance,pose:PoseStack){
        if(ShaderPackCompat.shadowPass()||s.isWreck||s.phase==AbstractHellpodEntity.Phase.SPENT)return
        world();val index=instance.getIndex("barrel");if(index<0)return
        val bone=instance.getGlobalTransform(index);val model=pose.last().pose()
        val camera=Minecraft.getInstance().gameRenderer.mainCamera.position
        val count=if(s.phase==AbstractHellpodEntity.Phase.ACTIVE)ceil(s.laserHeat/250.0*20).toInt().coerceIn(0,20)else 0
        val data=ArrayList<Float>()
        for(mesh in meshes){
            if(mesh.segment>=count)continue
            val points=mesh.points.map{raw->val p=Vector3f(raw);bone.transformPosition(p);model.transformPosition(p);Vec3(p.x.toDouble(),p.y.toDouble(),p.z.toDouble()).add(camera)}
            val color=if(mesh.segment<0)floatArrayOf(.017f,.024f,.028f)else floatArrayOf(1f,1f,1f)
            for(face in mesh.faces){
                val a=points[face[0][0]];val b=points[face[1][0]];val c=points[face[2][0]]
                // Bedrock mirrors X while baking. The resulting winding is
                // reversed, so its cross-product cannot choose the outward side.
                // Bias each visible screen towards the eye, never into its backing.
                var normal=b.subtract(a).cross(c.subtract(a)).normalize()
                if(normal.dot(camera.subtract(a))<0)normal=normal.scale(-1.0)
                normal=normal.scale(if(mesh.segment<0).0006 else .0016)
                for(corner in face){val p=points[corner[0]].add(normal);val uv=mesh.uvs[corner[1]];data.addAll(listOf(p.x.toFloat(),p.y.toFloat(),p.z.toFloat(),uv[0],uv[1],if(mesh.segment<0)2f else 3f,color[0],color[1],color[2],1f))}
            }
        }
        displays[s]=Display(System.nanoTime(),data.toFloatArray())
    }
    fun overheat(s:SentryHellpodEntity,count:Int){
        world();val rng=s.level().random;val axis=s.getShootVec("Main",1f).normalize()
        val at=s.getShootPos("Main",1f).subtract(axis.scale(.45))
        repeat(count){
            val p=at.add((rng.nextDouble()-.5)*.3,(rng.nextDouble()-.5)*.3,(rng.nextDouble()-.5)*.3)
            val v=Vec3((rng.nextDouble()-.5)*.15,.04+rng.nextDouble()*.09,(rng.nextDouble()-.5)*.15)
            sparks.add(Spark(p,p,v,0,7+rng.nextInt(8)));emittedSparks++
        }
        if(sparks.size>600)sparks.subList(0,sparks.size-600).clear()
    }
    @SubscribeEvent fun advance(e:net.neoforged.neoforge.client.event.ClientTickEvent.Post){
        world();if(Minecraft.getInstance().isPaused)return
        val it=sparks.iterator();while(it.hasNext()){
            val s=it.next();if(++s.age>=s.life){it.remove();continue}
            s.previous=s.p;s.p=s.p.add(s.v);s.v=s.v.scale(.96).add(0.0,-.008,0.0)
        }
    }
    fun collect(partial:Float,camera:Vec3,frustum:net.minecraft.client.renderer.culling.Frustum):FloatArray {
        world();val level=world?:return FloatArray(0);val data=ArrayList<Float>()
        fun vertex(p:Vec3,u:Float,v:Float,mode:Float,r:Float,g:Float,b:Float,a:Float){val q=p.subtract(camera);data.addAll(listOf(q.x.toFloat(),q.y.toFloat(),q.z.toFloat(),u,v,mode,r,g,b,a))}
        fun quad(a:Vec3,b:Vec3,c:Vec3,d:Vec3,mode:Float,r:Float,g:Float,blue:Float,alpha:Float){
            vertex(a,0f,0f,mode,r,g,blue,alpha);vertex(b,0f,1f,mode,r,g,blue,alpha);vertex(c,1f,1f,mode,r,g,blue,alpha)
            vertex(a,0f,0f,mode,r,g,blue,alpha);vertex(c,1f,1f,mode,r,g,blue,alpha);vertex(d,1f,0f,mode,r,g,blue,alpha)
        }
        val rotation=org.joml.Matrix3f().rotation(net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera.rotation());val rr=Vector3f(1f,0f,0f).mul(rotation);val uu=Vector3f(0f,1f,0f).mul(rotation)
        val right=Vec3(rr.x.toDouble(),rr.y.toDouble(),rr.z.toDouble());val up=Vec3(uu.x.toDouble(),uu.y.toDouble(),uu.z.toDouble())
        val now=System.nanoTime()
        for((entity,display)in displays){
            if(entity.isRemoved||entity.isWreck||entity.phase==AbstractHellpodEntity.Phase.SPENT||now-display.time>150_000_000)continue
            for(i in display.data.indices step 10){val a=display.data;val p=Vec3(a[i].toDouble(),a[i+1].toDouble(),a[i+2].toDouble());vertex(p,a[i+3],a[i+4],a[i+5],a[i+6],a[i+7],a[i+8],a[i+9])}
        }
        for(e in level.entitiesForRendering())if(e is SentryHellpodEntity&&e.profile.isLaser&&e.phase==AbstractHellpodEntity.Phase.ACTIVE&&e.laserState==LaserClock.State.FIRING&&e.beamLength>0){
            val start=LaserSentryClient.beamOrigin(e,partial);val axis=e.getShootVec("Main",partial).normalize();val end=start.add(axis.scale(e.beamLength.toDouble()))
            if(!frustum.isVisible(AABB(start,end).inflate(.3)))continue
            var side=axis.cross(camera.subtract(start)).normalize();if(side.lengthSqr()<.01)side=right
            val width=.16*(1+.04*sin((e.tickCount+partial)*1.7));val offset=side.scale(width)
            quad(start.subtract(offset),start.add(offset),end.add(offset),end.subtract(offset),0f,2.7f,1.8f,.08f,1f)
            if(e.beamLength<199){val r=right.scale(.13);val u=up.scale(.13);quad(end.subtract(r).subtract(u),end.subtract(r).add(u),end.add(r).add(u),end.add(r).subtract(u),1f,2.7f,1.9f,.6f,.85f)}
        }
        for(s in sparks){
            val p=s.previous.lerp(s.p,partial.toDouble());if(p.distanceToSqr(camera)>96*96)continue
            val tail=p.subtract(s.v.scale(.7));var side=s.v.cross(camera.subtract(p)).normalize();if(side.lengthSqr()<.01)side=right
            val r=side.scale(.025);val alpha=(1-(s.age-1+partial)/s.life).coerceIn(0f,1f)
            quad(tail.subtract(r),tail.add(r),p.add(r),p.subtract(r),0f,3f,1.5f,.22f,alpha)
        }
        return data.toFloatArray()
    }
}
