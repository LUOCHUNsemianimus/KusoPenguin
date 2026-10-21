package com.luochun.kusopenguin.client.music;

import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** One paginated library, shared by imports and already-stored server tracks. */
public final class DiscScreen extends Screen {
    private EditBox path;
    private int page;
    private String rememberedPath = "";
    public DiscScreen() { super(Component.translatable("gui.kuso_penguin.disc.title")); }
    @Override public boolean isPauseScreen() { return false; }
    private int rows() { return Math.max(1, (height - 168) / 22); }
    @Override protected void init() {
        int panel = Math.min(460, width - 32), left = (width - panel) / 2;
        path = new EditBox(font, left, 35, panel, 20, Component.translatable("gui.kuso_penguin.disc.path"));
        path.setMaxLength(2048); path.setValue(rememberedPath); addRenderableWidget(path);
        addRenderableWidget(Button.builder(Component.translatable("gui.kuso_penguin.disc.import"), b -> importPath())
                .bounds(left, 60, panel / 2 - 3, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.kuso_penguin.disc.cancel"), b -> DiscTransfers.cancelUpload())
                .bounds(left + panel / 2 + 3, 60, panel / 2 - 3, 20).build());
        int count = DiscClient.catalog().size(); page = Math.max(0, Math.min(page, Math.max(0, (count - 1) / rows())));
        for (int row = 0; row < rows() && page * rows() + row < count; row++) {
            var track = DiscClient.catalog().get(page * rows() + row);
            String label = (track.id().equals(DiscClient.selected()) ? "> " : "") + track.title();
            addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(label, panel - 12)), b -> {
                DiscClient.selected(track.id()); catalogChanged();
            }).bounds(left, 94 + row * 22, panel, 20).build());
        }
        int bottom = height - 66;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; catalogChanged(); }).bounds(left, bottom, 28, 20).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; catalogChanged(); }).bounds(left + 34, bottom, 28, 20).build()).active = (page + 1) * rows() < count;
        addRenderableWidget(Button.builder(Component.translatable("gui.kuso_penguin.disc.refresh"), b -> DiscClient.refresh()).bounds(left + 68, bottom, 75, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.kuso_penguin.disc.write"), b -> DiscClient.selectTrack(DiscClient.selected()))
                .bounds(left + panel - 143, bottom, 143, 20).build()).active = !DiscClient.selected().isEmpty();
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 65, height - 24, 130, 20).build());
    }
    public void catalogChanged() { rememberedPath = path == null ? rememberedPath : path.getValue(); rebuildWidgets(); }
    private void importPath() {
        String value = path.getValue().strip();
        if (value.length() > 1 && value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
        try { DiscTransfers.importFile(Path.of(value)); } catch (RuntimeException error) { DiscTransfers.reportError(error); }
    }
    @Override public void onFilesDrop(List<Path> files) {
        if (!files.isEmpty()) { path.setValue(files.getFirst().toString()); DiscTransfers.importFile(files.getFirst()); }
    }
    @Override public void removed() { DiscTransfers.cancelUpload(); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        renderBackground(graphics, mouseX, mouseY, partial);
        super.render(graphics, mouseX, mouseY, partial);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        int left = (width - Math.min(460, width - 32)) / 2;
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable("gui.kuso_penguin.disc.hint").getString(), width - 32), left, 24, 0xBBBBBB, false);
        graphics.drawString(font, font.plainSubstrByWidth(DiscTransfers.status().getString(), width - 32), 16, height - 42, 0xFFFFAA, false);
    }
}
