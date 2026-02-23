package com.ashwake.ashwake.registry;

import com.ashwake.ashwake.AshwakeMod;
import com.ashwake.ashwake.world.entity.RuneDiscEntity;
import com.ashwake.ashwake.world.entity.WorldCoreOrbEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AshwakeEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AshwakeMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<WorldCoreOrbEntity>> WORLD_CORE_ORB =
            ENTITY_TYPES.register("world_core_orb", () -> EntityType.Builder
                    .<WorldCoreOrbEntity>of(WorldCoreOrbEntity::new, MobCategory.MISC)
                    .sized(0.95F, 0.95F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .fireImmune()
                    .build(ResourceLocation.fromNamespaceAndPath(AshwakeMod.MODID, "world_core_orb").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<RuneDiscEntity>> RUNE_DISC =
            ENTITY_TYPES.register("rune_disc", () -> EntityType.Builder
                    .<RuneDiscEntity>of(RuneDiscEntity::new, MobCategory.MISC)
                    .sized(5.4F, 0.4F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .fireImmune()
                    .build(ResourceLocation.fromNamespaceAndPath(AshwakeMod.MODID, "rune_disc").toString()));

    private AshwakeEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
