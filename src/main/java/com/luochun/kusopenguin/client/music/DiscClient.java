package com.luochun.kusopenguin.client.music;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.music.*;
import com.luochun.kusopenguin.registry.ModEffects;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Main-thread orchestration. Only server packets decide playback and dance membership. */
@EventBusSubscriber(modid = KusoPenguin.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class DiscClient implements DiscClientHooks.Listener {
    private record Key(ResourceLocation dimension, BlockPos pos) {}
    private static final class Playing {
        final DiscPackets.Playback packet; DiscMusicSound sound;
        Playing(DiscPackets.Playback packet) { this.packet = packet; }
    }
    private static final Map<Key, Playing> MUSIC = new HashMap<>();
    private static final Map<UUID, DiscPackets.Dance> DANCERS = new HashMap<>();
    private static List<MusicLibrary.Track> tracks = List.of();
    private static String selection = "";
    private static ResourceLocation dimension;
    private static final Minecraft MC = Minecraft.getInstance();
    public static List<MusicLibrary.Track> catalog() { return tracks; }
    public static String selected() { return selection; }
    public static void selected(String id) { selection = id; }
    public static void refresh() { PacketDistributor.sendToServer(new DiscPackets.Request(DiscPackets.Action.LIST, UUID.randomUUID(), "", "", 0)); }
    public static void selectTrack(String id) { PacketDistributor.sendToServer(new DiscPackets.Request(DiscPackets.Action.SELECT, UUID.randomUUID(), id, "", 0)); }
    @Override public void onReply(DiscPackets.Reply reply) { DiscTransfers.onReply(reply); }
    @Override public void onChunk(DiscPackets.Chunk chunk) { DiscTransfers.onChunk(chunk); }
    @Override public void onCatalog(DiscPackets.Catalog catalog) {
        tracks = List.copyOf(catalog.tracks());
        if (MC.screen instanceof DiscScreen screen) screen.catalogChanged();
        else if (catalog.open()) MC.setScreen(new DiscScreen());
    }
    @Override public void onPlayback(DiscPackets.Playback packet) {
        Key key = new Key(packet.dimension(), packet.pos()); Playing old = MUSIC.get(key);
        if (!packet.playing()) {
            if (old != null && old.packet.session().equals(packet.session())) { MUSIC.remove(key); stop(old); }
            DANCERS.values().removeIf(d -> d.session().equals(packet.session())); return;
        }
        if (old != null && old.packet.session().equals(packet.session())) return;
        if (old != null) stop(old);
        Playing playing = new Playing(packet); MUSIC.put(key, playing);
        DiscTransfers.audio(packet.trackId()).whenComplete((path, error) -> MC.execute(() -> {
            if (MUSIC.get(key) != playing || !valid(playing)) return;
            if (error != null) { DiscTransfers.reportError(error); return; }
            playing.sound = new DiscMusicSound(packet, path, () -> MUSIC.get(key) == playing);
            MC.getSoundManager().play(playing.sound);
        }));
    }
    private static boolean valid(Playing playing) {
        return MC.level != null && MC.level.dimension().location().equals(playing.packet.dimension())
                && MC.level.getGameTime() < playing.packet.startedGameTime() + playing.packet.durationTicks();
    }
    private static void stop(Playing playing) {
        if (playing.sound != null) { playing.sound.end(); MC.getSoundManager().stop(playing.sound); }
    }
    @Override public void onDance(DiscPackets.Dance packet) {
        if (packet.playing()) DANCERS.put(packet.entity(), packet);
        else { var old = DANCERS.get(packet.entity()); if (old != null && old.session().equals(packet.session())) DANCERS.remove(packet.entity()); }
    }
    public static double danceElapsed(LivingEntity entity, float partial) {
        var state = DANCERS.get(entity.getUUID());
        if (state == null || !state.dimension().equals(entity.level().dimension().location())
                || !entity.isAlive() || entity.isBaby() || !entity.hasEffect(ModEffects.PENGUIN)) return -1;
        return Math.max(0, (entity.level().getGameTime() - state.startedGameTime() + partial) / 20.0);
    }
    public static boolean dancing(LivingEntity entity) { return danceElapsed(entity, 0) >= 0; }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (MC.level == null) return;
        ResourceLocation current = MC.level.dimension().location();
        if (dimension != null && !dimension.equals(current)) clear();
        dimension = current; DiscTransfers.tick();
        MUSIC.values().removeIf(p -> { if (!valid(p)) { stop(p); return true; } return false; });
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    public static void clear() {
        for (Playing playing : MUSIC.values()) stop(playing);
        MUSIC.clear(); DANCERS.clear(); tracks = List.of(); selection = ""; dimension = null; DiscTransfers.clear();
    }
}
