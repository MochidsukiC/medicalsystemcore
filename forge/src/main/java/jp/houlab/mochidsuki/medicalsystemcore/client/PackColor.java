package jp.houlab.mochidsuki.medicalsystemcore.client;

import net.minecraft.client.color.item.ItemColor;
import net.minecraft.world.item.ItemStack;

public class PackColor implements ItemColor {

    private final int color;
    public PackColor(int color){
        this.color = color;
    }
    
    @Override
    public int getColor(ItemStack stack, int tintIndex) {
        if(tintIndex == 1) {
            return color;
        }
        return -1;
    }
}
