package com.luochun.kusopenguin.compat;

import com.luochun.kusopenguin.registry.ModEffects;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Optional maid film/tombstone transaction. All maid API references live in the lazy nested
 * implementation; a standalone or dedicated server never initializes it unless the mod is loaded.
 */
public final class MaidRevivalCompat {
    private static final String MOD_ID = "touhou_little_maid";
    private static final boolean AVAILABLE = ModList.get().isLoaded(MOD_ID);

    public record Target(Object tombstone, int slot, ItemStack film, CompoundTag data, Vec3 position) {
    }

    private MaidRevivalCompat() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static Target findTarget(ServerLevel level, ServerPlayer owner, UUID tombstoneUuid) {
        return AVAILABLE ? Impl.findTarget(level, owner, tombstoneUuid) : null;
    }

    public static boolean validate(ServerLevel level, ServerPlayer owner, Target target) {
        return AVAILABLE && Impl.validate(level, owner, target);
    }

    /**
     * Reconstructs the maid only after the caller has waited out the channel. The tombstone slot
     * is rechecked after a successful spawn and only then is exactly one film consumed; a failed
     * spawn or commit discards the candidate and leaves every tombstone slot untouched.
     */
    public static boolean resurrect(ServerLevel level, ServerPlayer owner, Target target, Vec3 position) {
        return AVAILABLE && Impl.resurrect(level, owner, target, position);
    }

    private static final class Impl {
        private Impl() {
        }

        static Target findTarget(ServerLevel level, ServerPlayer owner, UUID tombstoneUuid) {
            Entity found = null;
            List<Entity> nearby = level.getEntities((Entity) null, owner.getBoundingBox().inflate(4.0),
                    entity -> entity.getUUID().equals(tombstoneUuid));
            for (Entity entity : nearby) {
                if (entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.item.EntityTombstone) {
                    found = entity;
                    break;
                }
            }
            if (!(found instanceof com.github.tartaricacid.touhoulittlemaid.entity.item.EntityTombstone tombstone)
                    || tombstone.isRemoved() || !tombstone.getOwnerId().equals(owner.getUUID())) {
                return null;
            }
            int slot = -1;
            ItemStack film = ItemStack.EMPTY;
            CompoundTag data = null;
            int valid = 0;
            for (int i = 0; i < tombstone.getItems().getSlots(); i++) {
                ItemStack stack = tombstone.getItems().getStackInSlot(i);
                CompoundTag tag = validFilmData(stack, owner);
                if (tag == null) {
                    continue;
                }
                valid++;
                slot = i;
                film = stack.copy();
                data = tag;
            }
            if (valid != 1 || slot < 0) {
                return null;
            }
            return new Target(tombstone, slot, film, data, tombstone.position());
        }

        static boolean validate(ServerLevel level, ServerPlayer owner, Target target) {
            if (!(target.tombstone() instanceof com.github.tartaricacid.touhoulittlemaid.entity.item.EntityTombstone tombstone)
                    || tombstone.isRemoved() || !tombstone.getOwnerId().equals(owner.getUUID())) {
                return false;
            }
            if (target.slot() < 0 || target.slot() >= tombstone.getItems().getSlots()) {
                return false;
            }
            ItemStack current = tombstone.getItems().getStackInSlot(target.slot());
            if (!ItemStack.matches(current, target.film())) {
                return false;
            }
            int valid = 0;
            for (int i = 0; i < tombstone.getItems().getSlots(); i++) {
                if (validFilmData(tombstone.getItems().getStackInSlot(i), owner) != null) {
                    valid++;
                }
            }
            return valid == 1;
        }

        static boolean resurrect(ServerLevel level, ServerPlayer owner, Target target, Vec3 position) {
            if (!validate(level, owner, target)) {
                return false;
            }
            com.github.tartaricacid.touhoulittlemaid.entity.item.EntityTombstone tombstone =
                    (com.github.tartaricacid.touhoulittlemaid.entity.item.EntityTombstone) target.tombstone();
            ItemStack current = tombstone.getItems().getStackInSlot(target.slot());
            CompoundTag data = target.data().copy();
            com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid =
                    new com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid(level);
            var event = new com.github.tartaricacid.touhoulittlemaid.api.event.MaidAndItemTransformEvent.ToMaid(
                    maid, current.copy(), data);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
            data = event.getData();
            // The event may rewrite the tag, so re-check the owner before loading anything.
            if (!data.contains("Owner") || !owner.getUUID().equals(data.getUUID("Owner"))) {
                return false;
            }
            // Entity.load restores generic fields (CustomName/NeoForgeData/attachments/Tags) and
            // then calls the maid's readAdditionalSaveData. The private normalized copy keeps the
            // candidate's fresh UUID and supplies only the native-required defaults; the original
            // film tag and the fields the film intentionally removes (inventory/armor/hands/XP/
            // Health/DeathTime/HurtTime/ActiveEffects) are never touched or invented.
            CompoundTag loadTag = data.copy();
            loadTag.remove("UUID");
            ListTag pos = new ListTag();
            pos.add(DoubleTag.valueOf(position.x));
            pos.add(DoubleTag.valueOf(position.y));
            pos.add(DoubleTag.valueOf(position.z));
            loadTag.put("Pos", pos);
            ListTag motion = new ListTag();
            motion.add(DoubleTag.valueOf(0.0D));
            motion.add(DoubleTag.valueOf(0.0D));
            motion.add(DoubleTag.valueOf(0.0D));
            loadTag.put("Motion", motion);
            if (!loadTag.contains("Rotation")) {
                ListTag rotation = new ListTag();
                rotation.add(FloatTag.valueOf(0.0F));
                rotation.add(FloatTag.valueOf(0.0F));
                loadTag.put("Rotation", rotation);
            }
            maid.load(loadTag);
            if (uuidExists(level, maid.getUUID())) {
                return false;
            }
            maid.setPos(position.x, position.y, position.z);
            if (!level.addFreshEntity(maid)) {
                maid.discard();
                return false;
            }
            ItemStack afterSpawn = tombstone.getItems().getStackInSlot(target.slot());
            if (!ItemStack.matches(afterSpawn, target.film())) {
                maid.discard();
                return false;
            }
            ItemStack consumed = tombstone.getItems().extractItem(target.slot(), 1, false);
            if (consumed.isEmpty()) {
                maid.discard();
                return false;
            }
            // Added only after the successful commit, so the existing onEffectAdded TAUNT runs.
            maid.addEffect(new MobEffectInstance(ModEffects.PENGUIN, 20 * 180, 0));
            return true;
        }

        private static boolean uuidExists(ServerLevel level, UUID uuid) {
            for (Entity entity : level.getAllEntities()) {
                if (!entity.isRemoved() && entity.getUUID().equals(uuid)) {
                    return true;
                }
            }
            return false;
        }

        private static CompoundTag validFilmData(ItemStack stack, ServerPlayer owner) {
            if (stack.isEmpty() || stack.getCount() != 1
                    || stack.getItem() != com.github.tartaricacid.touhoulittlemaid.init.InitItems.FILM.get()) {
                return null;
            }
            var info = stack.get(com.github.tartaricacid.touhoulittlemaid.init.InitDataComponent.MAID_INFO);
            if (info == null) {
                return null;
            }
            CompoundTag data = info.copyTag();
            ResourceLocation id = ResourceLocation.tryParse(data.getString("id"));
            if (id == null || !com.luochun.kusopenguin.revival.RevivalIds.isMaidEntityId(id)
                    || !data.contains("Owner") || !owner.getUUID().equals(data.getUUID("Owner"))) {
                return null;
            }
            return data;
        }
    }
}
