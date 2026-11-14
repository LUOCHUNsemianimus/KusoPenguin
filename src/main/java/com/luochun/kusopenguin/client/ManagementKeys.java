package com.luochun.kusopenguin.client;

import com.luochun.kusopenguin.KusoPenguin;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Two unbound management hotkeys. They only reuse the existing server commands that open the disc
 * library and the dance manager; no new wire action, permission check or penguin-effect gate is
 * added here. The commands themselves still perform their normal server-side validation.
 */
@EventBusSubscriber(modid = KusoPenguin.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ManagementKeys {
    private static final KeyMapping DISCS = new KeyMapping("key.kuso_penguin.manage_discs",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.kuso_penguin");
    private static final KeyMapping DANCES = new KeyMapping("key.kuso_penguin.manage_dances",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.kuso_penguin");

    private ManagementKeys() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(DISCS);
        event.register(DANCES);
    }

    /** Called from the existing main-client tick; clicks are always drained outside gameplay. */
    public static void poll() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || !minecraft.isWindowActive() || minecraft.isPaused()) {
            while (DISCS.consumeClick()) { }
            while (DANCES.consumeClick()) { }
            return;
        }
        while (DISCS.consumeClick()) {
            open("kusopenguin disc");
        }
        while (DANCES.consumeClick()) {
            open("kusopenguin dances");
        }
    }

    private static void open(String command) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
            || minecraft.getConnection() == null) {
            return;
        }
        minecraft.getConnection().sendCommand(command);
    }
}
