package xyz.mashtoolz.wtz.features.mount;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.text.Text;
import xyz.mashtoolz.wtz.client.WTZClient;

public class MountStatsUpdater {

    private static final String PILL_FONT_FRAGMENT = "pill";
    private static String lastDecoded = "";

    private enum State {IDLE, WAITING, OPEN_PENDING, SCREEN_PENDING}

    private static State state = State.IDLE;
    private static int waitTicks = 0;
    private static final int WAIT_DELAY = 0;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> tick());
    }

    public static void onScreenOpened(Screen screen) {
        if (!WTZClient.CONFIG.mountStatsAutoUpdate) return;
        if (state != State.SCREEN_PENDING) return;
        if (!(screen instanceof HandledScreen<?>)) return;
    }

    public static void onSlotsUpdated(HandledScreen<?> screen) {
        if (!WTZClient.CONFIG.mountStatsAutoUpdate) return;
        if (state != State.SCREEN_PENDING) return;
        if (WTZClient.client().currentScreen != screen) return;

        ClientPlayerEntity player = WTZClient.player();
        if (player == null) return;

        closeHandledScreen(player);
        state = State.IDLE;
    }

    public static void onActionBar(Text message) {
        if (!WTZClient.CONFIG.mountStatsAutoUpdate) return;
        if (!MountUtils.isMounted()) return;

        StringBuilder pillChars = new StringBuilder();
        extractPillText(message, pillChars);

        if (pillChars.isEmpty()) {
            lastDecoded = "";
            return;
        }

        String decoded = decode(pillChars.toString());
        if (decoded.isBlank()) return;
        if (decoded.equals(lastDecoded)) return;
        lastDecoded = decoded;

        if (state == State.IDLE) {
            waitTicks = WAIT_DELAY;
            state = State.WAITING;
        }
    }

    private static void tick() {
        MinecraftClient client = WTZClient.client();
        ClientPlayerEntity player = WTZClient.player();
        if (player == null) return;
        if (!WTZClient.CONFIG.mountStatsAutoUpdate) {
            state = State.IDLE;
            return;
        }

        switch (state) {
            case WAITING -> {
                if (--waitTicks <= 0) {
                    state = State.OPEN_PENDING;
                }
            }
            case OPEN_PENDING -> {
                if (client.currentScreen != null) {
                    state = State.IDLE;
                    return;
                }
                ClientPlayNetworkHandler net = client.getNetworkHandler();
                if (net == null) {
                    state = State.IDLE;
                    return;
                }
                net.sendPacket(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.OPEN_INVENTORY));
                waitTicks = 20; 
                state = State.SCREEN_PENDING;
            }
            case SCREEN_PENDING -> {
                if (--waitTicks <= 0) {
                    state = State.IDLE;
                }
            }
            default -> {
            }
        }
    }

    private static void closeHandledScreen(ClientPlayerEntity player) {
        player.closeHandledScreen();
    }

    private static void extractPillText(Text text, StringBuilder sb) {
        String font = text.getStyle().getFont().toString();
        if (font.contains(PILL_FONT_FRAGMENT)) {
            sb.append(text.getString());
        }
        for (Text sibling : text.getSiblings()) {
            extractPillText(sibling, sb);
        }
    }

    private static String decode(String raw) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '\uE020' && c <= '\uE029') {
                sb.append((char) ('0' + (c - '\uE020')));
            } else if (c == '\uE072') {
                sb.append('.');
            } else if (c >= '\uE000' && c <= '\uE019') {
                sb.append((char) ('A' + (c - '\uE000')));
            } else if (c == '+' || c == '-' || c == ' ') {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }
}
