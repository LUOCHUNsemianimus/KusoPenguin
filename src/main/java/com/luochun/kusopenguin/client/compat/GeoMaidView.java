package com.luochun.kusopenguin.client.compat;

import com.luochun.kusopenguin.client.PenguinPoseAdapter;
import com.luochun.kusopenguin.client.PenguinProportions;
import org.joml.Matrix4f;

/**
 * Model-view contract for an optional Touhou Little Maid Gecko model.
 *
 * <p>The proxy is a real {@code EntityModel} instance so every existing profile, pose-adapter and
 * head helper can keep its current signature. All methods below return neutral values when the
 * active Gecko model has no supported native head, which makes the caller keep the complete
 * original renderer.
 *
 * <p>Coordinates: the native Gecko model is Y-up with the floor at Y=0. The shared penguin body
 * is authored in the vanilla model frame (Y-down, floor at 24 px) and is drawn under the vanilla
 * {@code scale(-1,-1,1) / translate(0,-1.5,0)} flip. {@link #kusoPenguin$headMatrix()} therefore
 * returns the native head transform expressed in that same penguin model frame.
 */
public interface GeoMaidView {
    /** True only when the current Gecko model has a usable native head subtree. */
    boolean kusoPenguin$ready();

    PenguinProportions.Profile kusoPenguin$profile();

    PenguinPoseAdapter kusoPenguin$adapter();

    /** Native head matrix in penguin/vanilla canonical model coordinates. */
    Matrix4f kusoPenguin$headMatrix();
}
