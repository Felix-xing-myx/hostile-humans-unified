package dev.felix.hostilehumans.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/** Persistent non-ticking author carrier. Runtime authority remains SettlementIndex. */
public final class SettlementInformationBlockEntity extends BlockEntity {
    public static final String AUTHOR = "settlement_author";
    private CompoundTag author = new CompoundTag();
    private UUID instance;
    private UUID selectionIdentity = UUID.randomUUID();
    private String module = "";

    public SettlementInformationBlockEntity(BlockPos position, BlockState state) {
        super(SettlementContent.INFORMATION_ENTITY.get(), position, state);
    }

    public CompoundTag author() {
        return author.copy();
    }

    public int revision() {
        return author.getInt("revision");
    }

    UUID selectionIdentity() { return selectionIdentity; }

    public UUID instance() {
        return instance;
    }

    public String module() {
        return module;
    }

    public void author(CompoundTag value) {
        author = value.copy();
        setChanged();
    }

    public void bind(UUID value, String module) {
        this.instance = value;
        this.module = module;
        setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        author = tag.getCompound(AUTHOR).copy();
        if (tag.hasUUID("selection_identity")) selectionIdentity = tag.getUUID("selection_identity");
        instance = tag.hasUUID("settlement_instance") ? tag.getUUID("settlement_instance") : null;
        module = tag.getString("settlement_module");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(AUTHOR, author.copy());
        tag.putUUID("selection_identity", selectionIdentity);
        if (instance != null) tag.putUUID("settlement_instance", instance);
        tag.putString("settlement_module", module);
    }
}
