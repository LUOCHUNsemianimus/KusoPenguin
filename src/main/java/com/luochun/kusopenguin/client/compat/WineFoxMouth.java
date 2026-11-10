package com.luochun.kusopenguin.client.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.luochun.kusopenguin.client.vocals.VocalClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3d;

/** Render-scoped mouth shapes for the TLM Gecko Wine Fox model family. */
public final class WineFoxMouth {
    private WineFoxMouth() {
    }

    public static boolean supports(LivingEntity entity) {
        return MaidCompatClient.isExactMaid(entity) && Impl.supports(entity);
    }

    public static void render(LivingEntity entity, AnimatedGeoModel model, Runnable draw) {
        int level = supports(entity) ? VocalClient.mouthLevel(entity) : -1;
        if (level < 0 || model == null) {
            draw.run();
            return;
        }
        Impl.render(entity, model, level, draw);
    }

    private static final class Impl {
        private static final String SAINT = "geckolib:winefox_saint";
        private static final String MINI = "geckolib:winefox_mini";
        private static final String STELLAR = "touhou_little_maid_spell:stellar_witch";

        private static boolean supports(LivingEntity entity) {
            String id = ((EntityMaid) entity).getModelId();
            return id != null && (id.equals("geckolib:winefox") || id.startsWith("geckolib:winefox_")
                    || id.equals(STELLAR));
        }

        private static void render(LivingEntity entity, AnimatedGeoModel model, int level, Runnable draw) {
            String modelId = ((EntityMaid) entity).getModelId();
            AnimatedGeoBone container = model.bones().get(MINI.equals(modelId) ? "zuiba" : "Mouth");
            if (container == null && !MINI.equals(modelId)) {
                container = model.bones().get("mouth");
            }
            if (container == null) {
                draw.run();
                return;
            }
            List<AnimatedGeoBone> branches = container.children();
            AnimatedGeoBone selected = null;
            int bestRank = -1;
            int closest = Integer.MAX_VALUE;
            boolean hasRest = false;
            boolean hasOpen = false;
            int target = SAINT.equals(modelId) || MINI.equals(modelId) ? (level == 0 ? 0 : 2) : level;
            for (var bone : branches) {
                String name = bone.getName().toLowerCase(Locale.ROOT);
                int rank = shape(modelId, name);
                if (rank < 0 || !hasGeometry(bone)) {
                    continue;
                }
                hasRest |= rank <= 1;
                hasOpen |= rank >= 2;
                int distance = Math.abs(target - rank);
                if (distance < closest || (distance == closest && rank < bestRank)
                        || (distance == closest && rank == bestRank && name.startsWith("mouth_"))) {
                    closest = distance;
                    bestRank = rank;
                    selected = bone;
                }
            }
            if (!hasRest || !hasOpen || selected == null) {
                draw.run();
                return;
            }
            var visibility = new ArrayList<Visibility>();
            var transforms = new ArrayList<Transform>();
            try {
                for (var branch : branches) {
                    if (branch != selected) {
                        setVisible(branch, false, visibility);
                    }
                }
                float openness = SAINT.equals(modelId) && bestRank == 2 ? 0.8F + 0.4F * level : 1F;
                reveal(selected, revealDepth(modelId, selected.getName()), openness,
                        visibility, transforms);
                draw.run();
            } finally {
                for (var state : transforms) {
                    state.bone().setPosition(state.position());
                    state.bone().setScale(state.scale());
                    state.bone().setRotation(state.rotation());
                }
                for (var state : visibility) {
                    state.bone().setHidden(state.hidden(), state.childrenHidden());
                    state.bone().setCubesHidden(state.cubesHidden());
                }
            }
        }

        private static boolean hasGeometry(AnimatedGeoBone bone) {
            return bone.geoBone().cubes().getCubeCount() != 0
                    || bone.children().stream().anyMatch(Impl::hasGeometry);
        }

        private static void setVisible(AnimatedGeoBone bone, boolean show, List<Visibility> saved) {
            saved.add(new Visibility(bone, bone.isHidden(), bone.childBonesAreHiddenToo(),
                    bone.cubesAreHidden()));
            bone.setHidden(!show, !show);
            bone.setCubesHidden(!show);
        }

        private static void reveal(AnimatedGeoBone bone, float depth, float openness,
                                   List<Visibility> visibility, List<Transform> transforms) {
            setVisible(bone, true, visibility);
            transforms.add(new Transform(bone, bone.getPosition(), bone.getScale(), bone.getRotation()));
            var initial = bone.getInitialSnapshot();
            // Move the expression root in front of the face. Its descendants inherit that offset
            // and retain their bind geometry, including the four smaller pieces under xiao.
            bone.setPosition(initial.positionOffsetX, initial.positionOffsetY, initial.positionOffsetZ + depth);
            bone.setScale(initial.scaleValueX * openness, initial.scaleValueY * openness,
                    initial.scaleValueZ * openness);
            bone.setRotation(initial.rotationValueX, initial.rotationValueY, initial.rotationValueZ);
            for (var child : bone.children()) {
                reveal(child, 0F, 1F, visibility, transforms);
            }
        }

        private static float revealDepth(String modelId, String name) {
            return switch (modelId) {
                case "geckolib:winefox_little" -> -0.8F;
                case "geckolib:winefox_magical", STELLAR -> -0.91406F;
                case "geckolib:winefox_astronaut" -> name.equalsIgnoreCase("Mouth_smile2") ? 0F : -1F;
                case "geckolib:winefox_matured" -> name.equalsIgnoreCase("xiao") ? -1.8F : -1F;
                case "geckolib:winefox_momo" -> name.equalsIgnoreCase("jingya") ? -0.125F : -1F;
                case SAINT -> name.equalsIgnoreCase("yinchang") ? -1.00781F : -1F;
                case MINI -> name.equalsIgnoreCase("v") ? 0F : -1F;
                default -> -1F;
            };
        }

        private static int shape(String modelId, String name) {
            if (SAINT.equals(modelId)) {
                return switch (name) {
                    case "weixiao" -> 0;
                    case "yinchang" -> 2;
                    default -> -1;
                };
            }
            if (MINI.equals(modelId)) {
                return switch (name) {
                    case "v" -> 0;
                    case "ww" -> 2;
                    default -> -1;
                };
            }
            return switch (name) {
                case "chill", "mouth_chill", "kongju" -> 0;
                case "smile2", "mouth_smile2", "weixiao" -> 1;
                case "smile1", "mouth_smile1", "mouth_smile", "xiao" -> 2;
                case "surprise", "mouth_surprise", "jingya" -> 3;
                default -> -1;
            };
        }

        private record Visibility(AnimatedGeoBone bone, boolean hidden,
                                  boolean childrenHidden, boolean cubesHidden) {
        }

        private record Transform(AnimatedGeoBone bone, Vector3d position, Vector3d scale,
                                 Vector3d rotation) {
        }
    }
}
