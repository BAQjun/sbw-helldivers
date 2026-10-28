package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.MOD_ID
import net.neoforged.neoforge.event.entity.EntityMountEvent
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod

@EventBusSubscriber(modid=MOD_ID)
object PlayerDropEvents {
 @SubscribeEvent fun mount(e:EntityMountEvent){val pod=e.entityBeingMounted as? PlayerHellpodEntity?:return
  if(!pod.level().isClientSide&&e.isDismounting&&!pod.releasing&&!pod.isRemoved&&e.entityMounting.isAlive)e.isCanceled=true
 }
 @SubscribeEvent fun protect(e:LivingIncomingDamageEvent){if(e.entity.vehicle is PlayerHellpodEntity||PlayerDrops.pending(e.entity.uuid)!=null)e.isCanceled=true}
 @SubscribeEvent fun logout(e:PlayerEvent.PlayerLoggedOutEvent){(e.entity as? net.minecraft.server.level.ServerPlayer)?.let{PlayerDrops.cancel(it)};(e.entity.vehicle as? PlayerHellpodEntity)?.let{it.release();it.discard()}}
 @SubscribeEvent fun dimension(e:PlayerEvent.PlayerChangedDimensionEvent){(e.entity.vehicle as? PlayerHellpodEntity)?.release()}
}
