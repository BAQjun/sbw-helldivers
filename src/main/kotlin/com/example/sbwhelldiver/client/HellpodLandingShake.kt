package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.AbstractHellpodEntity
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ViewportEvent
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import kotlin.math.*

/** Brief landing impulses; never modify the player's actual aim or movement. */
@EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object HellpodLandingShake {
    private data class Impulse(val time:Long,val strength:Double,val seed:Double)
    private val impulses=ArrayList<Impulse>()
    private var world:net.minecraft.client.multiplayer.ClientLevel?=null
    fun impact(pod:AbstractHellpodEntity){
        val mc=Minecraft.getInstance();val player=mc.player?:return
        if(mc.level!==pod.level())return
        if(world!==mc.level){impulses.clear();world=mc.level}
        // An occupant uses an external camera above the pod, but feels the full impact.
        val distance=if(player.vehicle===pod)0.0 else player.position().distanceTo(pod.position())
        if(distance>=24.0)return
        val gain=(1.0-distance/24.0).pow(1.6)
        if(impulses.size>=8)impulses.removeAt(0)
        impulses.add(Impulse(pod.level().gameTime,gain,pod.id*.73))
    }
    fun apply(e:ViewportEvent.ComputeCameraAngles){
        val level=Minecraft.getInstance().level?:return
        if(world!==level){impulses.clear();world=level;return}
        val now=level.gameTime+e.partialTick
        impulses.removeAll{now-it.time>=10.0||now-it.time<0.0}
        var pitch=0.0;var yaw=0.0;var roll=0.0
        for(i in impulses){
            val age=now-i.time
            val envelope=(1.0-age/10.0).pow(2)*i.strength
            pitch+=sin(age*2.7)*3.8*envelope
            yaw+=sin(age*2.1+i.seed)*1.3*envelope
            roll+=sin(age*2.4+i.seed)*1.8*envelope
        }
        e.pitch+=pitch.coerceIn(-4.5,4.5).toFloat()
        e.yaw+=yaw.coerceIn(-1.8,1.8).toFloat()
        e.roll+=roll.coerceIn(-2.5,2.5).toFloat()
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    fun camera(e:ViewportEvent.ComputeCameraAngles){
        if(PlayerDropClient.mounted()==null)apply(e)
    }
    @SubscribeEvent fun logout(e:ClientPlayerNetworkEvent.LoggingOut){impulses.clear();world=null}
}
