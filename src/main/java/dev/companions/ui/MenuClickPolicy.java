package dev.companions.ui;

import net.minecraft.world.inventory.ContainerInput;

/** Menu icons are controls, never transferable inventory items. */
public final class MenuClickPolicy {
    private MenuClickPolicy() {}
    public static boolean activate(int slot,int button,ContainerInput type,boolean emptyCursor) {
        return slot>=0 && slot<54 && button==0 && type==ContainerInput.PICKUP && emptyCursor;
    }
}
