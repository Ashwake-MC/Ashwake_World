package com.ashwake.ashwake.network;

import java.util.Objects;
import java.util.function.Consumer;

public final class IntroPopupClientBridge {
    private static volatile Consumer<OpenIntroGuiPayload> openHandler = payload -> {
    };

    private IntroPopupClientBridge() {
    }

    public static void setOpenHandler(Consumer<OpenIntroGuiPayload> handler) {
        openHandler = Objects.requireNonNull(handler);
    }

    public static void handleOpen(OpenIntroGuiPayload payload) {
        openHandler.accept(payload);
    }
}
