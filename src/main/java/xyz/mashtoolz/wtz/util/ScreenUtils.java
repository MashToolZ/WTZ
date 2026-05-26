package xyz.mashtoolz.wtz.util;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import xyz.mashtoolz.wtz.client.WTZClient;

public final class ScreenUtils {

    private ScreenUtils() {
    }

    public static HandledScreen<?> currentHandledScreenOrNull() {
        Screen screen = WTZClient.client().currentScreen;
        if (screen instanceof HandledScreen<?> handled) {
            return handled;
        }
        return null;
    }

    public static boolean handledScreenHasTitle(HandledScreen<?> handled, String expectedTitle) {
        return handled != null && expectedTitle.equals(handled.getTitle().getString());
    }
}


