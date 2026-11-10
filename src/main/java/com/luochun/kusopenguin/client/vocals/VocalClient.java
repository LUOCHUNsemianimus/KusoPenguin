package com.luochun.kusopenguin.client.vocals;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.client.compat.WineFoxMouth;
import com.luochun.kusopenguin.client.music.DiscTransfers;
import com.luochun.kusopenguin.vocals.VocalClientHooks;
import com.luochun.kusopenguin.vocals.VocalData;
import com.luochun.kusopenguin.vocals.VocalItems;
import com.luochun.kusopenguin.vocals.VocalPackets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client playback orchestration for item-bound vocals.
 *
 * <p>Every client tick scans nearby players/maids and derives one {@link VocalTimeline.Snapshot}
 * per entity. A voice starts only when the instrument is explicitly enabled and the effective
 * melody plus fingerprint matches its binding. Downloads reuse {@link DiscTransfers}; no item
 * settings are copied unless the custom-data component object actually changes.</p>
 */
@EventBusSubscriber(modid = KusoPenguin.MODID, value = Dist.CLIENT,
    bus = EventBusSubscriber.Bus.GAME)
public final class VocalClient implements VocalClientHooks.Listener {
    public static final VocalClient INSTANCE = new VocalClient();

    private static final Minecraft MC = Minecraft.getInstance();
    private static final Logger LOGGER = LoggerFactory.getLogger("KusoPenguin/VocalClient");
    private static final double AUDIBLE_RANGE_SQR = 32.0D * 32.0D;
    private static final int MAX_VOICE_SLOTS = 64;
    private static final long DRIFT_LIMIT_MS = 400L;
    private static final long RESEEK_COOLDOWN_NANOS = 1_000_000_000L;

    private static final Map<UUID, Voice> VOICES = new HashMap<>();
    private static final Map<ItemStack, Settings> SETTINGS = new WeakHashMap<>();
    private static volatile VocalPackets.State latestState;
    private static volatile Consumer<VocalPackets.State> stateListener;
    private static volatile Component status = Component.empty();
    private static ResourceLocation dimension;

    private VocalClient() {
    }

    /** Latest server editing state; playback does not send protocol messages. */
    public static VocalPackets.State state() {
        return latestState;
    }

    /** Installs the next UI step's consumer; null restores the default no-screen behavior. */
    public static void installStateListener(Consumer<VocalPackets.State> listener) {
        stateListener = listener;
    }

    /** Last one-shot playback problem, or an empty component. */
    public static Component status() {
        return status;
    }

    /** -1 leaves the native expression alone. Zero is the resting mouth within a vocal. */
    public static int mouthLevel(LivingEntity entity) {
        Voice voice = activeVoice(entity);
        return voice == null ? -1 : voice.sound.mouthLevel();
    }

    static boolean performingWithMicrophone(LivingEntity entity) {
        Voice voice = activeVoice(entity);
        if (voice == null || !VocalData.MICROPHONE_MELODY.equals(voice.key.melodyId())) {
            return false;
        }
        ItemStack main = entity.getMainHandItem();
        ItemStack mic = VocalItems.isMicrophone(main) ? main : entity.getOffhandItem();
        return VocalItems.playing(mic) && VocalItems.startTime(mic) == voice.key.startTime();
    }

    private static Voice activeVoice(LivingEntity entity) {
        Voice voice = VOICES.get(entity.getUUID());
        if (voice == null || voice.entity != entity || voice.spent || !entity.isAlive()
            || MC.level != entity.level() || MC.isPaused() || !audible()) {
            return null;
        }
        VocalSound sound = voice.sound;
        if (sound == null || !sound.streamOpened() || sound.isStopped()
            || !MC.getSoundManager().isActive(sound)) {
            return null;
        }
        long position = sound.audioProgressMs();
        return position < Math.min(sound.streamEndMs(), sound.midiEndMs()) ? voice : null;
    }

    /** Number of currently active/pending vocal slots, for diagnostics. */
    public static int activeVoices() {
        int active = 0;
        for (Voice voice : VOICES.values()) {
            if (!voice.spent) {
                active++;
            }
        }
        return active;
    }

    @Override
    public void onState(VocalPackets.State state) {
        latestState = state;
        Consumer<VocalPackets.State> listener = stateListener;
        if (listener != null) {
            listener.accept(state);
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        boolean paused = MC.isPaused();
        for (Voice voice : VOICES.values()) {
            if (voice.sound != null) {
                voice.sound.setPaused(paused);
            }
        }
        if (paused) {
            return;
        }
        if (MC.level == null || MC.player == null) {
            clearPlayback();
            return;
        }
        ResourceLocation current = MC.level.dimension().location();
        if (dimension != null && !dimension.equals(current)) {
            clearPlayback();
        }
        dimension = current;
        scan();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clearPlayback();
        latestState = null;
        VocalScreens.clear();
    }

    private static void scan() {
        Set<UUID> seen = new HashSet<>();
        for (Entity entity : MC.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living.isRemoved()
                || !living.isAlive() || living.distanceToSqr(MC.player) > AUDIBLE_RANGE_SQR) {
                continue;
            }
            VocalTimeline.Snapshot snapshot = VocalTimeline.snapshot(living);
            if (snapshot == null) {
                continue;
            }
            Settings settings = settings(snapshot.stack());
            if (!settings.enabled()) {
                continue;
            }
            VocalData.Binding binding = settings.bindings().get(
                bindingKey(snapshot.melodyId(), snapshot.fingerprint()));
            if (binding == null) {
                continue;
            }
            UUID id = living.getUUID();
            Voice voice = VOICES.get(id);
            if (voice == null) {
                if (VOICES.size() >= MAX_VOICE_SLOTS) {
                    continue;
                }
                voice = new Voice(living);
                VOICES.put(id, voice);
            }
            seen.add(id);
            voice.entity = living;
            long expected = snapshot.phaseMs() - binding.offsetMs();
            long midiEnd = snapshot.lengthMs() - binding.offsetMs();
            Key key = new Key(binding.trackId(), snapshot.melodyId(), snapshot.fingerprint(),
                binding.offsetMs(), binding.gain(), snapshot.loop(), snapshot.startTime());
            if (!key.equals(voice.key)) {
                String oldTrack = voice.key == null ? "" : voice.key.trackId();
                stopSound(voice);
                voice.key = key;
                if (!key.trackId().equals(oldTrack)) {
                    voice.path = null;
                }
                voice.downloadStarted = false;
                voice.failed = false;
                voice.failedReported = false;
                voice.spent = false;
                voice.lastReseekNanos = 0L;
                voice.generation++;
            }
            voice.expectedAudioMs = expected;
            voice.midiEndMs = midiEnd;
            voice.offsetMs = binding.offsetMs();
            voice.gain = binding.gain();
            update(voice);
            if (VocalData.MICROPHONE_MELODY.equals(key.melodyId())) {
                MicrophonePerformance.emitNote(living);
            }
        }
        Iterator<Map.Entry<UUID, Voice>> iterator = VOICES.entrySet().iterator();
        while (iterator.hasNext()) {
            Voice voice = iterator.next().getValue();
            if (!seen.contains(voice.entity.getUUID())) {
                stopSound(voice);
                iterator.remove();
            }
        }
    }

    private static void update(Voice voice) {
        if (voice.spent) {
            return;
        }
        if (voice.expectedAudioMs < 0L) {
            // A positive offset has not reached the melody yet; delay the stream rather than
            // opening at zero and audibly starting early.
            return;
        }
        if (voice.sound != null) {
            if (!voice.sound.streamOpened() && voice.sound.streamFailed()) {
                fail(voice, voice.sound.streamError());
                return;
            }
            if (voice.sound.naturallyEnded() || voice.sound.isStopped()) {
                voice.spent = true;
                return;
            }
            voice.sound.setExpectedAudioMs(voice.expectedAudioMs);
            if (voice.sound.streamOpened()) {
                long drift = Math.abs(voice.sound.audioProgressMs() - voice.expectedAudioMs);
                long now = System.nanoTime();
                // Resource reload removes native channels without stopping their sound object.
                // Reacquire at the current phase, using the same throttle as a drift correction.
                if ((drift > DRIFT_LIMIT_MS || !MC.getSoundManager().isActive(voice.sound))
                    && now - voice.lastReseekNanos >= RESEEK_COOLDOWN_NANOS) {
                    voice.lastReseekNanos = now;
                    stopSound(voice);
                    play(voice);
                }
            }
            return;
        }
        if (voice.failed) {
            return;
        }
        if (voice.path != null) {
            play(voice);
            return;
        }
        if (!voice.downloadStarted) {
            download(voice);
        }
    }

    private static void download(Voice voice) {
        voice.downloadStarted = true;
        long generation = voice.generation;
        Key key = voice.key;
        DiscTransfers.audio(key.trackId()).whenComplete((path, error) -> MC.execute(() -> {
            Voice current = VOICES.get(voice.entity.getUUID());
            if (current != voice || voice.generation != generation
                || !voice.key.equals(key) || voice.spent) {
                return;
            }
            voice.downloadStarted = false;
            if (error != null) {
                fail(voice, error.getMessage() == null ? error.getClass().getSimpleName()
                    : error.getMessage());
                return;
            }
            voice.path = path;
            if (voice.expectedAudioMs >= 0L && !MC.isPaused()) {
                play(voice);
            }
        }));
    }

    private static void play(Voice voice) {
        if (voice.entity.isRemoved() || voice.entity.isDeadOrDying() || MC.level == null
            || voice.entity.level() != MC.level || voice.path == null) {
            voice.spent = true;
            return;
        }
        if (voice.gain <= 0.0F || !audible()) {
            // Do not create a silent, permanently unopened slot while the user has muted the
            // master/records category. The next unpaused tick retries with normal volume.
            return;
        }
        VocalSound sound = new VocalSound(voice.entity, voice.path, voice.key.trackId(),
            voice.offsetMs, voice.expectedAudioMs, voice.midiEndMs, voice.gain,
            WineFoxMouth.supports(voice.entity));
        voice.sound = sound;
        status = Component.empty();
        MC.getSoundManager().play(sound);
    }

    private static boolean audible() {
        return MC.options.getSoundSourceVolume(SoundSource.MASTER) > 0.0F
            && MC.options.getSoundSourceVolume(SoundSource.RECORDS) > 0.0F;
    }

    private static void fail(Voice voice, String message) {
        voice.spent = true;
        voice.failed = true;
        if (!voice.failedReported) {
            voice.failedReported = true;
            String detail = boundedDetail(message);
            status = Component.translatable("message.kuso_penguin.vocals.audio_error", detail);
            LOGGER.warn("Vocal audio for track {} unavailable: {}", voice.key.trackId(), detail);
        }
    }

    private static String boundedDetail(String message) {
        String detail = message == null ? "unknown" : message;
        detail = detail.replaceAll("[\\p{Cntrl}]", " ").strip();
        if (detail.isEmpty()) {
            return "unknown";
        }
        return detail.length() > 96 ? detail.substring(0, 96) : detail;
    }

    private static void stopSound(Voice voice) {
        if (voice.sound != null) {
            voice.sound.end();
            MC.getSoundManager().stop(voice.sound);
            voice.sound = null;
        }
        voice.downloadStarted = false;
    }

    private static Settings settings(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        Settings cached = SETTINGS.get(stack);
        if (cached != null && cached.custom() == data) {
            return cached;
        }
        Map<String, VocalData.Binding> bindings = new HashMap<>();
        for (VocalData.Binding binding : VocalData.bindings(stack)) {
            bindings.put(bindingKey(binding.melodyId(), binding.melodyHash()), binding);
        }
        Settings fresh = new Settings(data, VocalData.enabled(stack), Map.copyOf(bindings));
        SETTINGS.put(stack, fresh);
        return fresh;
    }

    private static String bindingKey(String melodyId, String fingerprint) {
        return melodyId + '\u0000' + fingerprint;
    }

    private static void clearPlayback() {
        for (Voice voice : VOICES.values()) {
            stopSound(voice);
        }
        VOICES.clear();
        SETTINGS.clear();
        dimension = null;
        status = Component.empty();
        VocalTimeline.clearCache();
    }

    private record Settings(CustomData custom, boolean enabled,
                            Map<String, VocalData.Binding> bindings) {
    }

    private record Key(String trackId, String melodyId, String fingerprint, int offsetMs,
                       float gain, long loop, long startTime) {
    }

    private static final class Voice {
        private LivingEntity entity;
        private Key key;
        private Path path;
        private VocalSound sound;
        private long expectedAudioMs;
        private long midiEndMs;
        private int offsetMs;
        private float gain;
        private long lastReseekNanos;
        private long generation;
        private boolean downloadStarted;
        private boolean failed;
        private boolean failedReported;
        private boolean spent;

        private Voice(LivingEntity entity) {
            this.entity = entity;
        }
    }
}
