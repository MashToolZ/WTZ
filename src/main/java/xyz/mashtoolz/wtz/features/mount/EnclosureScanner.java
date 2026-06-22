package xyz.mashtoolz.wtz.features.mount;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.enums.GUI;
import xyz.mashtoolz.wtz.features.mount.enclosure.BreedingQueueCache;
import xyz.mashtoolz.wtz.features.mount.enclosure.BreedingResultReporter;
import xyz.mashtoolz.wtz.features.mount.enclosure.EnclosureState;
import xyz.mashtoolz.wtz.features.mount.enclosure.EnclosureStateParser;
import xyz.mashtoolz.wtz.relay.RelayManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class EnclosureScanner {

    private static final long DEBOUNCE_MS = 500;
    private static final ScheduledExecutorService debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "WTZ-Enclosure-Debounce");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile ScheduledFuture<?> pendingScan;
    private static String lastSentJson = "";

    private EnclosureScanner() {
    }

    public static void onScreenClose(HandledScreen<?> screen) {
        if (!GUI.ENCLOSURE.is(screen)) return;
        ScheduledFuture<?> previous = pendingScan;
        if (previous != null) previous.cancel(false);
        lastSentJson = "";
        scan(screen, true);
        BreedingResultReporter.flushQueuedReports();
    }

    public static void onSlotChanged(HandledScreen<?> screen) {
        if (GUI.ENCLOSURE.is(screen)) scheduleScan(screen);
    }

    public static void onSlotsUpdated(HandledScreen<?> screen) {
        if (!GUI.ENCLOSURE.is(screen)) return;
        lastSentJson = "";
        scheduleScan(screen);
    }

    private static void scheduleScan(HandledScreen<?> screen) {
        ScheduledFuture<?> previous = pendingScan;
        if (previous != null) previous.cancel(false);
        pendingScan = debounceExecutor.schedule(
                () -> WTZClient.client().execute(() -> scan(screen)),
                DEBOUNCE_MS,
                TimeUnit.MILLISECONDS
        );
    }

    private static void scan(HandledScreen<?> screen) {
        scan(screen, false);
    }

    private static void scan(HandledScreen<?> screen, boolean closing) {
        EnclosureState state = EnclosureStateParser.parse(screen.getScreenHandler());
        BreedingQueueCache.getInstance().capture(state, closing);

        JsonObject data = state.toSyncJson();
        String json = data.toString();
        if (json.equals(lastSentJson)) return;
        lastSentJson = json;
        RelayManager.getInstance().sendAppMessage("wtz.enclosure", data);
    }
}
