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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Optional maid film transaction. All maid API references live in the lazy nested implementation;
 * a standalone or dedicated server never initializes it unless the mod is loaded.
 *
 * <p>The grave no longer takes part in the ritual. The player retrieves the film with the native
 * tombstone right-click, then holds a valid owned film. This facade only reads the live main/off
 * hand stack chosen by the server-side hold channel, snapshots it, and restores the maid from the
 * snapshot after the full channel has elapsed.
 */
public final class MaidRevivalCompat {
    private static final String MOD_ID = "touhou_little_maid";
    private static final boolean AVAILABLE = ModList.get().isLoaded(MOD_ID);

    /**
     * Immutable-by-convention film snapshot. {@code film} and {@code data} are private copies, not
     * live inventory references; the server compares them against the live slot every tick.
     */
    public record HeldFilm(ItemStack film, CompoundTag data, InteractionHand hand, int slot) {
    }

    private MaidRevivalCompat() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /** True only for the specified live hand; a held-film channel never falls back to the other. */
    public static HeldFilm findHeldFilm(ServerPlayer owner, InteractionHand hand) {
        return AVAILABLE ? Impl.findHeldFilm(owner, hand) : null;
    }

    /**
     * Shared lazy guarded client/prompt check. It is not authority: the server revalidates the
     * specified hand, owner and components before it opens or advances any channel.
     */
    public static boolean validOwnedFilm(Player owner, ItemStack stack) {
        return AVAILABLE && Impl.validOwnedFilm(owner, stack);
    }

    /** Exact live-slot check: same hand, selected main slot, item, count and components. */
    public static boolean matches(ServerPlayer owner, HeldFilm held) {
        return AVAILABLE && Impl.matches(owner, held);
    }

    /**
     * Reconstructs the maid only after the caller's hold channel has completed. The live slot and
     * the private snapshot are rechecked after a successful spawn and only then is exactly one
     * film consumed from that same slot. A failed spawn or commit discards the candidate and leaves
     * the held film unchanged.
     */
    public static boolean resurrectFromHeld(ServerLevel level, ServerPlayer owner, HeldFilm held,
                                            Vec3 position) {
        return AVAILABLE && Impl.resurrectFromHeld(level, owner, held, position);
    }

    private static final class Impl {
        private Impl() {
        }

        static HeldFilm findHeldFilm(ServerPlayer owner, InteractionHand hand) {
            if (hand == InteractionHand.MAIN_HAND) {
                int selected = owner.getInventory().selected;
                return read(owner, InteractionHand.MAIN_HAND, selected,
                        owner.getInventory().getItem(selected));
            }
            if (hand == InteractionHand.OFF_HAND) {
                return read(owner, InteractionHand.OFF_HAND, 0, owner.getOffhandItem());
            }
            return null;
        }

        static boolean validOwnedFilm(Player owner, ItemStack stack) {
            return validFilmData(stack, owner) != null;
        }

        private static HeldFilm read(ServerPlayer owner, InteractionHand hand, int slot,
                                     ItemStack stack) {
            CompoundTag data = validFilmData(stack, owner);
            return data == null ? null : new HeldFilm(stack.copy(), data, hand, slot);
        }

        static boolean matches(ServerPlayer owner, HeldFilm held) {
            if (held == null) {
                return false;
            }
            ItemStack live = live(owner, held);
            return !live.isEmpty() && ItemStack.matches(live, held.film())
                    && validFilmData(live, owner) != null;
        }

        private static ItemStack live(ServerPlayer owner, HeldFilm held) {
            if (held.hand() == InteractionHand.MAIN_HAND) {
                // A selected-slot change is a different hand content even when the item looks equal.
                if (owner.getInventory().selected != held.slot()) {
                    return ItemStack.EMPTY;
                }
                return owner.getInventory().getItem(held.slot());
            }
            return owner.getOffhandItem();
        }

        static boolean resurrectFromHeld(ServerLevel level, ServerPlayer owner, HeldFilm held,
                                         Vec3 position) {
            if (owner.serverLevel() != level || !matches(owner, held)) {
                return false;
            }
            ItemStack live = live(owner, held);
            CompoundTag data = held.data().copy();
            com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid =
                    new com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid(level);
            var event = new com.github.tartaricacid.touhoulittlemaid.api.event.MaidAndItemTransformEvent.ToMaid(
                    maid, live.copy(), data);
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
            // Commit point: validate the exact same live slot again, then consume one. The
            // snapshot is still a private copy, so no event or stack mutation can fake this check.
            ItemStack current = live(owner, held);
            if (current.isEmpty() || !ItemStack.matches(current, held.film())
                    || validFilmData(current, owner) == null) {
                maid.discard();
                return false;
            }
            current.shrink(1);
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

        private static CompoundTag validFilmData(ItemStack stack, Player owner) {
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
