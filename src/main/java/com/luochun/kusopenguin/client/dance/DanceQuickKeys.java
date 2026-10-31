package com.luochun.kusopenguin.client.dance;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.dance.Prefs;
import com.luochun.kusopenguin.registry.ModEffects;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Eight independent quick-dance keys. They are intentionally unbound by default and rely on the
 * vanilla options file for persistence; each key sends the same server control as a wheel wedge.
 */
@EventBusSubscriber(modid = KusoPenguin.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class DanceQuickKeys {
    public static final int COUNT = Prefs.SLOT_COUNT;

    private static final KeyMapping[] KEYS = new KeyMapping[COUNT];

    static {
        for (int slot = 0; slot < COUNT; slot++) {
            KEYS[slot] = new KeyMapping("key.kuso_penguin." + keyName(slot),
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN,
                "key.categories.kuso_penguin");
        }
    }

    private DanceQuickKeys() {
    }

    private static String keyName(int slot) {
        return switch (slot) {
            case 0 -> "dance_slot_1";
            case 1 -> "dance_slot_2";
            case 2 -> "dance_slot_3";
            case 3 -> "dance_slot_4";
            case 4 -> "dance_slot_5";
            case 5 -> "dance_slot_6";
            case 6 -> "dance_slot_7";
            default -> "dance_slot_8";
        };
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        for (KeyMapping key : KEYS) {
            event.register(key);
        }
    }

    /** Called from the main-client tick so this class stays on the MOD event bus. */
    public static void poll() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || !minecraft.isWindowActive() || minecraft.isPaused()) {
            // Typing in a GUI must not leave a queued dance to fire when the screen closes.
            for (KeyMapping key : KEYS) while (key.consumeClick()) { }
            return;
        }
        for (int slot = 0; slot < COUNT; slot++) {
            while (KEYS[slot].consumeClick()) {
                play(slot);
            }
        }
    }

    private static void play(int slot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
            || !minecraft.player.hasEffect(ModEffects.PENGUIN)) {
            return;
        }
        UUID target = DanceClient.pointedMaidOrSelf();
        if (target == null) {
            return;
        }
        String clipId = DanceClient.prefs().slots().get(slot);
        if (clipId.isEmpty()) {
            minecraft.player.displayClientMessage(
                Component.translatable("message.kuso_penguin.dance.slot_empty", slot + 1), true);
            return;
        }
        DanceClient.playSlot(slot, target);
    }
}
