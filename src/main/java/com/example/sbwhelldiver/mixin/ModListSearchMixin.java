package com.example.sbwhelldiver.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.neoforged.neoforge.client.gui.ModListScreen;
import net.neoforged.neoforgespi.language.IModInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Forge searches display names only. Add aliases for this mod without changing other results. */
@Mixin(value = ModListScreen.class, remap = false)
public abstract class ModListSearchMixin {
    @Shadow private List<IModInfo> mods;
    @Shadow @Final private List<IModInfo> unsortedMods;
    @Shadow private EditBox search;

    @Inject(method = "reloadMods", at = @At("TAIL"))
    private void helldiversSearchAliases(CallbackInfo ci) {
        String query = helldiversNormalize(search.getValue());
        if (query.isEmpty()) return;
        for (IModInfo info : unsortedMods) {
            if (!info.getModId().equals("sbw_helldiver") || mods.contains(info)) continue;
            for (String alias : List.of(info.getDisplayName(), info.getModId(), "SBW Helldivers",
                    "SBW Helldiver", "绝地潜兵", "地狱潜兵")) {
                if (helldiversNormalize(alias).contains(query)) {
                    mods.add(info);
                    break;
                }
            }
        }
    }

    @Unique
    private static String helldiversNormalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_\\-]+", "");
    }
}
