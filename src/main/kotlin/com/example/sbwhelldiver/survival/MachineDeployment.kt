package com.example.sbwhelldiver.survival

import com.example.sbwhelldiver.stratagem.Stratagems
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/** Shared by the terminal and the future container placement code. */
enum class MachineDeployment(val id:String, val width:Int, val depth:Int, val height:Int, val code:List<Int>) {
    BIOPROCESSOR("bioprocessor",3,2,4,listOf(0,3,2,0,1)),
    REFINERY("refinery",5,5,7,listOf(0,3,2,2,1));
    val translation get()="machine.sbw_helldiver.$id"
    val icon get()=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("sbw_helldiver","textures/gui/machines/$id.png")
    data class Material(val item:net.minecraft.world.item.Item,val count:Int) {
        fun available(inventory:net.minecraft.world.entity.player.Inventory)=(0 until inventory.containerSize).sumOf{slot->inventory.getItem(slot).let{if(it.`is`(item))it.count else 0}}
    }
    val materials:List<Material> get() {
        val refinery=this==REFINERY
        val sbw=com.atsuishio.superbwarfare.init.ModItems
        return listOf(Material(net.minecraft.world.item.Items.IRON_BLOCK,if(refinery)5 else 2),
            Material(net.minecraft.world.item.Items.IRON_INGOT,if(refinery)10 else 12),
            Material(sbw.STEEL_BLOCK.get(),if(refinery)5 else 2),Material(sbw.STEEL_PLATE.get(),if(refinery)15 else 20),
            Material(sbw.LARGE_BATTERY_PACK.get(),1),Material(sbw.LARGE_MOTOR.get(),2))
    }
    fun hasMaterials(inventory:net.minecraft.world.entity.player.Inventory)=materials.all{it.available(inventory)>=it.count}
    /** Called on the server thread after the whole bill and deployment reservation pass. */
    fun consumeMaterials(inventory:net.minecraft.world.entity.player.Inventory):Boolean {
        if(!hasMaterials(inventory))return false
        for(material in materials) {
            var left=material.count
            for(slot in 0 until inventory.containerSize) {
                val stack=inventory.getItem(slot)
                if(stack.`is`(material.item)) {val take=minOf(left,stack.count);stack.shrink(take);left-=take}
                if(left==0)break
            }
        }
        inventory.setChanged();return true
    }
    /** Both facilities are centred on the beacon; the interface side follows its heading. */
    fun footprint(at:Vec3,yaw:Float):AABB {
        val rotated=(net.minecraft.util.Mth.floor((yaw+45f)/90f) and 1)!=0
        val w=if(rotated)depth else width;val d=if(rotated)width else depth
        val x=kotlin.math.floor(at.x)-(w/2);val z=kotlin.math.floor(at.z)-(d/2)
        return AABB(x,at.y+.02,z,x+w,at.y+height,z+d)
    }
    fun spaceAvailable(level:ServerLevel,at:Vec3,yaw:Float,ignore:net.minecraft.world.entity.Entity?):Boolean {
        val box=footprint(at,yaw)
        if(box.minY<level.minBuildHeight || box.maxY>level.maxBuildHeight || !level.worldBorder.isWithinBounds(box))return false
        val lo=BlockPos.containing(box.minX,box.minY,box.minZ);val hi=BlockPos.containing(box.maxX-.001,box.maxY-.001,box.maxZ-.001)
        for(p in BlockPos.betweenClosed(lo,hi)) {
            if(!level.hasChunkAt(p))return false
            val state=level.getBlockState(p)
            if(!state.fluidState.isEmpty || !state.getCollisionShape(level,p).isEmpty)return false
        }
        // Full support is required, including the refinery's outer blocks.
        for(x in lo.x..hi.x)for(z in lo.z..hi.z) {
            val support=BlockPos(x,net.minecraft.util.Mth.floor(at.y-.01),z)
            if(!level.getBlockState(support).isFaceSturdy(level,support,net.minecraft.core.Direction.UP))return false
        }
        return level.getEntities(ignore,box){it !is net.minecraft.world.entity.player.Player && it.isPickable && it !is com.example.sbwhelldiver.entity.StratagemMarker}.isEmpty()
    }
    companion object {
        val callCode=listOf(3,0,2,1,0)
        fun checkCodes() {
            val existing=(Stratagems.definitions.filter{it.id!="machine_deployment_beacon"}+listOf(Stratagems.destroyerCall,Stratagems.destroyerDirection,Stratagems.destroyerManage,Stratagems.eagleRearm)).map{it.code}
            require(entries.map{it.code}.distinct().size==entries.size)
            require((entries.map{it.code}+listOf(callCode)).none{it in existing})
            require(entries.all{it.code.take(3)==listOf(0,3,2)})
            require(existing.none{it.take(3)==listOf(0,3,2)})
        }
    }
}
