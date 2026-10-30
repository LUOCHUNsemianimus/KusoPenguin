package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.client.vocals.VocalScreens;
import com.luochun.kusopenguin.compat.MelodiesVocalCompat;
import com.luochun.kusopenguin.vocals.VocalPackets;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the vocal settings entry to Immersive Melodies' native melody screen.
 *
 * <p>The injection runs at the end of {@code refreshPage}, because IM clears all widgets whenever
 * the selected song changes. Existing play/pause/song controls are untouched; the new button only
 * requests the vocal screen for the actually held main instrument (else the off hand).</p>
 */
@Pseudo
@Mixin(targets = "immersive_melodies.client.gui.ImmersiveMelodiesScreen", remap = false)
public abstract class MelodiesVocalScreenMixin extends Screen {
    protected MelodiesVocalScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "refreshPage", at = @At("TAIL"), remap = false)
    private void kuso$addVocalsButton(CallbackInfo callback) {
        int y = Math.min(this.height - 22, this.height / 2 + 92);
        addRenderableWidget(Button.builder(
                Component.translatable("gui.kuso_penguin.vocals.native_button"), button -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.player == null) {
                        return;
                    }
                    ItemStack main = minecraft.player.getMainHandItem();
                    InteractionHand hand = MelodiesVocalCompat.isInstrument(main)
                        ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                    VocalScreens.requestOpen((Screen) (Object) this);
                    PacketDistributor.sendToServer(new VocalPackets.Request(
                        VocalPackets.Action.OPEN, UUID.randomUUID(), hand, "", 0, 1.0F, false));
                }).bounds(this.width / 2 - 75, y, 150, 20).build());
    }
}
