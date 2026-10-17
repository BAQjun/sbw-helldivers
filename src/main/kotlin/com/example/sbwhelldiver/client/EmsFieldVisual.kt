package com.example.sbwhelldiver.client
import com.example.sbwhelldiver.entity.MortarFieldEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.phys.Vec3
import kotlin.math.*
/** Blue-white ground haze, flickering vertical discharges and pin-point flashes. */
object EmsFieldVisual {
    private fun quad(pose:PoseStack,vc:VertexConsumer,c:Vec3,w:Double,h:Double,color:FloatArray) {
        val cam=Minecraft.getInstance().entityRenderDispatcher.cameraOrientation();val right=org.joml.Vector3f(1f,0f,0f).rotate(cam);val up=org.joml.Vector3f(0f,1f,0f)
        val matrix=pose.last().pose()
        for(v in arrayOf(floatArrayOf(-1f,-1f,0f,0f),floatArrayOf(1f,-1f,1f,0f),floatArrayOf(1f,1f,1f,1f),floatArrayOf(-1f,1f,0f,1f))) {
            val p=org.joml.Vector3f(c.x.toFloat(),c.y.toFloat(),c.z.toFloat()).fma((v[0]*w*.5).toFloat(),right).fma((v[1]*h*.5).toFloat(),up)
            vc.vertex(matrix,p.x,p.y,p.z).color(color[0],color[1],color[2],color[3]).uv(v[2],v[3]).endVertex()
        }
    }
    fun render(e:MortarFieldEntity,partial:Float,pose:PoseStack,buffers:MultiBufferSource) {
        if(ShaderPackCompat.active()||ShaderPackCompat.shadowPass())return
        val fade=min((e.tickCount+partial)/6.0,e.remainingTicks/12.0).coerceIn(0.0,1.0)
        if(!TeslaRenderTypes.isReady())return
        val arcs=buffers.getBuffer(TeslaRenderTypes.ARC);val matrix=pose.last().pose()
        val camera=Minecraft.getInstance().gameRenderer.mainCamera.position.subtract(e.position())
        fun drawSegment(a:Vec3,b:Vec3,width:Double,alpha:Double) {
            val axis=b.subtract(a);val len=axis.length();if(len<.001)return
            val tangent=axis.scale(1/len);var side=tangent.cross(camera.subtract(a)).normalize();if(side.lengthSqr()<.01)side=Vec3(1.0,0.0,0.0)
            val packed=((log2(len/width)+5)/12*255).roundToInt().coerceIn(0,255);val aspect=2.0.pow(packed/255.0*12-5);val half=len/aspect
            side=side.scale(half);val start=a.subtract(tangent.scale(half));val end=b.add(tangent.scale(half))
            fun vertex(p:Vec3,u:Double,v:Float){arcs.vertex(matrix,p.x.toFloat(),p.y.toFloat(),p.z.toFloat()).color(packed,0,255,(alpha*255).toInt().coerceIn(0,255)).uv(u.toFloat(),v).endVertex()}
            vertex(start.subtract(side),0.0,0f);vertex(start.add(side),0.0,1f);vertex(end.add(side),aspect+2,1f);vertex(end.subtract(side),aspect+2,0f)
        }
        val flashes=mutableListOf<Vec3>()
        collect(e,partial,object:FieldArcGeometry.Sink {
            override fun segment(a:Vec3,b:Vec3,width:Double,alpha:Double)=drawSegment(a,b,width,alpha)
            override fun flash(at:Vec3,size:Double,alpha:Double){flashes.add(at)}
        })
        val plasma=buffers.getBuffer(TeslaRenderTypes.PLASMA)
        if(e.tickCount<9)quad(pose,plasma,Vec3(0.0,1.0,0.0),8.0,5.0,floatArrayOf(1f,1f,1f,((1-(e.tickCount+partial)/9.0)*.5).toFloat()))
        flashes.forEach{quad(pose,plasma,it,.40,.40,floatArrayOf(1f,1f,1f,(fade*.60).toFloat()))}
    }
    /** Shared branching/timing; compatible rendering changes only the material. */
    fun collect(e:MortarFieldEntity,partial:Float,sink:FieldArcGeometry.Sink) {
        val fade=min((e.tickCount+partial)/6.0,e.remainingTicks/12.0).coerceIn(0.0,1.0)
        for(i in 0 until 64) {
            val period=17+i%19;val age=(e.tickCount+i*11)%period
            if(age>=8)continue
            val cycle=(e.tickCount+i*11)/period;val rng=java.util.Random(e.id*317L+i*199L+cycle*827L)
            val a=rng.nextDouble()*PI*2;val r=sqrt(rng.nextDouble())*e.radius*.88
            val base=Vec3(cos(a)*r,.06+rng.nextDouble()*.35,sin(a)*r)
            val end=base.add((rng.nextDouble()-.5)*3.4,if(i%3==0).25+rng.nextDouble()*.6 else 1.3+rng.nextDouble()*1.8,(rng.nextDouble()-.5)*3.4)
            fun branch(start:Vec3,finish:Vec3,displacement:Double,depth:Int):List<Vec3> {
                if(depth==0)return listOf(start,finish)
                val mid=start.add(finish).scale(.5).add((rng.nextDouble()-.5)*displacement,(rng.nextDouble()-.5)*displacement,(rng.nextDouble()-.5)*displacement)
                return branch(start,mid,displacement*.52,depth-1).dropLast(1)+branch(mid,finish,displacement*.52,depth-1)
            }
            val points=branch(base,end,.9,4)
            val pulse=(.65+.35*sin(age*3.1+i)).coerceIn(.15,1.0)
            val alpha=(1-age/8.0).pow(.5)*fade*pulse
            points.zipWithNext().forEach{(p,q)->sink.segment(p,q,.18,alpha)}
            for(j in listOf(5,10)) {
                val origin=points[j];val tip=origin.add((rng.nextDouble()-.5)*1.5,.4+rng.nextDouble()*.7,(rng.nextDouble()-.5)*1.5)
                branch(origin,tip,.45,3).zipWithNext().forEach{(p,q)->sink.segment(p,q,.08,alpha*.7)}
            }
            if(age<3)sink.flash(points[8],.40,fade*.60)
        }
    }

}

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="sbw_helldiver",value=[net.minecraftforge.api.distmarker.Dist.CLIENT])
object FieldVolumePass {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    fun render(event:net.minecraftforge.client.event.RenderLevelStageEvent) { FieldVolumeRenderer.render(event) }
}
