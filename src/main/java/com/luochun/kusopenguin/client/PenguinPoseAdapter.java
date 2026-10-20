package com.luochun.kusopenguin.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.LivingEntity;

public interface PenguinPoseAdapter {
    void apply(PenguinBodyModel penguin, EntityModel<?> original, LivingEntity entity);
}
