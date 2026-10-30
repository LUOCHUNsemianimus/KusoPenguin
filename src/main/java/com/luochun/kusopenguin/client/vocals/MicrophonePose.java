package com.luochun.kusopenguin.client.vocals;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.vocals.VocalItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;

/**
 * Optional chest-held microphone pose for players and maids.
 *
 * <p>The outer facade has no Immersive Melodies type in a public signature. Registration only
 * touches IM's animator map when the mod is loaded; the same animator is then used by the native
 * player model and by the shared penguin/maid snapshot, so there is no second renderer path. The
 * animator writes the physical main/off arm selected from {@link LivingEntity#getMainArm()}, never
 * the flipped default accessor, and leaves the head and other arm alone. No lip/mouth animation is
 * included.</p>
 */
public final class MicrophonePose {
    private static final String IM_MOD_ID = "immersive_melodies";
    private static final boolean AVAILABLE = ModList.get().isLoaded(IM_MOD_ID);

    private MicrophonePose() {
    }

    public static void register() {
        if (AVAILABLE) {
            Impl.register();
        }
    }

    /** Lazy IM-only implementation; never initialized when the optional mod is absent. */
    private static final class Impl {
        private Impl() {
        }

        static void register() {
            immersive_melodies.client.animation.ItemAnimators.register(
                ResourceLocation.fromNamespaceAndPath(KusoPenguin.MODID, "microphone"),
                new MicrophoneAnimator());
        }
    }

    private static final class MicrophoneAnimator
            implements immersive_melodies.client.animation.animators.Animator {
        @Override
        public <T extends Entity> void setAngles(
                immersive_melodies.client.animation.accessors.ModelAccessor<T> accessor,
                immersive_melodies.client.MelodyProgress progress, float time) {
            if (!(accessor.getEntity() instanceof LivingEntity living)) {
                return;
            }
            HumanoidArm arm = heldArm(living);
            if (arm == null) {
                return;
            }
            boolean right = arm == HumanoidArm.RIGHT;
            (right ? accessor.getRightArm() : accessor.getLeftArm()).ifPresent(part -> {
                part.xRot = -1.05F;
                part.yRot = right ? -0.35F : 0.35F;
                part.zRot = right ? -0.25F : 0.25F;
            });
        }

        private static HumanoidArm heldArm(LivingEntity living) {
            if (VocalItems.isMicrophone(living.getMainHandItem())) {
                return living.getMainArm();
            }
            if (!VocalItems.isMicrophone(living.getOffhandItem())) {
                return null;
            }
            return living.getMainArm() == HumanoidArm.RIGHT ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        }
    }
}
