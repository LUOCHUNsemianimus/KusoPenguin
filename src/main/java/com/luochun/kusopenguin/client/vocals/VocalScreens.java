package com.luochun.kusopenguin.client.vocals;

import com.luochun.kusopenguin.music.DiscPackets;
import com.luochun.kusopenguin.vocals.VocalPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Routes protocol states to one live vocal screen without inventing screens for stale replies. */
public final class VocalScreens {
    private static final Minecraft MC = Minecraft.getInstance();
    private static VocalScreen current;
    private static Screen pendingParent;

    private VocalScreens() {
    }

    /** Called by the native IM button before its OPEN request. */
    public static void requestOpen(Screen parent) {
        pendingParent = parent;
    }

    /** Installed as the VocalClient state listener. */
    public static void accept(VocalPackets.State state) {
        if (state == null) {
            return;
        }
        if (state.open()) {
            Screen parent = pendingParent;
            pendingParent = null;
            if (current != null && current.ticket().equals(state.ticket())) {
                current.updateState(state);
                return;
            }
            VocalScreen next = new VocalScreen(state, parent);
            current = next;
            MC.setScreen(next);
            return;
        }
        pendingParent = null;
        if (current != null && current.ticket().equals(state.ticket())) {
            if (!state.itemId().isEmpty()) {
                current.updateState(state);
            } else {
                current.onFailure(state);
            }
            return;
        }
        if (!state.status().isEmpty() && MC.gui != null) {
            MC.gui.getChat().addMessage(Component.translatable(state.status()));
        }
    }

    static void removed(VocalScreen screen) {
        if (current == screen) {
            current = null;
        }
    }

    /** A new server must not inherit a parent screen from an earlier connection. */
    public static void clear() {
        current = null;
        pendingParent = null;
    }

    public static void onDiscReply(DiscPackets.Reply reply) {
        if (current != null) {
            current.onDiscReply(reply);
        }
    }

    public static void onCatalog() {
        if (current != null) {
            current.catalogChanged();
        }
    }
}
