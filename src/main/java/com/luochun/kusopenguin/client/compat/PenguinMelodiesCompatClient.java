package com.luochun.kusopenguin.client.compat;

import com.luochun.kusopenguin.client.PenguinBodyModel;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Optional Immersive Melodies 0.8.0 instrument-pose bridge.
 *
 * <p>The outer facade contains no Immersive Melodies type in a signature. The nested implementation
 * is resolved only after {@link ModList} confirms the mod is present; a standalone game or a
 * dedicated server never loads its classes. The bridge evaluates IM's own public animator on
 * disposable scratch {@link ModelPart}s, seeded at neutral, then copies the resulting physical arm
 * rotations onto the prepared shared penguin wings. Native player/Bedrock/Gecko paths keep their
 * own clock; only an actual YSM maid view, whose native wrapper never advances the IM visual
 * envelope, receives one envelope update per cached snapshot. It never reads native player/maid
 * bones.
 */
public final class PenguinMelodiesCompatClient {
    private static final String MOD_ID = "immersive_melodies";
    private static final ResourceLocation MAID_ID =
            ResourceLocation.fromNamespaceAndPath("touhou_little_maid", "maid");
    private static final ResourceLocation LUTE_ID =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "lute");
    /**
     * Authored model-pixel forward clearance for the lute body inside the short, rounded penguin
     * belly. This is a per-item mount offset, not a wing-root move or a body/instrument resize.
     * 8.0F is validated against player/Bedrock/Gecko/YSM main/offhand visuals.
     */
    private static final float LUTE_FORWARD_CLEARANCE_PIXELS = 8.0F;
    private static final boolean AVAILABLE = ModList.get().isLoaded(MOD_ID);

    private PenguinMelodiesCompatClient() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /** Cheap hand check used before the render-context priority gates run. */
    public static boolean holdingInstrument(LivingEntity entity) {
        return AVAILABLE && supportedEntity(entity) && Impl.holdingInstrument(entity);
    }

    /**
     * Evaluates one IM instrument pose for this draw. The caller caches the result per render frame
     * and owns every priority gate (death, kick, casting, posture, mesh mode and replacement).
     */
    public static Snapshot snapshot(LivingEntity entity) {
        return snapshot(entity, false);
    }

    /**
     * @param advanceVisualProgress true only for an actual YSM maid render view, whose native
     *        wrapper does not run the player IM update; all other paths pass false and keep the
     *        native clock untouched.
     */
    public static Snapshot snapshot(LivingEntity entity, boolean advanceVisualProgress) {
        return AVAILABLE && supportedEntity(entity)
                ? Impl.snapshot(entity, advanceVisualProgress) : null;
    }

    /**
     * Authored forward clearance in canonical penguin model pixels for the item actually held in
     * the requested physical arm. Main/offhand is resolved against the entity's main arm, so a
     * left-handed player and a maid use the same physical mapping. Only the exact
     * {@code immersive_melodies:lute} returns a non-zero value; every other item, including other
     * IM instruments, returns zero. The selected/leading instrument is deliberately not consulted
     * because two instruments may coexist in different hands.
     */
    public static float heldItemForwardPixels(LivingEntity entity, HumanoidArm arm) {
        if (entity == null || arm == null) {
            return 0.0F;
        }
        ItemStack stack = arm == entity.getMainArm()
                ? entity.getMainHandItem() : entity.getOffhandItem();
        if (stack.isEmpty()) {
            return 0.0F;
        }
        return LUTE_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                ? LUTE_FORWARD_CLEARANCE_PIXELS : 0.0F;
    }

    /**
     * The exact vanilla player plus the exact Touhou Little Maid entity type. Bosses, other maids'
     * variants and every other mob keep their complete existing renderer.
     */
    private static boolean supportedEntity(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        return entity instanceof Player
                || MAID_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    /**
     * Immutable per-draw instrument pose. Wing angles are physical left/right arm rotations;
     * penguin wing names are historical, so the anatomical left arm maps to the positive-X
     * {@code right_wing} and the anatomical right arm maps to the negative-X {@code left_wing}.
     */
    public static final class Snapshot {
        private final float rightWingX;
        private final float rightWingY;
        private final float rightWingZ;
        private final float leftWingX;
        private final float leftWingY;
        private final float leftWingZ;
        private final boolean headPitchWritten;
        private final boolean headYawWritten;
        private final float headPitch;
        private final float headYaw;

        private Snapshot(float rightWingX, float rightWingY, float rightWingZ,
                         float leftWingX, float leftWingY, float leftWingZ,
                         boolean headPitchWritten, boolean headYawWritten,
                         float headPitch, float headYaw) {
            this.rightWingX = rightWingX;
            this.rightWingY = rightWingY;
            this.rightWingZ = rightWingZ;
            this.leftWingX = leftWingX;
            this.leftWingY = leftWingY;
            this.leftWingZ = leftWingZ;
            this.headPitchWritten = headPitchWritten;
            this.headYawWritten = headYawWritten;
            this.headPitch = headPitch;
            this.headYaw = headYaw;
        }

        /**
         * Applies only rotations to the already prepared wings. Body/leg pivots, translations,
         * preserved bind scales and the shared wing-tip held-item mount stay untouched.
         */
        public void applyTo(PenguinBodyModel penguin) {
            if (penguin == null) {
                return;
            }
            ModelPart rightWing = penguin.rightWing();
            rightWing.xRot = rightWingX;
            rightWing.yRot = rightWingY;
            rightWing.zRot = rightWingZ;
            ModelPart leftWing = penguin.leftWing();
            leftWing.xRot = leftWingX;
            leftWing.yRot = leftWingY;
            leftWing.zRot = leftWingZ;
        }

        /**
         * Rigid head-channel replacement for native views that do not already run IM's humanoid
         * mixin. The current canonical rotation is decomposed as ZYX; only channels IM actually
         * wrote are replaced (zero is an authored value), while Z and unwritten channels remain.
         * The target is built in the same ZYX order and applied as a rigid delta around the current
         * head pivot, so native head size/shear/translation are never mutated.
         *
         * <p>Player/Bedrock heads already contain IM's head angles, so the caller passes
         * {@code supplement=false} there and this returns null.
         */
        public Matrix4f headDelta(Matrix4f canonicalHead, boolean supplement) {
            if (!supplement || canonicalHead == null
                    || (!headPitchWritten && !headYawWritten)) {
                return null;
            }
            Quaternionf current = canonicalHead.getUnnormalizedRotation(new Quaternionf());
            if (current.lengthSquared() < 1.0E-8F) {
                current.identity();
            } else {
                current.normalize();
            }
            // JOML 1.10.5's quaternion ZYX extractor uses a wrong +y*y denominator for the X
            // channel (primary source:
            // https://github.com/JOML-CI/JOML/blob/1.10.5/src/org/joml/Quaternionf.java#L1748-L1752).
            // Extract from a pure UNIT rotation matrix instead: canonicalHead itself may carry
            // nonuniform scale, so its matrix Euler extraction must not be used directly.
            Vector3f euler = new Matrix4f().rotation(current).getEulerAnglesZYX(new Vector3f());
            if (headPitchWritten) {
                euler.x = headPitch;
            }
            if (headYawWritten) {
                euler.y = headYaw;
            }
            Quaternionf target = new Quaternionf().rotationZYX(euler.z, euler.y, euler.x);
            Quaternionf delta = target.mul(new Quaternionf(current).conjugate());
            Vector3f pivot = canonicalHead.getTranslation(new Vector3f());
            return new Matrix4f().translation(pivot).rotate(delta)
                    .translate(-pivot.x, -pivot.y, -pivot.z);
        }
    }

    /**
     * Lazy implementation. This class is not initialized until the IM mod is loaded, so its
     * compile-only imports never become a production runtime requirement.
     */
    private static final class Impl {
        private Impl() {
        }

        static boolean holdingInstrument(LivingEntity entity) {
            return immersive_melodies.client.animation.EntityModelAnimator.getInstrument(entity) != null;
        }

        static Snapshot snapshot(LivingEntity entity, boolean advanceVisualProgress) {
            Item instrument = immersive_melodies.client.animation.EntityModelAnimator
                    .getInstrument(entity);
            if (instrument == null) {
                return null;
            }
            ModelPart head = new ModelPart(List.of(), Map.of());
            ModelPart hat = new ModelPart(List.of(), Map.of());
            ModelPart leftArm = new ModelPart(List.of(), Map.of());
            ModelPart rightArm = new ModelPart(List.of(), Map.of());
            RecordingAccessor accessor = new RecordingAccessor(entity, head, hat, leftArm,
                    rightArm);
            // Same frame-clock expression as EntityModelAnimator.setAngles. Native
            // player/Bedrock/Gecko already own visualTick; only the actual YSM maid view, whose
            // native wrapper never calls the player IM update, advances the missing envelope once
            // here. The per-frame snapshot cache makes all head/body/held layers share that update.
            float time = (Minecraft.getInstance().isPaused() ? 0.0F
                    : Minecraft.getInstance().getFrameTimeNs() / 1_000_000_000.0F)
                    + entity.tickCount;
            immersive_melodies.client.MelodyProgress progress =
                    immersive_melodies.client.MelodyProgressManager.INSTANCE.getProgress(entity);
            if (advanceVisualProgress) {
                progress.visualTick(time);
            }
            immersive_melodies.client.animation.ItemAnimators
                    .get(BuiltInRegistries.ITEM.getKey(instrument))
                    .setAngles(accessor, progress, time);
            return new Snapshot(leftArm.xRot, leftArm.yRot, leftArm.zRot,
                    rightArm.xRot, rightArm.yRot, rightArm.zRot,
                    accessor.headPitchWritten(), accessor.headYawWritten(),
                    head.xRot, head.yRot);
        }

        /**
         * Starts every scratch part at neutral and records only whether the animator wrote head
         * channels. Arm read-back uses the physical parts, so IM's main/offhand and handedness
         * mapping stays in IM's own {@code ArmsAndHeadAccessor}.
         */
        private static final class RecordingAccessor extends
                immersive_melodies.client.animation.accessors.ArmsAndHeadAccessor<LivingEntity> {
            private boolean headPitchWritten;
            private boolean headYawWritten;

            RecordingAccessor(LivingEntity entity, ModelPart head, ModelPart hat,
                              ModelPart leftArm, ModelPart rightArm) {
                super(entity, head, hat, leftArm, rightArm);
            }

            @Override
            public void headPitch(float pitch) {
                headPitchWritten = true;
                super.headPitch(pitch);
            }

            @Override
            public void headYaw(float yaw) {
                headYawWritten = true;
                super.headYaw(yaw);
            }

            boolean headPitchWritten() {
                return headPitchWritten;
            }

            boolean headYawWritten() {
                return headYawWritten;
            }
        }
    }
}
