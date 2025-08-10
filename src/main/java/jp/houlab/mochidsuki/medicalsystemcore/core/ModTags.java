package jp.houlab.mochidsuki.medicalsystemcore.core;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public class ModTags {
    public static class Items {
        public static final TagKey<Item> BLOOD_PACKS = tag("blood_packs");
        public static final TagKey<Item> DRUG_PACKS = tag("drug_packs");

        private static TagKey<Item> tag(String name) {
            return ItemTags.create(new ResourceLocation(Medicalsystemcore.MODID, name));
        }
    }
}