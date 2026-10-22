package com.example.sbwhelldiver.command

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.PlayerHellpodEntity
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.marking.MarkSettings
import com.mojang.brigadier.arguments.BoolArgumentType
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.network.chat.Component
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid=MOD_ID)
object HelldiversCommands {
 @SubscribeEvent fun register(e:RegisterCommandsEvent){
  e.dispatcher.register(Commands.literal("helldivers").requires{it.hasPermission(2)}
   .then(Commands.literal("cooldown").executes { c -> cooldown(c, listOf(c.source.playerOrException), null) }
    .then(Commands.argument("players",EntityArgument.players()).executes { c -> cooldown(c,EntityArgument.getPlayers(c,"players"),null) })
    .then(Commands.literal("set").then(Commands.argument("enabled",BoolArgumentType.bool())
     .executes { c -> cooldown(c,listOf(c.source.playerOrException),BoolArgumentType.getBool(c,"enabled")) }
     .then(Commands.argument("players",EntityArgument.players()).executes { c -> cooldown(c,EntityArgument.getPlayers(c,"players"),BoolArgumentType.getBool(c,"enabled")) }))))
   .then(Commands.literal("drop").then(Commands.argument("players",EntityArgument.players())
    .executes{drop(it,null)}.then(Commands.argument("pos",Vec3Argument.vec3()).executes{drop(it,Vec3Argument.getVec3(it,"pos"))})))
   .then(Commands.literal("marking").executes{c->c.source.sendSuccess({Component.translatable("commands.sbw_helldiver.marking.status",MarkSettings.get(c.source.server).enabled)},false);1}
    .then(Commands.argument("enabled",BoolArgumentType.bool()).executes{c->
     val enabled=BoolArgumentType.getBool(c,"enabled");MarkSettings.set(c.source.server,enabled)
     c.source.sendSuccess({Component.translatable("commands.sbw_helldiver.marking.set",enabled)},true);1
    })))
 }
 private fun cooldown(c:com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack>,players:Collection<net.minecraft.server.level.ServerPlayer>,enabled:Boolean?):Int {
  for(p in players) {
   val value=enabled ?: !com.example.sbwhelldiver.stratagem.StratagemCooldowns.state(p).enabled
   com.example.sbwhelldiver.stratagem.StratagemCooldowns.setEnabled(p,value)
   c.source.sendSuccess({Component.translatable("commands.sbw_helldiver.cooldown.set",p.displayName,Component.translatable(if(value)"options.on" else "options.off"))},true)
  }
  return players.size
 }
 private fun drop(c:com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack>,at:Vec3?):Int {
  var count=0
  for(p in EntityArgument.getPlayers(c,"players")){
   if(!p.isAlive||p.isSpectator||p.vehicle is PlayerHellpodEntity||com.example.sbwhelldiver.entity.PlayerDrops.pending(p.uuid)!=null){c.source.sendFailure(Component.translatable("commands.sbw_helldiver.drop.unavailable",p.displayName));continue}
   val l=if(at==null)p.serverLevel() else c.source.level
   val base=at?:p.position()
   var landing:Vec3?=null
   // Separate simultaneous drops and avoid an existing pod's landing column.
   for(i in 0..24){
    val angle=i*2.399963229728653;val radius=if(i==0)0.0 else 4.0*kotlin.math.sqrt(i.toDouble())
    val x=base.x+kotlin.math.cos(angle)*radius;val z=base.z+kotlin.math.sin(angle)*radius
    if(!l.worldBorder.isWithinBounds(net.minecraft.core.BlockPos.containing(x,base.y,z)))continue
    l.getChunk(net.minecraft.core.BlockPos.containing(x,base.y,z))
    val y=if(at!=null&&i==0)base.y else l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,kotlin.math.floor(x).toInt(),kotlin.math.floor(z).toInt()).toDouble()
    val point=Vec3(x,y,z)
    if(!PlayerHellpodEntity.safeLanding(l,p,point)||com.example.sbwhelldiver.entity.PlayerDrops.reserved(l,point))continue
    if(l.getEntitiesOfClass(PlayerHellpodEntity::class.java,net.minecraft.world.phys.AABB(point,point).inflate(3.0,1500.0,3.0)){!it.isRemoved}.isNotEmpty())continue
    landing=point;break
   }
   if(landing==null){c.source.sendFailure(Component.translatable("commands.sbw_helldiver.drop.no_ground",p.displayName));continue}
   if(com.example.sbwhelldiver.entity.PlayerDrops.prepare(p,l,landing)){count++;c.source.sendSuccess({Component.translatable("commands.sbw_helldiver.drop.success",p.displayName)},true)}
  }
  return count
 }
}
