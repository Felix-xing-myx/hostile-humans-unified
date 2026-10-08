package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.ai.control.JumpControl;

/** Every voluntary jump uses the same landing check, including flag-free combat helpers. */
public final class HumanSafeJumpControl extends JumpControl {
    private final Human human;
    public HumanSafeJumpControl(Human human) { super(human); this.human = human; }
    @Override public void jump() {
        if (HumanTerrainSafety.safeCombatJump(human)) super.jump();
    }
}
