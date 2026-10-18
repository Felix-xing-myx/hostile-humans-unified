package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.HumanEntity;
import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.entities.ModEntityType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;
import java.util.*;

/** One catalog of built-in faction entities; no natural spawn entries or replacement AI. */
public final class FactionContent {
    public record Boss(String faction, int tier, RegistryObject<EntityType<FactionBossHuman>> type) {}
    public static final List<Boss> BOSSES = bosses();
    private FactionContent() {}
    private static List<Boss> bosses() {
        List<Boss> values = new ArrayList<>();
        for (String faction : List.of("crimson", "azure", "verdant")) for (int rank = 1; rank <= 3; rank++) {
            final int tier = rank;
            String name = "faction_" + faction + "_tier" + tier + "_boss";
            var type = ModEntityType.ENTITIES.register(name, () -> EntityType.Builder
                    .<FactionBossHuman>of((entityType, level) -> new FactionBossHuman(entityType, level, tier, faction), HumanEntity.CATEGORY)
                    .sized(.6F, 1.8F).clientTrackingRange(16).build(HumanGunner.MOD_ID + ":" + name));
            values.add(new Boss(faction, tier, type));
        }
        return List.copyOf(values);
    }
    public static void register(IEventBus modBus) { modBus.addListener(FactionContent::attributes); }
    private static void attributes(EntityAttributeCreationEvent event) {
        for (Boss boss : BOSSES) {
            var builder = Human.createAttributes();
            if (boss.tier() == 3) builder.add(Attributes.MAX_HEALTH, 50).add(Attributes.FOLLOW_RANGE, 48).add(Attributes.KNOCKBACK_RESISTANCE, .15);
            event.put(boss.type().get(), builder.build());
        }
    }
}
