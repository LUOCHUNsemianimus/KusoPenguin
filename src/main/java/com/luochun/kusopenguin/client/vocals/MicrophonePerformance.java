package com.luochun.kusopenguin.client.vocals;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

/** Client-only visual cues for a live microphone stream, independent of MIDI. */
public final class MicrophonePerformance {
    private MicrophonePerformance() {
    }

    static void emitNote(LivingEntity singer) {
        Minecraft minecraft = Minecraft.getInstance();
        if (Math.floorMod(singer.tickCount + singer.getId(), 8) != 0
            || (singer == minecraft.player && minecraft.options.getCameraType().isFirstPerson())
            || !VocalClient.performingWithMicrophone(singer)) {
            return;
        }
        float yaw = singer.yBodyRot * Mth.DEG_TO_RAD;
        double forwardX = -Mth.sin(yaw);
        double forwardZ = Mth.cos(yaw);
        singer.level().addParticle(ParticleTypes.NOTE,
                singer.getX() + forwardX * 0.35, singer.getEyeY() + 0.15,
                singer.getZ() + forwardZ * 0.35, singer.getRandom().nextInt(24) / 24.0, 0, 0);
    }

    public static void render(LivingEntity singer, float partialTick, PoseStack stack, Runnable draw) {
        if (!VocalClient.performingWithMicrophone(singer)) {
            draw.run();
            return;
        }
        float yaw = Mth.rotLerp(partialTick, singer.yBodyRotO, singer.yBodyRot) * Mth.DEG_TO_RAD;
        float roll = Mth.sin((singer.tickCount + partialTick) * 0.1F) * 0.05F;
        stack.pushPose();
        try {
            stack.mulPose(new Quaternionf().rotationAxis(roll, -Mth.sin(yaw), 0, Mth.cos(yaw)));
            draw.run();
        } finally {
            stack.popPose();
        }
    }
}
