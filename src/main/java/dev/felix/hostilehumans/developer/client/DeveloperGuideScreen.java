package dev.felix.hostilehumans.developer.client;

import dev.felix.hostilehumans.client.ui.framework.PanelScreen;
import dev.felix.hostilehumans.client.ui.framework.PanelGeometry;
import dev.felix.hostilehumans.client.ui.framework.PanelItemRow;
import dev.felix.hostilehumans.client.ui.framework.UiLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class DeveloperGuideScreen extends PanelScreen {
    private static final String[] TOOLS={"creative_soldier_controller","patrol_command_baton","natural_state_tool","attribute_observer",
            "navigation_test_pointer","navigation_observer_helmet","human_monitor","wild_human_cleanup",
            "settlement_information","settlement_region_tool","settlement_facility_tool","settlement_selection_tool","loot_editor"};
    private String selected=TOOLS[0];
    private DeveloperGuideScreen(){super(label("title"),PanelGeometry.Size.L);}
    public static void open(){Minecraft.getInstance().setScreen(new DeveloperGuideScreen());}
    private static Component label(String key){return Component.translatable("developer_guide."+key);}
    @Override protected UiLayout.Node buildContent(){
        List<UiLayout.Node> nodes=new ArrayList<>();nodes.add(text(label("intro"),()->theme().muted()));
        for(String name:TOOLS){
            var item=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("hostile_humans_unified",name));
            if(item==null)continue;
            var row=body(new PanelItemRow(new ItemStack(item),item.getDescription(),label(name+".summary"),Component.empty(),
                    button->{selected=name;rebuildWidgets();},this::theme));row.selected(selected.equals(name));nodes.add(node(row));
            if(selected.equals(name))nodes.add(text(label(name+".detail"),()->theme().text()));
        }
        return UiLayout.column(6,nodes);
    }
    @Override protected void buildFooter(){footer(Component.translatable("gui.done"),button->onClose());}
}
