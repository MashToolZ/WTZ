package xyz.mashtoolz.wtz.util;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import xyz.mashtoolz.wtz.client.WTZClient;

import java.net.URI;

public class ChatHelper {

    private static final TextColor BRACKET_COLOR = TextColor.fromRgb(0x404040);
    private static final TextColor WTZ_COLOR = TextColor.fromRgb(0xFF4800);

    public static MutableText prefix() {
        return Text.literal("[").setStyle(Style.EMPTY.withColor(BRACKET_COLOR))
                .append(Text.literal("WTZ").setStyle(Style.EMPTY.withColor(WTZ_COLOR)))
                .append(Text.literal("] ").setStyle(Style.EMPTY.withColor(BRACKET_COLOR)));
    }

    public static void send(String message, int color) {
        ClientPlayerEntity player = WTZClient.player();
        if (player == null) return;
        player.sendMessage(prefix().append(Text.literal(message).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)))), false);
    }

    public static void sendSuccess(String message) {
        send(message, 0x2ECC71);
    }

    public static void sendWarning(String message) {
        send(message, 0xFFCC00);
    }

    public static void sendError(String message) {
        send(message, 0xFF5555);
    }

    public static void sendInfo(String message) {
        send(message, 0xAAAAAA);
    }

    public static void sendLink(String message, String label, String url, int color) {
        ClientPlayerEntity player = WTZClient.player();
        if (player == null) return;
        MutableText text = prefix()
                .append(Text.literal(message).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))))
                .append(Text.literal(" "))
                .append(Text.literal(label).setStyle(Style.EMPTY
                        .withColor(TextColor.fromRgb(0x55AAFF))
                        .withUnderline(true)
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal(url)))));
        player.sendMessage(text, false);
    }

    public static void send(MutableText message) {
        ClientPlayerEntity player = WTZClient.player();
        if (player == null) return;
        player.sendMessage(prefix().append(message), false);
    }

    public static void sendBlank() {
        ClientPlayerEntity player = WTZClient.player();
        if (player == null) return;
        player.sendMessage(Text.empty(), false);
    }
}
