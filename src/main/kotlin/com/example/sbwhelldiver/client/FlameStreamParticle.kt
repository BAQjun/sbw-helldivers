package com.example.sbwhelldiver.client

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.particle.SpriteSet
import net.minecraft.client.particle.TextureSheetParticle
import net.minecraft.core.particles.SimpleParticleType

/** Translucent white fuel smoke during warmup, full-bright flame while spraying. */
class FlameStreamParticle(level: ClientLevel,x: Double,y: Double,z: Double,
    vx: Double,vy: Double,vz: Double,sprites: SpriteSet,private val smoke: Boolean,private val soot: Boolean=false) : TextureSheetParticle(level,x,y,z) {
    private var initialAlpha=if(soot).24f else if(smoke).26f else .95f
    init {
        xd=vx;yd=vy;zd=vz;gravity=0f;friction=1f;hasPhysics=true
        lifetime=if(soot)14 else if(smoke)10 else 16
        quadSize=if(soot).17f else if(smoke).12f else .21f
        alpha=initialAlpha
        setColor(if(soot).10f else if(smoke).94f else 1f,if(soot).10f else if(smoke).96f else .76f,if(soot).11f else if(smoke).97f else .23f)
        pickSprite(sprites);setSize(.08f,.08f)
    }
    fun configureSoot(progress: Double) {
        if(!soot)return
        val amount=progress.coerceIn(0.0,1.0).toFloat()
        quadSize=.25f+.85f*amount
        initialAlpha=.24f+.14f*amount
        alpha=initialAlpha
    }
    override fun tick() {
        super.tick()
        quadSize+=if(soot).024f else if(smoke).017f else .03f
        alpha=initialAlpha*(1f-age.toFloat()/lifetime).coerceAtLeast(0f)
        if(onGround || (x==xo && y==yo && z==zo)) remove()
    }
    override fun getLightColor(partial: Float)=if(smoke || soot)super.getLightColor(partial) else 0xF000F0
    override fun getRenderType()=ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT
    class Provider(private val sprites: SpriteSet,private val smoke: Boolean,private val soot: Boolean=false): ParticleProvider<SimpleParticleType> {
        override fun createParticle(type: SimpleParticleType,level: ClientLevel,x: Double,y: Double,z: Double,vx: Double,vy: Double,vz: Double)=
            FlameStreamParticle(level,x,y,z,vx,vy,vz,sprites,smoke,soot)
    }
}
