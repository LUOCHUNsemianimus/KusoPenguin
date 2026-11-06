package com.luochun.kusopenguin.client;

import com.luochun.kusopenguin.animation.PenguinEntitySupport;
import com.luochun.kusopenguin.client.compat.GeoMaidView;
import com.luochun.kusopenguin.client.compat.MaidCompatClient;
import com.luochun.kusopenguin.client.compat.YsmCompatClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * One scoped classification and pose snapshot for the exact Touhou Little Maid entity.
 *
 * <p>Only the TLM {@code touhou_little_maid:maid} entity type is classified. Sleeping is rejected
 * before every other state, and every unrelated passenger/vehicle combination stays out. The four
 * native supports are:
 * <ul>
 *   <li>{@link Kind#FLOOR_SIT}: {@code TamableAnimal.isInSittingPose()} with no vehicle. TLM's
 *       {@code isMaidInSittingPose()} delegates to that same flag.</li>
 *   <li>{@link Kind#ENTITY_SIT}: the maid rides TLM's ground {@code EntitySit}, whose actual
 *       passenger attachment is {@code Y=-0.125}.</li>
 *   <li>{@link Kind#CHAIR_SIT}: the maid rides TLM's {@code EntityChair}, whose actual passenger
 *       attachment is {@code getMountedHeight()+0.125}.</li>
 *   <li>{@link Kind#CARRIED}: the maid is the first passenger of a player (native TLM carry).</li>
 * </ul>
 *
 * <p>The snapshot itself is classification only. The seat fit is evaluated by
 * {@link #applyCorrections} after the shared adapter has copied the native pose: it folds the two
 * short foot roots, measures the actual weighted assembly and moves all five roots by one signed
 * floor correction, then recomputes the canonical neck translation from the final body pose. The
 * native head orientation and the native head-gear path are kept.
 */
public final class PenguinMaidPosture {
    /** Moderate forward fold: the connected tiny feet stay visible under the body. */
    private static final float FLOOR_X_ROT = -0.55F;
    private static final float FLOOR_Y_ROT = (float) (Math.PI / 10.0);
    private static final float FLOOR_Z_ROT = 0.07853982F;
    /** Chair seat is distinct from the floor: a deeper fold keeps the short feet off the cushion. */
    private static final float CHAIR_X_ROT = -0.9F;
    private static final float CHAIR_Y_ROT = 0.10F;
    private static final float CHAIR_Z_ROT = 0.035F;
    /** Actual TLM passenger attachments relative to the ordinary ground contact. */
    private static final float ENTITY_SIT_ADJUST_BLOCKS = -0.125F;

    /**
     * TLM's Bedrock carry placement. It is the native reference, not an extra roll; the wrapper
     * only shortens this offset when the carrier's arms are the shorter penguin wings.
     */
    private static final float TLM_CARRY_X = -0.375F;
    private static final float TLM_CARRY_Z = 0.375F;
    /** Vanilla player arm length in model pixels; the native carrier cue is authored for it. */
    private static final float HUMANOID_ARM_PIXELS = 12.0F;
    /** Shared penguin wing reach as an explicit fraction of the profile body height. */
    private static final float PENGUIN_WING_BODY_FRACTION = 0.5F;
    /** Native humanoid arms cued down by 65 degrees in TLM's carry placement. */
    private static final float NATIVE_ARM_DOWN_COS = (float) Math.cos(Math.toRadians(65.0));
    /** Living renderers draw the humanoid model at 15/16 of a block. */
    private static final float PLAYER_MODEL_SCALE = 0.9375F;

    public enum Kind {
        NONE, FLOOR_SIT, ENTITY_SIT, CHAIR_SIT, CARRIED, CARRIER
    }

    private final Kind kind;
    private final float seatOffset;
    /** Canonical block-space translation computed from the final prepared body/neck. */
    private Matrix4f headDelta;
    private boolean fitted;

    private PenguinMaidPosture(Kind kind, float seatOffset) {
        this.kind = kind;
        this.seatOffset = seatOffset;
    }

    /** True only for the supported TLM maid seat/carry states; never for a general passenger. */
    public static boolean isSupported(LivingEntity entity) {
        Kind kind = classify(entity);
        return kind == Kind.FLOOR_SIT || kind == Kind.ENTITY_SIT
                || kind == Kind.CHAIR_SIT || kind == Kind.CARRIED;
    }

    /**
     * A transformed penguin player carrying the exact TLM maid. This is used to suppress competing
     * clips for the carrier; the native TLM arm cues stay mapped through the ordinary adapter.
     */
    public static boolean isCarrier(LivingEntity entity) {
        if (!(entity instanceof Player player) || entity.isSleeping()
                || entity.hasPose(Pose.SLEEPING)) {
            return false;
        }
        Entity first = player.getFirstPassenger();
        return first != null
                && PenguinEntitySupport.isMaid(first.getType())
                && MaidCompatClient.isExactMaid(first);
    }

    static PenguinMaidPosture of(LivingEntity entity, EntityModel<?> model,
                                 PenguinProportions.Profile profile, PenguinBodyModel penguin) {
        Kind kind = classify(entity);
        if (kind == Kind.NONE || kind == Kind.CARRIER || profile == null || penguin == null) {
            return null;
        }
        // Classification only: the neck translation depends on the adapted/fitted body and is
        // therefore computed later, after the adapter has run. Carry keeps the native TLM pose.
        float offset = switch (kind) {
            case ENTITY_SIT -> ENTITY_SIT_ADJUST_BLOCKS;
            case CHAIR_SIT -> MaidCompatClient.chairContactOffset(entity);
            default -> 0.0F;
        };
        return new PenguinMaidPosture(kind, offset);
    }

    private static Kind classify(LivingEntity entity) {
        // Absolute first rejection: a sleeping maid keeps the complete native renderer.
        if (entity.isSleeping() || entity.hasPose(Pose.SLEEPING)) {
            return Kind.NONE;
        }
        if (!PenguinEntitySupport.isMaid(entity.getType())
                || !MaidCompatClient.isExactMaid(entity)) {
            return Kind.NONE;
        }
        int vehicle = MaidCompatClient.maidVehicleKind(entity);
        if (vehicle == 1) {
            return Kind.ENTITY_SIT;
        }
        if (vehicle == 2) {
            return Kind.CHAIR_SIT;
        }
        if (vehicle == 3) {
            return Kind.CARRIED;
        }
        if (vehicle < 0) {
            return Kind.NONE;
        }
        if (entity instanceof TamableAnimal tame && tame.isInSittingPose()) {
            return Kind.FLOOR_SIT;
        }
        return Kind.NONE;
    }

    boolean suppressesSpecialClips() {
        return this.kind != Kind.NONE;
    }

    /** Only the three actual seats need a fitted body/neck; a carried maid keeps TLM's pose. */
    boolean bodyFitPending() {
        return !this.fitted;
    }

    /**
     * Seat fit, evaluated after the adapter copied the native pose. The two short foot roots are
     * folded first, then the weighted assembly is measured and all five roots move together by one
     * signed correction to the actual contact height. The native head is re-anchored to the
     * resulting penguin neck, preserving its native rotation; the carry path is left untouched.
     */
    void applyCorrections(PenguinBodyModel penguin, EntityModel<?> original) {
        if (this.kind == Kind.CARRIED && original instanceof GeoMaidView) {
            // The delegated root already owns the cradle rotation. Native vehicle animations
            // can rotate the whole Geo skeleton, so use the shared controls in that cradle frame.
            penguin.resetPose();
            foldFeet(penguin, CHAIR_X_ROT, CHAIR_Y_ROT, CHAIR_Z_ROT);
            PenguinProportions.Profile profile = PenguinProportions.forModel(original);
            if (profile != null && YsmCompatClient.isView(original)) {
                // YSM replays captured, already-skinned head vertices. Preserve their orientation.
                this.headDelta = neckDelta(penguin, original, profile);
            } else if (profile != null) {
                Matrix4f current = PenguinHeadRenderer.headMatrix(original);
                Quaternionf inverseRotation = current.getUnnormalizedRotation(new Quaternionf())
                        .normalize().conjugate();
                this.headDelta = new Matrix4f().translation(0.0F, profile.bodyOffsetBlocks(), 0.0F)
                        .rotate(inverseRotation).translate(-current.m30(), -current.m31(), -current.m32());
            }
            this.fitted = true;
            return;
        }
        if (this.kind == Kind.FLOOR_SIT || this.kind == Kind.ENTITY_SIT) {
            foldFeet(penguin, FLOOR_X_ROT, FLOOR_Y_ROT, FLOOR_Z_ROT);
        } else if (this.kind == Kind.CHAIR_SIT) {
            foldFeet(penguin, CHAIR_X_ROT, CHAIR_Y_ROT, CHAIR_Z_ROT);
        } else {
            this.fitted = true;
            return;
        }
        PenguinProportions.Profile profile = PenguinProportions.forModel(original);
        if (profile == null || profile.bodyScale() == 0.0F) {
            this.fitted = true;
            return;
        }
        // Weighted bounds are only available after a complete tier load; without them no seat fit
        // is guessed. The head is still re-anchored to whatever body pose the adapter produced.
        PenguinMeshModel mesh = PenguinMeshModel.current();
        if (mesh != null) {
            float lowest = Float.NEGATIVE_INFINITY;
            for (String name : new String[]{"body", "right_foot", "left_foot"}) {
                lowest = Math.max(lowest,
                        profile.bodyOffsetBlocks() + mesh.maxY(penguin, name) * profile.bodyScale());
            }
            float target = profile.footY() / 16.0F + this.seatOffset;
            // Y increases downward, so a negative target-minus-current delta lifts the assembly.
            float correction = (target - lowest) * 16.0F / profile.bodyScale();
            for (ModelPart part : new ModelPart[]{penguin.body(), penguin.rightWing(),
                    penguin.leftWing(), penguin.rightFoot(), penguin.leftFoot()}) {
                part.y += correction;
            }
        }
        this.headDelta = neckDelta(penguin, original, profile);
        this.fitted = true;
    }

    private static void foldFeet(PenguinBodyModel penguin, float xRot, float yRot, float zRot) {
        ModelPart right = penguin.rightFoot();
        right.xRot = xRot;
        right.yRot = yRot;
        right.zRot = zRot;
        ModelPart left = penguin.leftFoot();
        left.xRot = xRot;
        left.yRot = -yRot;
        left.zRot = -zRot;
    }

    /**
     * Canonical block-space neck delta from the final fitted body. The native head matrix already
     * includes the profile head drop; the desired neck is the drawn penguin body position. A pure
     * translation keeps the native head orientation and is applied once by {@link #applyHead} for
     * every native headgear path.
     */
    private static Matrix4f neckDelta(PenguinBodyModel penguin, EntityModel<?> original,
                                      PenguinProportions.Profile profile) {
        Matrix4f current = PenguinHeadRenderer.headMatrix(original);
        ModelPart body = penguin.body();
        float scale = profile.bodyScale();
        return new Matrix4f().translation(
                body.x * scale / 16.0F - current.m30(),
                profile.bodyOffsetBlocks() + body.y * scale / 16.0F - current.m31(),
                body.z * scale / 16.0F - current.m32());
    }

    void applyHead(PoseStack poseStack) {
        if (this.headDelta != null) {
            poseStack.mulPose(this.headDelta);
        }
    }

    /**
     * Visual-only carry correction for a maid carried by a transformed penguin player. It is a
     * translation only; the native TLM carry rotation and the Geo/YSM native transforms stay
     * untouched.
     *
     * <p>Full support delta in common player model pixels: the native humanoid shoulder versus the
     * penguin wing shoulder, plus the vertical reach the 65-degree TLM arm cue loses when the wing
     * is shorter than the 12-pixel arm. The horizontal TLM offset keeps its modest wing/arm
     * shortening, and is rotated from the carrier body frame into render-world space by
     * {@code 180 - interpolated yBodyRot}. The result is converted to blocks at the 15/16 player
     * model scale, giving about {@code -0.51} on the standard maid profile.
     */
    public static CarryCorrection carryCorrection(Mob maid, float partialTick) {
        Player carrier = carrierOf(maid);
        if (carrier == null || !PenguinRenderSupport.shouldReplace(carrier)) {
            return null;
        }
        EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher()
                .getRenderer(carrier);
        if (!(renderer instanceof LivingEntityRenderer<?, ?> living)
                || !(living.getModel() instanceof HumanoidModel<?> humanoid)) {
            return null;
        }
        PenguinProportions.Profile profile = PenguinProportions.forModel(living.getModel());
        PenguinBodyModel shared = PenguinRenderContext.sharedModel();
        if (profile == null || shared == null) {
            return null;
        }
        float wingReach = profile.bodyHeight() * PENGUIN_WING_BODY_FRACTION;
        float ratio = wingReach / HUMANOID_ARM_PIXELS;
        float inward = ratio - 1.0F;
        float x = TLM_CARRY_X * inward;
        float z = TLM_CARRY_Z * inward;
        // Native TLM arm cue places the maid through leftArm; compare its initial shoulder with
        // the drawn penguin wing shoulder, then project the shorter arm reach vertically.
        float nativeShoulderY = humanoid.leftArm.getInitialPose().y;
        float penguinShoulderY = profile.bodyOffset()
                + shared.rightWing().getInitialPose().y * profile.bodyScale();
        float reachDifference = NATIVE_ARM_DOWN_COS * (HUMANOID_ARM_PIXELS - wingReach);
        float y = (nativeShoulderY - penguinShoulderY + reachDifference)
                * PLAYER_MODEL_SCALE / 16.0F;
        // The pose stack inside the maid renderer is not aligned with the carrier body frame.
        float yaw = (float) Math.toRadians(carryYaw(carrier, partialTick));
        float cos = Mth.cos(yaw);
        float sin = Mth.sin(yaw);
        return new CarryCorrection(x * cos + z * sin, y, -x * sin + z * cos);
    }

    private static Player carrierOf(LivingEntity maid) {
        if (maid.isSleeping() || maid.hasPose(Pose.SLEEPING)
                || !PenguinEntitySupport.isMaid(maid.getType()) || !MaidCompatClient.isExactMaid(maid)) {
            return null;
        }
        return maid.getVehicle() instanceof Player player && player.getFirstPassenger() == maid ? player : null;
    }

    private static float carryYaw(Player carrier, float partialTick) {
        return 180.0F - Mth.rotLerp(partialTick, carrier.yBodyRotO, carrier.yBodyRot);
    }

    /** TLM applies this carry frame in Bedrock setupRotations, which delegated renders bypass. */
    public static void applyDelegatedCarry(PoseStack stack, Entity entity, float partialTick) {
        if (!(entity instanceof LivingEntity maid)) {
            return;
        }
        Player carrier = carrierOf(maid);
        if (carrier == null || !(PenguinRenderSupport.shouldReplace(maid)
                || PenguinRenderSupport.shouldReplace(carrier))) {
            return;
        }
        float yaw = carryYaw(carrier, partialTick);
        stack.mulPose(Axis.YP.rotationDegrees(yaw));
        stack.translate(TLM_CARRY_X, 0.8325F, TLM_CARRY_Z);
        stack.mulPose(Axis.ZN.rotationDegrees(65.0F));
        stack.mulPose(Axis.YP.rotationDegrees(80.0F));
        stack.mulPose(Axis.YP.rotationDegrees(-yaw));
    }

    /** Scoped entity-local translation added by the TLM render wrapper. */
    public record CarryCorrection(float x, float y, float z) {
    }
}
