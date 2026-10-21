package com.luochun.kusopenguin.music;

/**
 * Common-side dispatch point for disc packets. A later client subscriber installs a listener;
 * this class deliberately has no {@code net.minecraft.client} imports so the dedicated server
 * can load it safely.
 */
public final class DiscClientHooks {
    public interface Listener {
        default void onReply(DiscPackets.Reply payload) {
        }

        default void onCatalog(DiscPackets.Catalog payload) {
        }

        default void onChunk(DiscPackets.Chunk payload) {
        }

        default void onPlayback(DiscPackets.Playback payload) {
        }

        default void onDance(DiscPackets.Dance payload) {
        }
    }

    private static volatile Listener listener;

    private DiscClientHooks() {
    }

    public static void install(Listener newListener) {
        listener = newListener;
    }

    public static void receive(DiscPackets.Reply payload) {
        Listener current = listener;
        if (current != null) {
            current.onReply(payload);
        }
    }

    public static void receive(DiscPackets.Catalog payload) {
        Listener current = listener;
        if (current != null) {
            current.onCatalog(payload);
        }
    }

    public static void receive(DiscPackets.Chunk payload) {
        Listener current = listener;
        if (current != null) {
            current.onChunk(payload);
        }
    }

    public static void receive(DiscPackets.Playback payload) {
        Listener current = listener;
        if (current != null) {
            current.onPlayback(payload);
        }
    }

    public static void receive(DiscPackets.Dance payload) {
        Listener current = listener;
        if (current != null) {
            current.onDance(payload);
        }
    }
}
