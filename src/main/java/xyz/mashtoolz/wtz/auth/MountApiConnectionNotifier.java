package xyz.mashtoolz.wtz.auth;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import xyz.mashtoolz.wtz.util.ChatHelper;

public final class MountApiConnectionNotifier {

    private static final LinkStateStore STORE = new LinkStateStore();
    private static final int JOIN_MESSAGE_DELAY_TICKS = 20;
    private static int pendingTicks = -1;
    private static boolean shownThisSession = false;

    private MountApiConnectionNotifier() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static void onWynncraftJoin() {
        if (shownThisSession || pendingTicks >= 0) return;
        pendingTicks = JOIN_MESSAGE_DELAY_TICKS;
    }

    private static void reset() {
        pendingTicks = -1;
        shownThisSession = false;
    }

    private static void tick() {
        if (pendingTicks < 0) return;
        if (pendingTicks-- > 0) return;

        pendingTicks = -1;
        if (STORE.hasToken()) {
            shownThisSession = true;
            ChatHelper.sendSuccess("Mount API connected");
        }
    }
}
