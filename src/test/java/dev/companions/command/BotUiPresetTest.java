package dev.companions.command;

import dev.companions.ui.UiPresets;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotUiPresetTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    @Test void everyPresetParsesThroughProductionCommandsForSingleAndAllBots() {
        for(String selected:new String[]{"bob","all"}) {
            for(String block:UiPresets.MINERALS)for(int amount:new int[]{1,9,32,64,128})
                CommandParsingTest.parse(UiPresets.mine(selected,block,amount));
            for(String shape:UiPresets.SHAPES)for(int size:new int[]{5,10,20,32})
                CommandParsingTest.parse(UiPresets.build(selected,shape,size));
            for(String flag:UiPresets.FLAGS)for(String value:new String[]{"on","off"})
                CommandParsingTest.parse("bot config "+selected+" "+flag+" "+value);
            for(String action:new String[]{"follow","return","protect","guard","stay","stop","sethome","home","escape","passive","eat","status","remove"})
                CommandParsingTest.parse("bot "+action+" "+selected);
        }
    }
    @Test void buildingSizesMatchShapeSemantics() {
        assertEquals("bot build all wall 10 3",UiPresets.build("all","wall",10));
        assertEquals("bot build bob floor 5 5",UiPresets.build("bob","floor",5));
        assertEquals("bot build bob bridge 20",UiPresets.build("bob","bridge",20));
    }
}
