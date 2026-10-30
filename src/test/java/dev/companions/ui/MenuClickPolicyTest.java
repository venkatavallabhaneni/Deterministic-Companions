package dev.companions.ui;

import net.minecraft.world.inventory.ContainerInput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuClickPolicyTest {
    @Test void onlyEmptyCursorLeftClicksInsideControlsCanActivate() {
        for(int slot=-1;slot<=90;slot++)for(ContainerInput type:ContainerInput.values())for(int button=0;button<=8;button++) {
            assertEquals(slot>=0 && slot<54 && type==ContainerInput.PICKUP && button==0,
                MenuClickPolicy.activate(slot,button,type,true));
            assertFalse(MenuClickPolicy.activate(slot,button,type,false),"Held real stacks must never be used to activate controls");
        }
        assertFalse(MenuClickPolicy.activate(-999,0,ContainerInput.PICKUP,true));
    }
}
