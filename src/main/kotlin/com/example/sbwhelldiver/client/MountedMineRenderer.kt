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
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import org.joml.Matrix4f
import org.joml.Matrix3f
import java.util.WeakHashMap

/** Mounted mines have no world entity or per-pod mesh allocation during descent.
 * Once opened, immutable bind-space meshes are shared by every dispenser. */
object MountedMineRenderer {
    private data class Mount(val parent:String,val transform:Matrix4f)
    private data class Slot(val parent:Int,val inverseBind:Matrix4f,val transform:Matrix4f,val normal:Matrix3f)
    private val mounts=mutableMapOf<String,List<Mount>>()
    private val models=WeakHashMap<BakedBedrockModel,List<Slot>>()
    fun reload(resources:ResourceManager){
        models.clear();mounts.clear()
        resources.openAsReader(ResourceLocation.fromNamespaceAndPath(MOD_ID,"helldivers/mine_mounts.json")).use{reader->
            for((name,value) in JsonParser.parseReader(reader).asJsonObject.entrySet()){
                mounts[name]=value.asJsonArray.map{item->
                    val entry=item.asJsonObject;val rows=entry.getAsJsonArray("matrix")
                    val matrix=Matrix4f()
                    for(row in 0..2)for(col in 0..3)matrix.set(col,row,rows[row].asJsonArray[col].asFloat)
                    // Indexed writes use JOML's unsafe path and leave the old
                    // identity flag intact. Recompute it before multiplying.
                    matrix.determineProperties()
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
                Slot(parent,Matrix4f(bind.getGlobalTransform(parent)).invert(),mount.transform,
                    normalTransform(mount.transform,Matrix3f()))
            }
        }
        val distance=pod.position().distanceToSqr(Minecraft.getInstance().gameRenderer.mainCamera.position)
        val mesh=AntiPersonnelMineRenderer.Model.meshes[Triple(kind,ModelLod.mineTier(distance),false)]?:return
        val texture=AntiPersonnelMineRenderer.Model.textures.getValue(kind)
        val out=buffers.getBuffer(RenderType.entityCutoutNoCull(texture))
        val bulk=PackedEntityVertices.create(out)
        val delta=Matrix4f()
        val transform=Matrix4f()
        val panelNormal=Matrix3f()
        val normal=Matrix3f()
        val remainingWave=pod.releasedWaves.coerceAtLeast(0)
        for(panel in 0 until 6){
            val panelStart=panel*pod.minesPerPanel
            val first=slots[panelStart]
            delta.set(model.getGlobalTransform(first.parent)).mul(first.inverseBind)
            normalTransform(delta,panelNormal)
            for(index in panelStart+remainingWave until panelStart+pod.minesPerPanel){
                val slot=slots[index]
                pose.pushPose()
                pose.mulPose(delta.mul(slot.transform,transform))
                // The inverse transpose of panel * mount is the product of
                // their inverse transposes in the same order.
                pose.last().normal().mul(panelNormal.mul(slot.normal,normal))
                mesh.render(pose,out,light,true,bulk)
                pose.popPose()
            }
        }
        bulk?.flush()
    }

    private fun normalTransform(transform:Matrix4f,result:Matrix3f):Matrix3f {
        // The authored mount scale belongs to its positions only.
        val scale=Math.cbrt(kotlin.math.abs(transform.determinant().toDouble())).toFloat()
        return result.set(transform).invert().transpose().scale(scale)
    }
}
