package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.survival.*
import com.google.gson.JsonParser
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.*
import net.minecraft.client.renderer.entity.*
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.*
import net.minecraft.world.phys.AABB
import org.joml.*
import java.util.zip.GZIPInputStream

class EquipmentBeaconRenderer(context:EntityRendererProvider.Context):EntityRenderer<EquipmentDeploymentBeaconEntity>(context) {
    override fun getTextureLocation(e:EquipmentDeploymentBeaconEntity)=Model.textures[0]
    override fun shouldRender(e:EquipmentDeploymentBeaconEntity,f:net.minecraft.client.renderer.culling.Frustum,x:Double,y:Double,z:Double)=e.shouldRender(x,y,z)&&f.isVisible(AABB(e.x-3,e.y-6,e.z-3,e.x+3,e.y+256,e.z+3))
    override fun render(e:EquipmentDeploymentBeaconEntity,yaw:Float,partial:Float,p:PoseStack,b:MultiBufferSource,light:Int) {
        val elapsed=((e.level().gameTime-e.phaseStart+partial)/20f).coerceAtLeast(0f)
        val braking=e.phase==EquipmentDeploymentBeaconEntity.PACKED&&e.flightBraking
        val clip=when(e.phase){0->if(braking)"retro_burn" else "packed";1->"deploy";2,4->"deployed";3->"retract";else->"packed"}
        val time=when(e.phase){1->elapsed.coerceAtMost(2.2f);3->(elapsed*3.95f/3f).coerceAtMost(3.95f);else->0f}
        p.pushPose();p.mulPose(Axis.YP.rotationDegrees(-e.yRot))
        val pose=p.last();val v=Vector3f();val n=Vector3f()
        for((material,triangles)in Model.pose(clip,time,e.capReleased).withIndex()) {
            for(emissive in listOf(false,true)) {
                if(emissive && material !in 2..3)continue
                val vc=b.getBuffer(if(emissive)RenderType.eyes(Model.glows[material])else RenderType.entityCutoutNoCull(Model.textures[material]))
                val bulk=PackedEntityVertices.create(vc)
                for(t in triangles) {
                    n.set(t.n).mul(pose.normal())
                    for(k in intArrayOf(0,1,2,2)) {
                        v.set(t.p[k]).mulPosition(pose.pose());val uv=t.uv[k];val illumination=if(emissive)LightTexture.FULL_BRIGHT else light
                        if(bulk!=null)bulk.vertex(v.x,v.y,v.z,-1,uv[0],uv[1],OverlayTexture.NO_OVERLAY,illumination,n.x,n.y,n.z)
                        else vc.vertex(v.x,v.y,v.z,1f,1f,1f,1f,uv[0],uv[1],OverlayTexture.NO_OVERLAY,illumination,n.x,n.y,n.z)
                    }
                };bulk?.flush()
            }
        }
        if(braking)DeliveryFlightVisuals.drawJets(e,partial,p,b)
        if(e.phase==EquipmentDeploymentBeaconEntity.ACTIVE||e.phase==EquipmentDeploymentBeaconEntity.COUNTDOWN)drawTerminal(e,partial,p,b)
        if(e.phase==EquipmentDeploymentBeaconEntity.ACTIVE||e.phase==EquipmentDeploymentBeaconEntity.COUNTDOWN)beam(e,p,b)
        p.popPose()
    }
    private fun beam(e:EquipmentDeploymentBeaconEntity,p:PoseStack,b:MultiBufferSource) {
        val start=(EquipmentBeaconGeometry.beam.y+e.payloadTravelOffset()).toFloat()
        val v=b.getBuffer(RenderType.lightning());val mat=p.last().pose()
        for((radius,alpha)in listOf(.018f to .9f,.045f to .32f,.1f to .1f))for(i in 0..7) {
            val a=i*Math.PI/4;val c=(i+1)*Math.PI/4
            val x=(kotlin.math.cos(a)*radius).toFloat();val z=(kotlin.math.sin(a)*radius).toFloat();val xx=(kotlin.math.cos(c)*radius).toFloat();val zz=(kotlin.math.sin(c)*radius).toFloat()
            for(q in arrayOf(floatArrayOf(x,start,z),floatArrayOf(xx,start,zz),floatArrayOf(xx,256f,zz),floatArrayOf(x,256f,z)))v.vertex(mat,q[0],q[1],q[2]).color(.15f,.65f,1f,alpha).endVertex()
        }
    }
    private fun drawTerminal(e:EquipmentDeploymentBeaconEntity,partial:Float,p:PoseStack,b:MultiBufferSource) {
        val g=EquipmentBeaconGeometry;val tl=g.center.subtract(g.right.scale(g.width*.5)).add(g.up.scale(g.height*.5)).add(g.normal.scale(.002))
        val frame=Matrix4f().setColumn(0,Vector4f(g.right.x.toFloat(),g.right.y.toFloat(),g.right.z.toFloat(),0f)).setColumn(1,Vector4f(-g.up.x.toFloat(),-g.up.y.toFloat(),-g.up.z.toFloat(),0f)).setColumn(2,Vector4f(g.normal.x.toFloat(),g.normal.y.toFloat(),g.normal.z.toFloat(),0f)).setColumn(3,Vector4f(tl.x.toFloat(),tl.y.toFloat(),tl.z.toFloat(),1f))
        p.pushPose();p.mulPoseMatrix(frame);p.last().normal().mul(org.joml.Matrix3f(frame));val scale=(g.width/256).toFloat();p.scale(scale,scale,scale)
        val h=(g.height/scale).toFloat();quad(p,b,BeaconModel.WHITE,0f,0f,256f,h,0xFF07151B.toInt())
        val font=Minecraft.getInstance().font
        fun text(c:Component,x:Float,y:Float,color:Int=0xFFEFECDD.toInt(),maxWidth:Float=248f-x) {
            val bold=c.copy().withStyle(net.minecraft.ChatFormatting.BOLD)
            val size=minOf(1.3f,maxWidth/font.width(bold).coerceAtLeast(1))
            p.pushPose();p.translate(x.toDouble(),y.toDouble(),.2);p.scale(size,size,1f)
            font.drawInBatch(bold,0f,0f,color,false,p.last().pose(),b,Font.DisplayMode.NORMAL,0,LightTexture.FULL_BRIGHT)
            p.popPose()
        }
        val inputError=EquipmentTerminalFeedback.flashing(e,partial)
        fun arrows(code:List<Int>,x:Float,y:Float,progress:Int) {
            code.forEachIndexed{i,d->
                val color=if(inputError)0xFFFF554D.toInt()else if(i<progress)0xFF65D8A0.toInt()else 0xFFEFECDD.toInt()
                ArrowGlyph.forEachPixel(d){dx,dy->
                    quad(p,b,BeaconModel.WHITE,x+i*14+dx*1.5f,y+dy*1.5f,1.5f,1.5f,color)
                }
            }
        }
        text(Component.translatable("terminal.sbw_helldiver.title"),8f,6f,0xFFE8D474.toInt())
        val machine=e.selectedMachine
        if(e.phase==EquipmentDeploymentBeaconEntity.COUNTDOWN && machine!=null) {
            quad(p,b,machine.icon,12f,35f,44f,44f,-1)
            text(Component.translatable(machine.translation),66f,39f)
            val seconds=kotlin.math.ceil((e.deploymentEnd-e.level().gameTime).coerceAtLeast(0)/20.0).toInt()
            text(Component.translatable("terminal.sbw_helldiver.countdown",seconds),66f,62f)
        }else if(machine==null) {
            for((i,m)in MachineDeployment.entries.withIndex()) {
                val y=25f+i*52f;quad(p,b,m.icon,9f,y,40f,40f,-1)
                text(Component.translatable(m.translation),59f,y+4)
                arrows(m.code,59f,y+22,if(m.code.take(e.prefix.size)==e.prefix)e.prefix.size else 0)
            }
            arrows(List(6){3},10f,119f,if(e.prefix.all{it==3})e.prefix.size else 0)
            text(Component.translatable("terminal.sbw_helldiver.recall"),110f,119f,maxWidth=138f)
        }else {
            quad(p,b,machine.icon,10f,24f,20f,20f,-1);text(Component.translatable(machine.translation),39f,27f)
            val mc=Minecraft.getInstance()
            for((i,material)in machine.materials.withIndex()) {
                val x=8f+(i%2)*124f;val y=49f+(i/2)*22f
                p.pushPose();p.translate((x+8).toDouble(),(y+8).toDouble(),3.0);p.scale(16f,-16f,16f)
                mc.itemRenderer.renderStatic(net.minecraft.world.item.ItemStack(material.item),net.minecraft.world.item.ItemDisplayContext.GUI,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,p,b,e.level(),0)
                p.popPose()
                val have=mc.player?.inventory?.let(material::available)?:0
                text(Component.translatable("terminal.sbw_helldiver.material_line",Component.translatable(material.item.descriptionId),have,material.count),x+21,y+3,
                    if(mc.player?.isCreative==true||have>=material.count)0xFF65D8A0.toInt() else 0xFFFF8777.toInt(),94f)
            }
            arrows(List(6){1},10f,117f,e.prefix.size)
            arrows(listOf(3),174f,117f,0)
            text(Component.translatable("terminal.sbw_helldiver.back"),190f,117f)
        }
        if(e.error>0)text(Component.translatable(when(e.error){1->"terminal.sbw_helldiver.no_space";2->"terminal.sbw_helldiver.no_material";else->"terminal.sbw_helldiver.deployment_busy"}),8f,h-15,0xFFFF766A.toInt())
        else if(e.paid)text(Component.translatable("terminal.sbw_helldiver.waiting"),8f,h-15,0xFF86A8AE.toInt())
        p.popPose()
    }
    private fun quad(p:PoseStack,b:MultiBufferSource,texture:ResourceLocation,x:Float,y:Float,w:Float,h:Float,color:Int) {
        val vc=b.getBuffer(RenderType.entityCutoutNoCull(texture));val m=p.last()
        val red=(color ushr 16 and 255)/255f;val green=(color ushr 8 and 255)/255f;val blue=(color and 255)/255f
        val depth=if(color==0xFF07151B.toInt())-.1f else .01f
        for(v in arrayOf(floatArrayOf(x,y,0f,0f),floatArrayOf(x,y+h,0f,1f),floatArrayOf(x+w,y+h,1f,1f),floatArrayOf(x+w,y,1f,0f)))vc.vertex(m.pose(),v[0],v[1],depth).color(red,green,blue,1f).uv(v[2],v[3]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(m.normal(),0f,0f,1f).endVertex()
    }
    object Model:ResourceManagerReloadListener {
        data class Node(val parent:Int,val mesh:Int,val t:Vector3f,val r:Quaternionf,val s:Vector3f,val cap:Boolean)
        data class Primitive(val material:Int,val p:Array<Vector3f>,val uv:Array<FloatArray>,val indices:IntArray)
        data class Channel(val node:Int,val path:String,val times:FloatArray,val values:List<FloatArray>,val step:Boolean)
        data class Tri(val p:Array<Vector3f>,val uv:Array<FloatArray>,val n:Vector3f)
        val textures=List(5){ResourceLocation(MOD_ID,"textures/mesh/equipment_beacon_$it.png")}
        val glows=List(5){ResourceLocation(MOD_ID,"textures/mesh/equipment_beacon_${it}_emissive.png")}
        private var nodes=emptyList<Node>();private var meshes=emptyList<List<Primitive>>();private var clips=emptyMap<String,List<Channel>>()
        private val cache=object:LinkedHashMap<String,List<List<Tri>>>(8,.75f,true){override fun removeEldestEntry(e:MutableMap.MutableEntry<String,List<List<Tri>>>?)=size>6}
        override fun onResourceManagerReload(r:ResourceManager) {
            val root=r.open(ResourceLocation(MOD_ID,"models/mesh/equipment_beacon.mesh.json.gz")).use{GZIPInputStream(it).reader().use{reader->JsonParser.parseReader(reader).asJsonObject}}
            fun floats(a:com.google.gson.JsonArray)=a.map{it.asFloat}.toFloatArray()
            fun vector(a:com.google.gson.JsonArray)=floats(a).let{Vector3f(it[0],it[1],it[2])}
            nodes=root.getAsJsonArray("nodes").map{raw->val n=raw.asJsonObject;val q=floats(n.getAsJsonArray("r"));Node(n["parent"].asInt,n["mesh"].asInt,vector(n.getAsJsonArray("t")),Quaternionf(q[0],q[1],q[2],q[3]),vector(n.getAsJsonArray("s")),n["cap"].asBoolean)}
            meshes=root.getAsJsonArray("meshes").map{raw->raw.asJsonArray.map{r->val a=r.asJsonObject;Primitive(a["material"].asInt,a.getAsJsonArray("p").map{vector(it.asJsonArray)}.toTypedArray(),a.getAsJsonArray("uv").map{floats(it.asJsonArray)}.toTypedArray(),a.getAsJsonArray("indices").map{it.asInt}.toIntArray())}}
            clips=root.getAsJsonObject("clips").entrySet().associate{(key,value)->key to value.asJsonArray.map{raw->val c=raw.asJsonObject;Channel(c["node"].asInt,c["path"].asString,floats(c.getAsJsonArray("times")),c.getAsJsonArray("values").map{floats(it.asJsonArray)},c["interpolation"].asString=="STEP")}}
            cache.clear()
        }
        fun pose(clip:String,time:Float,capReleased:Boolean):List<List<Tri>> {
            val frame=(time*60).toInt();val key="$clip:$frame:$capReleased"
            return cache.getOrPut(key){
                val t=nodes.map{Vector3f(it.t)};val q=nodes.map{Quaternionf(it.r)};val s=nodes.map{Vector3f(it.s)}
                for(c in clips[clip].orEmpty()) {
                    val tt=frame/60f;var i=0;while(i<c.times.lastIndex && c.times[i+1]<=tt)i++
                    val j=(i+1).coerceAtMost(c.times.lastIndex);val f=if(i==j||c.step)0f else ((tt-c.times[i])/(c.times[j]-c.times[i])).coerceIn(0f,1f)
                    val a=c.values[i];val z=c.values[j]
                    when(c.path){"translation"->t[c.node].set(a[0],a[1],a[2]).lerp(Vector3f(z[0],z[1],z[2]),f);"scale"->s[c.node].set(a[0],a[1],a[2]).lerp(Vector3f(z[0],z[1],z[2]),f);"rotation"->q[c.node].set(a[0],a[1],a[2],a[3]).slerp(Quaternionf(z[0],z[1],z[2],z[3]),f)}
                }
                val world=arrayOfNulls<Matrix4f>(nodes.size)
                fun transform(i:Int):Matrix4f {
                    world[i]?.let{return it}
                    val local=Matrix4f().translationRotateScale(t[i],q[i],s[i]);val m=if(nodes[i].parent<0)local else Matrix4f(transform(nodes[i].parent)).mul(local)
                    world[i]=m;return m
                }
                val lists=List(5){ArrayList<Tri>()}
                for((i,node)in nodes.withIndex())if(node.mesh>=0 && !(capReleased&&node.cap)) {
                    val m=transform(i)
                    for(mesh in meshes[node.mesh]) {
                        val pp=mesh.p.map{Vector3f(it).mulPosition(m)}
                        for(k in mesh.indices.indices step 3) {
                            val ids=mesh.indices.sliceArray(k..k+2);val p=Array(3){pp[ids[it]]};val normal=Vector3f(p[1]).sub(p[0]).cross(Vector3f(p[2]).sub(p[0]))
                            if(normal.lengthSquared()>1e-15f)lists[mesh.material].add(Tri(p,Array(3){mesh.uv[ids[it]]},normal.normalize()))
                        }
                    }
                };lists
            }
        }
    }
}
