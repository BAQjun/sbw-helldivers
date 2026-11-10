package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.item.GuardDogBackpack
import com.example.sbwhelldiver.stratagem.Stratagems
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack

/** Extra real-render scenes in the disposable MG test world. */
internal object AttachedLightClientCheck {
    private var start=Long.MIN_VALUE
    private var blocker:java.util.UUID?=null
    private var uncovered=false
    private var deployed=false
    private var movedBlocker=false
    private var laserDeployed=false
    private val captured=mutableSetOf<String>()
    fun tick(mc:Minecraft):Boolean {
        val level=mc.level?:return false
        val server=mc.singleplayerServer?:return false
        val player=mc.player?:return false
        if(start==Long.MIN_VALUE) {
            start=level.gameTime
            mc.options.keyUp.setDown(false);mc.options.keyDown.setDown(false)
            mc.options.cameraType=CameraType.FIRST_PERSON;mc.options.bobView().set(true);mc.options.fov().set(65)
            server.execute {
                val world=server.overworld();val p=server.playerList.getPlayer(player.uuid)!!
                world.allEntities.toList().filter{it !is net.minecraft.world.entity.player.Player}.forEach{it.discard()}
                p.teleportTo(world,.5,80.0,-3.5,0f,-5f)
                val marker=ModEntities.STRATAGEM_MARKER.get().create(world)!!
                marker.moveTo(.5,80.0,8.5,0f,0f)
                marker.configure(Stratagems.get("guard_dog")!!.dropId,10000,null,groundY=80.0)
                check(world.addFreshEntity(marker))
                val golem=EntityType.IRON_GOLEM.create(world)!!
                golem.setNoAi(true);golem.moveTo(.5,80.0,3.5,180f,0f)
                check(world.addFreshEntity(golem));blocker=golem.uuid
            }
        }
        val age=level.gameTime-start
        fun shot(name:String){if(captured.add(name))Screenshot.grab(mc.gameDirectory,"attached-light-$name.png",mc.mainRenderTarget){LOGGER.info("ATTACHED_LIGHT_SCREENSHOT {}",name)}}
        if(age>=200)shot("entity-occlusion")
        if(age>=240L&&!uncovered){uncovered=true;server.execute{blocker?.let(server.overworld()::getEntity)?.isInvisible=true}}
        if(age>=280)shot("beam-uncovered")
        if(age>=300&&!movedBlocker){movedBlocker=true;server.execute {
            blocker?.let(server.overworld()::getEntity)?.let{it.isInvisible=false;it.moveTo(.5,80.0,13.5,180f,0f)}
        }}
        if(age>=340)shot("beam-in-front-of-entity")
        if(age>=360L&&!deployed){deployed=true;server.execute {
            val p=server.playerList.getPlayer(player.uuid)!!
            p.setItemSlot(EquipmentSlot.CHEST,ItemStack(ModItems.GUARD_DOG_BACKPACK.get()))
            GuardDogBackpack.toggle(p)
            LOGGER.info("ATTACHED_LIGHT_DRONE active={} pack={}",GuardDogBackpack.equipped(p)?.let(GuardDogBackpack::active),p.getItemBySlot(EquipmentSlot.CHEST))
        }}
        // Face the player from ahead so the higher right-rear drone remains in frame.
        if(age>=480) {
            mc.options.cameraType=CameraType.THIRD_PERSON_FRONT
            mc.options.fov().set(75)
            player.yRot=0f;player.yRotO=0f;player.xRot=0f;player.xRotO=0f
            mc.options.keyUp.setDown(age<550||age in 680..750)
            if(age>=510)shot("moving-exhaust-a")
            if(age>=533)shot("moving-exhaust-b")
            if(age in 510..550||age in 710..750)check(level.entitiesForRendering().any{it is com.example.sbwhelldiver.entity.GuardDogEntity}){"Walking scene has no Guard Dog"}
            if(age>=710)shot("moving-rover-exhaust-a")
            if(age>=733)shot("moving-rover-exhaust-b")
        }
        if(age>=560&&!laserDeployed){laserDeployed=true;server.execute {
            val p=server.playerList.getPlayer(player.uuid)!!
            GuardDogBackpack.equipped(p)?.let(GuardDogBackpack::active)?.let(server.overworld()::getEntity)?.discard()
            p.setItemSlot(EquipmentSlot.CHEST,ItemStack(ModItems.LASER_GUARD_DOG_BACKPACK.get()))
            GuardDogBackpack.toggle(p)
        }}
        if(age<760)return false
        mc.options.keyUp.setDown(false)
        LOGGER.info("ATTACHED_LIGHT_PASSED: generated entity occlusion and walking-camera exhaust scenes with view bob enabled")
        return true
    }
}
