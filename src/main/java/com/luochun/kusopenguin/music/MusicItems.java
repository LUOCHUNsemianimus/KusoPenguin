package com.luochun.kusopenguin.music;

import com.luochun.kusopenguin.KusoPenguin;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxPlayable;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Items and sound event for the custom playable disc. */
public final class MusicItems {
    public static final String TRACK_TAG = "KusoTrack";
    public static final String TITLE_TAG = "KusoTitle";

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(KusoPenguin.MODID);
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS =
        DeferredRegister.create(Registries.SOUND_EVENT, KusoPenguin.MODID);

    private static final ResourceKey<net.minecraft.world.item.JukeboxSong> CUSTOM_SONG =
        ResourceKey.create(Registries.JUKEBOX_SONG,
            ResourceLocation.fromNamespaceAndPath(KusoPenguin.MODID, "custom"));

    public static final DeferredItem<Item> CUSTOM_DISC = ITEMS.register("custom_disc",
        () -> new Item(new Item.Properties().stacksTo(1).component(DataComponents.JUKEBOX_PLAYABLE,
            new JukeboxPlayable(new EitherHolder<>(CUSTOM_SONG), true))));

    public static final DeferredHolder<SoundEvent, SoundEvent> CUSTOM_MUSIC = SOUND_EVENTS.register(
        "custom_music", () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(KusoPenguin.MODID, "custom_music")));

    private MusicItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
    }

    /** Stored track id for this exact custom disc item, or an empty string. */
    public static String trackId(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(CUSTOM_DISC.get())) {
            return "";
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString(TRACK_TAG);
    }

    /** Replace a held custom disc, otherwise add to inventory and drop only if it is full. */
    public static void give(ServerPlayer player, MusicLibrary.Track track) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TRACK_TAG, track.id());
        tag.putString(TITLE_TAG, track.title());

        ItemStack stack = new ItemStack(CUSTOM_DISC.get());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(track.title()));

        if (player.getMainHandItem().is(CUSTOM_DISC.get())) {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            return;
        }
        if (player.getOffhandItem().is(CUSTOM_DISC.get())) {
            player.setItemInHand(InteractionHand.OFF_HAND, stack);
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
