package com.luochun.kusopenguin.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional Iron's Spellbooks / PlayerAnimator bridge, reflection only.
 *
 * <p>The mod is compiled without either dependency. Every optional type is resolved lazily by name
 * after {@link ModList} confirms both mods are loaded, so a standalone client or dedicated server
 * never links a missing class. Expected reflection failures are logged once and disable only this
 * bridge; the native renderer keeps ownership.
 *
 * <p>One immutable {@link Snapshot} is taken per draw. It reads the final post-mirror/fade applier
 * values; it never ticks an animation, calls {@code updatePart}, replays a frame or applies a
 * mirror again. Native PlayerAnimator applies bends outside the {@code ModelPart} tree, so the
 * snapshot keeps the render-layer body bend separate from the torso+body bend copied by the native
 * applier.
 */
public final class IronCastingCompatClient {
    private static final String IRONS_MOD_ID = "irons_spellbooks";
    private static final String PLAYER_ANIM_MOD_ID = "playeranimator";
    /** Iron creates this modifier layer id in {@code SpellAnimations.ANIMATION_RESOURCE}. */
    private static final ResourceLocation ANIMATION_LAYER =
            ResourceLocation.fromNamespaceAndPath(IRONS_MOD_ID, "animation");
    private static final Logger LOGGER = LoggerFactory.getLogger("kuso_penguin/casting_compat");
    private static final boolean IRONS_LOADED = ModList.get().isLoaded(IRONS_MOD_ID);
    private static final boolean PLAYER_ANIM_LOADED = ModList.get().isLoaded(PLAYER_ANIM_MOD_ID);
    private static volatile boolean reflectionFailureLogged;

    private IronCastingCompatClient() {
    }

    /** PlayerAnimator bend pair: {@code direction} is Pair.left, {@code amount} is Pair.right (radians). */
    public record Bend(float direction, float amount) {
        public static final Bend NONE = new Bend(0.0F, 0.0F);

        public Bend plus(Bend other) {
            return new Bend(direction + other.direction, amount + other.amount);
        }
    }

    /**
     * Final native item channel sampled from the same applier as the bends. Scale is unitless,
     * position is in model pixels (native {@code changeItemLocation} divides by 16) and rotation
     * is in radians. It is the exact {@code leftItem}/{@code rightItem} transform PlayerAnimator
     * appends after vanilla's hand tail, including the continuous_thrust non-identity values.
     */
    public record ItemChannel(float scaleX, float scaleY, float scaleZ,
                              float posX, float posY, float posZ,
                              float rotX, float rotY, float rotZ) {
        public static final ItemChannel IDENTITY =
                new ItemChannel(1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    /**
     * Final per-frame values. {@code body} is the native torso+body sum copied by the applier into
     * the body ModelPart; {@code layerBody} is the body-only bend PlayerAnimator applies to every
     * upper render layer; the limb values are the applier bends for that limb. Both item channels
     * are sampled in the same pass and never re-read during the draw.
     */
    public record Snapshot(boolean bendEnabled, Bend body, Bend layerBody, Bend leftArm,
                           Bend rightArm, Bend leftLeg, Bend rightLeg,
                           ItemChannel leftItem, ItemChannel rightItem) {
    }

    /**
     * Returns null unless Iron's associated animation layer and the PlayerAnimator applier are both
     * currently active. Both gates are required so ordinary PlayerAnimator emotes are untouched.
     */
    public static Snapshot snapshot(AbstractClientPlayer player) {
        if (player == null || !IRONS_LOADED || !PLAYER_ANIM_LOADED) {
            return null;
        }
        try {
            return Impl.snapshot(player);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logFailure(exception);
            return null;
        }
    }

    /**
     * Applies the exact inverse of PlayerAnimator's layer-level body bend:
     * {@code T(0,.375,0) * R(-amount) * T(0,-.375,0)} in renderer blocks. It pushes only when the
     * layer is a native upper part and the body bend is non-zero; the caller owns the matching pop.
     *
     * @return true only when a push was made
     */
    public static boolean pushInverseBodyBend(PoseStack poseStack, Object layer,
                                              float direction, float amount) {
        if (!PLAYER_ANIM_LOADED || poseStack == null || layer == null || amount == 0.0F) {
            return false;
        }
        try {
            if (!Impl.isUpperPart(layer)) {
                return false;
            }
            poseStack.pushPose();
            try {
                Impl.rotateBodyBend(poseStack, direction, -amount);
                return true;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                poseStack.popPose();
                throw exception;
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logFailure(exception);
            return false;
        }
    }

    /**
     * Appends PlayerAnimator's native item channel after vanilla's hand tail, in its exact order:
     * scale, translate(positionPixels / 16), then Rz, Ry, Rx. The channel was already sampled from
     * the same applier after mirror/fade, so it is applied once and never re-read here.
     */
    public static void applyItemTransform(PoseStack poseStack, ItemChannel channel) {
        if (poseStack == null || channel == null) {
            return;
        }
        // Native changeItemLocation uses Vec3f.scale(0.0625F) for position: model pixels -> blocks.
        poseStack.scale(channel.scaleX(), channel.scaleY(), channel.scaleZ());
        poseStack.translate(channel.posX() / 16.0F, channel.posY() / 16.0F,
                channel.posZ() / 16.0F);
        poseStack.mulPose(Axis.ZP.rotation(channel.rotZ()));
        poseStack.mulPose(Axis.YP.rotation(channel.rotY()));
        poseStack.mulPose(Axis.XP.rotation(channel.rotX()));
    }

    private static void logFailure(Exception exception) {
        // Bounded: one line for the first expected incompatibility, never a per-frame spam loop.
        if (!reflectionFailureLogged) {
            reflectionFailureLogged = true;
            LOGGER.warn("Iron casting compatibility disabled after an optional API mismatch", exception);
        }
    }

    /**
     * Lazy implementation holder. This nested class is initialized only after both mods are present,
     * and only when a snapshot or layer bend is actually requested.
     */
    private static final class Impl {
        private static boolean ready;
        private static Class<?> ASSOCIATED_DATA;
        private static Method GET_ASSOCIATED_DATA;
        private static Method GET_LAYER;
        private static Method LAYER_ACTIVE;
        private static Method GET_ANIMATION;
        private static Method APPLIER_ACTIVE;
        private static Method GET_BEND;
        private static Method PAIR_LEFT;
        private static Method PAIR_RIGHT;
        private static Method GET_3D_TRANSFORM;
        private static Constructor<?> VEC3F_CONSTRUCTOR;
        private static Method VEC3F_X;
        private static Method VEC3F_Y;
        private static Method VEC3F_Z;
        private static Object TRANSFORM_SCALE;
        private static Object TRANSFORM_POSITION;
        private static Object TRANSFORM_ROTATION;
        private static Method IS_BEND_ENABLED;
        private static Class<?> UPPER_PART;
        private static Method IS_UPPER_PART;
        private static Object BEND_HELPER;
        private static Method ROTATE_MATRIX_STACK;
        private static Constructor<?> PAIR_CONSTRUCTOR;

        static {
            try {
                Class<?> access = Class.forName(
                        "dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess");
                GET_ASSOCIATED_DATA = access.getMethod("getPlayerAssociatedData",
                        AbstractClientPlayer.class);
                ASSOCIATED_DATA = Class.forName(
                        "dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess$PlayerAssociatedAnimationData");
                GET_LAYER = ASSOCIATED_DATA.getMethod("get", ResourceLocation.class);
                Class<?> modifierLayer = Class.forName(
                        "dev.kosmx.playerAnim.api.layered.ModifierLayer");
                LAYER_ACTIVE = modifierLayer.getMethod("isActive");
                Class<?> animatedPlayer = Class.forName(
                        "dev.kosmx.playerAnim.impl.IAnimatedPlayer");
                GET_ANIMATION = animatedPlayer.getMethod("playerAnimator_getAnimation");
                Class<?> applier = Class.forName(
                        "dev.kosmx.playerAnim.impl.animation.AnimationApplier");
                APPLIER_ACTIVE = applier.getMethod("isActive");
                GET_BEND = applier.getMethod("getBend", String.class);
                Class<?> processor = Class.forName(
                        "dev.kosmx.playerAnim.core.impl.AnimationProcessor");
                Class<?> transformType = Class.forName(
                        "dev.kosmx.playerAnim.api.TransformType");
                Class<?> vec3f = Class.forName("dev.kosmx.playerAnim.core.util.Vec3f");
                GET_3D_TRANSFORM = processor.getMethod("get3DTransform", String.class,
                        transformType, vec3f);
                VEC3F_CONSTRUCTOR = vec3f.getConstructor(float.class, float.class, float.class);
                VEC3F_X = vec3f.getMethod("getX");
                VEC3F_Y = vec3f.getMethod("getY");
                VEC3F_Z = vec3f.getMethod("getZ");
                TRANSFORM_SCALE = transformType.getField("SCALE").get(null);
                TRANSFORM_POSITION = transformType.getField("POSITION").get(null);
                TRANSFORM_ROTATION = transformType.getField("ROTATION").get(null);
                Class<?> pair = Class.forName("dev.kosmx.playerAnim.core.util.Pair");
                PAIR_LEFT = pair.getMethod("getLeft");
                PAIR_RIGHT = pair.getMethod("getRight");
                PAIR_CONSTRUCTOR = pair.getConstructor(Object.class, Object.class);
                Class<?> helper = Class.forName("dev.kosmx.playerAnim.impl.Helper");
                IS_BEND_ENABLED = helper.getMethod("isBendEnabled");
                UPPER_PART = Class.forName("dev.kosmx.playerAnim.impl.IUpperPartHelper");
                IS_UPPER_PART = UPPER_PART.getMethod("isUpperPart");
                Class<?> bendHelper = Class.forName(
                        "dev.kosmx.playerAnim.impl.animation.IBendHelper");
                Field instance = bendHelper.getField("INSTANCE");
                BEND_HELPER = instance.get(null);
                ROTATE_MATRIX_STACK = bendHelper.getMethod("rotateMatrixStack",
                        PoseStack.class, pair);
                ready = true;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                // Bounded failure: the optional bridge stays off and the native renderer runs.
                logFailure(exception);
            }
        }

        private Impl() {
        }

        static Snapshot snapshot(AbstractClientPlayer player) throws ReflectiveOperationException {
            if (!ready) {
                return null;
            }
            Object data = GET_ASSOCIATED_DATA.invoke(null, player);
            Object layer = GET_LAYER.invoke(data, ANIMATION_LAYER);
            if (layer == null || !Boolean.TRUE.equals(LAYER_ACTIVE.invoke(layer))) {
                return null;
            }
            Object applier = GET_ANIMATION.invoke(player);
            if (applier == null || !Boolean.TRUE.equals(APPLIER_ACTIVE.invoke(applier))) {
                return null;
            }
            boolean bends = Boolean.TRUE.equals(IS_BEND_ENABLED.invoke(null));
            // Both item channels are sampled from the same final applier even when bends are off:
            // native HeldItemMixin.changeItemLocation is independent of Helper.isBendEnabled.
            ItemChannel leftItem = readItem(applier, "leftItem");
            ItemChannel rightItem = readItem(applier, "rightItem");
            if (!bends) {
                // Native bend disabled: neutral limbs and keep the already-animated roots.
                return new Snapshot(false, Bend.NONE, Bend.NONE, Bend.NONE, Bend.NONE,
                        Bend.NONE, Bend.NONE, leftItem, rightItem);
            }
            Bend torso = readBend(applier, "torso");
            Bend body = readBend(applier, "body");
            return new Snapshot(true, torso.plus(body), body,
                    readBend(applier, "leftArm"), readBend(applier, "rightArm"),
                    readBend(applier, "leftLeg"), readBend(applier, "rightLeg"),
                    leftItem, rightItem);
        }

        static boolean isUpperPart(Object layer) throws ReflectiveOperationException {
            return ready && UPPER_PART.isInstance(layer)
                    && Boolean.TRUE.equals(IS_UPPER_PART.invoke(layer));
        }

        static void rotateBodyBend(PoseStack poseStack, float direction, float amount)
                throws ReflectiveOperationException {
            Object pair = PAIR_CONSTRUCTOR.newInstance(Float.valueOf(direction),
                    Float.valueOf(amount));
            ROTATE_MATRIX_STACK.invoke(BEND_HELPER, poseStack, pair);
        }

        private static Bend readBend(Object applier, String part)
                throws ReflectiveOperationException {
            Object pair = GET_BEND.invoke(applier, part);
            Object left = PAIR_LEFT.invoke(pair);
            Object right = PAIR_RIGHT.invoke(pair);
            if (!(left instanceof Number) || !(right instanceof Number)) {
                throw new IllegalStateException("PlayerAnimator returned a non-numeric bend for "
                        + part);
            }
            float direction = ((Number) left).floatValue();
            float amount = ((Number) right).floatValue();
            if (!Float.isFinite(direction) || !Float.isFinite(amount)) {
                throw new IllegalStateException("PlayerAnimator returned a non-finite bend for "
                        + part);
            }
            return new Bend(direction, amount);
        }

        /**
         * Reads one item channel exactly as native changeItemLocation does: SCALE defaults to 1,
         * POSITION to zero pixels and ROTATION to zero radians. All three calls use the same final
         * applier, after mirror/fade, and are made once per draw.
         */
        private static ItemChannel readItem(Object applier, String part)
                throws ReflectiveOperationException {
            Object scale = get3DTransform(applier, part, TRANSFORM_SCALE, 1.0F);
            Object position = get3DTransform(applier, part, TRANSFORM_POSITION, 0.0F);
            Object rotation = get3DTransform(applier, part, TRANSFORM_ROTATION, 0.0F);
            return new ItemChannel(
                    vec(scale, VEC3F_X), vec(scale, VEC3F_Y), vec(scale, VEC3F_Z),
                    vec(position, VEC3F_X), vec(position, VEC3F_Y), vec(position, VEC3F_Z),
                    vec(rotation, VEC3F_X), vec(rotation, VEC3F_Y), vec(rotation, VEC3F_Z));
        }

        private static Object get3DTransform(Object applier, String part, Object type,
                                             float defaultValue)
                throws ReflectiveOperationException {
            Object fallback = VEC3F_CONSTRUCTOR.newInstance(defaultValue, defaultValue,
                    defaultValue);
            return GET_3D_TRANSFORM.invoke(applier, part, type, fallback);
        }

        private static float vec(Object vec3f, Method getter) throws ReflectiveOperationException {
            Object value = getter.invoke(vec3f);
            if (!(value instanceof Number number) || !Float.isFinite(number.floatValue())) {
                throw new IllegalStateException("PlayerAnimator returned a non-finite item value");
            }
            return number.floatValue();
        }
    }
}
