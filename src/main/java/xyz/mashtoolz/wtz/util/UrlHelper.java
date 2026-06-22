package xyz.mashtoolz.wtz.util;

import net.minecraft.util.Util;

public final class UrlHelper {

    private UrlHelper() {
    }

    public static void openOrSendFallback(String url, String openedMessage, String fallbackMessage) {
        try {
            Util.getOperatingSystem().open(url);
            ChatHelper.sendInfo(openedMessage);
        } catch (Exception ignored) {
        }

        ChatHelper.sendLink(fallbackMessage, url, url, 0xFFCC00);
    }
}
