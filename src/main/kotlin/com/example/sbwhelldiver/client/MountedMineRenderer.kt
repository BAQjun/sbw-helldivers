package com.example.sbwhelldiver.client

import com.atsuishio.superbwarfare.client.model.entity.VehicleModelInstance
import com.example.sbwhelldiver.entity.*
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedBedrockModel
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BakedModelInstance
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.init.ModEntities
import com.google.gson.JsonParser
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.*
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import org.joml.Matrix4f
import org.joml.Matrix3f
import java.util.WeakHashMap

/** Mounted mines have no world entity or per-pod mesh allocation during descent.
 * Once opened, immutable bind-space meshes are shared by every dispenser. */
object MountedMineRenderer {
    private data class Mount(val parent:String,val transform:Matrix4f)
    private data class Slot(val parent:Int,val inverseBind:Matrix4f,val transform:Matrix4f)
    private val mounts=mutableMapOf<String,List<Mount>>()
    private val models=WeakHashMap<BakedBedrockModel,List<Slot>>()
    fun reload(resources:ResourceManager){
        models.clear();mounts.clear()
        resources.openAsReader(ResourceLocation(MOD_ID,"helldivers/mine_mounts.json")).use{reader->
            for((name,value) in JsonParser.parseReader(reader).asJsonObject.entrySet()){
                mounts[name]=value.asJsonArray.map{item->
                    val entry=item.asJsonObject;val rows=entry.getAsJsonArray("matrix")
                    val matrix=Matrix4f()
                    for(row in 0..2)for(col in 0..3)matrix.set(col,row,rows[row].asJsonArray[col].asFloat)
                    Mount(entry["parent"].asString,matrix)
                }
            }
        }
    }
    fun visible(pod:MineDispenserEntity,index:Int)=pod.capReleased&&
        (pod.phase==AbstractHellpodEntity.Phase.DEPLOYING||pod.phase==AbstractHellpodEntity.Phase.ACTIVE)&&
        index%pod.minesPerPanel>=pod.releasedWaves
    fun render(pod:MineDispenserEntity,model:VehicleModelInstance,pose:PoseStack,buffers:MultiBufferSource,light:Int){
        if(!visible(pod,pod.mineCount-1))return
        val kind=when(pod.type){
            ModEntities.INCENDIARY_MINEFIELD.get()->AntiPersonnelMineEntity.Kind.INCENDIARY
            ModEntities.GAS_MINEFIELD.get()->AntiPersonnelMineEntity.Kind.GAS
            else->if(pod.isAntiTank)AntiPersonnelMineEntity.Kind.ANTI_TANK else AntiPersonnelMineEntity.Kind.PERSONNEL
        }
        val name=when(kind){
            AntiPersonnelMineEntity.Kind.PERSONNEL->"ap_mine_hellpod_v1"
            AntiPersonnelMineEntity.Kind.INCENDIARY->"incendiary_mine_hellpod_v1"
            AntiPersonnelMineEntity.Kind.GAS->"gas_mine_hellpod_v1"
            AntiPersonnelMineEntity.Kind.ANTI_TANK->"anti_tank_mine_hellpod_v1"
        }
        val slots=models.getOrPut(model.baseModel()){
            val bind=BakedModelInstance(model.baseModel())
            mounts.getValue(name).map{mount->
                val parent=bind.getIndex(mount.parent)
                check(parent>=0){"Missing mine dispenser panel ${mount.parent}"}
                Slot(parent,Matrix4f(bind.getGlobalTransform(parent)).invert(),mount.transform)
            }
        }
        val distance=pod.position().distanceToSqr(Minecraft.getInstance().gameRenderer.mainCamera.position)
        val mesh=AntiPersonnelMineRenderer.Model.meshes[Triple(kind,if(distance>196)2 else if(distance>36)1 else 0,false)]?:return
        val texture=AntiPersonnelMineRenderer.Model.textures.getValue(kind)
        val out=buffers.getBuffer(RenderType.entityCutoutNoCull(texture))
        for(panel in 0 until 6){
            val slot=slots[panel*pod.minesPerPanel]
            val delta=Matrix4f(model.getGlobalTransform(slot.parent)).mul(slot.inverseBind)
            for(index in panel*pod.minesPerPanel until (panel+1)*pod.minesPerPanel)if(visible(pod,index)){
                pose.pushPose()
                val transform=Matrix4f(delta).mul(slots[index].transform)
                pose.mulPoseMatrix(transform)
                // Authored mounts uniformly scale the mine. Remove that scale
                // from normals before their components are packed into bytes.
                val normal=Matrix3f(transform).invert().transpose()
                normal.scale(Math.cbrt(kotlin.math.abs(transform.determinant().toDouble())).toFloat())
                pose.last().normal().mul(normal)
                mesh.render(pose,out,light,true)
                pose.popPose()
            }
        }
    }
}
