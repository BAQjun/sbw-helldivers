package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.stratagem.Stratagems
import com.example.sbwhelldiver.survival.*
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.*
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod.EventBusSubscriber
import net.minecraftforge.client.event.RenderLevelStageEvent
import org.joml.Matrix4f
import org.joml.Vector4f
import kotlin.math.sqrt

/** The real glass surface is shared by world rendering, camera placement and mouse picking. */
@EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object TacticalTerminalDisplay {
    const val WIDTH=340
    const val HEIGHT=156
    private const val SCALE=1.25f/WIDTH
    private val diagonal=1.0/sqrt(2.0)
    private var projection:Matrix4f?=null
    private var inverse:Matrix4f?=null
    private var eye=Vec3.ZERO
    private val common=ResourceLocation(MOD_ID,"textures/item/common_sample.png")
    private val rare=ResourceLocation(MOD_ID,"textures/item/rare_sample.png")
    private val superSample=ResourceLocation(MOD_ID,"textures/item/super_sample.png")
    private fun facing(e:TacticalTerminalBlockEntity)=e.blockState.getValue(TacticalTerminalBlock.FACING)
    private fun right(e:TacticalTerminalBlockEntity)=facing(e).counterClockWise.let{Vec3(it.stepX.toDouble(),0.0,it.stepZ.toDouble())}
    fun normal(e:TacticalTerminalBlockEntity)=facing(e).let{Vec3(it.stepX*diagonal,diagonal,it.stepZ*diagonal)}
    private fun up(e:TacticalTerminalBlockEntity)=facing(e).let{Vec3(-it.stepX*diagonal,diagonal,-it.stepZ*diagonal)}
    fun center(e:TacticalTerminalBlockEntity):Vec3 {
        val p=e.blockPos;val f=facing(e);val anchor=f.clockWise
        return Vec3(p.x+.5+anchor.stepX*.5+f.stepX*.17,p.y+1.06,p.z+.5+anchor.stepZ*.5+f.stepZ*.17)
            .add(normal(e).scale(.002))
    }
    @SubscribeEvent fun frame(event:RenderLevelStageEvent){
        if(event.stage!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return
        // Preserve the actual frame's FOV, aspect, camera roll and shader projection.
        projection=Matrix4f(event.projectionMatrix).mul(Matrix4f().rotationY(Math.PI.toFloat()).rotate(org.joml.Quaternionf(event.camera.rotation()).conjugate()))
        inverse=Matrix4f(projection!!).invert();eye=event.camera.position
    }
    fun screenPoint(e:TacticalTerminalBlockEntity,x:Double,y:Double):Pair<Double,Double>? {
        val m=projection?:return null;val mc=Minecraft.getInstance()
        val v=center(e).add(right(e).scale((x-WIDTH*.5)*SCALE)).add(up(e).scale((HEIGHT*.5-y)*SCALE)).subtract(eye)
        val clip=m.transform(Vector4f(v.x.toFloat(),v.y.toFloat(),v.z.toFloat(),1f))
        if(clip.w<=0)return null
        return (clip.x/clip.w*.5+.5)*mc.window.guiScaledWidth to (.5-clip.y/clip.w*.5)*mc.window.guiScaledHeight
    }
    fun pointer(e:TacticalTerminalBlockEntity,mouseX:Double,mouseY:Double):Pair<Double,Double>? {
        val m=inverse?:return null;val mc=Minecraft.getInstance()
        val x=(mouseX/mc.window.guiScaledWidth*2-1).toFloat();val y=(1-mouseY/mc.window.guiScaledHeight*2).toFloat()
        fun unproject(z:Float):Vec3 {
            val p=m.transform(Vector4f(x,y,z,1f));return Vec3(p.x.toDouble()/p.w,p.y.toDouble()/p.w,p.z.toDouble()/p.w).add(eye)
        }
        val origin=unproject(-1f);val ray=unproject(1f).subtract(origin).normalize();val n=normal(e)
        val denominator=ray.dot(n)
        if(denominator>=-1e-8)return null
        val distance=center(e).subtract(origin).dot(n)/denominator
        if(distance<0)return null
        val delta=origin.add(ray.scale(distance)).subtract(center(e))
        val px=WIDTH*.5+delta.dot(right(e))/SCALE;val py=HEIGHT*.5-delta.dot(up(e))/SCALE
        return if(px in 0.0..WIDTH.toDouble() && py in 0.0..HEIGHT.toDouble())px to py else null
    }
    fun draw(e:TacticalTerminalBlockEntity,partial:Float,p:PoseStack,b:MultiBufferSource){
        val mc=Minecraft.getInstance();val view=(mc.screen as? TacticalTerminalScreen)?.takeIf{it.terminalPos==e.blockPos}
        // Screen-right is local -X; this also keeps the font quads facing out of the glass.
        val frame=Matrix4f().setColumn(0,Vector4f(-1f,0f,0f,0f))
            .setColumn(1,Vector4f(0f,-diagonal.toFloat(),-diagonal.toFloat(),0f))
            .setColumn(2,Vector4f(0f,diagonal.toFloat(),-diagonal.toFloat(),0f))
            .setColumn(3,Vector4f(WIDTH*SCALE*.5f,1.06f+HEIGHT*SCALE*.5f*diagonal.toFloat()+.001414f,-.17f+HEIGHT*SCALE*.5f*diagonal.toFloat()-.001414f,1f))
        p.pushPose();p.mulPoseMatrix(frame);p.last().normal().mul(org.joml.Matrix3f(frame));p.scale(SCALE,SCALE,SCALE)
        val c=Canvas(p,b)
        c.fill(0,0,WIDTH,HEIGHT,0xFF07131A.toInt(),0f)
        if(center(e).distanceToSqr(mc.gameRenderer.mainCamera.position)<=32.0*32.0){
            c.fill(0,0,WIDTH,4,0xFFE2C122.toInt())
            c.text(Component.translatable("block.sbw_helldiver.tactical_terminal"),12,10,0xFFF3ECCE.toInt())
            c.text(Component.translatable("gui.sbw_helldiver.destroyer_uses",DestroyerClient.callsRemaining),180,10,0xFFFFDD55.toInt())
            val category=view?.category
            if(category==null){
                row(c,10,28,320,26,"destroyer_call",Component.translatable("gui.sbw_helldiver.replenish"),TacticalTerminalOffers.destroyerCall,null,view,partial)
                TerminalCategory.entries.forEachIndexed{i,categoryEntry->
                    val y=57+i*25;c.fill(10,y,320,22,background(view,10,y,320,22))
                    c.text(Component.translatable("gui.sbw_helldiver.category.${categoryEntry.name.lowercase()}"),20,y+7)
                    c.text(Component.literal(">"),314,y+7,0xFFE5C62D.toInt())
                }
            }else{
                c.text(Component.translatable("gui.sbw_helldiver.back"),12,27,0xFFE5C62D.toInt())
                c.text(Component.translatable("gui.sbw_helldiver.category.${category.name.lowercase()}"),65,27)
                val entries=TacticalTerminalOffers.all.filter{it.category==category}
                entries.drop(view?.scroll?:0).take(4).forEachIndexed{i,offer->
                    val def=Stratagems.get(offer.id)?:return@forEachIndexed
                    row(c,10,38+i*29,320,26,offer.id,Component.translatable(def.translation),offer.cost,def.icon,view,partial)
                }
                if(entries.size>4){
                    c.fill(333,38,2,116,0xFF34444A.toInt())
                    val h=116*4/entries.size;val y=38+(116-h)*(view?.scroll?:0)/(entries.size-4)
                    c.fill(333,y,2,h,0xFFE5C62D.toInt())
                }
            }
            view?.feedback?.let{
                c.fill(0,HEIGHT-18,WIDTH,18,0xFF07131A.toInt(),2f)
                c.text(Component.translatable(it),10,HEIGHT-13,if(it.endsWith("success"))0xFF99EEAA.toInt()else 0xFFFF7777.toInt(),2.2f)
            }
        }
        p.popPose()
    }
    private fun background(view:TacticalTerminalScreen?,x:Int,y:Int,w:Int,h:Int):Int {
        val point=view?.pointer
        return if(point!=null && point.first in x.toDouble()..(x+w).toDouble() && point.second in y.toDouble()..(y+h).toDouble())0xFF3A484E.toInt() else 0xFF263239.toInt()
    }
    private fun row(c:Canvas,x:Int,y:Int,w:Int,h:Int,id:String,label:Component,cost:SampleCost,icon:ResourceLocation?,view:TacticalTerminalScreen?,partial:Float){
        c.fill(x,y,w,h,background(view,x,y,w,h))
        if(view?.held==id){
            val progress=TacticalTerminalHold.maskProgress(view.heldTicks.toDouble()+partial)
            // An opaque tint produces a stable white mask under shader packs as well.
            c.fill(x,y,(w*progress).toInt(),h,0xFF8B9599.toInt(),.2f)
        }
        if(icon!=null)c.image(icon,x+4,y+5,16,16)
        val font=Minecraft.getInstance().font
        c.text(Component.literal(font.plainSubstrByWidth(label.string,145)),x+(if(icon==null)8 else 24),y+9)
        listOf(common to cost.common,rare to cost.rare,superSample to cost.superSample).forEachIndexed{i,(texture,n)->
            if(n>0){val sx=x+w-114+i*38;c.image(texture,sx,y+5,16,16);c.text(Component.literal(n.toString()),sx+16,y+10)}
        }
    }
    private class Canvas(val p:PoseStack,val b:MultiBufferSource){
        fun fill(x:Int,y:Int,w:Int,h:Int,color:Int,z:Float=.1f)=quad(BeaconModel.WHITE,x,y,w,h,color,z)
        fun image(texture:ResourceLocation,x:Int,y:Int,w:Int,h:Int)=quad(texture,x,y,w,h,-1,.4f)
        fun text(text:Component,x:Int,y:Int,color:Int=0xFFF6F3E8.toInt(),z:Float=.6f){
            p.pushPose();p.translate(x.toDouble(),y.toDouble(),z.toDouble())
            Minecraft.getInstance().font.drawInBatch(text,0f,0f,color,false,p.last().pose(),b,Font.DisplayMode.NORMAL,0,LightTexture.FULL_BRIGHT)
            p.popPose()
        }
        private fun quad(texture:ResourceLocation,x:Int,y:Int,w:Int,h:Int,color:Int,z:Float){
            val v=b.getBuffer(RenderType.entityCutoutNoCull(texture));val pose=p.last()
            for(q in arrayOf(floatArrayOf(x.toFloat(),y.toFloat(),0f,0f),floatArrayOf(x.toFloat(),(y+h).toFloat(),0f,1f),floatArrayOf((x+w).toFloat(),(y+h).toFloat(),1f,1f),floatArrayOf((x+w).toFloat(),y.toFloat(),1f,0f))){
                v.vertex(pose.pose(),q[0],q[1],z).color((color ushr 16 and 255)/255f,(color ushr 8 and 255)/255f,(color and 255)/255f,1f)
                    .uv(q[2],q[3]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(pose.normal(),0f,0f,1f).endVertex()
            }
        }
    }
}
