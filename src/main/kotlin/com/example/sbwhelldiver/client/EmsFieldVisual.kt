package com.example.sbwhelldiver.client
import com.example.sbwhelldiver.entity.MortarFieldEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.phys.Vec3
import kotlin.math.*
/** An airy volume of suspended blue-white charge, fine short discharges above a brief vanilla impact explosion. */
object EmsFieldVisual {
    private fun quad(pose:PoseStack,vc:VertexConsumer,c:Vec3,w:Double,h:Double,color:FloatArray) {
        val cam=Minecraft.getInstance().entityRenderDispatcher.cameraOrientation();val right=org.joml.Vector3f(1f,0f,0f).rotate(cam);val up=org.joml.Vector3f(0f,1f,0f).rotate(cam)
        val matrix=pose.last().pose()
        for(v in arrayOf(floatArrayOf(-1f,-1f,0f,0f),floatArrayOf(1f,-1f,1f,0f),floatArrayOf(1f,1f,1f,1f),floatArrayOf(-1f,1f,0f,1f))) {
            val p=org.joml.Vector3f(c.x.toFloat(),c.y.toFloat(),c.z.toFloat()).fma((v[0]*w*.5).toFloat(),right).fma((v[1]*h*.5).toFloat(),up)
            vc.vertex(matrix,p.x,p.y,p.z).color(color[0],color[1],color[2],color[3]).uv(v[2],v[3]).endVertex()
        }
    }
    fun render(e:MortarFieldEntity,partial:Float,pose:PoseStack,buffers:MultiBufferSource) {
        if(ShaderPackCompat.active()||ShaderPackCompat.shadowPass())return
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
        data class Flash(val at:Vec3,val size:Double,val alpha:Double)
        val flashes=ArrayList<Flash>()
        collect(e,partial,object:FieldArcGeometry.Sink {
            override fun segment(a:Vec3,b:Vec3,width:Double,alpha:Double)=drawSegment(a,b,width,alpha)
            override fun flash(at:Vec3,size:Double,alpha:Double){flashes.add(Flash(at,size,alpha))}
        })
        val plasma=buffers.getBuffer(TeslaRenderTypes.PLASMA)
        flashes.forEach{quad(pose,plasma,it.at,it.size*2,it.size*2,floatArrayOf(1f,1f,1f,it.alpha.toFloat()))}
    }

    // Stateless integer hashing keeps positions stable between frames without allocating recursive
    // lightning trees or a fresh random generator for every filament on every frame.
    private fun noise(seed:Int):Double {
        var n=seed
        n=(n xor (n ushr 16))*0x45d9f3b
        n=(n xor (n ushr 16))*0x45d9f3b
        return ((n xor (n ushr 16)).toLong() and 0xffffffffL)/4294967296.0
    }
    /** Shared geometry and sub-tick animation for vanilla and shader rendering. */
    fun collect(e:MortarFieldEntity,partial:Float,sink:FieldArcGeometry.Sink) {
        val age=e.lifetimeTicks-e.remainingTicks+partial.toDouble()
        val fade=min(age/5.0,(e.remainingTicks-partial)/14.0).coerceIn(0.0,1.0)
        val seed=e.id*317
        val height=e.fieldHeight
        val motes=(e.radius*9).roundToInt().coerceIn(36,120)
        for(i in 0 until motes) {
            val key=seed+i*199
            val angle=noise(key)*PI*2
            val radius=sqrt(noise(key+1))*e.radius*.94
            val phase=noise(key+2)*PI*2
            val y=.18+noise(key+3)*(height-.35)+.10*sin(age*.045+phase)
            val at=Vec3(cos(angle)*radius+.10*sin(age*.024+phase),y,sin(angle)*radius+.10*cos(age*.029+phase))
            val shimmer=(.5+.5*sin(age*(.13+noise(key+4)*.19)+phase)).pow(3)
            sink.flash(at,.13+noise(key+5)*.10,fade*(.16+shimmer*.84))
            // Occasional soft, slender downward charge trails, rather than thick electric ropes.
            if(i%4==0) sink.segment(at,at.add(.025,-.22-noise(key+6)*.32,.015),.035,fade*shimmer*.55)
        }
        for(i in 0 until 32) {
            val key=seed+i*827
            val period=19+(noise(key)*30).toInt()
            val clock=age+noise(key+1)*period
            val cycle=floor(clock/period).toInt()
            val t=clock-cycle*period
            if(t>=3.0)continue
            val k=key+cycle*1999
            val angle=noise(k+2)*PI*2
            val radius=sqrt(noise(k+3))*e.radius*.90
            val at=Vec3(cos(angle)*radius,.12+noise(k+4)*height*.83,sin(angle)*radius)
            val end=at.add((noise(k+5)-.5)*1.2,min(.28+noise(k+6)*.7,height-.06-at.y),(noise(k+7)-.5)*1.2)
            val alpha=fade*sin(t/3.0*PI).coerceAtLeast(0.0)*(.4+noise(k+8)*.35)
            var last=at
            for(j in 1..7) {
                val f=j/7.0
                val jitter=sin(f*PI)*.12
                val next=at.lerp(end,f).add((noise(k+j*23)-.5)*jitter,(noise(k+j*23+1)-.5)*jitter,(noise(k+j*23+2)-.5)*jitter)
                sink.segment(last,next,.055,alpha)
                last=next
            }
            sink.flash(end,.14,alpha*.65)
        }

    }

}

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="sbw_helldiver",value=[net.minecraftforge.api.distmarker.Dist.CLIENT])
object FieldVolumePass {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    fun render(event:net.minecraftforge.client.event.RenderLevelStageEvent) { FieldVolumeRenderer.render(event) }
}
