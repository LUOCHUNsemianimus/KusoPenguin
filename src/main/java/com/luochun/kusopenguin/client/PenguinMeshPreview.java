package com.luochun.kusopenguin.client;

import com.luochun.kusopenguin.KusoPenguin;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Client-only detail switch between the three low-poly mesh tiers. F8 cycles
 * high -> medium -> low -> high. It changes only the visual body path while the penguin effect is
 * active; vanilla entities, the original head, proportions, animation, hitbox and first-person view
 * are untouched.
 */
@EventBusSubscriber(modid = KusoPenguin.MODID, bus = EventBusSubscriber.Bus.GAME,
        value = Dist.CLIENT)
public final class PenguinMeshPreview {
    /** Rendering quality selected by F8; the wire format and animation roots are shared. */
    public enum Quality {
        HIGH("high"),
        MEDIUM("medium"),
        LOW("low");

        private final String id;

        Quality(String id) {
            this.id = id;
        }

        public Quality next() {
            Quality[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public String labelKey() {
            return "mesh.kuso_penguin.quality." + this.id;
        }
    }

    private static final KeyMapping TOGGLE_KEY = new KeyMapping(
            "key.kuso_penguin.mesh_preview",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "key.categories.kuso_penguin");
    private static Quality quality = Quality.HIGH;

    private PenguinMeshPreview() {
    }

    public static KeyMapping toggleKey() {
        return TOGGLE_KEY;
    }

    public static Quality quality() {
        return quality;
    }

    public static void toggle() {
        quality = quality.next();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            Component keyName = TOGGLE_KEY.getTranslatedKeyMessage();
            Component message = Component.translatable(
                    "message.kuso_penguin.mesh_preview.quality",
                    Component.translatable(quality.labelKey()),
                    keyName,
                    Component.translatable(quality.next().labelKey()));
            minecraft.player.displayClientMessage(message, true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (TOGGLE_KEY.consumeClick()) {
            toggle();
        }
    }
}
