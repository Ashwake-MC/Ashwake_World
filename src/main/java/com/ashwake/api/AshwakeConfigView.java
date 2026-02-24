package com.ashwake.api;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public interface AshwakeConfigView {
    int getCycleMinutes();

    int getOmenSeconds();

    int getSafeRadius();

    boolean isSleepLockEnabled();

    Set<ResourceLocation> getSleepLockStates();

    boolean isSystemEnabled();
}
