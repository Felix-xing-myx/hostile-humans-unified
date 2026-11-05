package dev.felix.hostilehumans.loot.client;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

/** Keep the live author session when JEI opens a nested screen. Never manufacture a stale token. */
@Mod.EventBusSubscriber(modid=HumanRuntime.MOD_ID,value=Dist.CLIENT)
public final class LootEditorReturn {
    private static LootEditorScreen parent;
    static LootEditorScreen parent(){return parent!=null&&!parent.closed()?parent:null;}
    @SubscribeEvent public static void opening(ScreenEvent.Opening event){
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(mc.screen instanceof LootEditorScreen editor&&!editor.closed()&&event.getNewScreen()!=null
                &&!(event.getNewScreen() instanceof LootEditorScreen))parent=editor;
        if(event.getNewScreen()==null||event.getNewScreen() instanceof LootEditorScreen)parent=null;
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST) public static void escape(ScreenEvent.KeyPressed.Pre event){
        if(event.getKeyCode()!=org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE||parent==null||parent.closed())return;
        var editor=parent;parent=null;event.setCanceled(true);
        net.minecraft.client.Minecraft.getInstance().setScreen(editor);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){parent=null;}
}
