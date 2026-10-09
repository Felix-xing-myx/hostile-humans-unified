package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.data.inner.AttackListData;
import com.github.tartaricacid.touhoulittlemaid.entity.misc.MonsterType;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitTaskData;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.concurrent.atomic.AtomicBoolean;

/** Isolates the upstream configuration adapter from badge/faction decisions.
 * IAttackTask.canAttack also applies default monster classification, which
 * would reject our neutral Human type before its owner's badge is considered.
 * Keep only the explicit upstream vetoes here until an equivalent public API exists.
 */
final class MaidTargetRules {
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private MaidTargetRules() { }

    static boolean permits(EntityMaid maid, Human human) {
        try {
            if (human.getCustomName() != null && human.getCustomName().getString()
                    .startsWith(IAttackTask.MAID_NO_ATTACK_TAG)) return false;
            var id = ForgeRegistries.ENTITY_TYPES.getKey(human.getType());
            if (id == null || MaidConfig.MAID_ATTACK_IGNORE.get().contains(id.toString())) return false;
            AttackListData list = maid.getData(InitTaskData.ATTACK_LIST);
            return list == null || list.attackGroups().get(id) != MonsterType.FRIENDLY;
        } catch (LinkageError | RuntimeException error) {
            if (WARNED.compareAndSet(false, true)) {
                HumanGunner.LOGGER.error("Touhou Little Maid target-rule API is incompatible; "
                        + "maid attacks on humans are denied to preserve owner exclusions. "
                        + "Update the compatibility adapter before enabling them again.", error);
            }
            return false;
        }
    }
}
