package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.entity.PlayerHellpodEntity
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import com.example.sbwhelldiver.MOD_ID
import com.google.gson.JsonParser

/** Original player-entry event 1703780702 and near/far retro blend containers.
 * RTPC 3703045256 has no recovered name: camera distance is our adaptation of
 * its near/far blend input, rather than a claim about HD2's gameplay parameter.
 */
object PlayerDropAudio {
    private const val LOCATOR = "sfx_pod_lid"
    val entryCues = (1..3).map { SoundTimeline.Cue(0.0,"sfx_hellpod_player_entry_0$it",LOCATOR) }
    private data class StartupGroup(val route:Int,val variants:List<List<SoundTimeline.Cue>>)
    private var startupGroups=emptyList<StartupGroup>()
    fun reload(manager:ResourceManager){
        val resource=manager.getResource(ResourceLocation(MOD_ID,"helldivers/player_drop_audio.json")).orElseThrow()
        val root=resource.openAsReader().use{JsonParser.parseReader(it).asJsonObject}
        startupGroups=root["startup_groups"].asJsonArray.map{group->
            StartupGroup(group.asJsonObject["listener_route"].asInt,group.asJsonObject["variants"].asJsonArray.map{variant->variant.asJsonArray.map{
                val cue=it.asJsonObject
                SoundTimeline.Cue(cue["time"].asDouble,cue["effect"].asString,cue["locator"].asString)
            }})
        }
    }
    fun ignitionCues(entity:PlayerHellpodEntity):List<SoundTimeline.Cue> {
        // Four sampled Play actions in 10682639; select each random container once.
        // InitialDelay belongs to individual sounds, in addition to the Play delay.
        val mc=Minecraft.getInstance()
        val route=when{
            PlayerDropClient.belongsToLocalPlayer(entity)->0
            mc.player?.isSpectator==true && (mc.cameraEntity===entity || mc.cameraEntity?.vehicle===entity)->1
            else->2
        }
        return listOf(
            SoundTimeline.Cue(0.0,"sfx_hellpod_retro_start",LOCATOR),
            SoundTimeline.Cue(.12,"sfx_hellpod_flap_open",LOCATOR)
        )+startupGroups.filter{it.route==route}.flatMap{it.variants[entity.level().random.nextInt(it.variants.size)]}
    }
    fun isDescent(effect:String)=effect.startsWith("sfx_hellpod_player_") ||
        effect.startsWith("sfx_hellpod_retro") || effect.startsWith("sfx_hellpod_thruster")

    fun gain(entity:PlayerHellpodEntity,effect:String,onboard:Boolean):Float {
        val distance=if(onboard) 22.0 else Minecraft.getInstance().gameRenderer.mainCamera.position.distanceTo(entity.position())
        return layerGain(effect,distance,onboard,entity.brakeAge)
    }
    fun layerGain(effect:String,distance:Double,onboard:Boolean,brakeAge:Int):Float {
        // S-curve crossfades from player container 290347164. The exterior
        // container uses its own 50..125 m blend, not six full-volume parallel layers.
        val first=blend(distance,50.0,120.0)
        val second=blend(distance,if(onboard)80.0 else 50.0,if(onboard)220.0 else 125.0)
        val gain=when(effect){
            "sfx_hellpod_retro_layer_01"->first
            "sfx_hellpod_retro_layer_03","sfx_hellpod_retro_layer_04"->1.0-first
            "sfx_hellpod_retro_layer_05"->1.0-second
            "sfx_hellpod_retro_layer_06"->second
            "sfx_hellpod_retro_layer_02"->if(onboard) when{
                distance<=30->0.0;distance<50->(distance-30)/20
                distance<=100->1.0;distance<160->(160-distance)/60;else->0.0
            } else when{
                distance<=0->0.0;distance<20->distance/20
                distance<80->1.0-(distance-20)/60*.292054206
                distance<140->.707945794*(140-distance)/60;else->0.0
            }
            // SetGameParameter action in player thruster event ducks entry by 4 dB
            // over 500 ms so the ignition transient remains distinct from the wind.
            "sfx_hellpod_player_entry_01"->1.0-(1.0-.630957)* (brakeAge/10.0).coerceIn(0.0,1.0)
            "sfx_hellpod_player_entry_03"->1.0-(brakeAge/10.0).coerceIn(0.0,1.0)
            else->1.0
        }
        return gain.coerceIn(0.0,1.0).toFloat()
    }
    private fun blend(value:Double,start:Double,end:Double):Double {
        val t=((value-start)/(end-start)).coerceIn(0.0,1.0)
        return t*t*(3-2*t)
    }
}
