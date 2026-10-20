package com.luochun.kusopenguin.client;

/** Model-pixel bind contract, independent of native mob bone lengths. */
public final class PenguinRig {
    public static final int COUNT=16, ROOT=0, PELVIS=1, CHEST=2, HEAD=3;
    public static final int RIGHT_WING=4, LEFT_WING=7, RIGHT_HIP=10, LEFT_HIP=13;
    public static final String[] NAMES={"root","pelvis","chest","head_mount",
        "right_wing","right_wing_mid","right_wing_tip","left_wing","left_wing_mid","left_wing_tip",
        "right_hip","right_knee","right_foot","left_hip","left_knee","left_foot"};
    public static final int[] PARENTS={-1,0,1,2,2,4,5,2,7,8,0,10,11,0,13,14};
    private PenguinRig() {}
}
