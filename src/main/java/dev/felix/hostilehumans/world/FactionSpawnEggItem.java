package dev.felix.hostilehumans.world;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.faction.FactionContent;
import dev.felix.hostilehumans.faction.FactionDefinitions;
import dev.felix.hostilehumans.faction.HumanFactions;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeSpawnEggItem;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;

/** Vanilla placement adapter: fixes the type and explicit faction before any initialization observes the new resident. */
public final class FactionSpawnEggItem extends ForgeSpawnEggItem {
    private final Supplier<? extends EntityType<? extends Human>> humanType;
    private final String faction;
    private final boolean boss;
    public FactionSpawnEggItem(Supplier<? extends EntityType<? extends Human>> type, String faction, boolean boss,
                        int background, int highlight, Properties properties) {
        super(type, background, highlight, properties);
        this.humanType = type; this.faction = java.util.Objects.requireNonNull(FactionDefinitions.id(faction)).toString(); this.boss = boss;
    }
    public String faction() { return faction; }
    public boolean boss() { return boss; }
    @Override public EntityType<?> getType(CompoundTag tag) { return humanType.get(); }
    static CompoundTag identityTag(CompoundTag original, String type, String faction, boolean boss, boolean spawner) {
        CompoundTag entity = original.copy();
        entity.putString("id", type);
        CompoundTag forge = entity.getCompound("ForgeData").copy();
        forge.putString(HumanFactions.FACTION, faction);
        forge.putBoolean(HumanFactions.BOSS, boss);
        forge.putBoolean(FactionContent.AUTHORED_EQUIPMENT, equipmentList(original.get("HandItems")) || equipmentList(original.get("ArmorItems")));
        if (spawner) {
            forge.putBoolean(FactionContent.SPAWNER_BIRTH, true);
        } else {
            forge.remove(FactionContent.SPAWNER_BIRTH);
        }
        entity.put("ForgeData", forge);
        return entity;
    }
    private static boolean equipmentList(Tag tag) {
        return tag instanceof ListTag list && (list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND);
    }

    private CompoundTag identity(ItemStack stack, boolean spawner) {
        String type=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(humanType.get()).toString();
        CompoundTag entity=identityTag(stack.getTagElement("EntityTag")==null?new CompoundTag():stack.getTagElement("EntityTag"),type,faction,boss,spawner);
        if(!spawner)stack.getOrCreateTag().put("EntityTag",entity);
        return entity;
    }
    static CompoundTag spawnerTag(CompoundTag original, CompoundTag entity) {
        CompoundTag saved=original.copy();
        CompoundTag data=saved.getCompound("SpawnData").copy();
        data.put("entity",entity.copy());saved.put("SpawnData",data);
        saved.remove("SpawnPotentials");
        return saved;
    }
    private boolean allowed(Player player) {
        boolean enabled = !boss || UnifiedConfig.get().featureEnabled("boss", true);
        if (!enabled && player != null && !player.level().isClientSide)
            player.displayClientMessage(Component.translatable("message.hostile_humans_unified.boss.disabled"), true);
        return enabled;
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().isClientSide && !allowed(context.getPlayer())) return InteractionResult.FAIL;
        if (!context.getLevel().isClientSide) {
            if(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof SpawnerBlockEntity spawner) {
                // Delays/count/ranges/custom rules stay intact; selecting this egg replaces
                // the spawn identity pool, so subsequent waves cannot revert to an old type.
                CompoundTag saved=spawnerTag(spawner.getSpawner().save(new CompoundTag()),identity(context.getItemInHand(),true));
                spawner.getSpawner().load(context.getLevel(),context.getClickedPos(),saved);
                spawner.setChanged();
                var state=context.getLevel().getBlockState(context.getClickedPos());
                context.getLevel().sendBlockUpdated(context.getClickedPos(),state,state,3);
                context.getLevel().gameEvent(context.getPlayer(),GameEvent.BLOCK_CHANGE,context.getClickedPos());
                context.getItemInHand().shrink(1);
                return InteractionResult.CONSUME;
            }
            identity(context.getItemInHand(),false);
        }
        return super.useOn(context);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && !allowed(player)) return InteractionResultHolder.fail(player.getItemInHand(hand));
        if (!level.isClientSide) identity(player.getItemInHand(hand),false);
        return super.use(level, player, hand);
    }
    @Override protected net.minecraft.core.dispenser.DispenseItemBehavior createDispenseBehavior() {
        // Forge installs the returned adapter after egg registration, avoiding setup-order races.
        return new OptionalDispenseItemBehavior() {
            @Override protected ItemStack execute(net.minecraft.core.BlockSource source,ItemStack stack) {
                setSuccess(false);
                if(!allowed(null))return stack;
                identity(stack,false);
                Direction direction=source.getBlockState().getValue(DispenserBlock.FACING);
                var position=source.getPos().relative(direction);
                var spawned=humanType.get().spawn(source.getLevel(),stack,null,position,net.minecraft.world.entity.MobSpawnType.DISPENSER,
                        direction!=Direction.UP,false);
                if(spawned!=null) {
                    stack.shrink(1);setSuccess(true);
                    source.getLevel().gameEvent(null,GameEvent.ENTITY_PLACE,position);
                }
                return stack;
            }
        };
    }
}
