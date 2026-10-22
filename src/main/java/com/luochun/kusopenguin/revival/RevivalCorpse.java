package com.luochun.kusopenguin.revival;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Non-living, non-colliding render marker for an online dead player. It stores no inventory and
 * never ticks AI; the client renderer builds a separate RemotePlayer visual from the owner UUID.
 */
public final class RevivalCorpse extends Entity {
    private static final EntityDataAccessor<String> DATA_OWNER =
            SynchedEntityData.defineId(RevivalCorpse.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_NAME =
            SynchedEntityData.defineId(RevivalCorpse.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_PENGUIN =
            SynchedEntityData.defineId(RevivalCorpse.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> DATA_CREATED =
            SynchedEntityData.defineId(RevivalCorpse.class, EntityDataSerializers.LONG);

    public RevivalCorpse(EntityType<? extends RevivalCorpse> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
        setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER, "");
        builder.define(DATA_NAME, "");
        builder.define(DATA_PENGUIN, false);
        builder.define(DATA_CREATED, 0L);
    }

    public void configure(UUID owner, String name, boolean penguin, long createdGameTime) {
        entityData.set(DATA_OWNER, owner.toString());
        entityData.set(DATA_NAME, name == null ? "" : name);
        entityData.set(DATA_PENGUIN, penguin);
        entityData.set(DATA_CREATED, createdGameTime);
    }

    public UUID ownerUuid() {
        String value = entityData.get(DATA_OWNER);
        try {
            return value.isEmpty() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public String ownerName() {
        return entityData.get(DATA_NAME);
    }

    public boolean penguin() {
        return entityData.get(DATA_PENGUIN);
    }

    public long createdGameTime() {
        return entityData.get(DATA_CREATED);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // noSave marker: intentionally nothing to persist.
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // noSave marker: intentionally nothing to persist.
    }
}
