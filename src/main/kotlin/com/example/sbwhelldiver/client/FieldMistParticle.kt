package com.example.sbwhelldiver.client

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.*
import net.minecraft.core.particles.SimpleParticleType

class FieldMistParticle(level: ClientLevel,x: Double,y: Double,z: Double,vx: Double,vy: Double,vz: Double,
                        private val sprites: SpriteSet,private val ems: Boolean) : TextureSheetParticle(level,x,y,z,vx,vy,vz) {
    init {
        xd=vx;yd=vy;zd=vz
        lifetime=45+random.nextInt(25)
        friction=.96f;gravity=0f
        quadSize=if(ems)1.65f else 2.0f
        alpha=0f
        if(ems) { rCol=.65f;gCol=.85f;bCol=.94f } else { rCol=.49f;gCol=.60f;bCol=.18f }
        setSpriteFromAge(sprites)
    }
    override fun tick() {
        super.tick()
        alpha=(kotlin.math.min(age/8f,(lifetime-age)/15f).coerceIn(0f,1f))*(if(ems).14f else .22f)
        setSpriteFromAge(sprites)
    }
    override fun getRenderType()=ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT
    class Provider(private val sprites: SpriteSet,private val ems: Boolean) : ParticleProvider<SimpleParticleType> {
        override fun createParticle(type: SimpleParticleType,level: ClientLevel,x: Double,y: Double,z: Double,vx: Double,vy: Double,vz: Double) =
            FieldMistParticle(level,x,y,z,vx,vy,vz,sprites,ems)
    }
}
