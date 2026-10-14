package com.astune.fresco;

/**
 * Shared client input state consumed by paint providers without linking them
 * to Minecraft's client-only classes.
 */
public final class ClientInputState {
    private static volatile boolean useKeyDown;

    private ClientInputState() {}

    public static boolean isUseKeyDown() {
        return useKeyDown;
    }

    public static void setUseKeyDown(boolean down) {
        useKeyDown = down;
    }
}
