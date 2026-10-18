package com.example.sbwhelldiver.client;

import com.example.sbwhelldiver.init.ModBlocks;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Use the active pack's vanilla-fire material, including emissiveness and
 * bloom. IDs belong to each pack and must never be hard-coded. */
public final class IrisFireMaterials {
    private IrisFireMaterials(){}
    public static void extend(Object2IntMap<BlockState> ids){
        if(ids==null||!ModBlocks.INSTANCE.getSENTRY_FIRE().isPresent())return;
        var fallback=Blocks.FIRE.defaultBlockState();
        for(var state:ModBlocks.INSTANCE.getSENTRY_FIRE().get().getStateDefinition().getPossibleStates()){
            var vanilla=fallback;
            for(var property:state.getProperties())if(vanilla.hasProperty(property))vanilla=copy(state,vanilla,property);
            if(ids.containsKey(vanilla))ids.put(state,ids.getInt(vanilla));
            else if(ids.containsKey(fallback))ids.put(state,ids.getInt(fallback));
        }
    }
    private static <T extends Comparable<T>> BlockState copy(BlockState from,BlockState to,Property<T> property){return to.setValue(property,from.getValue(property));}
}
