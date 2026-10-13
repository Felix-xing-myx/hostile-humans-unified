package com.craftix.hostile_humans.client.renderer;

import club.someoneice.humangunner.GunSupport;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;

/** One visual handedness policy; never changes the entity's hands or equipment. */
final class HumanGunPresentation {
    private HumanGunPresentation() {}

    static HumanoidArm mainArm(Human human) {
        var stack = human.getMainHandItem();
        return GunSupport.isGunItem(stack)
                ? HumanoidArm.RIGHT : human.getMainArm();
    }

    static HumanoidArm itemArm(Human human, HumanoidArm actualArm) {
        if (mainArm(human) == human.getMainArm()) return actualArm;
        return actualArm == HumanoidArm.LEFT ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
    }

    static ItemDisplayContext context(HumanoidArm visualArm) {
        return visualArm == HumanoidArm.RIGHT ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                : ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }
}
