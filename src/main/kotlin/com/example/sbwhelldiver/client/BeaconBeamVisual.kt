package com.example.sbwhelldiver.client

import com.mojang.blaze3d.vertex.PoseStack
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

/** All beacon columns share the after-cloud pass and the completed scene's depth. */
internal object BeaconBeamVisual {
    private val vertices=ArrayList<Float>()
    fun beginFrame()=vertices.clear()

    fun capture(pose:PoseStack,start:Float,height:Float,color:Int,strength:Float) {
        if(strength<=0f||ShaderPackCompat.shadowPass())return
        val matrix=pose.last().pose()
        val r=((color shr 16)and 255)/255f
        val g=((color shr 8)and 255)/255f
        val b=(color and 255)/255f
        for((radius,alpha)in listOf(.018f to .9f,.045f to .32f,.1f to .1f))for(i in 0..7) {
            val a=i*Math.PI/4;val c=(i+1)*Math.PI/4
            val x=(cos(a)*radius).toFloat();val z=(sin(a)*radius).toFloat()
            val xx=(cos(c)*radius).toFloat();val zz=(sin(c)*radius).toFloat()
            val corners=arrayOf(Vector3f(x,start,z),Vector3f(x,height,z),Vector3f(xx,height,zz),Vector3f(xx,start,zz))
            for(k in intArrayOf(0,1,2,0,2,3)) {
                val p=Vector3f(corners[k]).mulPosition(matrix)
                vertices.addAll(listOf(p.x,p.y,p.z,0f,0f,5f,r,g,b,alpha*strength))
            }
        }
    }

    fun collect():FloatArray=vertices.toFloatArray()
}
