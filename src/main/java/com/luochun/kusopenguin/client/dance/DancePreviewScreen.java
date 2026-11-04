package com.luochun.kusopenguin.client.dance;

import com.luochun.kusopenguin.client.PenguinAnimationClip;
import com.luochun.kusopenguin.client.PenguinRenderContext;
import com.luochun.kusopenguin.registry.ModEffects;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Pose;

/**
 * Large native preview. The avatar is a client-only detached {@link RemotePlayer} that is never
 * registered in a level; scoped context data replaces only this screen's pose evaluation.
 */
public final class DancePreviewScreen extends Screen {
    private final Screen returnTo;
    private final String clipId;
    private final PenguinAnimationClip clip;
    private final Component clipLabel;
    private RemotePlayer avatar;
    private boolean playing = true;
    private long started = Util.getMillis();
    private float pausedAge;

    public DancePreviewScreen(Screen returnTo, String clipId, PenguinAnimationClip clip,
                              Component clipLabel) {
        super(Component.translatable("gui.kuso_penguin.dance.preview_title"));
        this.returnTo = returnTo;
        this.clipId = clipId;
        this.clip = clip;
        this.clipLabel = clipLabel;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable(playing
            ? "gui.kuso_penguin.dance.pause" : "gui.kuso_penguin.dance.play"), button -> {
                if (playing) pausedAge = (Util.getMillis() - started) / 1000.0F;
                else started = Util.getMillis() - (long) (pausedAge * 1000.0F);
                playing = !playing;
                rebuildWidgets();
            }).bounds(width / 2 - 70, height - 28, 66, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.kuso_penguin.dance.back"),
            button -> onClose()).bounds(width / 2 + 4, height - 28, 66, 20).build());
    }

    private void ensureAvatar() {
        Minecraft minecraft = Minecraft.getInstance();
        if (avatar == null && minecraft.level != null && minecraft.player != null) {
            avatar = new RemotePlayer(minecraft.level, minecraft.player.getGameProfile());
            avatar.addEffect(new MobEffectInstance(ModEffects.PENGUIN, 1_000_000, 0, false, false));
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        // A solid preview stage keeps the model distinct from the live world and its GUI blur.
        graphics.fill(0, 0, width, height, 0xFF202731);
        graphics.flush();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        // Screen.render owns the background as well as widgets in this Minecraft version.
        // Draw the entity afterwards so neither a background quad nor blur can cover it.
        super.render(graphics, mouseX, mouseY, partial);
        ensureAvatar();
        if (avatar != null) {
            avatar.tickCount = (int) (Util.getMillis() / 50L);
            avatar.setPos(0.0D, 0.0D, 0.0D);
            avatar.setPose(Pose.STANDING);
            avatar.setYRot(180.0F);
            avatar.setXRot(0.0F);
            avatar.yBodyRot = 180.0F;
            avatar.yBodyRotO = 180.0F;
            avatar.yHeadRot = 180.0F;
            avatar.yHeadRotO = 180.0F;
            float age = playing ? (Util.getMillis() - started) / 1000.0F : pausedAge;
            try (PenguinRenderContext.PreviewScope ignored =
                     PenguinRenderContext.pushPreview(avatar, clipId, clip, age)) {
                InventoryScreen.renderEntityInInventoryFollowsAngle(graphics,
                    width / 2 - 100, 38, width / 2 + 100, height - 38,
                    90, -0.15F, 0.0F, 0.0F, avatar);
            }
        }
        graphics.drawCenteredString(font, getTitle(), width / 2, 10, 0xFFFFFF);
        graphics.drawCenteredString(font, clipLabel, width / 2, 24, 0xFFFFAA);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(returnTo);
    }

    @Override
    public void removed() {
        if (avatar != null) avatar.discard();
        avatar = null;
    }
}
