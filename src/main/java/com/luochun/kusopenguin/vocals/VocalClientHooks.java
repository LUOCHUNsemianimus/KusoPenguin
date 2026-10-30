package com.luochun.kusopenguin.vocals;

/**
 * Common bridge to the future vocal screen. It deliberately contains no client class import, so a
 * dedicated server can load it and simply never installs a listener.
 */
public final class VocalClientHooks {
    public interface Listener {
        void onState(VocalPackets.State state);
    }

    private static volatile Listener listener;

    private VocalClientHooks() {
    }

    /** Installs the single active screen listener; the next UI step owns its lifecycle. */
    public static void install(Listener newListener) {
        listener = newListener;
    }

    /** Delivers a server reply on the client thread already supplied by the packet handler. */
    public static void receive(VocalPackets.State state) {
        if (state == null) {
            return;
        }
        Listener current = listener;
        if (current != null) {
            current.onState(state);
        }
    }
}
