package com.example.sbwhelldiver.stratagem

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.item.StratagemItem
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import java.util.UUID

enum class StratagemCategory(val beamColor: Int) {
    GREEN(0x27BAFF), BLUE(0x27BAFF), RED(0xFF3434)
}

data class StratagemDefinition(val id: String, val code: List<Int>, val category: StratagemCategory = StratagemCategory.GREEN) {
    val dropId get() = ResourceLocation(MOD_ID, id)
    val itemId get() = ResourceLocation(MOD_ID, "${id}_stratagem")
    val translation get() = "item.$MOD_ID.${id}_stratagem"
    val icon get() = ResourceLocation(MOD_ID, "textures/item/${id}_stratagem.png")
}

/** Direction order and codes taken from the installed game's StratagemSettings. */
object Stratagems {
    const val UP = 0
    const val RIGHT = 1
    const val DOWN = 2
    const val LEFT = 3
    val definitions = listOf(
        StratagemDefinition("orbital_precision_strike",listOf(RIGHT,RIGHT,UP),StratagemCategory.RED),
        StratagemDefinition("eagle_smoke_strike",listOf(0,1,0,2),StratagemCategory.RED),
        StratagemDefinition("eagle_gas_strike",listOf(0,1,3,1),StratagemCategory.RED),
        StratagemDefinition("eagle_napalm_airstrike",listOf(0,1,2,0),StratagemCategory.RED),
        StratagemDefinition("eagle_cluster_bomb",listOf(0,1,2,2,1),StratagemCategory.RED),
        StratagemDefinition("eagle_110mm_rocket_pods",listOf(0,1,0,3),StratagemCategory.RED),
        StratagemDefinition("eagle_500kg_bomb",listOf(0,1,2,2,2),StratagemCategory.RED),
        StratagemDefinition("eagle_airstrike",listOf(UP,RIGHT,DOWN,RIGHT),StratagemCategory.RED),
        StratagemDefinition("eagle_strafing_run",listOf(UP,RIGHT,RIGHT),StratagemCategory.RED),
        StratagemDefinition("shield_generator",listOf(DOWN,UP,LEFT,RIGHT,LEFT,DOWN)),
        StratagemDefinition("grenadier_battlement",listOf(DOWN,RIGHT,DOWN,LEFT,RIGHT)),
        StratagemDefinition("anti_tank_emplacement",listOf(DOWN,UP,LEFT,RIGHT,RIGHT,RIGHT)),
        StratagemDefinition("hmg_emplacement",listOf(DOWN,UP,LEFT,RIGHT,RIGHT,LEFT)),
        StratagemDefinition("machine_gun_sentry", listOf(DOWN, UP, RIGHT, RIGHT, UP)),
        StratagemDefinition("gatling_sentry", listOf(DOWN, UP, RIGHT, LEFT)),
        StratagemDefinition("rocket_sentry", listOf(DOWN, UP, RIGHT, RIGHT, LEFT)),
        StratagemDefinition("autocannon_sentry", listOf(DOWN, UP, RIGHT, UP, LEFT, UP)),
        StratagemDefinition("mortar_sentry", listOf(DOWN, UP, RIGHT, RIGHT, DOWN)),
        StratagemDefinition("gas_mortar_sentry", listOf(DOWN, UP, RIGHT, DOWN, LEFT)),
        StratagemDefinition("ems_mortar_sentry", listOf(DOWN, DOWN, UP, UP, LEFT)),
        StratagemDefinition("flame_sentry", listOf(DOWN, UP, RIGHT, DOWN, UP, UP)),
        StratagemDefinition("laser_sentry", listOf(DOWN, UP, RIGHT, DOWN, UP, RIGHT)),
        StratagemDefinition("anti_personnel_minefield", listOf(DOWN, LEFT, UP, RIGHT)),
        StratagemDefinition("incendiary_minefield", listOf(DOWN, LEFT, LEFT, DOWN)),
        StratagemDefinition("gas_minefield", listOf(DOWN, LEFT, LEFT, RIGHT)),
        StratagemDefinition("anti_tank_minefield", listOf(DOWN, LEFT, UP, UP)),
        StratagemDefinition("tesla_tower", listOf(DOWN, UP, RIGHT, UP, LEFT, RIGHT)),
    )
    val destroyerCall = StratagemDefinition("call_super_destroyer",listOf(UP,UP,DOWN,DOWN,LEFT,RIGHT,LEFT,RIGHT))
    val destroyerDirection = StratagemDefinition("destroyer_direction",listOf(UP,LEFT,RIGHT,DOWN))
    val destroyerManage = StratagemDefinition("destroyer_manage",listOf(LEFT,RIGHT))
    fun isOrbital(id:String)=id.startsWith("orbital_")
    val eagleRearm = StratagemDefinition("eagle_rearm", listOf(UP, UP, LEFT, UP, RIGHT))
    fun commands(loadout: List<StratagemDefinition?>, hasEagles: Boolean = loadout.any { it?.id?.startsWith("eagle_") == true }): List<StratagemDefinition?> =
        (if (loadout.any { it?.id?.let(::isOrbital) == true }) listOf(destroyerCall) else emptyList()) + (if (hasEagles) listOf(eagleRearm) + loadout else loadout)
    fun get(id: String) = definitions.firstOrNull { it.id == id }
    fun fromItem(stack: ItemStack) = (stack.item as? StratagemItem)?.dropEntityId?.path?.let(::get)
    fun matching(loadout: List<StratagemDefinition?>, prefix: List<Int>) = loadout.filterNotNull().filter {
        prefix.size <= it.code.size && it.code.take(prefix.size) == prefix
    }
}

object BeaconData {
    const val LOADOUT = "StratagemLoadout"
    const val SELECTED = "ActivatedStratagem"
    const val ID = "BeaconIdentity"
    fun identity(stack: ItemStack): UUID? = stack.tag?.takeIf { it.hasUUID(ID) }?.getUUID(ID)
    fun ensureIdentity(stack: ItemStack): UUID = identity(stack) ?: UUID.randomUUID().also { stack.orCreateTag.putUUID(ID, it) }
    fun storedItems(stack: ItemStack): List<ItemStack> {
        val slots = MutableList(4) { ItemStack.EMPTY }
        val list = stack.tag?.getList(LOADOUT, Tag.TAG_COMPOUND.toInt()) ?: return slots
        for (entry in list) {
            val tag = entry as? CompoundTag ?: continue
            val index = tag.getInt("Slot")
            val item = ItemStack.of(tag)
            if (index in 0..3 && !item.isEmpty && Stratagems.fromItem(item) != null) slots[index] = item.copyWithCount(1)
        }
        return slots
    }
    fun loadout(stack: ItemStack) = storedItems(stack).map(Stratagems::fromItem)
    fun setItems(stack: ItemStack, items: List<ItemStack>) {
        val list = ListTag()
        val seen = mutableSetOf<String>()
        for ((index, item) in items.take(4).withIndex()) {
            val def = Stratagems.fromItem(item) ?: continue
            if (!seen.add(def.id)) continue
            list.add(item.copyWithCount(1).save(CompoundTag()).also { it.putInt("Slot", index) })
        }
        stack.orCreateTag.put(LOADOUT, list)
        clear(stack)
    }
    fun selected(stack: ItemStack): StratagemDefinition? {
        val id = stack.tag?.getString(SELECTED) ?: return null
        return loadout(stack).filterNotNull().firstOrNull { it.id == id }
    }
    fun activate(stack: ItemStack, def: StratagemDefinition) { stack.orCreateTag.putString(SELECTED, def.id) }
    fun clear(stack: ItemStack) { stack.tag?.remove(SELECTED) }
}
