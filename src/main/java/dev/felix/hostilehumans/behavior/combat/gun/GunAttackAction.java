package dev.felix.hostilehumans.behavior.combat.gun;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.HumanBehaviorControl;
import dev.felix.hostilehumans.compat.spartan.SpartanEquipmentCompat;

import dev.felix.hostilehumans.behavior.ActionCoordinator;
import java.util.EnumSet;

/** A synchronous shot owns ATTACK through the native event/callback boundary. */
public final class GunAttackAction {
    private GunAttackAction() {}
    public static boolean owns(Human human) {
        return HumanBehaviorControl.actions(human).owner(ActionCoordinator.Resource.ATTACK).equals("gun-shot")
                &&!(human.isUsingItem()&&SpartanEquipmentCompat.isShield(human.getUseItem()));
    }
    public static void run(Human human,Runnable shot) {
        if(human.isUsingItem()&&SpartanEquipmentCompat.isShield(human.getUseItem())) return;
        var actions=HumanBehaviorControl.actions(human);
        if(!actions.owner(ActionCoordinator.Resource.ATTACK).isEmpty()) return;
        var token=actions.request("gun-shot",EnumSet.of(ActionCoordinator.Resource.ATTACK,
                ActionCoordinator.Resource.MAIN_HAND, ActionCoordinator.Resource.FACING),80,false,1);
        if(!actions.acquire(token)) {
            actions.finish(token,ActionCoordinator.Status.CANCELLED,"shot authority busy");
            return;
        }
        boolean completed=false;
        try {
            shot.run();completed=true;
        } finally {
            actions.finish(token,completed?ActionCoordinator.Status.COMPLETED:ActionCoordinator.Status.FAILED,
                    completed?"native shot attempt":"native shot failed");
        }
    }
}
