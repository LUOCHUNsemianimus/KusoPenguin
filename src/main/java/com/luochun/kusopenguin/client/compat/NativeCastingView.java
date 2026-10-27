package com.luochun.kusopenguin.client.compat;

import com.luochun.kusopenguin.client.PenguinAnimationPose;
import com.luochun.kusopenguin.client.PenguinBodyModel;
import com.luochun.kusopenguin.client.PenguinProportions;

/**
 * Marker and pose provider for an optional native Gecko4 caster whose animation was already
 * evaluated by {@code GeoEntityRenderer}. It extends the existing {@link GeoMaidView} contract so
 * profile, adapter and head helpers keep their signatures.
 *
 * <p>The view is a live proxy over the already-animated {@code GeoBone} state. It never runs
 * {@code handleAnimations}, resets bones, restarts a clip or substitutes a Kuso taunt/kick/dance.
 * All returned rig values are in canonical penguin model pixels; the native-to-canonical
 * conversion and the native block/pixel boundary are documented at the implementation.
 */
public interface NativeCastingView extends GeoMaidView {
    /**
     * Builds the shared 16-joint pose from the current native bone state. Called once per draw and
     * cached by {@code PenguinRenderContext}; the caller owns body preparation.
     */
    PenguinAnimationPose kusoPenguin$nativePose(PenguinBodyModel parts,
                                                PenguinProportions.Profile profile);
}
