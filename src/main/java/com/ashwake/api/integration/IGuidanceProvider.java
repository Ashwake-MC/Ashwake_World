package com.ashwake.api.integration;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public interface IGuidanceProvider {
    Optional<Component> getGuidanceHint(ServerLevel level);

    Optional<BlockPos> getGuidanceLocation(ServerLevel level);
}
