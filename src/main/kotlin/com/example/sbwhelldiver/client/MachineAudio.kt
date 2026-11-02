package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.asset.AssetCatalog
import com.example.sbwhelldiver.survival.SampleMachineBlockEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Root block ticks supply nearby machines, independently of camera visibility. */
@EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object MachineAudio:net.minecraft.server.packs.resources.ResourceManagerReloadListener {
    private val sounds=WeakHashMap<SampleMachineBlockEntity,WorkingSound>()
    private var level:net.minecraft.client.multiplayer.ClientLevel?=null
    val activeCount get()=sounds.values.count{!it.isStopped}
    var starts=0;private set
    var stops=0;private set
    override fun onResourceManagerReload(manager:net.minecraft.server.packs.resources.ResourceManager) {
        sounds.values.forEach{it.finish()};sounds.clear()
    }
    fun observe(be:SampleMachineBlockEntity) {
        val mc=Minecraft.getInstance();val listener=mc.player?:return
        if(be.root!=be.blockPos || !be.working || be.isRemoved || be.level!==mc.level)return
        if(listener.distanceToSqr(be.blockPos.x+.5,be.blockPos.y+1.0,be.blockPos.z+.5)>64.0*64.0)return
        if(sounds[be]?.isStopped==false)return
        val sound=WorkingSound(be);sounds[be]=sound;starts++;mc.soundManager.play(sound)
    }
    @SubscribeEvent fun tick(event:ClientTickEvent.Post) {
        val mc=Minecraft.getInstance()
        if(level!==mc.level){sounds.values.forEach{it.finish()};sounds.clear();level=mc.level}
        val iterator=sounds.iterator()
        while(iterator.hasNext()) {
            val (be,sound)=iterator.next()
            if(be.isRemoved || !be.working || be.level!==mc.level || sound.isStopped){sound.finish();iterator.remove()}
        }
    }
    private class WorkingSound(be:SampleMachineBlockEntity):AbstractTickableSoundInstance(
        SoundEvent.createFixedRangeEvent(AssetCatalog.sound("machine_${be.machine.id}_loop"),64f),SoundSource.BLOCKS,RandomSource.create()) {
        private val reference=WeakReference(be)
        private val sourceLevel=be.level
        init{x=be.blockPos.x+.5;y=be.blockPos.y+1.0;z=be.blockPos.z+.5;looping=true;delay=0;volume=1f;pitch=1f}
        fun finish(){if(!isStopped){volume=0f;stop();stops++;Minecraft.getInstance().soundManager.stop(this)}}
        override fun tick() {
            val be=reference.get();val mc=Minecraft.getInstance()
            if(be==null || be.isRemoved || !be.working || mc.level!==sourceLevel || sourceLevel?.getBlockEntity(be.blockPos)!==be){finish();return}
            if(mc.player?.distanceToSqr(x,y,z)?.let{it>68.0*68.0}!=false)finish()
        }
    }
}
