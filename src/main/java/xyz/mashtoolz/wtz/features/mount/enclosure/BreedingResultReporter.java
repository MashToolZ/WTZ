package xyz.mashtoolz.wtz.features.mount.enclosure;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.mashtoolz.wtz.auth.LinkStateStore;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.util.ChatHelper;

import java.util.List;
import java.util.Optional;

public final class BreedingResultReporter {

    private static final Logger LOGGER = LoggerFactory.getLogger("WTZ-BreedingResultReporter");
    private static final int MAX_BREED_RESULTS_PER_BATCH = 30;
    private static final int QUEUE_RETRY_INTERVAL_TICKS = 200;
    private static final LinkStateStore LINK_STORE = new LinkStateStore();
    private static final BreedingResultQueue QUEUE = new BreedingResultQueue();

    private static boolean registered = false;
    private static boolean loaded = false;
    private static volatile boolean queuePostInFlight = false;
    private static int retryTicksRemaining = QUEUE_RETRY_INTERVAL_TICKS;
    private static long lastMissingTokenWarningAt = 0;

    private BreedingResultReporter() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ensureLoaded();
        ClientTickEvents.END_CLIENT_TICK.register(client -> tickQueueRetry());
    }

    public static void enqueue(String location, int row, int mountSlot, JsonObject parentA, JsonObject parentB, JsonObject result, String source) {
        ensureLoaded();
        String id = QUEUE.add(location, row, mountSlot, parentA, parentB, result, source);
        LOGGER.info("Queued breeding result report {}.", id);
    }

    public static void flushQueuedReports() {
        ensureLoaded();
        if (!WTZClient.CONFIG.mountBreedReportingEnabled) return;
        if (queuePostInFlight || QUEUE.isEmpty()) return;

        Optional<String> token = LINK_STORE.loadToken();
        if (token.isEmpty()) {
            warnMissingToken();
            return;
        }

        List<JsonObject> batch = QUEUE.firstBatch(MAX_BREED_RESULTS_PER_BATCH);
        if (batch.isEmpty()) return;

        queuePostInFlight = true;
        LOGGER.info("Submitting {} queued breeding result report(s).", batch.size());
        BreedingResultSubmitter.submit(batch, token.get())
                .whenComplete((result, ex) -> WTZClient.client().execute(() -> {
                    queuePostInFlight = false;
                    retryTicksRemaining = QUEUE_RETRY_INTERVAL_TICKS;

                    if (ex != null) {
                        LOGGER.error("Breeding result POST error for {} report(s)", batch.size(), ex);
                        return;
                    }

                    if (result.unauthorized()) {
                        LOGGER.warn("Breeding result POST rejected as unauthorized. Link a valid token with /wtz link <token>.");
                        warnMissingToken();
                        return;
                    }

                    if (result.accepted()) {
                        QUEUE.removeAccepted(result.acceptedIds());
                        int accepted = result.acceptedIds().size();
                        if (accepted > 0) {
                            ChatHelper.sendSuccess(accepted == 1
                                    ? "Mount Breeding Result submitted"
                                    : "Mount Breeding Results submitted (" + accepted + ")");
                        }
                        return;
                    }

                    LOGGER.warn("Breeding result POST rejected for {} report(s). status={} body={}",
                            batch.size(), result.statusCode(), result.body());
                }));
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        QUEUE.load();
    }

    private static void tickQueueRetry() {
        if (!WTZClient.CONFIG.mountBreedReportingEnabled) return;
        if (queuePostInFlight || QUEUE.isEmpty()) return;
        if (retryTicksRemaining > 0) {
            retryTicksRemaining--;
            return;
        }

        retryTicksRemaining = QUEUE_RETRY_INTERVAL_TICKS;
        flushQueuedReports();
    }

    private static void warnMissingToken() {
        long now = System.currentTimeMillis();
        if (now - lastMissingTokenWarningAt < 60_000) return;
        lastMissingTokenWarningAt = now;
        ChatHelper.sendWarning("No WynnToolZ token configured. Run /wtz link to open the link page.");
    }
}
