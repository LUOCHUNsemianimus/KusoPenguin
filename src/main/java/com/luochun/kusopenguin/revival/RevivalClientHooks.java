package com.luochun.kusopenguin.revival;

/**
 * Common-side dispatch point for the revival state packet. The client installs a listener; this
 * class has no client imports, so dedicated servers can load it safely.
 */
public final class RevivalClientHooks {
    public interface Listener {
        void onState(RevivalPackets.State payload);
    }

    private static volatile Listener listener;

    private RevivalClientHooks() {
    }

    public static void install(Listener newListener) {
        listener = newListener;
    }

    public static void receive(RevivalPackets.State payload) {
        Listener current = listener;
        if (current != null) {
            current.onState(payload);
        }
    }
}
