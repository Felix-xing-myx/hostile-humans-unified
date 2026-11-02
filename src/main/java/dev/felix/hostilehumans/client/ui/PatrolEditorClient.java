package dev.felix.hostilehumans.client.ui;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.soldier.PatrolCommandBatonItem;
import dev.felix.hostilehumans.soldier.PatrolToolNetwork;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=HumanRuntime.MOD_ID,value=Dist.CLIENT)
public final class PatrolEditorClient {
    private PatrolEditorClient() {}
    @SubscribeEvent public static void attack(InputEvent.InteractionKeyMappingTriggered e) {
        var p=Minecraft.getInstance().player;
        if(e.isAttack()&&p!=null&&p.getMainHandItem().getItem() instanceof PatrolCommandBatonItem){
            PatrolToolNetwork.clearDraft();e.setCanceled(true);e.setSwingHand(false);
        }
    }
}
