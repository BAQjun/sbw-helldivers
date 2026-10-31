package com.example.sbwhelldiver.client
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.network.DestroyerSnapshotPacket
import com.example.sbwhelldiver.stratagem.DestroyerAnchor
import com.example.sbwhelldiver.stratagem.DestroyerState
import com.example.sbwhelldiver.stratagem.DestroyerWarp
import com.google.gson.JsonParser
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import org.joml.Vector3f

@EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object DestroyerClient:ResourceManagerReloadListener {
 data class Tri(val points:Array<Vector3f>,val uv:Array<FloatArray>,val normal:Vector3f,val emissive:Boolean)
 private var mesh=emptyList<Tri>()
 private var ships=emptyList<DestroyerState>()
 private var own=DestroyerState()
 private var serverTime=0L;private var localTime=0L
 private var radius=1024.0
 var equipped=false;private set
 val transitionEnd get()=own.transitionEnd
 fun now(partial:Float=0f)=serverTime+((Minecraft.getInstance().level?.gameTime?:localTime)-localTime)+partial.toDouble()
 fun receive(p:DestroyerSnapshotPacket){
  serverTime=p.serverTime;localTime=Minecraft.getInstance().level?.gameTime?:0
  ships=p.data.getList("Ships",Tag.TAG_COMPOUND.toInt()).map{DestroyerState.load(it as net.minecraft.nbt.CompoundTag)}
  own=DestroyerState.load(p.data.getCompound("Own"));radius=p.data.getDouble("Range");equipped=p.data.getBoolean("Equipped")
 }
 fun available():Boolean {
  val p=Minecraft.getInstance().player?:return false
  return own.available(p.level().dimension().location().toString(),p.position(),radius,now().toLong())
 }
 @SubscribeEvent fun logout(e:ClientPlayerNetworkEvent.LoggingOut){ships=emptyList();own=DestroyerState();equipped=false;serverTime=0;localTime=0}
 override fun onResourceManagerReload(r:ResourceManager){
  val out=mutableListOf<Tri>()
  r.openAsReader(ResourceLocation.fromNamespaceAndPath(MOD_ID,"models/mesh/super_destroyer.mesh.json")).use{reader->
   JsonParser.parseReader(reader).asJsonObject.getAsJsonArray("triangles").forEach{raw->
    val a=raw.asJsonArray;val vv=a[1].asJsonArray.map{it.asJsonArray};val pp=vv.map{Vector3f(it[0].asFloat,it[1].asFloat,it[2].asFloat)}.toTypedArray()
    val n=Vector3f(pp[1]).sub(pp[0]).cross(Vector3f(pp[2]).sub(pp[0]))
    if(n.lengthSquared()>1e-15f)out.add(Tri(pp,vv.map{floatArrayOf(it[3].asFloat,it[4].asFloat)}.toTypedArray(),n.normalize(),a[0].asInt==1))
   }
  };mesh=out
 }
 private val corners=intArrayOf(0,1,2,2)
 private val buffers=MultiBufferSource.immediate(ByteBufferBuilder(1048576))
 private fun drawWarpTrail(e:RenderLevelStageEvent,flight:DestroyerWarp.Pose,camera:Vec3,factor:Double){
  val forward=flight.motion
  val head=flight.position.subtract(forward.scale(110.0))
  val tail=head.subtract(forward.scale(flight.trailLength))
  var right=forward.cross(head.subtract(camera)).normalize()
  if(right.lengthSqr()<1e-6)right=Vec3(1.0,0.0,0.0)
  val pose=e.poseStack.last();val vc=buffers.getBuffer(DestroyerRenderTypes.GLOW)
  fun ribbon(width:Double,r:Float,g:Float,b:Float,alpha:Float){
   val edge=right.scale(width)
   for(v in listOf(head.add(edge),tail.add(edge.scale(.12)),tail.subtract(edge.scale(.12)),head.subtract(edge))){
    val q=v.subtract(camera).scale(factor)
    vc.vertex(pose.pose(),q.x.toFloat(),q.y.toFloat(),q.z.toFloat()).color(r,g,b,alpha).uv(0f,0f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(pose.normal(),0f,1f,0f).endVertex()
   }
  }
  ribbon(4.5,.25f,.55f,1f,.4f);ribbon(1.4,.8f,.93f,1f,.95f)
  buffers.endBatch()
 }
 @SubscribeEvent fun render(e:RenderLevelStageEvent){
  if(ShaderPackCompat.shadowPass())return
  if(Minecraft.getInstance().level?.dimension()==net.minecraft.world.level.Level.NETHER)return
  val mc=Minecraft.getInstance();val level=mc.level?:return
  if(e.stage==RenderLevelStageEvent.Stage.AFTER_SKY){
   val fogStart=RenderSystem.getShaderFogStart();val fogEnd=RenderSystem.getShaderFogEnd()
   RenderSystem.setShaderFogStart(4096f);RenderSystem.setShaderFogEnd(8192f)
   // AFTER_SKY precedes the entity pass that installs camera rotation globally in 1.21.
   // Its event pose is identity, so apply the supplied view to both the hull and warp trail.
   val skyPose=e.poseStack;skyPose.pushPose();skyPose.mulPose(e.modelViewMatrix)
   try{
    var drewShip=false
    val dimension=level.dimension().location().toString();val camera=e.camera.position
    for(flight in ships.flatMap{DestroyerWarp.poses(it,dimension,now(e.partialTick.getGameTimeDeltaPartialTick(false)))}){
     val a=flight.anchor
     val delta=flight.position.subtract(camera);val distance=delta.length();if(distance>16384||distance<1)continue
     val factor=minOf(1.0,96.0/distance);val p=e.poseStack;p.pushPose()
     try{
      p.translate(delta.x*factor,delta.y*factor,delta.z*factor);p.scale(factor.toFloat(),factor.toFloat(),factor.toFloat());p.mulPose(Axis.YP.rotationDegrees(flight.yaw))
      val pose=p.last();val vc=buffers.getBuffer(DestroyerRenderTypes.HULL)
      for(t in mesh){
       val shade=if(t.emissive)1f else .72f
       for(k in corners){val v=t.points[k];val uv=t.uv[k];vc.vertex(pose.pose(),v.x,v.y,v.z).color(shade,shade,shade,1f).uv(uv[0],uv[1]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(pose.normal(),t.normal.x,t.normal.y,t.normal.z).endVertex()}
      }
      buffers.endBatch();drewShip=true
     }finally{p.popPose()}
     if(flight.trailLength>0)drawWarpTrail(e,flight,camera,factor)
    }
    if(drewShip){RenderSystem.depthMask(true);RenderSystem.clear(256,Minecraft.ON_OSX)}
   }finally{skyPose.popPose();RenderSystem.setShaderFogStart(fogStart);RenderSystem.setShaderFogEnd(fogEnd)}
  }
 }
}
