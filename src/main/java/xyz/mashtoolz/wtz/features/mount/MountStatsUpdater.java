package xyz.mashtoolz.wtz.features.mount;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.text.Text;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.features.qol.QualityOfLife;

public class MountStatsUpdater {

    private static final String PILL_FONT_FRAGMENT = "pill";
    private static final String MOUNT_ENERGY_FONT_FRAGMENT = "hud/gameplay/default/center_left";
    private static final int MOUNT_ENERGY_FULL_GLYPH = 0xE000;
    private static final int MOUNT_ENERGY_EMPTY_GLYPH = 0xE02F;
    private static final int ENERGY_UPDATE_DEBOUNCE_TICKS = 4;

    private static String lastDecoded = "";
    private static String currentMountEnergySignature = "";
    private static String lastMountEnergySignature = "";
    private static int pendingEnergyUpdateTicks = -1;

    private enum State {IDLE, WAITING, OPEN_PENDING, SCREEN_PENDING}

    private static State state = State.IDLE;
    private static int waitTicks = 0;
    private static final int WAIT_DELAY = 0;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> tick());
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
        String mountEnergySignature = mountEnergySignature(message);
        boolean mountEnergyChanged = updateMountEnergySignature(mountEnergySignature);

        if (!WTZClient.CONFIG.mountStatsAutoUpdate) return;
        if (isMissingMountedSignal()) return;

        if (mountEnergyChanged) {
            pendingEnergyUpdateTicks = ENERGY_UPDATE_DEBOUNCE_TICKS;
            return;
        }

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

        scheduleUpdate();
    }

    public static boolean hasMountEnergyBar() {
        return !currentMountEnergySignature.isEmpty();
    }

    private static boolean isMissingMountedSignal() {
        return !MountUtils.isMounted() || !hasMountEnergyBar();
    }

    private static void tick() {
        MinecraftClient client = WTZClient.client();
        ClientPlayerEntity player = WTZClient.player();
        if (player == null) {
            resetMountEnergyGlyph();
            return;
        }
        if (!MountUtils.isMounted()) {
            resetMountEnergyGlyph();
        }
        if (!WTZClient.CONFIG.mountStatsAutoUpdate) {
            state = State.IDLE;
            pendingEnergyUpdateTicks = -1;
            return;
        }

        tickPendingEnergyUpdate();

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
        QualityOfLife.scheduleMacOSMovementKeyFix();
    }

    private static void scheduleUpdate() {
        if (state != State.IDLE) return;

        waitTicks = WAIT_DELAY;
        state = State.WAITING;
    }

    private static void tickPendingEnergyUpdate() {
        if (pendingEnergyUpdateTicks < 0) return;
        if (isMissingMountedSignal()) {
            pendingEnergyUpdateTicks = -1;
            return;
        }

        if (pendingEnergyUpdateTicks-- > 0) return;

        pendingEnergyUpdateTicks = -1;
        scheduleUpdate();
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

    private static String mountEnergySignature(Text text) {
        StringBuilder signature = new StringBuilder();
        appendMountEnergyGlyphs(text, signature);
        return signature.toString();
    }

    private static void appendMountEnergyGlyphs(Text text, StringBuilder signature) {
        String font = text.getStyle().getFont().toString();
        if (font.contains(MOUNT_ENERGY_FONT_FRAGMENT)) {
            text.getString().codePoints()
                    .filter(MountStatsUpdater::isMountEnergyGlyph)
                    .forEach(signature::appendCodePoint);
        }

        for (Text sibling : text.getSiblings()) {
            appendMountEnergyGlyphs(sibling, signature);
        }
    }

    private static boolean isMountEnergyGlyph(int codepoint) {
        return codepoint >= MOUNT_ENERGY_FULL_GLYPH && codepoint <= MOUNT_ENERGY_EMPTY_GLYPH;
    }

    private static boolean updateMountEnergySignature(String signature) {
        currentMountEnergySignature = signature;

        if (signature.isEmpty()) {
            lastMountEnergySignature = "";
            return false;
        }

        if (lastMountEnergySignature.isEmpty()) {
            lastMountEnergySignature = signature;
            return false;
        }

        if (signature.equals(lastMountEnergySignature)) return false;

        lastMountEnergySignature = signature;
        return true;
    }

    private static void resetMountEnergyGlyph() {
        currentMountEnergySignature = "";
        lastMountEnergySignature = "";
        pendingEnergyUpdateTicks = -1;
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
