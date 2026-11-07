package com.luochun.kusopenguin.dance;

/**
 * Common-side dispatch point for dance packets. The next UI/playback step installs one listener;
 * this class deliberately has no {@code net.minecraft.client} imports so a dedicated server can
 * load it safely. Every payload is already immutable and bounded.
 */
public final class DanceClientHooks {
    public interface Listener {
        default void onReply(DancePackets.Reply payload) {
        }

        default void onCatalog(DancePackets.Catalog payload) {
        }

        default void onChunk(DancePackets.Chunk payload) {
        }

        default void onPlayback(DancePackets.Playback payload) {
        }

        default void onFakeTargetMarker(DancePackets.FakeTargetMarker payload) {
        }
    }

    private static volatile Listener listener;

    private DanceClientHooks() {
    }

    public static void install(Listener newListener) {
        listener = newListener;
    }

    public static void receive(DancePackets.Reply payload) {
        Listener current = listener;
        if (current != null) {
            current.onReply(payload);
        }
    }

    public static void receive(DancePackets.Catalog payload) {
        Listener current = listener;
        if (current != null) {
            current.onCatalog(payload);
        }
    }

    public static void receive(DancePackets.Chunk payload) {
        Listener current = listener;
        if (current != null) {
            current.onChunk(payload);
        }
    }

    public static void receive(DancePackets.Playback payload) {
        Listener current = listener;
        if (current != null) {
            current.onPlayback(payload);
        }
    }

    public static void receive(DancePackets.FakeTargetMarker payload) {
        Listener current = listener;
        if (current != null) {
            current.onFakeTargetMarker(payload);
        }
    }
}
