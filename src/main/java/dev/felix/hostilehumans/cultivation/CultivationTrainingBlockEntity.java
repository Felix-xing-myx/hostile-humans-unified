package dev.felix.hostilehumans.cultivation;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;

import java.util.UUID;

/**
 * Dedicated input, distinct from N3. Only investor-authenticated facade actions can operate the
 * slot.
 */
public final class CultivationTrainingBlockEntity extends BlockEntity {
    private UUID id = UUID.randomUUID();
    private long revision;
    private boolean testMode;
    private final ItemStackHandler input =
            new ItemStackHandler(1) {
                @Override
                public boolean isItemValid(int slot, ItemStack stack) {
                    return stack.is(CultivationContent.SUPPLIES.get());
                }

                @Override
                protected void onContentsChanged(int slot) {
                    setChanged();
                }
            };

    public CultivationTrainingBlockEntity(BlockPos p, BlockState s) {
        super(CultivationContent.TRAINING_ENTITY.get(), p, s);
    }

    public CultivationFacility reference() {
        if (level == null) throw new IllegalStateException("Unplaced training block");
        return new CultivationFacility(
                level.dimension().location().toString(),
                id,
                worldPosition.getX(),
                worldPosition.getY(),
                worldPosition.getZ(),
                revision,
                testMode);
    }

    public void testMode(boolean next) {
        if (testMode != next) {
            testMode = next;
            revision++;
            setChanged();
        }
    }

    /**
     * Bounded atomic intake from an already checked player's inventory; never exported as an
     * automation capability.
     */
    void intake(int count) {
        if (count <= 0 || count > 64 || !input.getStackInSlot(0).isEmpty())
            throw new IllegalStateException("Input slot busy");
        input.setStackInSlot(0, new ItemStack(CultivationContent.SUPPLIES.get(), count));
    }

    void finishIntake() {
        input.setStackInSlot(0, ItemStack.EMPTY);
    }

    ItemStack strandedInput() {
        return input.extractItem(0, 64, false);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putUUID("instance", id);
        t.putLong("revision", revision);
        t.putBoolean("test", testMode);
        t.put("input", input.serializeNBT());
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        if (t.hasUUID("instance")) id = t.getUUID("instance");
        revision = Math.max(0, t.getLong("revision"));
        testMode = t.getBoolean("test");
        input.deserializeNBT(t.getCompound("input"));
    }
}
