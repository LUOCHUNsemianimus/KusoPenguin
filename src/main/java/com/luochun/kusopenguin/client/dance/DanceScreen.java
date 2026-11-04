package com.luochun.kusopenguin.client.dance;

import com.luochun.kusopenguin.client.PenguinAnimationClip;
import com.luochun.kusopenguin.dance.DanceImport;
import com.luochun.kusopenguin.dance.DanceLibrary;
import com.luochun.kusopenguin.dance.Prefs;
import com.luochun.kusopenguin.dance.Trigger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Compact, paginated dance library and management screen. It only prepares a local preview;
 * uploading is an explicit second action, and every ownership/title/trigger/slot change waits for
 * the server catalog instead of showing a phantom success.
 */
public final class DanceScreen extends Screen {
    private EditBox pathBox;
    private EditBox titleBox;
    private String rememberedPath = "";
    private String rememberedTitle = "";
    private String selectedId = "";
    private int page;
    private int panel;
    private int left;
    private int listWidth;
    private int rightX;
    private int rightWidth;
    private int listTop = 110;
    private int listBottom;
    private int pageY;
    private PenguinAnimationClip previewClip;
    private String previewHash = "";
    private String previewTitle = "";
    private long lastPreparedGeneration = -1L;
    private boolean reopenPreview;
    private Component localMessage = Component.empty();

    private record Entry(String id, Component label, boolean builtin) {
    }

    public DanceScreen() {
        super(Component.translatable("gui.kuso_penguin.dance.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        panel = Math.min(430, width - 16);
        left = (width - panel) / 2;
        listWidth = Math.max(120, panel * 3 / 5);
        rightX = left + listWidth + 4;
        rightWidth = panel - listWidth - 4;
        listTop = 110;
        listBottom = height - 22;
        pageY = height - 18;
        int importWidth = 64;
        int fieldWidth = panel - importWidth - 4;
        pathBox = new EditBox(font, left, 14, fieldWidth, 16,
            Component.translatable("gui.kuso_penguin.dance.path"));
        pathBox.setMaxLength(2048);
        pathBox.setValue(rememberedPath);
        addRenderableWidget(pathBox);
        addRenderableWidget(Button.builder(Component.translatable("gui.kuso_penguin.dance.import"),
            button -> importPath()).bounds(left + fieldWidth + 4, 14, importWidth, 16).build());

        titleBox = new EditBox(font, left, 34, fieldWidth, 16,
            Component.translatable("gui.kuso_penguin.dance.clip_title"));
        titleBox.setMaxLength(DanceLibrary.MAX_TITLE_CHARS);
        titleBox.setValue(rememberedTitle);
        addRenderableWidget(titleBox);
        Button preview = Button.builder(Component.translatable("gui.kuso_penguin.dance.preview"),
            button -> openPreview()).bounds(left + fieldWidth + 4, 34, importWidth, 16).build();
        preview.active = previewReady();
        addRenderableWidget(preview);

        int actionWidth = (panel - 9) / 4;
        int actionY = 54;
        addRenderableWidget(Button.builder(Component.translatable("gui.kuso_penguin.dance.upload"),
            button -> DanceTransfers.upload(titleBox.getValue()))
            .bounds(left, actionY, actionWidth, 16).build()).active = DanceTransfers.previewReady();
        addRenderableWidget(Button.builder(Component.translatable("gui.kuso_penguin.dance.rename"),
            button -> renameSelected())
            .bounds(left + actionWidth + 3, actionY, actionWidth, 16).build()).active =
            ownSelected();
        addRenderableWidget(Button.builder(Component.translatable("gui.kuso_penguin.dance.remove"),
            button -> removeSelected())
            .bounds(left + (actionWidth + 3) * 2, actionY, actionWidth, 16).build()).active =
            ownSelected();
        addRenderableWidget(Button.builder(Component.translatable("gui.kuso_penguin.dance.cancel"),
            button -> DanceTransfers.cancelUpload())
            .bounds(left + (actionWidth + 3) * 3, actionY, actionWidth, 16).build());

        List<Entry> entries = entries();
        int rows = rows();
        int maxPage = Math.max(0, (entries.size() - 1) / rows);
        page = Math.max(0, Math.min(page, maxPage));
        for (int row = 0; row < rows && page * rows + row < entries.size(); row++) {
            Entry entry = entries.get(page * rows + row);
            String marker = entry.id().equals(selectedId) ? "> " : "";
            String text = marker + entry.label().getString();
            addRenderableWidget(Button.builder(
                Component.literal(font.plainSubstrByWidth(text, listWidth - 8)),
                button -> select(entry.id()))
                .bounds(left, listTop + row * 14, listWidth, 14).build());
        }

        // Four trigger rows plus four quick-slot rows must stay above the reset button even at
        // 1280x720 GUI 3 (240 scaled pixels). Keep the normal 12 px rows on taller screens.
        boolean compactRows = height < 260;
        int triggerStep = compactRows ? 11 : 12;
        int triggerY = listTop;
        for (Trigger trigger : Trigger.values()) {
            addRenderableWidget(triggerButton(trigger, rightX, triggerY, rightWidth, triggerStep));
            triggerY += triggerStep;
        }
        int slotStep = compactRows ? 11 : 12;
        int slotY = triggerY + (compactRows ? 2 : 4);
        int slotWidth = (rightWidth - 2) / 2;
        for (int slot = 0; slot < Prefs.SLOT_COUNT; slot++) {
            int column = slot % 2;
            int row = slot / 2;
            int slotIndex = slot;
            Button button = Button.builder(slotLabel(slot), b -> assignSlot(slotIndex))
                .bounds(rightX + column * (slotWidth + 2), slotY + row * slotStep,
                    slotWidth, slotStep).build();
            button.setTooltip(Tooltip.create(fullSlotLabel(slot)));
            button.active = DanceClient.selectable(selectedId);
            addRenderableWidget(button);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.kuso_penguin.dance.reset"),
            button -> DanceClient.resetDefaults())
            .bounds(rightX, listBottom - 14, rightWidth, 14).build());

        addRenderableWidget(Button.builder(Component.literal("<"),
            button -> changePage(-1)).bounds(left, pageY, 26, 16).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal(">"),
            button -> changePage(1)).bounds(left + 30, pageY, 26, 16).build()).active =
            (page + 1) * rows < entries.size();
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),
            button -> onClose()).bounds(left + panel - 64, pageY, 64, 16).build());
    }

    @Override
    protected void rebuildWidgets() {
        rememberFields();
        super.rebuildWidgets();
    }

    private void rememberFields() {
        if (pathBox != null) {
            rememberedPath = pathBox.getValue();
        }
        if (titleBox != null) {
            rememberedTitle = titleBox.getValue();
        }
    }

    private boolean ownSelected() {
        return DanceClient.ownClip(selectedId) != null;
    }

    private boolean previewReady() {
        return DanceLibrary.isBuiltin(selectedId)
            || (previewClip != null && selectedId.equals(previewHash));
    }

    private int rows() {
        return Math.max(1, (listBottom - listTop) / 14);
    }

    private List<Entry> entries() {
        List<Entry> result = new ArrayList<>();
        for (DanceLibrary.Builtin builtin : DanceClient.builtins()) {
            result.add(new Entry(builtin.id(),
                Component.translatable(builtin.translationKey()), true));
        }
        for (DanceLibrary.Clip clip : DanceClient.clips()) {
            result.add(new Entry(clip.hash(), Component.literal(clip.title()), false));
        }
        return result;
    }

    private Component triggerLabel(Trigger trigger) {
        return Component.translatable("gui.kuso_penguin.dance.trigger."
            + trigger.name().toLowerCase(java.util.Locale.ROOT));
    }

    private String currentSelection(Trigger trigger) {
        Prefs prefs = DanceClient.prefs();
        return switch (trigger) {
            case MUSIC -> prefs.music();
            case REVIVAL -> prefs.revival();
            case MAID_MUSIC -> prefs.maidMusic();
            case ANIMAL_MUSIC -> prefs.animalMusic();
        };
    }

    private Button triggerButton(Trigger trigger, int x, int y, int width, int rowHeight) {
        Component full = Component.literal(triggerLabel(trigger).getString() + ": "
            + DanceClient.clipLabel(currentSelection(trigger)).getString());
        String fitted = font.plainSubstrByWidth(full.getString(), width - 6);
        Button button = Button.builder(Component.literal(fitted), b -> assign(trigger))
            .bounds(x, y, width, rowHeight).build();
        Component tooltip = trigger == Trigger.ANIMAL_MUSIC
            ? full.copy().append("\n").append(Component.translatable(
                "gui.kuso_penguin.dance.trigger.animal_music.tooltip"))
            : full;
        button.setTooltip(Tooltip.create(tooltip));
        button.active = DanceClient.selectable(selectedId);
        return button;
    }

    private Component fullSlotLabel(int slot) {
        return Component.literal((slot + 1) + ": "
            + DanceClient.clipLabel(DanceClient.prefs().slots().get(slot)).getString());
    }

    private Component slotLabel(int slot) {
        String fitted = font.plainSubstrByWidth(fullSlotLabel(slot).getString(),
            (rightWidth - 2) / 2 - 6);
        return Component.literal(fitted);
    }

    private void importPath() {
        String value = pathBox == null ? "" : pathBox.getValue().strip();
        if (value.length() > 1 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        try {
            DanceTransfers.prepare(Path.of(value));
        } catch (RuntimeException exception) {
            DanceTransfers.reportError(exception);
        }
    }

    private void openPreview() {
        rememberFields();
        Minecraft minecraft = Minecraft.getInstance();
        if (DanceLibrary.isBuiltin(selectedId)) {
            reopenPreview = true;
            minecraft.setScreen(new DancePreviewScreen(this, selectedId, null,
                DanceClient.clipLabel(selectedId)));
            return;
        }
        if (previewClip != null && selectedId.equals(previewHash)) {
            reopenPreview = true;
            minecraft.setScreen(new DancePreviewScreen(this, selectedId, previewClip,
                DanceClient.clipLabel(selectedId)));
            return;
        }
        localMessage = Component.translatable("gui.kuso_penguin.dance.no_preview");
        rebuildWidgets();
    }

    private void select(String id) {
        selectedId = id;
        localMessage = Component.empty();
        if (DanceLibrary.isStrictHash(id)) {
            loadPreview(id);
        } else {
            previewClip = null;
            previewHash = "";
            previewTitle = "";
        }
        rebuildWidgets();
    }

    private void loadPreview(String hash) {
        PenguinAnimationClip cached = DanceTransfers.cachedClip(hash);
        if (cached != null) {
            setPreview(cached, DanceClient.clipLabel(hash).getString(), hash);
            return;
        }
        DanceClient.requestClip(hash).whenComplete((clip, error) ->
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
            if (net.minecraft.client.Minecraft.getInstance().screen != this
                || !selectedId.equals(hash)) {
                return;
            }
            if (error != null || clip == null) {
                DanceTransfers.reportError(error == null
                    ? new IllegalArgumentException("Missing dance clip") : error);
                return;
            }
            setPreview(clip, DanceClient.clipLabel(hash).getString(), hash);
            rebuildWidgets();
        }));
    }

    private void setPreview(PenguinAnimationClip clip, String label, String hash) {
        previewClip = clip;
        previewHash = hash;
        previewTitle = label;
    }

    private void acceptPrepared() {
        DanceImport.Result result = DanceTransfers.prepared();
        if (result == null) {
            previewClip = null;
            previewHash = "";
            previewTitle = "";
            return;
        }
        setPreview(result.canonical().clip(), result.name(), result.canonical().hash());
        selectedId = result.canonical().hash();
        if (rememberedTitle.isBlank()) {
            rememberedTitle = result.name();
            if (titleBox != null) {
                titleBox.setValue(result.name());
            }
        }
    }

    private void assign(Trigger trigger) {
        if (!DanceClient.selectable(selectedId)) {
            return;
        }
        DanceClient.setPreference(trigger, selectedId);
    }

    private void assignSlot(int slot) {
        if (!DanceClient.selectable(selectedId)) {
            return;
        }
        DanceClient.setSlot(slot, selectedId);
    }

    private void renameSelected() {
        DanceLibrary.Clip clip = DanceClient.ownClip(selectedId);
        if (clip == null || titleBox == null) {
            localMessage = Component.translatable("gui.kuso_penguin.dance.select_own");
            return;
        }
        DanceClient.renameClip(selectedId, titleBox.getValue());
    }

    private void removeSelected() {
        DanceLibrary.Clip clip = DanceClient.ownClip(selectedId);
        if (clip == null) {
            localMessage = Component.translatable("gui.kuso_penguin.dance.select_own");
            return;
        }
        DanceClient.deleteClip(selectedId);
    }

    private void changePage(int delta) {
        page = Math.max(0, page + delta);
        rebuildWidgets();
    }

    public void catalogChanged() {
        rebuildWidgets();
    }

    public void statusChanged() {
        // The server catalog is the only state change that requires a widget rebuild.
    }

    @Override
    public void tick() {
        super.tick();
        if (DanceTransfers.preparedGeneration() != lastPreparedGeneration) {
            lastPreparedGeneration = DanceTransfers.preparedGeneration();
            acceptPrepared();
            rebuildWidgets();
        }
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        if (files.isEmpty()) {
            return;
        }
        Path file = files.getFirst();
        if (pathBox != null) {
            pathBox.setValue(file.toString());
            rememberedPath = file.toString();
        }
        DanceTransfers.prepare(file);
    }

    @Override
    public void removed() {
        rememberFields();
        if (reopenPreview) {
            reopenPreview = false;
            return;
        }
        DanceTransfers.cancelUpload();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        renderBackground(graphics, mouseX, mouseY, partial);
        super.render(graphics, mouseX, mouseY, partial);
        graphics.drawCenteredString(font, getTitle(), width / 2, 2, 0xFFFFFF);
        String status = DanceTransfers.importing() || !localMessage.getString().isEmpty()
            ? (localMessage.getString().isEmpty()
                ? DanceTransfers.status().getString() : localMessage.getString())
            : DanceTransfers.status().getString();
        graphics.drawString(font, font.plainSubstrByWidth(status, panel - 40),
            left, 74, 0xFFFFAA, false);
        if (previewTitle != null && !previewTitle.isEmpty()) {
            graphics.drawString(font, font.plainSubstrByWidth(
                Component.translatable("gui.kuso_penguin.dance.preview_hint", previewTitle)
                    .getString(), panel - 40), left, 86, 0xAAAAAA, false);
        }
    }
}
