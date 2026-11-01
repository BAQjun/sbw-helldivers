package com.example.sbwhelldiver.survival

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.StringRepresentable
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/** Coordinates match the accepted Blockbench models, with the interface row facing north. */
object MachineLayout {
    enum class Port: StringRepresentable { NONE,INPUT,OUTPUT,ENERGY;override fun getSerializedName()=name.lowercase() }
    fun rotate(p:BlockPos,facing:Direction)=when(facing){Direction.EAST->BlockPos(-p.z,p.y,p.x);Direction.SOUTH->BlockPos(-p.x,p.y,-p.z);Direction.WEST->BlockPos(p.z,p.y,-p.x);else->p}
    fun rotate(b:AABB,facing:Direction):AABB {
        fun point(x:Double,z:Double)=when(facing){Direction.EAST->-z to x;Direction.SOUTH->-x to -z;Direction.WEST->z to -x;else->x to z}
        val p=point(b.minX,b.minZ);val q=point(b.maxX,b.maxZ)
        return AABB(minOf(p.first,q.first),b.minY,minOf(p.second,q.second),maxOf(p.first,q.first),b.maxY,maxOf(p.second,q.second))
    }
    fun boxes(m:MachineDeployment)=if(m==MachineDeployment.BIOPROCESSOR)listOf(AABB(-1.5,0.0,-1.5,1.5,2.74,.5))
        else listOf(AABB(-2.5,0.0,-2.5,2.5,1.25,2.5),AABB(-.75,1.25,-2.125,1.25,6.25,-.125))
    fun containerBounds(m:MachineDeployment)=if(m==MachineDeployment.BIOPROCESSOR)AABB(-2.0,0.0,-2.0,2.0,3.0,1.0)else AABB(-3.0,0.0,-3.0,3.0,7.0,3.0)
    fun portOffset(m:MachineDeployment,p:Port)=BlockPos(when(p){Port.INPUT->if(m==MachineDeployment.BIOPROCESSOR)-1 else -2;Port.ENERGY->if(m==MachineDeployment.BIOPROCESSOR)1 else 2;else->0},0,if(m==MachineDeployment.BIOPROCESSOR)-1 else -2)
    private val cache=HashMap<Pair<MachineDeployment,Direction>,Map<BlockPos,VoxelShape>>()
    fun shapes(m:MachineDeployment,f:Direction):Map<BlockPos,VoxelShape> = cache.getOrPut(m to f) {
        val result=LinkedHashMap<BlockPos,VoxelShape>()
        for(box in boxes(m).map{rotate(it,f)})for(x in kotlin.math.floor(box.minX+.5).toInt() until kotlin.math.ceil(box.maxX+.5).toInt())
            for(y in kotlin.math.floor(box.minY).toInt() until kotlin.math.ceil(box.maxY).toInt())
                for(z in kotlin.math.floor(box.minZ+.5).toInt() until kotlin.math.ceil(box.maxZ+.5).toInt()) {
                    val local=box.move(.5-x.toDouble(),-y.toDouble(),.5-z.toDouble()).intersect(AABB(0.0,0.0,0.0,1.0,1.0,1.0))
                    if(local.xsize>1e-6&&local.ysize>1e-6&&local.zsize>1e-6){val pos=BlockPos(x,y,z);result[pos]=Shapes.or(result[pos]?:Shapes.empty(),Shapes.create(local))}
                }
        result
    }
}
