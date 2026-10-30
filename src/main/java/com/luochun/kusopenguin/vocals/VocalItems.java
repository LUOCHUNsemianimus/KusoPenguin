package com.luochun.kusopenguin.vocals;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.music.MusicLibrary;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.joml.Vector3f;

/**
 * Standalone microphone item and its optional Immersive Melodies clock.
 *
 * <p>The microphone is a normal common item. Immersive Melodies is never a mandatory dependency:
 * when it is absent a plain item explains the requirement, and no IM class is loaded. When IM is
 * present a lazy subclass reuses its {@code InstrumentItem} so the native {@code PLAYING},
 * {@code START_TIME} and {@code PAUSED_TIME} components become a server-synchronised clip clock,
 * without MELODY/TRACKS or any MIDI note playback.
 */
public final class VocalItems {
    private static final String IM_MOD_ID = "immersive_melodies";
    private static final String SILENT_SOUND_NAME = "microphone_silent";
    private static final boolean IM_AVAILABLE = ModList.get().isLoaded(IM_MOD_ID);

    private static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(KusoPenguin.MODID);

    public static final DeferredItem<Item> MICROPHONE = ITEMS.register("microphone",
        VocalItems::createMicrophone);

    private VocalItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(VocalItems::addCreative);
    }

    public static boolean isMicrophone(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(MICROPHONE.get());
    }

    /** Server state carries only the registry id; this avoids relying on a held stack. */
    public static boolean isMicrophoneId(String itemId) {
        return itemId != null && itemId.equals(
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(MICROPHONE.get())
                .toString());
    }

    /** True when the optional Immersive Melodies clock is available. */
    public static boolean available() {
        return IM_AVAILABLE;
    }

    public static void play(ItemStack stack, Level world) {
        if (IM_AVAILABLE && isMicrophone(stack) && world != null) {
            Impl.play(stack, world);
        }
    }

    public static void pause(ItemStack stack, Level world) {
        if (IM_AVAILABLE && isMicrophone(stack) && world != null) {
            Impl.pause(stack, world);
        }
    }

    public static void reset(ItemStack stack) {
        if (IM_AVAILABLE && isMicrophone(stack)) {
            Impl.reset(stack);
        }
    }

    public static long startTime(ItemStack stack) {
        return IM_AVAILABLE && isMicrophone(stack) ? Impl.startTime(stack) : 0L;
    }

    public static boolean playing(ItemStack stack) {
        return IM_AVAILABLE && isMicrophone(stack) && Impl.playing(stack);
    }

    private static Item createMicrophone() {
        return IM_AVAILABLE ? Impl.create() : new PlainMicrophone();
    }

    private static void appendMicrophoneTooltip(ItemStack stack, Item.TooltipContext context,
                                                List<Component> components, TooltipFlag flag) {
        components.add(Component.translatable("item.kuso_penguin.microphone.tooltip"));
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(MICROPHONE.get());
        }
    }

    private static final class PlainMicrophone extends Item {
        private PlainMicrophone() {
            super(new Item.Properties().stacksTo(1));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level world, Player player,
                                                      InteractionHand hand) {
            if (!world.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.kuso_penguin.microphone.requires_im"), true);
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),
                world.isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context,
                                    List<Component> components, TooltipFlag flag) {
            super.appendHoverText(stack, context, components, flag);
            appendMicrophoneTooltip(stack, context, components, flag);
        }
    }

    /** Lazy IM-only implementation; no outer public signature exposes an IM type. */
    private static final class Impl {
        private Impl() {
        }

        static Item create() {
            // IM resolves every InstrumentItem through a separately baked *_hand model.
            // Register during item creation, before model baking, on the guarded IM path only.
            immersive_melodies.Items.customInventoryModels.addIfAbsent(
                ResourceLocation.fromNamespaceAndPath(KusoPenguin.MODID, "microphone"));
            return new MicrophoneItem(new Item.Properties().stacksTo(1),
                new SilentInstrument(), 0L, new Vector3f());
        }

        static void play(ItemStack stack, Level world) {
            ((MicrophoneItem) stack.getItem()).play(stack, world);
        }

        static void pause(ItemStack stack, Level world) {
            ((MicrophoneItem) stack.getItem()).pause(stack, world);
        }

        static void reset(ItemStack stack) {
            stack.set(immersive_melodies.item.InstrumentItem.PLAYING, false);
            stack.remove(immersive_melodies.item.InstrumentItem.START_TIME);
            stack.remove(immersive_melodies.item.InstrumentItem.PAUSED_TIME);
        }

        static long startTime(ItemStack stack) {
            return stack.getOrDefault(immersive_melodies.item.InstrumentItem.START_TIME, 0L);
        }

        static boolean playing(ItemStack stack) {
            return stack.getOrDefault(immersive_melodies.item.InstrumentItem.PLAYING, false);
        }
    }

    /**
     * Silent eight-octave adaptor. The superclass registers eight descriptors in IM's global map;
     * those exact, freshly-created keys are removed immediately so the normal IM registry cannot
     * accidentally pick up microphone placeholders. {@link #get} never exposes them anyway.
     */
    private static final class SilentInstrument extends immersive_melodies.Sounds.Instrument {
        private SilentInstrument() {
            super(KusoPenguin.MODID, SILENT_SOUND_NAME);
            for (int octave = 1; octave <= 8; octave++) {
                immersive_melodies.Sounds.sounds.remove(
                    ResourceLocation.fromNamespaceAndPath(KusoPenguin.MODID,
                        SILENT_SOUND_NAME + ".c" + octave));
            }
        }

        @Override
        public SoundEvent get(int octave) {
            return SoundEvents.EMPTY;
        }
    }

    private static final class MicrophoneItem
            extends immersive_melodies.item.InstrumentItem {
        private MicrophoneItem(Item.Properties properties,
                               immersive_melodies.Sounds.Instrument sound, long sustain,
                               Vector3f offset) {
            super(properties, sound, sustain, offset);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level world, Player user,
                                                      InteractionHand hand) {
            if (!world.isClientSide && user instanceof ServerPlayer player) {
                VocalServer.open(player, hand);
            }
            return InteractionResultHolder.sidedSuccess(user.getItemInHand(hand),
                world.isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context,
                                    List<Component> components, TooltipFlag flag) {
            super.appendHoverText(stack, context, components, flag);
            appendMicrophoneTooltip(stack, context, components, flag);
        }

        /** The microphone has no MIDI notes and must not fetch or advance an IM melody. */
        @Override
        public void inventoryClientTick(ItemStack stack, Level world, LivingEntity entity) {
        }

        @Override
        public void inventoryServerTick(ItemStack stack, ServerLevel world,
                                        LivingEntity entity) {
            if (entity instanceof Player || isPlaying(stack) || stack.has(PAUSED_TIME)) {
                return;
            }
            VocalData.Binding binding = VocalData.microphoneBinding(stack);
            if (binding == null || world.getServer() == null
                    || MusicLibrary.get(world.getServer()).get(binding.trackId()) == null) {
                return;
            }
            play(stack, world);
        }

        @Override
        public void play(ItemStack stack, Level world) {
            long now = world.getGameTime();
            if (isPlaying(stack)) {
                long existingStart = stack.getOrDefault(START_TIME, Long.MIN_VALUE);
                if (existingStart >= 0L && existingStart <= now) {
                    return;
                }
                // A future/negative start survives an unconditional playing no-op and would hold
                // the microphone at phase zero (or negative). Explicit play repairs it.
                stack.remove(PAUSED_TIME);
                stack.set(START_TIME, now);
                return;
            }
            if (stack.has(PAUSED_TIME)) {
                long pausedTime = stack.getOrDefault(PAUSED_TIME, now);
                long pausedDuration = Math.max(0L, now - pausedTime);
                stack.set(START_TIME, stack.getOrDefault(START_TIME, now) + pausedDuration);
                stack.remove(PAUSED_TIME);
            } else {
                stack.set(START_TIME, now);
            }
            stack.set(PLAYING, true);
        }

        @Override
        public void pause(ItemStack stack, Level world) {
            if (isPlaying(stack)) {
                stack.set(PAUSED_TIME, world.getGameTime());
            }
            stack.set(PLAYING, false);
        }
    }
}
