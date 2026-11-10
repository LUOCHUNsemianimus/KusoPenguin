package com.luochun.kusopenguin.client.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.luochun.kusopenguin.client.vocals.VocalClient;
import java.util.ArrayList;
import java.util.Locale;
import net.minecraft.world.entity.LivingEntity;

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
        private static boolean supports(LivingEntity entity) {
            String id = ((EntityMaid) entity).getModelId();
            return id != null && (id.equals("geckolib:winefox") || id.startsWith("geckolib:winefox_"));
        }

        private static void render(LivingEntity entity, AnimatedGeoModel model, int level, Runnable draw) {
            AnimatedGeoBone container = model.bones().get("Mouth");
            if (container == null) {
                container = model.bones().get("mouth");
            }
            if (container == null) {
                draw.run();
                return;
            }
            var saved = new ArrayList<Visibility>();
            AnimatedGeoBone selected = null;
            int closest = Integer.MAX_VALUE;
            boolean hasRest = false;
            boolean hasOpen = false;
            for (var bone : container.children()) {
                if (!bone.children().isEmpty() || bone.geoBone().cubes().getCubeCount() == 0) {
                    continue;
                }
                int shape = shape(bone.getName());
                if (shape >= 0) {
                    hasRest |= shape <= 1;
                    hasOpen |= shape >= 2;
                    int distance = Math.abs(level - shape);
                    if (distance < closest) {
                        closest = distance;
                        selected = bone;
                    }
                }
                saved.add(new Visibility(bone, bone.isHidden(), bone.childBonesAreHiddenToo(),
                        bone.cubesAreHidden()));
            }
            if (!hasRest || !hasOpen || selected == null) {
                draw.run();
                return;
            }
            var initial = selected.getInitialSnapshot();
            var position = selected.getPosition();
            var scale = selected.getScale();
            var rotation = selected.getRotation();
            String modelId = ((EntityMaid) entity).getModelId();
            try {
                // The pack hides its alternate mouths behind the face at bind. The expression
                // animations bring a selected shape forward and may also hide it with zero scale.
                selected.setPosition(initial.positionOffsetX, initial.positionOffsetY,
                        initial.positionOffsetZ + revealDepth(modelId, selected.getName()));
                selected.setScale(initial.scaleValueX, initial.scaleValueY, initial.scaleValueZ);
                selected.setRotation(initial.rotationValueX, initial.rotationValueY,
                        initial.rotationValueZ);
                for (var state : saved) {
                    boolean hidden = state.bone() != selected;
                    state.bone().setHidden(hidden, state.childrenHidden());
                    state.bone().setCubesHidden(hidden);
                }
                draw.run();
            } finally {
                selected.setPosition(position);
                selected.setScale(scale);
                selected.setRotation(rotation);
                for (var state : saved) {
                    state.bone().setHidden(state.hidden(), state.childrenHidden());
                    state.bone().setCubesHidden(state.cubesHidden());
                }
            }
        }

        private static float revealDepth(String modelId, String name) {
            return switch (modelId) {
                case "geckolib:winefox_little" -> -0.8F;
                case "geckolib:winefox_magical" -> -0.91406F;
                case "geckolib:winefox_astronaut" -> name.equalsIgnoreCase("Mouth_smile2") ? 0F : -1F;
                case "geckolib:winefox_matured" -> name.equalsIgnoreCase("xiao") ? -1.8F : -1F;
                case "geckolib:winefox_momo" -> name.equalsIgnoreCase("jingya") ? -0.125F : -1F;
                default -> -1F;
            };
        }

        private static int shape(String name) {
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "chill", "mouth_chill" -> 0;
                case "smile2", "mouth_smile2", "weixiao" -> 1;
                case "smile1", "mouth_smile1", "mouth_smile", "xiao" -> 2;
                case "surprise", "mouth_surprise", "jingya" -> 3;
                default -> -1;
            };
        }

        private record Visibility(AnimatedGeoBone bone, boolean hidden,
                                  boolean childrenHidden, boolean cubesHidden) {
        }
    }
}
