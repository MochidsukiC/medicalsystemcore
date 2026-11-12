package jp.houlab.mochidsuki.medicalsystemcore.core;

import jp.houlab.mochidsuki.medicalsystemcore.Medicalsystemcore;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** タグキー定義。タグ本体の JSON(data/medicalsystemcore/tags/item/)は段 2 で置く。 */
public class ModTags {
    public static class Items {
        public static final TagKey<Item> BLOOD_PACKS = tag("blood_packs");
        public static final TagKey<Item> DRUG_PACKS = tag("drug_packs");

        private static TagKey<Item> tag(String name) {
            return TagKey.create(Registries.ITEM, Medicalsystemcore.id(name));
        }
    }
}
