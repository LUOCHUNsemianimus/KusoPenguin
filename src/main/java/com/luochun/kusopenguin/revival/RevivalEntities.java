package com.luochun.kusopenguin.revival;

import com.luochun.kusopenguin.KusoPenguin;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class RevivalEntities {
    private static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, KusoPenguin.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<RevivalCorpse>> CORPSE =
            TYPES.register("revival_corpse", () -> EntityType.Builder
                    .<RevivalCorpse>of(RevivalCorpse::new, MobCategory.MISC)
                    .sized(0.7F, 1.8F)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .noSave()
                    .noSummon()
                    .build("revival_corpse"));

    private RevivalEntities() {
    }

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }
}
