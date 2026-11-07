package com.luochun.kusopenguin.client.dance;

import com.luochun.kusopenguin.dance.Prefs;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Eight-wedge radial quick-dance wheel opened by the K fallback. It only sends the existing server
 * control packets; the center stop closes the wheel and never touches the world from the UI.
 */
public final class DanceWheelScreen extends Screen {
    private static final double TAU = Math.PI * 2.0D;
    private static final double SECTOR = Math.PI / 4.0D;

    private final UUID target;
    /** Actual boss display name for a captured boss target; {@code null} keeps self/maid labels. */
    private final Component targetName;
    private int centerX;
    private int centerY;
    private int radius;
    private int inner;
    private int hovered = -1;

    public DanceWheelScreen(UUID target) {
        this(target, null);
    }

    /**
     * Captured exact-target overload. Boss callers pass {@link net.minecraft.world.entity.Entity#getDisplayName()}
     * so the existing wheel shows the real target name without a new translation key.
     */
    public DanceWheelScreen(UUID target, Component targetName) {
        super(Component.translatable("gui.kuso_penguin.dance.wheel.title"));
        this.target = target;
        this.targetName = targetName;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        centerX = width / 2;
        centerY = height / 2 - 8;
        radius = Math.max(92, Math.min(150, Math.min(width, height) / 3));
        inner = Math.max(38, radius / 4);
        addRenderableWidget(Button.builder(Component.translatable("gui.kuso_penguin.dance.wheel.stop"),
            button -> stop()).bounds(centerX - 34, centerY - 10, 68, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.kuso_penguin.dance.manage"),
            button -> manage()).bounds(width - 112, height - 26, 102, 20).build());
    }

    public void catalogChanged() {
        // Labels are read from the live catalog on every frame, so no widget rebuild is needed.
    }

    private void stop() {
        DanceClient.stopDance(target);
        onClose();
    }

    private void manage() {
        DanceClient.refresh();
        minecraft.setScreen(new DanceScreen());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance > radius) {
            return false;
        }
        if (distance < inner) {
            stop();
            return true;
        }
        int slot = slotAt(dx, dy);
        if (slot < 0) {
            return false;
        }
        DanceClient.playSlot(slot, target);
        onClose();
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        // The parent draws its background first; drawing sectors before super would blur them.
        super.render(graphics, mouseX, mouseY, partial);
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        hovered = distance >= inner && distance <= radius ? slotAt(dx, dy) : -1;
        Prefs prefs = DanceClient.prefs();
        drawWedges(graphics, prefs);
        graphics.drawCenteredString(font, getTitle(), width / 2, 8, 0xFFFFFF);
        Component targetLabel = targetName != null ? targetName : Component.translatable(target != null
            && target.equals(net.minecraft.client.Minecraft.getInstance().player == null
                ? null : net.minecraft.client.Minecraft.getInstance().player.getUUID())
            ? "gui.kuso_penguin.dance.wheel.self"
            : "gui.kuso_penguin.dance.wheel.maid");
        graphics.drawCenteredString(font,
            Component.translatable("gui.kuso_penguin.dance.wheel.target", targetLabel),
            width / 2, 20, 0xFFFFAA);
        for (int slot = 0; slot < Prefs.SLOT_COUNT; slot++) {
            double angle = -Math.PI / 2.0D + (slot + 0.5D) * SECTOR;
            int x = centerX + (int) Math.round(Math.cos(angle) * radius * 0.66D);
            int y = centerY + (int) Math.round(Math.sin(angle) * radius * 0.66D) - 4;
            String text = DanceClient.clipLabel(prefs.slots().get(slot)).getString();
            String fitted = font.plainSubstrByWidth(text, (int) (radius * 0.43D));
            graphics.drawCenteredString(font, Integer.toString(slot + 1), x, y - 7, 0xAAAAAA);
            graphics.drawCenteredString(font, fitted, x, y + 4,
                slot == hovered ? 0xFFFFFF : 0xDDDDDD);
        }
        graphics.drawCenteredString(font,
            Component.translatable("gui.kuso_penguin.dance.wheel.hint"),
            width / 2, centerY + radius + 8, 0xAAAAAA);
        if (hovered >= 0) {
            graphics.renderTooltip(font, DanceClient.clipLabel(prefs.slots().get(hovered)), mouseX, mouseY);
        }
    }

    private void drawWedges(GuiGraphics graphics, Prefs prefs) {
        long outer = (long) radius * radius;
        long innerSq = (long) inner * inner;
        for (int y = -radius; y <= radius; y++) {
            int maxX = (int) Math.sqrt(outer - (long) y * y);
            int runStart = Integer.MIN_VALUE;
            int runSlot = -1;
            for (int x = -maxX; x <= maxX; x++) {
                int slot = (long) x * x + (long) y * y >= innerSq ? slotAt(x, y) : -1;
                if (slot != runSlot) {
                    if (runSlot >= 0) {
                        graphics.fill(centerX + runStart, centerY + y, centerX + x,
                            centerY + y + 1, wedgeColor(prefs, runSlot));
                    }
                    runStart = x;
                    runSlot = slot;
                }
            }
            if (runSlot >= 0) {
                graphics.fill(centerX + runStart, centerY + y, centerX + maxX + 1,
                    centerY + y + 1, wedgeColor(prefs, runSlot));
            }
        }
    }

    private int wedgeColor(Prefs prefs, int slot) {
        if (slot == hovered) {
            return 0xDD4B7FB5;
        }
        return prefs.slots().get(slot).isEmpty()
            ? (slot % 2 == 0 ? 0x992A2A2A : 0x99383838)
            : (slot % 2 == 0 ? 0xAA24384C : 0xAA30445A);
    }

    private static int slotAt(double dx, double dy) {
        double angle = Math.atan2(dy, dx) + Math.PI / 2.0D;
        if (angle < 0.0D) {
            angle += TAU;
        }
        int slot = (int) (angle / SECTOR);
        return slot >= 0 && slot < Prefs.SLOT_COUNT ? slot : -1;
    }
}
