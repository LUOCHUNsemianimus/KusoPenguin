package com.luochun.kusopenguin.client.vocals;

import com.luochun.kusopenguin.client.music.DiscClient;
import com.luochun.kusopenguin.client.music.DiscTransfers;
import com.luochun.kusopenguin.music.DiscPackets;
import com.luochun.kusopenguin.vocals.VocalData;
import com.luochun.kusopenguin.vocals.VocalItems;
import com.luochun.kusopenguin.vocals.VocalPackets;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Human-facing vocal binding editor for the currently selected Immersive Melodies song.
 *
 * <p>The screen only edits persisted item bindings; native playback controls and the native
 * timeline remain untouched. It is deliberately non-pausing and resizes from remembered text
 * values so catalog/state refreshes do not discard typing.</p>
 */
public final class VocalScreen extends Screen {
    private static final int ROW_HEIGHT = 18;
    private static final int CATALOG_TOP = 72;
    private static final int BOTTOM_RESERVED = 112;

    private VocalPackets.State state;
    private final Screen parent;
    private InteractionHand hand;
    private java.util.UUID ticket;
    private String melodyId = "";
    private String melodyHash = "";
    private String itemId = "";
    private boolean microphone;
    private VocalData.Binding binding;
    private boolean enabled;
    private String lastMelodyKey = "";
    private Component status = Component.empty();

    private EditBox path;
    private EditBox offset;
    private EditBox gain;
    private String rememberedPath = "";
    private String rememberedOffset = "0";
    private String rememberedGain = "1.0";
    private int page;
    private String selectedTrack = "";
    private boolean awaitingUploadedTrack;
    private boolean ownImport;

    VocalScreen(VocalPackets.State state, Screen parent) {
        super(Component.translatable("gui.kuso_penguin.vocals.title"));
        this.parent = parent;
        this.ticket = state.ticket();
        this.hand = state.hand();
        this.state = state;
        this.melodyId = state.melodyId();
        this.melodyHash = state.melodyHash();
        this.itemId = state.itemId();
        this.microphone = VocalItems.isMicrophoneId(state.itemId());
        this.binding = state.binding();
        this.enabled = state.enabled();
        this.lastMelodyKey = melodyKey();
        applyBindingDefaults();
        if (!state.status().isEmpty()) {
            this.status = Component.translatable(state.status());
        }
    }

    java.util.UUID ticket() {
        return ticket;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        rememberFields();
        super.resize(minecraft, width, height);
    }

    @Override
    protected void init() {
        int panel = panelWidth();
        int left = (width - panel) / 2;
        int pathWidth = panel - 116;

        path = new EditBox(font, left, 32, pathWidth, 18,
            Component.translatable("gui.kuso_penguin.vocals.path"));
        path.setMaxLength(2048);
        path.setValue(rememberedPath);
        addRenderableWidget(path);
        addRenderableWidget(Button.builder(
                Component.translatable("gui.kuso_penguin.vocals.import"), b -> importPath())
            .bounds(left + pathWidth + 3, 32, 55, 18).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.kuso_penguin.vocals.cancel"), b -> cancelImport())
            .bounds(left + pathWidth + 61, 32, 55, 18).build());

        int pagerX = left + panel - 114;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            page = Math.max(0, page - 1);
            rememberFields();
            rebuildWidgets();
        }).bounds(pagerX, 52, 24, 16).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            page = Math.min(pages() - 1, page + 1);
            rememberFields();
            rebuildWidgets();
        }).bounds(pagerX + 27, 52, 24, 16).build()).active = page + 1 < pages();
        addRenderableWidget(Button.builder(
                Component.translatable("gui.kuso_penguin.vocals.refresh"),
                b -> DiscClient.refresh()).bounds(pagerX + 54, 52, 60, 16).build());

        int rows = rows();
        List<com.luochun.kusopenguin.music.MusicLibrary.Track> catalog = DiscClient.catalog();
        for (int row = 0; row < rows; row++) {
            int index = page * rows + row;
            if (index >= catalog.size()) {
                break;
            }
            var track = catalog.get(index);
            String label = (track.id().equals(selectedTrack) ? "> " : "") + track.title();
            addRenderableWidget(Button.builder(
                    Component.literal(font.plainSubstrByWidth(label, panel - 12)), b -> {
                        selectedTrack = track.id();
                        rememberFields();
                        rebuildWidgets();
                    }).bounds(left, CATALOG_TOP + row * ROW_HEIGHT, panel, 16).build());
        }

        int controlsY = height - 88;
        offset = new EditBox(font, left + 56, controlsY, 64, 18,
            Component.translatable("gui.kuso_penguin.vocals.offset"));
        offset.setMaxLength(7);
        offset.setValue(microphone ? "0" : rememberedOffset);
        offset.setEditable(!microphone);
        addRenderableWidget(offset);
        gain = new EditBox(font, left + 164, controlsY, 52, 18,
            Component.translatable("gui.kuso_penguin.vocals.gain"));
        gain.setMaxLength(8);
        gain.setValue(rememberedGain);
        addRenderableWidget(gain);

        int actionY = height - 66;
        addRenderableWidget(Button.builder(
                Component.translatable("gui.kuso_penguin.vocals.save"), b -> saveBinding())
            .bounds(width / 2 - 105, actionY, 100, 18).build()).active = !selectedTrack.isEmpty();
        addRenderableWidget(Button.builder(
                Component.translatable("gui.kuso_penguin.vocals.remove"), b -> removeBinding())
            .bounds(width / 2 + 5, actionY, 100, 18).build()).active = binding != null;

        if (microphone) {
            addRenderableWidget(Button.builder(
                    Component.translatable("gui.kuso_penguin.vocals.play"), b -> play())
                .bounds(width / 2 - 105, height - 44, 100, 18).build());
            addRenderableWidget(Button.builder(
                    Component.translatable("gui.kuso_penguin.vocals.pause"), b -> pause())
                .bounds(width / 2 + 5, height - 44, 100, 18).build());
        } else {
            addRenderableWidget(Button.builder(Component.translatable(
                    enabled ? "gui.kuso_penguin.vocals.sing"
                        : "gui.kuso_penguin.vocals.instrumental"),
                    b -> toggle()).bounds(width / 2 - 75, height - 44, 150, 18).build());
        }

        Component done = parent == null ? Component.translatable("gui.done")
            : Component.translatable("gui.kuso_penguin.vocals.back");
        addRenderableWidget(Button.builder(done, b -> onClose())
            .bounds(width / 2 - 65, height - 22, 130, 18).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        renderBackground(graphics, mouseX, mouseY, partial);
        super.render(graphics, mouseX, mouseY, partial);
        int panel = panelWidth();
        int left = (width - panel) / 2;

        graphics.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF);
        String handText = Component.translatable(hand == InteractionHand.MAIN_HAND
            ? "gui.kuso_penguin.vocals.main_hand" : "gui.kuso_penguin.vocals.off_hand").getString();
        int handWidth = font.width(handText);
        int songWidth = panel - handWidth - 8;
        String song = microphone ? microphoneSong() : (melodyId.isEmpty()
            ? Component.translatable("gui.kuso_penguin.vocals.empty").getString() : melodyId);
        String trimmedSong = font.plainSubstrByWidth(song, Math.max(40, songWidth));
        graphics.drawString(font, trimmedSong, left, 18, 0xCCCCCC, false);
        graphics.drawString(font, handText, left + panel - handWidth, 18, 0x999999, false);
        if (font.width(song) > songWidth && mouseY >= 18 && mouseY <= 30
            && mouseX >= left && mouseX <= left + songWidth) {
            graphics.renderTooltip(font, Component.literal(song), mouseX, mouseY);
        }

        String formats = Component.translatable("gui.kuso_penguin.vocals.formats").getString();
        graphics.drawString(font, font.plainSubstrByWidth(formats, Math.max(40, panel - 122)),
            left, 56, 0x888888, false);
        String pageText = (page + 1) + "/" + pages();
        graphics.drawString(font, pageText, left + panel - 136, 56, 0xAAAAAA, false);

        graphics.drawString(font, Component.translatable("gui.kuso_penguin.vocals.offset"),
            left, height - 83, 0xAAAAAA, false);
        graphics.drawString(font, Component.translatable("gui.kuso_penguin.vocals.gain"),
            left + 124, height - 83, 0xAAAAAA, false);
        graphics.drawString(font, Component.translatable("gui.kuso_penguin.vocals.offset_hint"),
            left + 220, height - 83, 0x777777, false);

        String hint = Component.translatable(microphone
            ? "gui.kuso_penguin.vocals.microphone_hint"
            : "gui.kuso_penguin.vocals.maid_hint").getString();
        graphics.drawString(font, font.plainSubstrByWidth(hint, panel), left, height - 110,
            0x777777, false);

        // The shared disc uploader also reports disc-specific saved/write messages. In the vocal
        // editor only this screen's own in-flight import may show that status; completion is
        // replaced by a vocal-specific message in onDiscReply.
        Component line = ownImport ? DiscTransfers.status() : Component.empty();
        if (line.getString().isEmpty()) {
            line = status;
        }
        if (line.getString().isEmpty()) {
            line = VocalClient.status();
        }
        if (!line.getString().isEmpty()) {
            graphics.drawString(font, font.plainSubstrByWidth(line.getString(), panel), left,
                height - 100, 0xFFFFAA, false);
        }
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        if (files.isEmpty()) {
            return;
        }
        String value = files.getFirst().toString();
        if (path != null) {
            path.setValue(value);
            rememberedPath = value;
        }
        importFile(value);
    }

    @Override
    public void onClose() {
        if (minecraft != null && parent != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public void removed() {
        if (ownImport) {
            DiscTransfers.cancelUpload();
            ownImport = false;
        }
        VocalScreens.removed(this);
    }

    void updateState(VocalPackets.State next) {
        boolean nextMicrophone = VocalItems.isMicrophoneId(next.itemId());
        // A microphone binding hash changes on every BIND/REMOVE; that is the same editor and must
        // not reset the selected clip or typed inputs. Ordinary melody changes still reset.
        boolean melodyChanged = !nextMicrophone
            && !melodyKey(next).equals(lastMelodyKey);
        if (!melodyChanged) {
            rememberFields();
        }
        state = next;
        ticket = next.ticket();
        hand = next.hand();
        itemId = next.itemId();
        melodyId = next.melodyId();
        melodyHash = next.melodyHash();
        binding = next.binding();
        enabled = next.enabled();
        microphone = nextMicrophone;
        if (microphone) {
            rememberedOffset = "0";
        }
        lastMelodyKey = melodyKey();
        if (melodyChanged) {
            page = 0;
            applyBindingDefaults();
        } else if (microphone && binding != null && selectedTrack.isEmpty()) {
            selectedTrack = binding.trackId();
        }
        if (!next.status().isEmpty()) {
            status = Component.translatable(next.status());
        }
        if (minecraft != null && minecraft.screen == this) {
            rebuildWidgets();
        }
    }

    void onFailure(VocalPackets.State failure) {
        if (!failure.status().isEmpty()) {
            status = Component.translatable(failure.status());
        }
        rebuildWidgets();
    }

    void catalogChanged() {
        rememberFields();
        if (awaitingUploadedTrack && !selectedTrack.isEmpty()) {
            int index = trackIndex(selectedTrack);
            if (index >= 0) {
                page = index / rows();
                awaitingUploadedTrack = false;
            }
        }
        page = Math.max(0, Math.min(page, pages() - 1));
        rebuildWidgets();
    }

    void onDiscReply(DiscPackets.Reply reply) {
        if (reply.action() != DiscPackets.Action.END_UPLOAD || !ownImport) {
            return;
        }
        ownImport = false;
        if (reply.success() && !reply.trackId().isEmpty()) {
            status = Component.translatable("message.kuso_penguin.vocals.imported");
            uploadFinished(reply.trackId());
            return;
        }
        status = Component.translatable(reply.message());
        rememberFields();
        rebuildWidgets();
    }

    void uploadFinished(String trackId) {
        if (!trackId.isEmpty()) {
            selectedTrack = trackId;
            awaitingUploadedTrack = true;
        }
        rememberFields();
        rebuildWidgets();
    }

    private void saveBinding() {
        rememberFields();
        if (selectedTrack.isEmpty()) {
            status = Component.translatable("message.kuso_penguin.vocals.select_track");
            rebuildWidgets();
            return;
        }
        Integer parsedOffset = microphone ? Integer.valueOf(0) : parseOffset();
        if (parsedOffset == null) {
            return;
        }
        Float parsedGain = parseGain();
        if (parsedGain == null) {
            return;
        }
        rememberFields();
        send(VocalPackets.Action.BIND, selectedTrack, parsedOffset, parsedGain, enabled);
    }

    private void removeBinding() {
        send(VocalPackets.Action.REMOVE, "", 0, 1.0F, false);
    }

    private void toggle() {
        send(VocalPackets.Action.TOGGLE, "", 0, 1.0F, !enabled);
    }

    private void play() {
        send(VocalPackets.Action.PLAY, "", 0, 1.0F, true);
    }

    private void pause() {
        send(VocalPackets.Action.PAUSE, "", 0, 1.0F, false);
    }

    private void send(VocalPackets.Action action, String track, int offsetMs, float gainValue,
                      boolean enable) {
        if (ticket == null || hand == null) {
            return;
        }
        PacketDistributor.sendToServer(new VocalPackets.Request(action, ticket, hand, track,
            offsetMs, gainValue, enable));
    }

    private Integer parseOffset() {
        try {
            int value = Integer.parseInt(offset.getValue().strip());
            if (value < VocalData.MIN_OFFSET_MS || value > VocalData.MAX_OFFSET_MS) {
                status = Component.translatable("message.kuso_penguin.vocals.invalid_offset");
                rebuildWidgets();
                return null;
            }
            rememberedOffset = Integer.toString(value);
            return value;
        } catch (NumberFormatException error) {
            status = Component.translatable("message.kuso_penguin.vocals.invalid_offset");
            rebuildWidgets();
            return null;
        }
    }

    private Float parseGain() {
        try {
            float value = Float.parseFloat(gain.getValue().strip().replace(',', '.'));
            if (!Float.isFinite(value) || value < 0.0F || value > VocalData.MAX_GAIN) {
                status = Component.translatable("message.kuso_penguin.vocals.invalid_gain");
                rebuildWidgets();
                return null;
            }
            rememberedGain = Float.toString(value);
            return value;
        } catch (NumberFormatException error) {
            status = Component.translatable("message.kuso_penguin.vocals.invalid_gain");
            rebuildWidgets();
            return null;
        }
    }

    private void importPath() {
        String value = path == null ? "" : path.getValue().strip();
        if (value.length() > 1 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        importFile(value);
    }

    private void importFile(String value) {
        if (value.isEmpty()) {
            return;
        }
        try {
            ownImport = true;
            DiscTransfers.importFile(Path.of(value));
        } catch (RuntimeException error) {
            ownImport = false;
            rememberFields();
            status = Component.translatable("message.kuso_penguin.vocals.import_failed");
            DiscTransfers.reportError(error);
            rebuildWidgets();
        }
    }

    private void cancelImport() {
        if (ownImport) {
            rememberFields();
            DiscTransfers.cancelUpload();
            ownImport = false;
            status = Component.translatable("gui.kuso_penguin.vocals.import_cancelled");
            rebuildWidgets();
        }
    }

    private void applyBindingDefaults() {
        if (microphone) {
            rememberedOffset = "0";
            rememberedGain = binding == null ? "1.0" : Float.toString(binding.gain());
            selectedTrack = binding == null ? "" : binding.trackId();
            return;
        }
        if (binding == null) {
            rememberedOffset = "0";
            rememberedGain = "1.0";
            selectedTrack = "";
            return;
        }
        rememberedOffset = Integer.toString(binding.offsetMs());
        rememberedGain = Float.toString(binding.gain());
        selectedTrack = binding.trackId();
    }

    private void rememberFields() {
        if (path != null) {
            rememberedPath = path.getValue();
        }
        if (offset != null) {
            rememberedOffset = offset.getValue();
        }
        if (gain != null) {
            rememberedGain = gain.getValue();
        }
    }

    private String microphoneSong() {
        String id = !selectedTrack.isEmpty() ? selectedTrack
            : binding == null ? "" : binding.trackId();
        if (id.isEmpty()) {
            return Component.translatable("gui.kuso_penguin.vocals.microphone_empty").getString();
        }
        String title = trackTitle(id);
        return title.isEmpty()
            ? Component.translatable("gui.kuso_penguin.vocals.microphone_missing").getString()
            : title;
    }

    private String trackTitle(String id) {
        for (var track : DiscClient.catalog()) {
            if (track.id().equals(id)) {
                return track.title();
            }
        }
        return "";
    }

    private int trackIndex(String id) {
        List<com.luochun.kusopenguin.music.MusicLibrary.Track> catalog = DiscClient.catalog();
        for (int i = 0; i < catalog.size(); i++) {
            if (catalog.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private int rows() {
        return Math.max(1, (height - BOTTOM_RESERVED - CATALOG_TOP) / ROW_HEIGHT);
    }

    private int pages() {
        int count = DiscClient.catalog().size();
        return Math.max(1, (count + rows() - 1) / rows());
    }

    private int panelWidth() {
        return Math.min(460, width - 24);
    }

    private String melodyKey() {
        return melodyKey(state);
    }

    private static String melodyKey(VocalPackets.State value) {
        return value.melodyId() + '\u0000' + value.melodyHash();
    }
}
