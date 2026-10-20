// Made with Blockbench 5.2.2
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports


public class kuso_penguin_rounded<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("modid", "kuso_penguin_rounded"), "main");
	private final ModelPart body;
	private final ModelPart torso_00_front_right_joint;
	private final ModelPart torso_00_front_left_joint;
	private final ModelPart torso_00_rear_right_joint;
	private final ModelPart torso_00_rear_left_joint;
	private final ModelPart torso_01_front_right_joint;
	private final ModelPart torso_01_front_left_joint;
	private final ModelPart torso_01_rear_right_joint;
	private final ModelPart torso_01_rear_left_joint;
	private final ModelPart torso_02_front_right_joint;
	private final ModelPart torso_02_front_left_joint;
	private final ModelPart torso_02_rear_right_joint;
	private final ModelPart torso_02_rear_left_joint;
	private final ModelPart torso_03_front_right_joint;
	private final ModelPart torso_03_front_left_joint;
	private final ModelPart torso_03_rear_right_joint;
	private final ModelPart torso_03_rear_left_joint;
	private final ModelPart torso_04_front_right_joint;
	private final ModelPart torso_04_front_left_joint;
	private final ModelPart torso_04_rear_right_joint;
	private final ModelPart torso_04_rear_left_joint;
	private final ModelPart torso_05_front_right_joint;
	private final ModelPart torso_05_front_left_joint;
	private final ModelPart torso_05_rear_right_joint;
	private final ModelPart torso_05_rear_left_joint;
	private final ModelPart torso_06_front_right_joint;
	private final ModelPart torso_06_front_left_joint;
	private final ModelPart torso_06_rear_right_joint;
	private final ModelPart torso_06_rear_left_joint;
	private final ModelPart torso_07_front_right_joint;
	private final ModelPart torso_07_front_left_joint;
	private final ModelPart torso_07_rear_right_joint;
	private final ModelPart torso_07_rear_left_joint;
	private final ModelPart torso_08_front_right_joint;
	private final ModelPart torso_08_front_left_joint;
	private final ModelPart torso_08_rear_right_joint;
	private final ModelPart torso_08_rear_left_joint;
	private final ModelPart torso_09_front_right_joint;
	private final ModelPart torso_09_front_left_joint;
	private final ModelPart torso_09_rear_right_joint;
	private final ModelPart torso_09_rear_left_joint;
	private final ModelPart torso_10_front_right_joint;
	private final ModelPart torso_10_front_left_joint;
	private final ModelPart torso_10_rear_right_joint;
	private final ModelPart torso_10_rear_left_joint;
	private final ModelPart torso_11_front_right_joint;
	private final ModelPart torso_11_front_left_joint;
	private final ModelPart torso_11_rear_right_joint;
	private final ModelPart torso_11_rear_left_joint;
	private final ModelPart torso_12_front_right_joint;
	private final ModelPart torso_12_front_left_joint;
	private final ModelPart torso_12_rear_right_joint;
	private final ModelPart torso_12_rear_left_joint;
	private final ModelPart torso_13_front_right_joint;
	private final ModelPart torso_13_front_left_joint;
	private final ModelPart torso_13_rear_right_joint;
	private final ModelPart torso_13_rear_left_joint;
	private final ModelPart torso_14_front_right_joint;
	private final ModelPart torso_14_front_left_joint;
	private final ModelPart torso_14_rear_right_joint;
	private final ModelPart torso_14_rear_left_joint;
	private final ModelPart torso_15_front_right_joint;
	private final ModelPart torso_15_front_left_joint;
	private final ModelPart torso_15_rear_right_joint;
	private final ModelPart torso_15_rear_left_joint;
	private final ModelPart torso_16_front_right_joint;
	private final ModelPart torso_16_front_left_joint;
	private final ModelPart torso_16_rear_right_joint;
	private final ModelPart torso_16_rear_left_joint;
	private final ModelPart torso_17_front_right_joint;
	private final ModelPart torso_17_front_left_joint;
	private final ModelPart torso_17_rear_right_joint;
	private final ModelPart torso_17_rear_left_joint;
	private final ModelPart torso_18_front_right_joint;
	private final ModelPart torso_18_front_left_joint;
	private final ModelPart torso_18_rear_right_joint;
	private final ModelPart torso_18_rear_left_joint;
	private final ModelPart torso_19_front_right_joint;
	private final ModelPart torso_19_front_left_joint;
	private final ModelPart torso_19_rear_right_joint;
	private final ModelPart torso_19_rear_left_joint;
	private final ModelPart torso_20_front_right_joint;
	private final ModelPart torso_20_front_left_joint;
	private final ModelPart torso_20_rear_right_joint;
	private final ModelPart torso_20_rear_left_joint;
	private final ModelPart torso_21_front_right_joint;
	private final ModelPart torso_21_front_left_joint;
	private final ModelPart torso_21_rear_right_joint;
	private final ModelPart torso_21_rear_left_joint;
	private final ModelPart torso_22_front_right_joint;
	private final ModelPart torso_22_front_left_joint;
	private final ModelPart torso_22_rear_right_joint;
	private final ModelPart torso_22_rear_left_joint;
	private final ModelPart torso_23_front_right_joint;
	private final ModelPart torso_23_front_left_joint;
	private final ModelPart torso_23_rear_right_joint;
	private final ModelPart torso_23_rear_left_joint;
	private final ModelPart torso_24_front_right_joint;
	private final ModelPart torso_24_front_left_joint;
	private final ModelPart torso_24_rear_right_joint;
	private final ModelPart torso_24_rear_left_joint;
	private final ModelPart torso_25_front_right_joint;
	private final ModelPart torso_25_front_left_joint;
	private final ModelPart torso_25_rear_right_joint;
	private final ModelPart torso_25_rear_left_joint;
	private final ModelPart torso_26_front_right_joint;
	private final ModelPart torso_26_front_left_joint;
	private final ModelPart torso_26_rear_right_joint;
	private final ModelPart torso_26_rear_left_joint;
	private final ModelPart torso_27_front_right_joint;
	private final ModelPart torso_27_front_left_joint;
	private final ModelPart torso_27_rear_right_joint;
	private final ModelPart torso_27_rear_left_joint;
	private final ModelPart ribbon_loop_-1_0_joint;
	private final ModelPart ribbon_loop_-1_1_joint;
	private final ModelPart ribbon_loop_-1_2_joint;
	private final ModelPart ribbon_tail_-1_0_joint;
	private final ModelPart ribbon_tail_-1_1_joint;
	private final ModelPart ribbon_tail_-1_2_joint;
	private final ModelPart ribbon_tail_-1_3_joint;
	private final ModelPart ribbon_loop_1_0_joint;
	private final ModelPart ribbon_loop_1_1_joint;
	private final ModelPart ribbon_loop_1_2_joint;
	private final ModelPart ribbon_tail_1_0_joint;
	private final ModelPart ribbon_tail_1_1_joint;
	private final ModelPart ribbon_tail_1_2_joint;
	private final ModelPart ribbon_tail_1_3_joint;
	private final ModelPart right_wing;
	private final ModelPart right_wing_feather_00_joint;
	private final ModelPart right_wing_feather_01_joint;
	private final ModelPart right_wing_feather_02_joint;
	private final ModelPart right_wing_feather_03_joint;
	private final ModelPart right_wing_feather_04_joint;
	private final ModelPart right_wing_feather_05_joint;
	private final ModelPart right_wing_feather_06_joint;
	private final ModelPart right_wing_feather_07_joint;
	private final ModelPart right_wing_feather_08_joint;
	private final ModelPart right_wing_feather_09_joint;
	private final ModelPart right_wing_feather_10_joint;
	private final ModelPart left_wing;
	private final ModelPart left_wing_feather_00_joint;
	private final ModelPart left_wing_feather_01_joint;
	private final ModelPart left_wing_feather_02_joint;
	private final ModelPart left_wing_feather_03_joint;
	private final ModelPart left_wing_feather_04_joint;
	private final ModelPart left_wing_feather_05_joint;
	private final ModelPart left_wing_feather_06_joint;
	private final ModelPart left_wing_feather_07_joint;
	private final ModelPart left_wing_feather_08_joint;
	private final ModelPart left_wing_feather_09_joint;
	private final ModelPart left_wing_feather_10_joint;
	private final ModelPart right_foot;
	private final ModelPart right_foot_web_toe_0_joint;
	private final ModelPart right_foot_toe_tip_0_joint;
	private final ModelPart right_foot_web_toe_2_joint;
	private final ModelPart right_foot_toe_tip_2_joint;
	private final ModelPart left_foot;
	private final ModelPart left_foot_web_toe_0_joint;
	private final ModelPart left_foot_toe_tip_0_joint;
	private final ModelPart left_foot_web_toe_2_joint;
	private final ModelPart left_foot_toe_tip_2_joint;

	public kuso_penguin_rounded(ModelPart root) {
		this.body = root.getChild("body");
		this.torso_00_front_right_joint = this.body.getChild("torso_00_front_right_joint");
		this.torso_00_front_left_joint = this.body.getChild("torso_00_front_left_joint");
		this.torso_00_rear_right_joint = this.body.getChild("torso_00_rear_right_joint");
		this.torso_00_rear_left_joint = this.body.getChild("torso_00_rear_left_joint");
		this.torso_01_front_right_joint = this.body.getChild("torso_01_front_right_joint");
		this.torso_01_front_left_joint = this.body.getChild("torso_01_front_left_joint");
		this.torso_01_rear_right_joint = this.body.getChild("torso_01_rear_right_joint");
		this.torso_01_rear_left_joint = this.body.getChild("torso_01_rear_left_joint");
		this.torso_02_front_right_joint = this.body.getChild("torso_02_front_right_joint");
		this.torso_02_front_left_joint = this.body.getChild("torso_02_front_left_joint");
		this.torso_02_rear_right_joint = this.body.getChild("torso_02_rear_right_joint");
		this.torso_02_rear_left_joint = this.body.getChild("torso_02_rear_left_joint");
		this.torso_03_front_right_joint = this.body.getChild("torso_03_front_right_joint");
		this.torso_03_front_left_joint = this.body.getChild("torso_03_front_left_joint");
		this.torso_03_rear_right_joint = this.body.getChild("torso_03_rear_right_joint");
		this.torso_03_rear_left_joint = this.body.getChild("torso_03_rear_left_joint");
		this.torso_04_front_right_joint = this.body.getChild("torso_04_front_right_joint");
		this.torso_04_front_left_joint = this.body.getChild("torso_04_front_left_joint");
		this.torso_04_rear_right_joint = this.body.getChild("torso_04_rear_right_joint");
		this.torso_04_rear_left_joint = this.body.getChild("torso_04_rear_left_joint");
		this.torso_05_front_right_joint = this.body.getChild("torso_05_front_right_joint");
		this.torso_05_front_left_joint = this.body.getChild("torso_05_front_left_joint");
		this.torso_05_rear_right_joint = this.body.getChild("torso_05_rear_right_joint");
		this.torso_05_rear_left_joint = this.body.getChild("torso_05_rear_left_joint");
		this.torso_06_front_right_joint = this.body.getChild("torso_06_front_right_joint");
		this.torso_06_front_left_joint = this.body.getChild("torso_06_front_left_joint");
		this.torso_06_rear_right_joint = this.body.getChild("torso_06_rear_right_joint");
		this.torso_06_rear_left_joint = this.body.getChild("torso_06_rear_left_joint");
		this.torso_07_front_right_joint = this.body.getChild("torso_07_front_right_joint");
		this.torso_07_front_left_joint = this.body.getChild("torso_07_front_left_joint");
		this.torso_07_rear_right_joint = this.body.getChild("torso_07_rear_right_joint");
		this.torso_07_rear_left_joint = this.body.getChild("torso_07_rear_left_joint");
		this.torso_08_front_right_joint = this.body.getChild("torso_08_front_right_joint");
		this.torso_08_front_left_joint = this.body.getChild("torso_08_front_left_joint");
		this.torso_08_rear_right_joint = this.body.getChild("torso_08_rear_right_joint");
		this.torso_08_rear_left_joint = this.body.getChild("torso_08_rear_left_joint");
		this.torso_09_front_right_joint = this.body.getChild("torso_09_front_right_joint");
		this.torso_09_front_left_joint = this.body.getChild("torso_09_front_left_joint");
		this.torso_09_rear_right_joint = this.body.getChild("torso_09_rear_right_joint");
		this.torso_09_rear_left_joint = this.body.getChild("torso_09_rear_left_joint");
		this.torso_10_front_right_joint = this.body.getChild("torso_10_front_right_joint");
		this.torso_10_front_left_joint = this.body.getChild("torso_10_front_left_joint");
		this.torso_10_rear_right_joint = this.body.getChild("torso_10_rear_right_joint");
		this.torso_10_rear_left_joint = this.body.getChild("torso_10_rear_left_joint");
		this.torso_11_front_right_joint = this.body.getChild("torso_11_front_right_joint");
		this.torso_11_front_left_joint = this.body.getChild("torso_11_front_left_joint");
		this.torso_11_rear_right_joint = this.body.getChild("torso_11_rear_right_joint");
		this.torso_11_rear_left_joint = this.body.getChild("torso_11_rear_left_joint");
		this.torso_12_front_right_joint = this.body.getChild("torso_12_front_right_joint");
		this.torso_12_front_left_joint = this.body.getChild("torso_12_front_left_joint");
		this.torso_12_rear_right_joint = this.body.getChild("torso_12_rear_right_joint");
		this.torso_12_rear_left_joint = this.body.getChild("torso_12_rear_left_joint");
		this.torso_13_front_right_joint = this.body.getChild("torso_13_front_right_joint");
		this.torso_13_front_left_joint = this.body.getChild("torso_13_front_left_joint");
		this.torso_13_rear_right_joint = this.body.getChild("torso_13_rear_right_joint");
		this.torso_13_rear_left_joint = this.body.getChild("torso_13_rear_left_joint");
		this.torso_14_front_right_joint = this.body.getChild("torso_14_front_right_joint");
		this.torso_14_front_left_joint = this.body.getChild("torso_14_front_left_joint");
		this.torso_14_rear_right_joint = this.body.getChild("torso_14_rear_right_joint");
		this.torso_14_rear_left_joint = this.body.getChild("torso_14_rear_left_joint");
		this.torso_15_front_right_joint = this.body.getChild("torso_15_front_right_joint");
		this.torso_15_front_left_joint = this.body.getChild("torso_15_front_left_joint");
		this.torso_15_rear_right_joint = this.body.getChild("torso_15_rear_right_joint");
		this.torso_15_rear_left_joint = this.body.getChild("torso_15_rear_left_joint");
		this.torso_16_front_right_joint = this.body.getChild("torso_16_front_right_joint");
		this.torso_16_front_left_joint = this.body.getChild("torso_16_front_left_joint");
		this.torso_16_rear_right_joint = this.body.getChild("torso_16_rear_right_joint");
		this.torso_16_rear_left_joint = this.body.getChild("torso_16_rear_left_joint");
		this.torso_17_front_right_joint = this.body.getChild("torso_17_front_right_joint");
		this.torso_17_front_left_joint = this.body.getChild("torso_17_front_left_joint");
		this.torso_17_rear_right_joint = this.body.getChild("torso_17_rear_right_joint");
		this.torso_17_rear_left_joint = this.body.getChild("torso_17_rear_left_joint");
		this.torso_18_front_right_joint = this.body.getChild("torso_18_front_right_joint");
		this.torso_18_front_left_joint = this.body.getChild("torso_18_front_left_joint");
		this.torso_18_rear_right_joint = this.body.getChild("torso_18_rear_right_joint");
		this.torso_18_rear_left_joint = this.body.getChild("torso_18_rear_left_joint");
		this.torso_19_front_right_joint = this.body.getChild("torso_19_front_right_joint");
		this.torso_19_front_left_joint = this.body.getChild("torso_19_front_left_joint");
		this.torso_19_rear_right_joint = this.body.getChild("torso_19_rear_right_joint");
		this.torso_19_rear_left_joint = this.body.getChild("torso_19_rear_left_joint");
		this.torso_20_front_right_joint = this.body.getChild("torso_20_front_right_joint");
		this.torso_20_front_left_joint = this.body.getChild("torso_20_front_left_joint");
		this.torso_20_rear_right_joint = this.body.getChild("torso_20_rear_right_joint");
		this.torso_20_rear_left_joint = this.body.getChild("torso_20_rear_left_joint");
		this.torso_21_front_right_joint = this.body.getChild("torso_21_front_right_joint");
		this.torso_21_front_left_joint = this.body.getChild("torso_21_front_left_joint");
		this.torso_21_rear_right_joint = this.body.getChild("torso_21_rear_right_joint");
		this.torso_21_rear_left_joint = this.body.getChild("torso_21_rear_left_joint");
		this.torso_22_front_right_joint = this.body.getChild("torso_22_front_right_joint");
		this.torso_22_front_left_joint = this.body.getChild("torso_22_front_left_joint");
		this.torso_22_rear_right_joint = this.body.getChild("torso_22_rear_right_joint");
		this.torso_22_rear_left_joint = this.body.getChild("torso_22_rear_left_joint");
		this.torso_23_front_right_joint = this.body.getChild("torso_23_front_right_joint");
		this.torso_23_front_left_joint = this.body.getChild("torso_23_front_left_joint");
		this.torso_23_rear_right_joint = this.body.getChild("torso_23_rear_right_joint");
		this.torso_23_rear_left_joint = this.body.getChild("torso_23_rear_left_joint");
		this.torso_24_front_right_joint = this.body.getChild("torso_24_front_right_joint");
		this.torso_24_front_left_joint = this.body.getChild("torso_24_front_left_joint");
		this.torso_24_rear_right_joint = this.body.getChild("torso_24_rear_right_joint");
		this.torso_24_rear_left_joint = this.body.getChild("torso_24_rear_left_joint");
		this.torso_25_front_right_joint = this.body.getChild("torso_25_front_right_joint");
		this.torso_25_front_left_joint = this.body.getChild("torso_25_front_left_joint");
		this.torso_25_rear_right_joint = this.body.getChild("torso_25_rear_right_joint");
		this.torso_25_rear_left_joint = this.body.getChild("torso_25_rear_left_joint");
		this.torso_26_front_right_joint = this.body.getChild("torso_26_front_right_joint");
		this.torso_26_front_left_joint = this.body.getChild("torso_26_front_left_joint");
		this.torso_26_rear_right_joint = this.body.getChild("torso_26_rear_right_joint");
		this.torso_26_rear_left_joint = this.body.getChild("torso_26_rear_left_joint");
		this.torso_27_front_right_joint = this.body.getChild("torso_27_front_right_joint");
		this.torso_27_front_left_joint = this.body.getChild("torso_27_front_left_joint");
		this.torso_27_rear_right_joint = this.body.getChild("torso_27_rear_right_joint");
		this.torso_27_rear_left_joint = this.body.getChild("torso_27_rear_left_joint");
		this.ribbon_loop_-1_0_joint = this.body.getChild("ribbon_loop_-1_0_joint");
		this.ribbon_loop_-1_1_joint = this.body.getChild("ribbon_loop_-1_1_joint");
		this.ribbon_loop_-1_2_joint = this.body.getChild("ribbon_loop_-1_2_joint");
		this.ribbon_tail_-1_0_joint = this.body.getChild("ribbon_tail_-1_0_joint");
		this.ribbon_tail_-1_1_joint = this.body.getChild("ribbon_tail_-1_1_joint");
		this.ribbon_tail_-1_2_joint = this.body.getChild("ribbon_tail_-1_2_joint");
		this.ribbon_tail_-1_3_joint = this.body.getChild("ribbon_tail_-1_3_joint");
		this.ribbon_loop_1_0_joint = this.body.getChild("ribbon_loop_1_0_joint");
		this.ribbon_loop_1_1_joint = this.body.getChild("ribbon_loop_1_1_joint");
		this.ribbon_loop_1_2_joint = this.body.getChild("ribbon_loop_1_2_joint");
		this.ribbon_tail_1_0_joint = this.body.getChild("ribbon_tail_1_0_joint");
		this.ribbon_tail_1_1_joint = this.body.getChild("ribbon_tail_1_1_joint");
		this.ribbon_tail_1_2_joint = this.body.getChild("ribbon_tail_1_2_joint");
		this.ribbon_tail_1_3_joint = this.body.getChild("ribbon_tail_1_3_joint");
		this.right_wing = root.getChild("right_wing");
		this.right_wing_feather_00_joint = this.right_wing.getChild("right_wing_feather_00_joint");
		this.right_wing_feather_01_joint = this.right_wing.getChild("right_wing_feather_01_joint");
		this.right_wing_feather_02_joint = this.right_wing.getChild("right_wing_feather_02_joint");
		this.right_wing_feather_03_joint = this.right_wing.getChild("right_wing_feather_03_joint");
		this.right_wing_feather_04_joint = this.right_wing.getChild("right_wing_feather_04_joint");
		this.right_wing_feather_05_joint = this.right_wing.getChild("right_wing_feather_05_joint");
		this.right_wing_feather_06_joint = this.right_wing.getChild("right_wing_feather_06_joint");
		this.right_wing_feather_07_joint = this.right_wing.getChild("right_wing_feather_07_joint");
		this.right_wing_feather_08_joint = this.right_wing.getChild("right_wing_feather_08_joint");
		this.right_wing_feather_09_joint = this.right_wing.getChild("right_wing_feather_09_joint");
		this.right_wing_feather_10_joint = this.right_wing.getChild("right_wing_feather_10_joint");
		this.left_wing = root.getChild("left_wing");
		this.left_wing_feather_00_joint = this.left_wing.getChild("left_wing_feather_00_joint");
		this.left_wing_feather_01_joint = this.left_wing.getChild("left_wing_feather_01_joint");
		this.left_wing_feather_02_joint = this.left_wing.getChild("left_wing_feather_02_joint");
		this.left_wing_feather_03_joint = this.left_wing.getChild("left_wing_feather_03_joint");
		this.left_wing_feather_04_joint = this.left_wing.getChild("left_wing_feather_04_joint");
		this.left_wing_feather_05_joint = this.left_wing.getChild("left_wing_feather_05_joint");
		this.left_wing_feather_06_joint = this.left_wing.getChild("left_wing_feather_06_joint");
		this.left_wing_feather_07_joint = this.left_wing.getChild("left_wing_feather_07_joint");
		this.left_wing_feather_08_joint = this.left_wing.getChild("left_wing_feather_08_joint");
		this.left_wing_feather_09_joint = this.left_wing.getChild("left_wing_feather_09_joint");
		this.left_wing_feather_10_joint = this.left_wing.getChild("left_wing_feather_10_joint");
		this.right_foot = root.getChild("right_foot");
		this.right_foot_web_toe_0_joint = this.right_foot.getChild("right_foot_web_toe_0_joint");
		this.right_foot_toe_tip_0_joint = this.right_foot.getChild("right_foot_toe_tip_0_joint");
		this.right_foot_web_toe_2_joint = this.right_foot.getChild("right_foot_web_toe_2_joint");
		this.right_foot_toe_tip_2_joint = this.right_foot.getChild("right_foot_toe_tip_2_joint");
		this.left_foot = root.getChild("left_foot");
		this.left_foot_web_toe_0_joint = this.left_foot.getChild("left_foot_web_toe_0_joint");
		this.left_foot_toe_tip_0_joint = this.left_foot.getChild("left_foot_toe_tip_0_joint");
		this.left_foot_web_toe_2_joint = this.left_foot.getChild("left_foot_web_toe_2_joint");
		this.left_foot_toe_tip_2_joint = this.left_foot.getChild("left_foot_toe_tip_2_joint");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create().texOffs(3, 3).addBox(-2.8225F, 21.2143F, -3.0721F, 5.6449F, 0.7857F, 6.6784F, new CubeDeformation(0.0F))
		.texOffs(32, 3).addBox(-4.2126F, 21.2143F, -1.682F, 8.4252F, 0.7857F, 3.8981F, new CubeDeformation(0.0F))
		.texOffs(109, 3).addBox(-3.5111F, 20.4286F, -3.5214F, 7.0223F, 0.7857F, 7.645F, new CubeDeformation(0.0F))
		.texOffs(143, 3).addBox(-5.2405F, 20.4286F, -1.792F, 10.481F, 0.7857F, 4.1863F, new CubeDeformation(0.0F))
		.texOffs(233, 3).addBox(-4.0547F, 19.6429F, -3.9496F, 8.1094F, 0.7857F, 8.5465F, new CubeDeformation(0.0F))
		.texOffs(271, 3).addBox(-6.0518F, 19.6429F, -1.9525F, 12.1036F, 0.7857F, 4.5523F, new CubeDeformation(0.0F))
		.texOffs(373, 3).addBox(-4.6382F, 18.8571F, -4.4092F, 9.2764F, 0.7857F, 9.5141F, new CubeDeformation(0.0F))
		.texOffs(415, 3).addBox(-6.9227F, 18.8571F, -2.1247F, 13.8453F, 0.7857F, 4.9451F, new CubeDeformation(0.0F))
		.texOffs(20, 18).addBox(-4.7578F, 18.0714F, -4.5216F, 9.5155F, 0.7857F, 9.7517F, new CubeDeformation(0.0F))
		.texOffs(63, 18).addBox(-7.1011F, 18.0714F, -2.1783F, 14.2023F, 0.7857F, 5.0649F, new CubeDeformation(0.0F))
		.texOffs(178, 18).addBox(-5.0206F, 17.2857F, -4.7995F, 10.0411F, 0.7857F, 10.34F, new CubeDeformation(0.0F))
		.texOffs(223, 18).addBox(-7.4934F, 17.2857F, -2.3267F, 14.9867F, 0.7857F, 5.3944F, new CubeDeformation(0.0F))
		.texOffs(340, 18).addBox(-5.3227F, 16.5F, -5.1189F, 10.6455F, 0.7857F, 11.0166F, new CubeDeformation(0.0F))
		.texOffs(388, 18).addBox(-7.9444F, 16.5F, -2.4973F, 15.8888F, 0.7857F, 5.7733F, new CubeDeformation(0.0F))
		.texOffs(22, 34).addBox(-5.491F, 15.7143F, -5.2968F, 10.982F, 0.7857F, 11.3933F, new CubeDeformation(0.0F))
		.texOffs(71, 34).addBox(-8.1955F, 15.7143F, -2.5923F, 16.391F, 0.7857F, 5.9842F, new CubeDeformation(0.0F))
		.texOffs(200, 34).addBox(-5.5289F, 14.9286F, -5.3456F, 11.0579F, 0.7857F, 11.4912F, new CubeDeformation(0.0F))
		.texOffs(250, 34).addBox(-8.2521F, 14.9286F, -2.6224F, 16.5043F, 0.7857F, 6.0448F, new CubeDeformation(0.0F))
		.texOffs(380, 34).addBox(-5.6232F, 14.1429F, -5.4688F, 11.2464F, 0.7857F, 11.7375F, new CubeDeformation(0.0F))
		.texOffs(430, 34).addBox(-8.3929F, 14.1429F, -2.6991F, 16.7857F, 0.7857F, 6.1982F, new CubeDeformation(0.0F))
		.texOffs(63, 51).addBox(-5.7201F, 13.3571F, -5.5953F, 11.4402F, 0.7857F, 11.9906F, new CubeDeformation(0.0F))
		.texOffs(114, 51).addBox(-8.5375F, 13.3571F, -2.7779F, 17.075F, 0.7857F, 6.3559F, new CubeDeformation(0.0F))
		.texOffs(245, 51).addBox(-5.7619F, 12.5714F, -5.65F, 11.5239F, 0.7857F, 12.0999F, new CubeDeformation(0.0F))
		.texOffs(297, 51).addBox(-8.5999F, 12.5714F, -2.812F, 17.1998F, 0.7857F, 6.4239F, new CubeDeformation(0.0F))
		.texOffs(433, 51).addBox(-5.7312F, 11.7857F, -5.6316F, 11.4624F, 0.7857F, 12.0448F, new CubeDeformation(0.0F))
		.texOffs(3, 68).addBox(-8.554F, 11.7857F, -2.8088F, 17.1081F, 0.7857F, 6.3992F, new CubeDeformation(0.0F))
		.texOffs(135, 68).addBox(-5.6693F, 11.0F, -5.5947F, 11.3386F, 0.7857F, 11.934F, new CubeDeformation(0.0F))
		.texOffs(186, 68).addBox(-8.4616F, 11.0F, -2.8023F, 16.9233F, 0.7857F, 6.3493F, new CubeDeformation(0.0F))
		.texOffs(317, 68).addBox(-5.6123F, 10.2143F, -5.5606F, 11.2246F, 0.7857F, 11.8319F, new CubeDeformation(0.0F))
		.texOffs(368, 68).addBox(-8.3766F, 10.2143F, -2.7964F, 16.7531F, 0.7857F, 6.3033F, new CubeDeformation(0.0F))
		.texOffs(3, 85).addBox(-5.5883F, 9.4286F, -5.5449F, 11.1767F, 0.7857F, 11.7867F, new CubeDeformation(0.0F))
		.texOffs(53, 85).addBox(-8.3408F, 9.4286F, -2.7924F, 16.6816F, 0.7857F, 6.2818F, new CubeDeformation(0.0F))
		.texOffs(183, 85).addBox(-5.4477F, 8.6429F, -5.4282F, 10.8953F, 0.7857F, 11.4834F, new CubeDeformation(0.0F))
		.texOffs(232, 85).addBox(-8.1308F, 8.6429F, -2.7451F, 16.2616F, 0.7857F, 6.1171F, new CubeDeformation(0.0F))
		.texOffs(361, 85).addBox(-5.2183F, 7.8571F, -5.2381F, 10.4366F, 0.7857F, 10.989F, new CubeDeformation(0.0F))
		.texOffs(408, 85).addBox(-7.7885F, 7.8571F, -2.6679F, 15.5771F, 0.7857F, 5.8486F, new CubeDeformation(0.0F))
		.texOffs(41, 102).addBox(-5.0304F, 7.0714F, -5.0822F, 10.0607F, 0.7857F, 10.5838F, new CubeDeformation(0.0F))
		.texOffs(87, 102).addBox(-7.508F, 7.0714F, -2.6046F, 15.016F, 0.7857F, 5.6285F, new CubeDeformation(0.0F))
		.texOffs(209, 102).addBox(-4.9615F, 6.2857F, -5.0228F, 9.923F, 0.7857F, 10.4392F, new CubeDeformation(0.0F))
		.texOffs(254, 102).addBox(-7.4052F, 6.2857F, -2.5791F, 14.8105F, 0.7857F, 5.5518F, new CubeDeformation(0.0F))
		.texOffs(371, 102).addBox(-4.7025F, 5.5F, -4.7881F, 9.4051F, 0.7857F, 9.9147F, new CubeDeformation(0.0F))
		.texOffs(414, 102).addBox(-7.0187F, 5.5F, -2.472F, 14.0374F, 0.7857F, 5.2823F, new CubeDeformation(0.0F))
		.texOffs(21, 118).addBox(-4.3425F, 4.7143F, -4.4619F, 8.6849F, 0.7857F, 9.1853F, new CubeDeformation(0.0F))
		.texOffs(61, 118).addBox(-6.4813F, 4.7143F, -2.323F, 12.9626F, 0.7857F, 4.9077F, new CubeDeformation(0.0F))
		.texOffs(169, 118).addBox(-4.0835F, 3.9286F, -4.2272F, 8.167F, 0.7857F, 8.6608F, new CubeDeformation(0.0F))
		.texOffs(207, 118).addBox(-6.0948F, 3.9286F, -2.2159F, 12.1895F, 0.7857F, 4.6382F, new CubeDeformation(0.0F))
		.texOffs(309, 118).addBox(-3.9391F, 3.1429F, -4.1043F, 7.8781F, 0.7857F, 8.3814F, new CubeDeformation(0.0F))
		.texOffs(346, 118).addBox(-5.8792F, 3.1429F, -2.1642F, 11.7584F, 0.7857F, 4.5011F, new CubeDeformation(0.0F))
		.texOffs(443, 118).addBox(-3.481F, 2.3571F, -3.7215F, 6.962F, 0.7857F, 7.5062F, new CubeDeformation(0.0F))
		.texOffs(476, 118).addBox(-5.1955F, 2.3571F, -2.007F, 10.391F, 0.7857F, 4.0772F, new CubeDeformation(0.0F))
		.texOffs(59, 132).addBox(-3.2151F, 1.5714F, -3.4992F, 6.4303F, 0.7857F, 6.9984F, new CubeDeformation(0.0F))
		.texOffs(90, 132).addBox(-4.7987F, 1.5714F, -1.9156F, 9.5974F, 0.7857F, 3.8312F, new CubeDeformation(0.0F))
		.texOffs(173, 132).addBox(-2.8836F, 0.7857F, -3.1876F, 5.7672F, 0.7857F, 6.3753F, new CubeDeformation(0.0F))
		.texOffs(202, 132).addBox(-4.3039F, 0.7857F, -1.7673F, 8.6078F, 0.7857F, 3.5347F, new CubeDeformation(0.0F))
		.texOffs(283, 132).addBox(-2.4025F, 0.0F, -2.7355F, 4.805F, 0.7857F, 5.471F, new CubeDeformation(0.0F))
		.texOffs(308, 132).addBox(-3.5858F, 0.0F, -1.5522F, 7.1716F, 0.7857F, 3.1044F, new CubeDeformation(0.0F))
		.texOffs(429, 144).addBox(-2.6F, 19.8F, 3.1F, 5.2F, 1.9F, 3.6F, new CubeDeformation(0.0F))
		.texOffs(451, 144).addBox(-1.9F, 20.7F, 4.5F, 3.8F, 1.5F, 2.5F, new CubeDeformation(0.0F))
		.texOffs(468, 144).addBox(-1.2F, 21.5F, 5.6F, 2.4F, 1.0F, 1.5F, new CubeDeformation(0.0F))
		.texOffs(480, 144).addBox(-0.9F, 1.4F, 3.7F, 1.8F, 1.6F, 0.9F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition torso_00_front_right_joint = body.addOrReplaceChild("torso_00_front_right_joint", CubeListBuilder.create().texOffs(61, 3).addBox(-0.983F, -0.3929F, -0.983F, 1.966F, 0.7857F, 1.966F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.8225F, 21.6071F, -1.682F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_00_front_left_joint = body.addOrReplaceChild("torso_00_front_left_joint", CubeListBuilder.create().texOffs(73, 3).addBox(-0.983F, -0.3929F, -0.983F, 1.966F, 0.7857F, 1.966F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.8225F, 21.6071F, -1.682F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_00_rear_right_joint = body.addOrReplaceChild("torso_00_rear_right_joint", CubeListBuilder.create().texOffs(85, 3).addBox(-0.983F, -0.3929F, -0.983F, 1.966F, 0.7857F, 1.966F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.8225F, 21.6071F, 2.2161F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_00_rear_left_joint = body.addOrReplaceChild("torso_00_rear_left_joint", CubeListBuilder.create().texOffs(97, 3).addBox(-0.983F, -0.3929F, -0.983F, 1.966F, 0.7857F, 1.966F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.8225F, 21.6071F, 2.2161F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_01_front_right_joint = body.addOrReplaceChild("torso_01_front_right_joint", CubeListBuilder.create().texOffs(177, 3).addBox(-1.2228F, -0.3929F, -1.2228F, 2.4457F, 0.7857F, 2.4457F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.5111F, 20.8214F, -1.792F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_01_front_left_joint = body.addOrReplaceChild("torso_01_front_left_joint", CubeListBuilder.create().texOffs(191, 3).addBox(-1.2228F, -0.3929F, -1.2228F, 2.4457F, 0.7857F, 2.4457F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.5111F, 20.8214F, -1.792F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_01_rear_right_joint = body.addOrReplaceChild("torso_01_rear_right_joint", CubeListBuilder.create().texOffs(205, 3).addBox(-1.2228F, -0.3929F, -1.2228F, 2.4457F, 0.7857F, 2.4457F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.5111F, 20.8214F, 2.3943F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_01_rear_left_joint = body.addOrReplaceChild("torso_01_rear_left_joint", CubeListBuilder.create().texOffs(219, 3).addBox(-1.2228F, -0.3929F, -1.2228F, 2.4457F, 0.7857F, 2.4457F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.5111F, 20.8214F, 2.3943F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_02_front_right_joint = body.addOrReplaceChild("torso_02_front_right_joint", CubeListBuilder.create().texOffs(309, 3).addBox(-1.4122F, -0.3929F, -1.4122F, 2.8243F, 0.7857F, 2.8243F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.0547F, 20.0357F, -1.9525F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_02_front_left_joint = body.addOrReplaceChild("torso_02_front_left_joint", CubeListBuilder.create().texOffs(325, 3).addBox(-1.4122F, -0.3929F, -1.4122F, 2.8243F, 0.7857F, 2.8243F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.0547F, 20.0357F, -1.9525F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_02_rear_right_joint = body.addOrReplaceChild("torso_02_rear_right_joint", CubeListBuilder.create().texOffs(341, 3).addBox(-1.4122F, -0.3929F, -1.4122F, 2.8243F, 0.7857F, 2.8243F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.0547F, 20.0357F, 2.5998F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_02_rear_left_joint = body.addOrReplaceChild("torso_02_rear_left_joint", CubeListBuilder.create().texOffs(357, 3).addBox(-1.4122F, -0.3929F, -1.4122F, 2.8243F, 0.7857F, 2.8243F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.0547F, 20.0357F, 2.5998F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_03_front_right_joint = body.addOrReplaceChild("torso_03_front_right_joint", CubeListBuilder.create().texOffs(457, 3).addBox(-1.6154F, -0.3929F, -1.6154F, 3.2307F, 0.7857F, 3.2307F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.6382F, 19.25F, -2.1247F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_03_front_left_joint = body.addOrReplaceChild("torso_03_front_left_joint", CubeListBuilder.create().texOffs(474, 3).addBox(-1.6154F, -0.3929F, -1.6154F, 3.2307F, 0.7857F, 3.2307F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.6382F, 19.25F, -2.1247F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_03_rear_right_joint = body.addOrReplaceChild("torso_03_rear_right_joint", CubeListBuilder.create().texOffs(491, 3).addBox(-1.6154F, -0.3929F, -1.6154F, 3.2307F, 0.7857F, 3.2307F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.6382F, 19.25F, 2.8204F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_03_rear_left_joint = body.addOrReplaceChild("torso_03_rear_left_joint", CubeListBuilder.create().texOffs(3, 18).addBox(-1.6154F, -0.3929F, -1.6154F, 3.2307F, 0.7857F, 3.2307F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.6382F, 19.25F, 2.8204F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_04_front_right_joint = body.addOrReplaceChild("torso_04_front_right_joint", CubeListBuilder.create().texOffs(106, 18).addBox(-1.657F, -0.3929F, -1.657F, 3.314F, 0.7857F, 3.314F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.7578F, 18.4643F, -2.1783F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_04_front_left_joint = body.addOrReplaceChild("torso_04_front_left_joint", CubeListBuilder.create().texOffs(124, 18).addBox(-1.657F, -0.3929F, -1.657F, 3.314F, 0.7857F, 3.314F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.7578F, 18.4643F, -2.1783F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_04_rear_right_joint = body.addOrReplaceChild("torso_04_rear_right_joint", CubeListBuilder.create().texOffs(142, 18).addBox(-1.657F, -0.3929F, -1.657F, 3.314F, 0.7857F, 3.314F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.7578F, 18.4643F, 2.8867F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_04_rear_left_joint = body.addOrReplaceChild("torso_04_rear_left_joint", CubeListBuilder.create().texOffs(160, 18).addBox(-1.657F, -0.3929F, -1.657F, 3.314F, 0.7857F, 3.314F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.7578F, 18.4643F, 2.8867F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_05_front_right_joint = body.addOrReplaceChild("torso_05_front_right_joint", CubeListBuilder.create().texOffs(268, 18).addBox(-1.7485F, -0.3929F, -1.7485F, 3.4971F, 0.7857F, 3.4971F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.0206F, 17.6786F, -2.3267F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_05_front_left_joint = body.addOrReplaceChild("torso_05_front_left_joint", CubeListBuilder.create().texOffs(286, 18).addBox(-1.7485F, -0.3929F, -1.7485F, 3.4971F, 0.7857F, 3.4971F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.0206F, 17.6786F, -2.3267F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_05_rear_right_joint = body.addOrReplaceChild("torso_05_rear_right_joint", CubeListBuilder.create().texOffs(304, 18).addBox(-1.7485F, -0.3929F, -1.7485F, 3.4971F, 0.7857F, 3.4971F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.0206F, 17.6786F, 3.0678F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_05_rear_left_joint = body.addOrReplaceChild("torso_05_rear_left_joint", CubeListBuilder.create().texOffs(322, 18).addBox(-1.7485F, -0.3929F, -1.7485F, 3.4971F, 0.7857F, 3.4971F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.0206F, 17.6786F, 3.0678F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_06_front_right_joint = body.addOrReplaceChild("torso_06_front_right_joint", CubeListBuilder.create().texOffs(436, 18).addBox(-1.8538F, -0.3929F, -1.8538F, 3.7076F, 0.7857F, 3.7076F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.3227F, 16.8929F, -2.4973F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_06_front_left_joint = body.addOrReplaceChild("torso_06_front_left_joint", CubeListBuilder.create().texOffs(455, 18).addBox(-1.8538F, -0.3929F, -1.8538F, 3.7076F, 0.7857F, 3.7076F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.3227F, 16.8929F, -2.4973F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_06_rear_right_joint = body.addOrReplaceChild("torso_06_rear_right_joint", CubeListBuilder.create().texOffs(474, 18).addBox(-1.8538F, -0.3929F, -1.8538F, 3.7076F, 0.7857F, 3.7076F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.3227F, 16.8929F, 3.276F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_06_rear_left_joint = body.addOrReplaceChild("torso_06_rear_left_joint", CubeListBuilder.create().texOffs(3, 34).addBox(-1.8538F, -0.3929F, -1.8538F, 3.7076F, 0.7857F, 3.7076F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.3227F, 16.8929F, 3.276F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_07_front_right_joint = body.addOrReplaceChild("torso_07_front_right_joint", CubeListBuilder.create().texOffs(120, 34).addBox(-1.9124F, -0.3929F, -1.9124F, 3.8248F, 0.7857F, 3.8248F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.491F, 16.1071F, -2.5923F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_07_front_left_joint = body.addOrReplaceChild("torso_07_front_left_joint", CubeListBuilder.create().texOffs(140, 34).addBox(-1.9124F, -0.3929F, -1.9124F, 3.8248F, 0.7857F, 3.8248F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.491F, 16.1071F, -2.5923F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_07_rear_right_joint = body.addOrReplaceChild("torso_07_rear_right_joint", CubeListBuilder.create().texOffs(160, 34).addBox(-1.9124F, -0.3929F, -1.9124F, 3.8248F, 0.7857F, 3.8248F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.491F, 16.1071F, 3.3919F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_07_rear_left_joint = body.addOrReplaceChild("torso_07_rear_left_joint", CubeListBuilder.create().texOffs(180, 34).addBox(-1.9124F, -0.3929F, -1.9124F, 3.8248F, 0.7857F, 3.8248F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.491F, 16.1071F, 3.3919F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_08_front_right_joint = body.addOrReplaceChild("torso_08_front_right_joint", CubeListBuilder.create().texOffs(300, 34).addBox(-1.9256F, -0.3929F, -1.9256F, 3.8512F, 0.7857F, 3.8512F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.5289F, 15.3214F, -2.6224F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_08_front_left_joint = body.addOrReplaceChild("torso_08_front_left_joint", CubeListBuilder.create().texOffs(320, 34).addBox(-1.9256F, -0.3929F, -1.9256F, 3.8512F, 0.7857F, 3.8512F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.5289F, 15.3214F, -2.6224F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_08_rear_right_joint = body.addOrReplaceChild("torso_08_rear_right_joint", CubeListBuilder.create().texOffs(340, 34).addBox(-1.9256F, -0.3929F, -1.9256F, 3.8512F, 0.7857F, 3.8512F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.5289F, 15.3214F, 3.4224F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_08_rear_left_joint = body.addOrReplaceChild("torso_08_rear_left_joint", CubeListBuilder.create().texOffs(360, 34).addBox(-1.9256F, -0.3929F, -1.9256F, 3.8512F, 0.7857F, 3.8512F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.5289F, 15.3214F, 3.4224F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_09_front_right_joint = body.addOrReplaceChild("torso_09_front_right_joint", CubeListBuilder.create().texOffs(480, 34).addBox(-1.9584F, -0.3929F, -1.9584F, 3.9169F, 0.7857F, 3.9169F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.6232F, 14.5357F, -2.6991F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_09_front_left_joint = body.addOrReplaceChild("torso_09_front_left_joint", CubeListBuilder.create().texOffs(3, 51).addBox(-1.9584F, -0.3929F, -1.9584F, 3.9169F, 0.7857F, 3.9169F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.6232F, 14.5357F, -2.6991F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_09_rear_right_joint = body.addOrReplaceChild("torso_09_rear_right_joint", CubeListBuilder.create().texOffs(23, 51).addBox(-1.9584F, -0.3929F, -1.9584F, 3.9169F, 0.7857F, 3.9169F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.6232F, 14.5357F, 3.4991F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_09_rear_left_joint = body.addOrReplaceChild("torso_09_rear_left_joint", CubeListBuilder.create().texOffs(43, 51).addBox(-1.9584F, -0.3929F, -1.9584F, 3.9169F, 0.7857F, 3.9169F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.6232F, 14.5357F, 3.4991F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_10_front_right_joint = body.addOrReplaceChild("torso_10_front_right_joint", CubeListBuilder.create().texOffs(165, 51).addBox(-1.9922F, -0.3929F, -1.9922F, 3.9844F, 0.7857F, 3.9844F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.7201F, 13.75F, -2.7779F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_10_front_left_joint = body.addOrReplaceChild("torso_10_front_left_joint", CubeListBuilder.create().texOffs(185, 51).addBox(-1.9922F, -0.3929F, -1.9922F, 3.9844F, 0.7857F, 3.9844F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.7201F, 13.75F, -2.7779F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_10_rear_right_joint = body.addOrReplaceChild("torso_10_rear_right_joint", CubeListBuilder.create().texOffs(205, 51).addBox(-1.9922F, -0.3929F, -1.9922F, 3.9844F, 0.7857F, 3.9844F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.7201F, 13.75F, 3.5779F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_10_rear_left_joint = body.addOrReplaceChild("torso_10_rear_left_joint", CubeListBuilder.create().texOffs(225, 51).addBox(-1.9922F, -0.3929F, -1.9922F, 3.9844F, 0.7857F, 3.9844F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.7201F, 13.75F, 3.5779F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_11_front_right_joint = body.addOrReplaceChild("torso_11_front_right_joint", CubeListBuilder.create().texOffs(349, 51).addBox(-2.0067F, -0.3929F, -2.0067F, 4.0135F, 0.7857F, 4.0135F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.7619F, 12.9643F, -2.812F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_11_front_left_joint = body.addOrReplaceChild("torso_11_front_left_joint", CubeListBuilder.create().texOffs(370, 51).addBox(-2.0067F, -0.3929F, -2.0067F, 4.0135F, 0.7857F, 4.0135F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.7619F, 12.9643F, -2.812F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_11_rear_right_joint = body.addOrReplaceChild("torso_11_rear_right_joint", CubeListBuilder.create().texOffs(391, 51).addBox(-2.0067F, -0.3929F, -2.0067F, 4.0135F, 0.7857F, 4.0135F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.7619F, 12.9643F, 3.612F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_11_rear_left_joint = body.addOrReplaceChild("torso_11_rear_left_joint", CubeListBuilder.create().texOffs(412, 51).addBox(-2.0067F, -0.3929F, -2.0067F, 4.0135F, 0.7857F, 4.0135F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.7619F, 12.9643F, 3.612F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_12_front_right_joint = body.addOrReplaceChild("torso_12_front_right_joint", CubeListBuilder.create().texOffs(55, 68).addBox(-1.996F, -0.3929F, -1.996F, 3.9921F, 0.7857F, 3.9921F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.7312F, 12.1786F, -2.8088F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_12_front_left_joint = body.addOrReplaceChild("torso_12_front_left_joint", CubeListBuilder.create().texOffs(75, 68).addBox(-1.996F, -0.3929F, -1.996F, 3.9921F, 0.7857F, 3.9921F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.7312F, 12.1786F, -2.8088F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_12_rear_right_joint = body.addOrReplaceChild("torso_12_rear_right_joint", CubeListBuilder.create().texOffs(95, 68).addBox(-1.996F, -0.3929F, -1.996F, 3.9921F, 0.7857F, 3.9921F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.7312F, 12.1786F, 3.5904F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_12_rear_left_joint = body.addOrReplaceChild("torso_12_rear_left_joint", CubeListBuilder.create().texOffs(115, 68).addBox(-1.996F, -0.3929F, -1.996F, 3.9921F, 0.7857F, 3.9921F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.7312F, 12.1786F, 3.5904F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_13_front_right_joint = body.addOrReplaceChild("torso_13_front_right_joint", CubeListBuilder.create().texOffs(237, 68).addBox(-1.9745F, -0.3929F, -1.9745F, 3.949F, 0.7857F, 3.949F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.6693F, 11.3929F, -2.8023F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_13_front_left_joint = body.addOrReplaceChild("torso_13_front_left_joint", CubeListBuilder.create().texOffs(257, 68).addBox(-1.9745F, -0.3929F, -1.9745F, 3.949F, 0.7857F, 3.949F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.6693F, 11.3929F, -2.8023F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_13_rear_right_joint = body.addOrReplaceChild("torso_13_rear_right_joint", CubeListBuilder.create().texOffs(277, 68).addBox(-1.9745F, -0.3929F, -1.9745F, 3.949F, 0.7857F, 3.949F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.6693F, 11.3929F, 3.547F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_13_rear_left_joint = body.addOrReplaceChild("torso_13_rear_left_joint", CubeListBuilder.create().texOffs(297, 68).addBox(-1.9745F, -0.3929F, -1.9745F, 3.949F, 0.7857F, 3.949F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.6693F, 11.3929F, 3.547F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_14_front_right_joint = body.addOrReplaceChild("torso_14_front_right_joint", CubeListBuilder.create().texOffs(419, 68).addBox(-1.9546F, -0.3929F, -1.9546F, 3.9093F, 0.7857F, 3.9093F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.6123F, 10.6071F, -2.7964F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_14_front_left_joint = body.addOrReplaceChild("torso_14_front_left_joint", CubeListBuilder.create().texOffs(439, 68).addBox(-1.9546F, -0.3929F, -1.9546F, 3.9093F, 0.7857F, 3.9093F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.6123F, 10.6071F, -2.7964F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_14_rear_right_joint = body.addOrReplaceChild("torso_14_rear_right_joint", CubeListBuilder.create().texOffs(459, 68).addBox(-1.9546F, -0.3929F, -1.9546F, 3.9093F, 0.7857F, 3.9093F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.6123F, 10.6071F, 3.507F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_14_rear_left_joint = body.addOrReplaceChild("torso_14_rear_left_joint", CubeListBuilder.create().texOffs(479, 68).addBox(-1.9546F, -0.3929F, -1.9546F, 3.9093F, 0.7857F, 3.9093F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.6123F, 10.6071F, 3.507F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_15_front_right_joint = body.addOrReplaceChild("torso_15_front_right_joint", CubeListBuilder.create().texOffs(103, 85).addBox(-1.9463F, -0.3929F, -1.9463F, 3.8926F, 0.7857F, 3.8926F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.5883F, 9.8214F, -2.7924F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_15_front_left_joint = body.addOrReplaceChild("torso_15_front_left_joint", CubeListBuilder.create().texOffs(123, 85).addBox(-1.9463F, -0.3929F, -1.9463F, 3.8926F, 0.7857F, 3.8926F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.5883F, 9.8214F, -2.7924F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_15_rear_right_joint = body.addOrReplaceChild("torso_15_rear_right_joint", CubeListBuilder.create().texOffs(143, 85).addBox(-1.9463F, -0.3929F, -1.9463F, 3.8926F, 0.7857F, 3.8926F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.5883F, 9.8214F, 3.4894F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_15_rear_left_joint = body.addOrReplaceChild("torso_15_rear_left_joint", CubeListBuilder.create().texOffs(163, 85).addBox(-1.9463F, -0.3929F, -1.9463F, 3.8926F, 0.7857F, 3.8926F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.5883F, 9.8214F, 3.4894F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_16_front_right_joint = body.addOrReplaceChild("torso_16_front_right_joint", CubeListBuilder.create().texOffs(281, 85).addBox(-1.8973F, -0.3929F, -1.8973F, 3.7946F, 0.7857F, 3.7946F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.4477F, 9.0357F, -2.7451F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_16_front_left_joint = body.addOrReplaceChild("torso_16_front_left_joint", CubeListBuilder.create().texOffs(301, 85).addBox(-1.8973F, -0.3929F, -1.8973F, 3.7946F, 0.7857F, 3.7946F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.4477F, 9.0357F, -2.7451F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_16_rear_right_joint = body.addOrReplaceChild("torso_16_rear_right_joint", CubeListBuilder.create().texOffs(321, 85).addBox(-1.8973F, -0.3929F, -1.8973F, 3.7946F, 0.7857F, 3.7946F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.4477F, 9.0357F, 3.372F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_16_rear_left_joint = body.addOrReplaceChild("torso_16_rear_left_joint", CubeListBuilder.create().texOffs(341, 85).addBox(-1.8973F, -0.3929F, -1.8973F, 3.7946F, 0.7857F, 3.7946F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.4477F, 9.0357F, 3.372F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_17_front_right_joint = body.addOrReplaceChild("torso_17_front_right_joint", CubeListBuilder.create().texOffs(455, 85).addBox(-1.8174F, -0.3929F, -1.8174F, 3.6348F, 0.7857F, 3.6348F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.2183F, 8.25F, -2.6679F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_17_front_left_joint = body.addOrReplaceChild("torso_17_front_left_joint", CubeListBuilder.create().texOffs(474, 85).addBox(-1.8174F, -0.3929F, -1.8174F, 3.6348F, 0.7857F, 3.6348F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.2183F, 8.25F, -2.6679F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_17_rear_right_joint = body.addOrReplaceChild("torso_17_rear_right_joint", CubeListBuilder.create().texOffs(3, 102).addBox(-1.8174F, -0.3929F, -1.8174F, 3.6348F, 0.7857F, 3.6348F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.2183F, 8.25F, 3.1807F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_17_rear_left_joint = body.addOrReplaceChild("torso_17_rear_left_joint", CubeListBuilder.create().texOffs(22, 102).addBox(-1.8174F, -0.3929F, -1.8174F, 3.6348F, 0.7857F, 3.6348F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.2183F, 8.25F, 3.1807F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_18_front_right_joint = body.addOrReplaceChild("torso_18_front_right_joint", CubeListBuilder.create().texOffs(133, 102).addBox(-1.752F, -0.3929F, -1.752F, 3.5039F, 0.7857F, 3.5039F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.0304F, 7.4643F, -2.6046F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_18_front_left_joint = body.addOrReplaceChild("torso_18_front_left_joint", CubeListBuilder.create().texOffs(152, 102).addBox(-1.752F, -0.3929F, -1.752F, 3.5039F, 0.7857F, 3.5039F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.0304F, 7.4643F, -2.6046F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_18_rear_right_joint = body.addOrReplaceChild("torso_18_rear_right_joint", CubeListBuilder.create().texOffs(171, 102).addBox(-1.752F, -0.3929F, -1.752F, 3.5039F, 0.7857F, 3.5039F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.0304F, 7.4643F, 3.0239F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_18_rear_left_joint = body.addOrReplaceChild("torso_18_rear_left_joint", CubeListBuilder.create().texOffs(190, 102).addBox(-1.752F, -0.3929F, -1.752F, 3.5039F, 0.7857F, 3.5039F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.0304F, 7.4643F, 3.0239F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_19_front_right_joint = body.addOrReplaceChild("torso_19_front_right_joint", CubeListBuilder.create().texOffs(299, 102).addBox(-1.728F, -0.3929F, -1.728F, 3.456F, 0.7857F, 3.456F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.9615F, 6.6786F, -2.5791F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_19_front_left_joint = body.addOrReplaceChild("torso_19_front_left_joint", CubeListBuilder.create().texOffs(317, 102).addBox(-1.728F, -0.3929F, -1.728F, 3.456F, 0.7857F, 3.456F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.9615F, 6.6786F, -2.5791F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_19_rear_right_joint = body.addOrReplaceChild("torso_19_rear_right_joint", CubeListBuilder.create().texOffs(335, 102).addBox(-1.728F, -0.3929F, -1.728F, 3.456F, 0.7857F, 3.456F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.9615F, 6.6786F, 2.9727F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_19_rear_left_joint = body.addOrReplaceChild("torso_19_rear_left_joint", CubeListBuilder.create().texOffs(353, 102).addBox(-1.728F, -0.3929F, -1.728F, 3.456F, 0.7857F, 3.456F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.9615F, 6.6786F, 2.9727F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_20_front_right_joint = body.addOrReplaceChild("torso_20_front_right_joint", CubeListBuilder.create().texOffs(457, 102).addBox(-1.6378F, -0.3929F, -1.6378F, 3.2756F, 0.7857F, 3.2756F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.7025F, 5.8929F, -2.472F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_20_front_left_joint = body.addOrReplaceChild("torso_20_front_left_joint", CubeListBuilder.create().texOffs(475, 102).addBox(-1.6378F, -0.3929F, -1.6378F, 3.2756F, 0.7857F, 3.2756F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.7025F, 5.8929F, -2.472F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_20_rear_right_joint = body.addOrReplaceChild("torso_20_rear_right_joint", CubeListBuilder.create().texOffs(493, 102).addBox(-1.6378F, -0.3929F, -1.6378F, 3.2756F, 0.7857F, 3.2756F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.7025F, 5.8929F, 2.8104F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_20_rear_left_joint = body.addOrReplaceChild("torso_20_rear_left_joint", CubeListBuilder.create().texOffs(3, 118).addBox(-1.6378F, -0.3929F, -1.6378F, 3.2756F, 0.7857F, 3.2756F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.7025F, 5.8929F, 2.8104F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_21_front_right_joint = body.addOrReplaceChild("torso_21_front_right_joint", CubeListBuilder.create().texOffs(101, 118).addBox(-1.5124F, -0.3929F, -1.5124F, 3.0248F, 0.7857F, 3.0248F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.3425F, 5.1071F, -2.323F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_21_front_left_joint = body.addOrReplaceChild("torso_21_front_left_joint", CubeListBuilder.create().texOffs(118, 118).addBox(-1.5124F, -0.3929F, -1.5124F, 3.0248F, 0.7857F, 3.0248F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.3425F, 5.1071F, -2.323F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_21_rear_right_joint = body.addOrReplaceChild("torso_21_rear_right_joint", CubeListBuilder.create().texOffs(135, 118).addBox(-1.5124F, -0.3929F, -1.5124F, 3.0248F, 0.7857F, 3.0248F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.3425F, 5.1071F, 2.5846F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_21_rear_left_joint = body.addOrReplaceChild("torso_21_rear_left_joint", CubeListBuilder.create().texOffs(152, 118).addBox(-1.5124F, -0.3929F, -1.5124F, 3.0248F, 0.7857F, 3.0248F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.3425F, 5.1071F, 2.5846F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_22_front_right_joint = body.addOrReplaceChild("torso_22_front_right_joint", CubeListBuilder.create().texOffs(245, 118).addBox(-1.4222F, -0.3929F, -1.4222F, 2.8444F, 0.7857F, 2.8444F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.0835F, 4.3214F, -2.2159F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_22_front_left_joint = body.addOrReplaceChild("torso_22_front_left_joint", CubeListBuilder.create().texOffs(261, 118).addBox(-1.4222F, -0.3929F, -1.4222F, 2.8444F, 0.7857F, 2.8444F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.0835F, 4.3214F, -2.2159F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_22_rear_right_joint = body.addOrReplaceChild("torso_22_rear_right_joint", CubeListBuilder.create().texOffs(277, 118).addBox(-1.4222F, -0.3929F, -1.4222F, 2.8444F, 0.7857F, 2.8444F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.0835F, 4.3214F, 2.4223F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_22_rear_left_joint = body.addOrReplaceChild("torso_22_rear_left_joint", CubeListBuilder.create().texOffs(293, 118).addBox(-1.4222F, -0.3929F, -1.4222F, 2.8444F, 0.7857F, 2.8444F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.0835F, 4.3214F, 2.4223F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_23_front_right_joint = body.addOrReplaceChild("torso_23_front_right_joint", CubeListBuilder.create().texOffs(383, 118).addBox(-1.3719F, -0.3929F, -1.3719F, 2.7438F, 0.7857F, 2.7438F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.9391F, 3.5357F, -2.1642F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_23_front_left_joint = body.addOrReplaceChild("torso_23_front_left_joint", CubeListBuilder.create().texOffs(398, 118).addBox(-1.3719F, -0.3929F, -1.3719F, 2.7438F, 0.7857F, 2.7438F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.9391F, 3.5357F, -2.1642F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_23_rear_right_joint = body.addOrReplaceChild("torso_23_rear_right_joint", CubeListBuilder.create().texOffs(413, 118).addBox(-1.3719F, -0.3929F, -1.3719F, 2.7438F, 0.7857F, 2.7438F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.9391F, 3.5357F, 2.3369F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_23_rear_left_joint = body.addOrReplaceChild("torso_23_rear_left_joint", CubeListBuilder.create().texOffs(428, 118).addBox(-1.3719F, -0.3929F, -1.3719F, 2.7438F, 0.7857F, 2.7438F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.9391F, 3.5357F, 2.3369F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_24_front_right_joint = body.addOrReplaceChild("torso_24_front_right_joint", CubeListBuilder.create().texOffs(3, 132).addBox(-1.2123F, -0.3929F, -1.2123F, 2.4247F, 0.7857F, 2.4247F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.481F, 2.75F, -2.007F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_24_front_left_joint = body.addOrReplaceChild("torso_24_front_left_joint", CubeListBuilder.create().texOffs(17, 132).addBox(-1.2123F, -0.3929F, -1.2123F, 2.4247F, 0.7857F, 2.4247F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.481F, 2.75F, -2.007F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_24_rear_right_joint = body.addOrReplaceChild("torso_24_rear_right_joint", CubeListBuilder.create().texOffs(31, 132).addBox(-1.2123F, -0.3929F, -1.2123F, 2.4247F, 0.7857F, 2.4247F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.481F, 2.75F, 2.0702F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_24_rear_left_joint = body.addOrReplaceChild("torso_24_rear_left_joint", CubeListBuilder.create().texOffs(45, 132).addBox(-1.2123F, -0.3929F, -1.2123F, 2.4247F, 0.7857F, 2.4247F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.481F, 2.75F, 2.0702F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_25_front_right_joint = body.addOrReplaceChild("torso_25_front_right_joint", CubeListBuilder.create().texOffs(121, 132).addBox(-1.1198F, -0.3929F, -1.1198F, 2.2395F, 0.7857F, 2.2395F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.2151F, 1.9643F, -1.9156F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_25_front_left_joint = body.addOrReplaceChild("torso_25_front_left_joint", CubeListBuilder.create().texOffs(134, 132).addBox(-1.1198F, -0.3929F, -1.1198F, 2.2395F, 0.7857F, 2.2395F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.2151F, 1.9643F, -1.9156F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_25_rear_right_joint = body.addOrReplaceChild("torso_25_rear_right_joint", CubeListBuilder.create().texOffs(147, 132).addBox(-1.1198F, -0.3929F, -1.1198F, 2.2395F, 0.7857F, 2.2395F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.2151F, 1.9643F, 1.9156F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_25_rear_left_joint = body.addOrReplaceChild("torso_25_rear_left_joint", CubeListBuilder.create().texOffs(160, 132).addBox(-1.1198F, -0.3929F, -1.1198F, 2.2395F, 0.7857F, 2.2395F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.2151F, 1.9643F, 1.9156F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_26_front_right_joint = body.addOrReplaceChild("torso_26_front_right_joint", CubeListBuilder.create().texOffs(231, 132).addBox(-1.0043F, -0.3929F, -1.0043F, 2.0086F, 0.7857F, 2.0086F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.8836F, 1.1786F, -1.7673F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_26_front_left_joint = body.addOrReplaceChild("torso_26_front_left_joint", CubeListBuilder.create().texOffs(244, 132).addBox(-1.0043F, -0.3929F, -1.0043F, 2.0086F, 0.7857F, 2.0086F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.8836F, 1.1786F, -1.7673F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_26_rear_right_joint = body.addOrReplaceChild("torso_26_rear_right_joint", CubeListBuilder.create().texOffs(257, 132).addBox(-1.0043F, -0.3929F, -1.0043F, 2.0086F, 0.7857F, 2.0086F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.8836F, 1.1786F, 1.7673F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_26_rear_left_joint = body.addOrReplaceChild("torso_26_rear_left_joint", CubeListBuilder.create().texOffs(270, 132).addBox(-1.0043F, -0.3929F, -1.0043F, 2.0086F, 0.7857F, 2.0086F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.8836F, 1.1786F, 1.7673F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_27_front_right_joint = body.addOrReplaceChild("torso_27_front_right_joint", CubeListBuilder.create().texOffs(333, 132).addBox(-0.8367F, -0.3929F, -0.8367F, 1.6735F, 0.7857F, 1.6735F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.4025F, 0.3929F, -1.5522F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_27_front_left_joint = body.addOrReplaceChild("torso_27_front_left_joint", CubeListBuilder.create().texOffs(344, 132).addBox(-0.8367F, -0.3929F, -0.8367F, 1.6735F, 0.7857F, 1.6735F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.4025F, 0.3929F, -1.5522F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_27_rear_right_joint = body.addOrReplaceChild("torso_27_rear_right_joint", CubeListBuilder.create().texOffs(355, 132).addBox(-0.8367F, -0.3929F, -0.8367F, 1.6735F, 0.7857F, 1.6735F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.4025F, 0.3929F, 1.5522F, 0.0F, -0.7854F, 0.0F));

		PartDefinition torso_27_rear_left_joint = body.addOrReplaceChild("torso_27_rear_left_joint", CubeListBuilder.create().texOffs(366, 132).addBox(-0.8367F, -0.3929F, -0.8367F, 1.6735F, 0.7857F, 1.6735F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.4025F, 0.3929F, 1.5522F, 0.0F, -0.7854F, 0.0F));

		PartDefinition ribbon_loop_-1_0_joint = body.addOrReplaceChild("ribbon_loop_-1_0_joint", CubeListBuilder.create().texOffs(490, 144).addBox(-0.1F, -1.0F, -0.35F, 2.0F, 1.9F, 0.65F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.7F, 2.3F, 3.9F, 0.0F, 0.0F, -0.384F));

		PartDefinition ribbon_loop_-1_1_joint = body.addOrReplaceChild("ribbon_loop_-1_1_joint", CubeListBuilder.create().texOffs(500, 144).addBox(0.65F, -0.7F, -0.35F, 2.0F, 1.9F, 0.65F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.7F, 2.3F, 3.9F, 0.0F, 0.0F, -0.4712F));

		PartDefinition ribbon_loop_-1_2_joint = body.addOrReplaceChild("ribbon_loop_-1_2_joint", CubeListBuilder.create().texOffs(3, 161).addBox(1.4F, -0.4F, -0.35F, 2.0F, 1.9F, 0.65F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.7F, 2.3F, 3.9F, 0.0F, 0.0F, -0.5585F));

		PartDefinition ribbon_tail_-1_0_joint = body.addOrReplaceChild("ribbon_tail_-1_0_joint", CubeListBuilder.create().texOffs(13, 161).addBox(-0.55F, -0.3F, -0.2F, 1.1F, 3.7602F, 0.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.15F, 3.2F, 4.5032F, 0.3755F, 0.0F, -0.1587F));

		PartDefinition ribbon_tail_-1_1_joint = body.addOrReplaceChild("ribbon_tail_-1_1_joint", CubeListBuilder.create().texOffs(21, 161).addBox(-0.55F, -0.3F, -0.2F, 1.1F, 3.6532F, 0.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.63F, 6.2F, 5.6858F, 0.2742F, 0.0F, -0.1587F));

		PartDefinition ribbon_tail_-1_2_joint = body.addOrReplaceChild("ribbon_tail_-1_2_joint", CubeListBuilder.create().texOffs(29, 161).addBox(-0.55F, -0.3F, -0.2F, 1.1F, 3.5535F, 0.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.11F, 9.2F, 6.5297F, 0.1014F, 0.0F, -0.1587F));

		PartDefinition ribbon_tail_-1_3_joint = body.addOrReplaceChild("ribbon_tail_-1_3_joint", CubeListBuilder.create().texOffs(37, 161).addBox(-0.55F, -0.3F, -0.2F, 1.1F, 3.5487F, 0.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.59F, 12.2F, 6.8349F, -0.0843F, 0.0F, -0.1587F));

		PartDefinition ribbon_loop_1_0_joint = body.addOrReplaceChild("ribbon_loop_1_0_joint", CubeListBuilder.create().texOffs(44, 161).addBox(-1.9F, -1.0F, -0.35F, 2.0F, 1.9F, 0.65F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.7F, 2.3F, 3.9F, 0.0F, 0.0F, 0.384F));

		PartDefinition ribbon_loop_1_1_joint = body.addOrReplaceChild("ribbon_loop_1_1_joint", CubeListBuilder.create().texOffs(54, 161).addBox(-2.65F, -0.7F, -0.35F, 2.0F, 1.9F, 0.65F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.7F, 2.3F, 3.9F, 0.0F, 0.0F, 0.4712F));

		PartDefinition ribbon_loop_1_2_joint = body.addOrReplaceChild("ribbon_loop_1_2_joint", CubeListBuilder.create().texOffs(64, 161).addBox(-3.4F, -0.4F, -0.35F, 2.0F, 1.9F, 0.65F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.7F, 2.3F, 3.9F, 0.0F, 0.0F, 0.5585F));

		PartDefinition ribbon_tail_1_0_joint = body.addOrReplaceChild("ribbon_tail_1_0_joint", CubeListBuilder.create().texOffs(74, 161).addBox(-0.55F, -0.3F, -0.2F, 1.1F, 3.7602F, 0.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.15F, 3.2F, 4.5032F, 0.3755F, 0.0F, 0.1587F));

		PartDefinition ribbon_tail_1_1_joint = body.addOrReplaceChild("ribbon_tail_1_1_joint", CubeListBuilder.create().texOffs(82, 161).addBox(-0.55F, -0.3F, -0.2F, 1.1F, 3.6532F, 0.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.63F, 6.2F, 5.6858F, 0.2742F, 0.0F, 0.1587F));

		PartDefinition ribbon_tail_1_2_joint = body.addOrReplaceChild("ribbon_tail_1_2_joint", CubeListBuilder.create().texOffs(90, 161).addBox(-0.55F, -0.3F, -0.2F, 1.1F, 3.5535F, 0.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.11F, 9.2F, 6.5297F, 0.1014F, 0.0F, 0.1587F));

		PartDefinition ribbon_tail_1_3_joint = body.addOrReplaceChild("ribbon_tail_1_3_joint", CubeListBuilder.create().texOffs(98, 161).addBox(-0.55F, -0.3F, -0.2F, 1.1F, 3.5487F, 0.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.59F, 12.2F, 6.8349F, -0.0843F, 0.0F, 0.1587F));

		PartDefinition right_wing = partdefinition.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(23, 144).addBox(-1.4F, -0.7F, -1.0F, 2.8F, 1.6F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(5.45F, 2.6F, 0.2F));

		PartDefinition right_wing_feather_00_joint = right_wing.addOrReplaceChild("right_wing_feather_00_joint", CubeListBuilder.create().texOffs(377, 132).addBox(-1.35F, 0.0F, -0.85F, 2.7F, 1.22F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.3316F));

		PartDefinition right_wing_feather_01_joint = right_wing.addOrReplaceChild("right_wing_feather_01_joint", CubeListBuilder.create().texOffs(390, 132).addBox(-1.5321F, 1.2F, -0.85F, 3.0643F, 1.22F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.3316F));

		PartDefinition right_wing_feather_02_joint = right_wing.addOrReplaceChild("right_wing_feather_02_joint", CubeListBuilder.create().texOffs(404, 132).addBox(-1.6734F, 2.4F, -0.85F, 3.3467F, 1.22F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.3316F));

		PartDefinition right_wing_feather_03_joint = right_wing.addOrReplaceChild("right_wing_feather_03_joint", CubeListBuilder.create().texOffs(419, 132).addBox(-1.7566F, 3.6F, -0.85F, 3.5133F, 1.22F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.3316F));

		PartDefinition right_wing_feather_04_joint = right_wing.addOrReplaceChild("right_wing_feather_04_joint", CubeListBuilder.create().texOffs(434, 132).addBox(-1.7684F, 4.8F, -0.85F, 3.5368F, 1.22F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.3316F));

		PartDefinition right_wing_feather_05_joint = right_wing.addOrReplaceChild("right_wing_feather_05_joint", CubeListBuilder.create().texOffs(449, 132).addBox(-1.7F, 6.0F, -0.85F, 3.4F, 1.22F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.3316F));

		PartDefinition right_wing_feather_06_joint = right_wing.addOrReplaceChild("right_wing_feather_06_joint", CubeListBuilder.create().texOffs(464, 132).addBox(-1.5484F, 7.2F, -0.85F, 3.0968F, 1.22F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.3316F));

		PartDefinition right_wing_feather_07_joint = right_wing.addOrReplaceChild("right_wing_feather_07_joint", CubeListBuilder.create().texOffs(478, 132).addBox(-1.3166F, 8.4F, -0.85F, 2.6333F, 1.22F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.3316F));

		PartDefinition right_wing_feather_08_joint = right_wing.addOrReplaceChild("right_wing_feather_08_joint", CubeListBuilder.create().texOffs(491, 132).addBox(-1.0134F, 9.6F, -0.85F, 2.0267F, 1.22F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.3316F));

		PartDefinition right_wing_feather_09_joint = right_wing.addOrReplaceChild("right_wing_feather_09_joint", CubeListBuilder.create().texOffs(3, 144).addBox(-0.6521F, 10.8F, -0.85F, 1.3043F, 1.22F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.3316F));

		PartDefinition right_wing_feather_10_joint = right_wing.addOrReplaceChild("right_wing_feather_10_joint", CubeListBuilder.create().texOffs(14, 144).addBox(-0.25F, 12.0F, -0.85F, 0.5F, 1.22F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.3316F));

		PartDefinition left_wing = partdefinition.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(183, 144).addBox(-1.4F, -0.7F, -1.0F, 2.8F, 1.6F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.45F, 2.6F, 0.2F));

		PartDefinition left_wing_feather_00_joint = left_wing.addOrReplaceChild("left_wing_feather_00_joint", CubeListBuilder.create().texOffs(37, 144).addBox(-1.35F, 0.0F, -0.85F, 2.7F, 1.22F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3316F));

		PartDefinition left_wing_feather_01_joint = left_wing.addOrReplaceChild("left_wing_feather_01_joint", CubeListBuilder.create().texOffs(50, 144).addBox(-1.5321F, 1.2F, -0.85F, 3.0643F, 1.22F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3316F));

		PartDefinition left_wing_feather_02_joint = left_wing.addOrReplaceChild("left_wing_feather_02_joint", CubeListBuilder.create().texOffs(64, 144).addBox(-1.6734F, 2.4F, -0.85F, 3.3467F, 1.22F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3316F));

		PartDefinition left_wing_feather_03_joint = left_wing.addOrReplaceChild("left_wing_feather_03_joint", CubeListBuilder.create().texOffs(79, 144).addBox(-1.7566F, 3.6F, -0.85F, 3.5133F, 1.22F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3316F));

		PartDefinition left_wing_feather_04_joint = left_wing.addOrReplaceChild("left_wing_feather_04_joint", CubeListBuilder.create().texOffs(94, 144).addBox(-1.7684F, 4.8F, -0.85F, 3.5368F, 1.22F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3316F));

		PartDefinition left_wing_feather_05_joint = left_wing.addOrReplaceChild("left_wing_feather_05_joint", CubeListBuilder.create().texOffs(109, 144).addBox(-1.7F, 6.0F, -0.85F, 3.4F, 1.22F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3316F));

		PartDefinition left_wing_feather_06_joint = left_wing.addOrReplaceChild("left_wing_feather_06_joint", CubeListBuilder.create().texOffs(124, 144).addBox(-1.5484F, 7.2F, -0.85F, 3.0968F, 1.22F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3316F));

		PartDefinition left_wing_feather_07_joint = left_wing.addOrReplaceChild("left_wing_feather_07_joint", CubeListBuilder.create().texOffs(138, 144).addBox(-1.3166F, 8.4F, -0.85F, 2.6333F, 1.22F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3316F));

		PartDefinition left_wing_feather_08_joint = left_wing.addOrReplaceChild("left_wing_feather_08_joint", CubeListBuilder.create().texOffs(151, 144).addBox(-1.0134F, 9.6F, -0.85F, 2.0267F, 1.22F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3316F));

		PartDefinition left_wing_feather_09_joint = left_wing.addOrReplaceChild("left_wing_feather_09_joint", CubeListBuilder.create().texOffs(163, 144).addBox(-0.6521F, 10.8F, -0.85F, 1.3043F, 1.22F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3316F));

		PartDefinition left_wing_feather_10_joint = left_wing.addOrReplaceChild("left_wing_feather_10_joint", CubeListBuilder.create().texOffs(174, 144).addBox(-0.25F, 12.0F, -0.85F, 0.5F, 1.22F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3316F));

		PartDefinition right_foot = partdefinition.addOrReplaceChild("right_foot", CubeListBuilder.create().texOffs(197, 144).addBox(-1.25F, -2.0F, -1.45F, 2.5F, 10.0F, 2.9F, new CubeDeformation(0.0F))
		.texOffs(212, 144).addBox(-1.55F, 6.95F, -1.8F, 3.1F, 1.9F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(257, 144).addBox(-1.1F, 7.75F, -6.0F, 2.2F, 1.25F, 5.2F, new CubeDeformation(0.0F))
		.texOffs(276, 144).addBox(-0.8F, 8.15F, -6.35F, 1.6F, 0.77F, 0.9F, new CubeDeformation(0.0F)), PartPose.offset(3.3F, 15.0F, 0.0F));

		PartDefinition right_foot_web_toe_0_joint = right_foot.addOrReplaceChild("right_foot_web_toe_0_joint", CubeListBuilder.create().texOffs(229, 144).addBox(0.4F, -0.25F, -5.2F, 2.2F, 1.25F, 5.2F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 8.0F, -0.8F, 0.0F, 0.2967F, 0.0F));

		PartDefinition right_foot_toe_tip_0_joint = right_foot.addOrReplaceChild("right_foot_toe_tip_0_joint", CubeListBuilder.create().texOffs(248, 144).addBox(0.7F, 0.15F, -5.55F, 1.6F, 0.77F, 0.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 8.0F, -0.8F, 0.0F, 0.2967F, 0.0F));

		PartDefinition right_foot_web_toe_2_joint = right_foot.addOrReplaceChild("right_foot_web_toe_2_joint", CubeListBuilder.create().texOffs(285, 144).addBox(-2.6F, -0.25F, -5.2F, 2.2F, 1.25F, 5.2F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 8.0F, -0.8F, 0.0F, -0.2967F, 0.0F));

		PartDefinition right_foot_toe_tip_2_joint = right_foot.addOrReplaceChild("right_foot_toe_tip_2_joint", CubeListBuilder.create().texOffs(304, 144).addBox(-2.3F, 0.15F, -5.55F, 1.6F, 0.77F, 0.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 8.0F, -0.8F, 0.0F, -0.2967F, 0.0F));

		PartDefinition left_foot = partdefinition.addOrReplaceChild("left_foot", CubeListBuilder.create().texOffs(313, 144).addBox(-1.25F, -2.0F, -1.45F, 2.5F, 10.0F, 2.9F, new CubeDeformation(0.0F))
		.texOffs(328, 144).addBox(-1.55F, 6.95F, -1.8F, 3.1F, 1.9F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(373, 144).addBox(-1.1F, 7.75F, -6.0F, 2.2F, 1.25F, 5.2F, new CubeDeformation(0.0F))
		.texOffs(392, 144).addBox(-0.8F, 8.15F, -6.35F, 1.6F, 0.77F, 0.9F, new CubeDeformation(0.0F)), PartPose.offset(-3.3F, 15.0F, 0.0F));

		PartDefinition left_foot_web_toe_0_joint = left_foot.addOrReplaceChild("left_foot_web_toe_0_joint", CubeListBuilder.create().texOffs(345, 144).addBox(0.4F, -0.25F, -5.2F, 2.2F, 1.25F, 5.2F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 8.0F, -0.8F, 0.0F, 0.2967F, 0.0F));

		PartDefinition left_foot_toe_tip_0_joint = left_foot.addOrReplaceChild("left_foot_toe_tip_0_joint", CubeListBuilder.create().texOffs(364, 144).addBox(0.7F, 0.15F, -5.55F, 1.6F, 0.77F, 0.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 8.0F, -0.8F, 0.0F, 0.2967F, 0.0F));

		PartDefinition left_foot_web_toe_2_joint = left_foot.addOrReplaceChild("left_foot_web_toe_2_joint", CubeListBuilder.create().texOffs(401, 144).addBox(-2.6F, -0.25F, -5.2F, 2.2F, 1.25F, 5.2F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 8.0F, -0.8F, 0.0F, -0.2967F, 0.0F));

		PartDefinition left_foot_toe_tip_2_joint = left_foot.addOrReplaceChild("left_foot_toe_tip_2_joint", CubeListBuilder.create().texOffs(420, 144).addBox(-2.3F, 0.15F, -5.55F, 1.6F, 0.77F, 0.9F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 8.0F, -0.8F, 0.0F, -0.2967F, 0.0F));

		return LayerDefinition.create(meshdefinition, 512, 512);
	}

	@Override
	public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		body.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		right_wing.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		left_wing.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		right_foot.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		left_foot.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}
}