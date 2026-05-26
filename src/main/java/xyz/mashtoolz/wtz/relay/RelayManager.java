package xyz.mashtoolz.wtz.relay;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.mashtoolz.wtz.auth.LinkStateStore;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.features.shoppinglist.ShoppingListManager;
import xyz.mashtoolz.wtz.net.Endpoints;
import xyz.mashtoolz.wtz.util.ChatHelper;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public final class RelayManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("WTZ-Link");
    private static final RelayManager INSTANCE = new RelayManager();

    private final Object lock = new Object();
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "WTZ-Link-Reconnect");
        thread.setDaemon(true);
        return thread;
    });
    private final LinkStateStore keyStore = new LinkStateStore();

    private RelayClient relayClient;
    private boolean shuttingDown = false;
    private boolean reconnectScheduled = false;
    private int reconnectAttempt = 0;
    private boolean reconnectSuppressed = false;
    private boolean disconnectWarnLogged = false;

    private boolean connected = false;
    private boolean paired = false;
    private String currentPairingKey = null;

    public static RelayManager getInstance() {
        return INSTANCE;
    }

    private RelayManager() {
    }

    public void init() {
        synchronized (lock) {
            if (shuttingDown) return;
            if (currentPairingKey == null) {
                currentPairingKey = keyStore.loadPairingKey().orElse(null);
                paired = false;
            }
        }
        connectNow();
    }

    public void shutdown() {
        RelayClient clientSnapshot;
        synchronized (lock) {
            if (shuttingDown) return;
            shuttingDown = true;
            connected = false;
            reconnectScheduled = false;
            clientSnapshot = relayClient;
            relayClient = null;
        }
        reconnectExecutor.shutdownNow();
        if (clientSnapshot != null) {
            try {
                clientSnapshot.close();
            } catch (Exception ignored) {
            }
        }
    }

    public String getOrCreateLocalPairingKey() {
        synchronized (lock) {
            if (currentPairingKey == null || currentPairingKey.isBlank()) {
                currentPairingKey = UUID.randomUUID().toString();
                keyStore.savePairingKey(currentPairingKey);
            }
        }
        connectNow();
        synchronized (lock) {
            return currentPairingKey;
        }
    }

    public void sendAppMessage(String type, JsonObject data) {
        RelayClient clientSnapshot;
        synchronized (lock) {
            if (!paired || !connected) return;
            clientSnapshot = relayClient;
        }
        if (clientSnapshot == null || !clientSnapshot.isOpen()) return;

        try {
            clientSnapshot.send(RelayMessages.toJson(RelayMessages.appMessage(type, data)));
        } catch (Exception ignored) {
        }
    }

    public void onRelayOpen(RelayClient client) {
        synchronized (lock) {
            if (relayClient != client) return;
            connected = true;
            reconnectScheduled = false;
            reconnectAttempt = 0;
            reconnectSuppressed = false;
            disconnectWarnLogged = false;
        }

        LOGGER.info("Connected to mod-link relay {}", client.getURI());
        sendHello();
    }

    public void onRelayMessage(RelayClient client, String message) {
        synchronized (lock) {
            if (relayClient != client) return;
        }
        runOnClientThread(() -> handleIncomingMessage(message));
    }

    public void onRelayBinaryMessage(RelayClient client) {
        synchronized (lock) {
            if (relayClient != client) return;
        }
        LOGGER.warn("Relay sent unsupported binary payload. Closing connection.");
        try {
            client.close(1003, "binary_unsupported");
        } catch (Exception ignored) {
        }
    }

    public void onRelayClose(RelayClient client, int code, String reason, boolean remote) {
        synchronized (lock) {
            if (relayClient != client) return;
            connected = false;
        }

        if (!disconnectWarnLogged) {
            LOGGER.warn("Relay disconnected (code={} remote={} reason='{}')", code, remote, reason == null ? "" : reason);
            disconnectWarnLogged = true;
        }

        scheduleReconnect();
    }

    public void onRelayError(RelayClient client, Exception ex) {
        synchronized (lock) {
            if (relayClient != client || shuttingDown) return;
        }
        LOGGER.warn("Relay error: {}", ex.getMessage());
    }

    private void handleIncomingMessage(String message) {
        Optional<JsonObject> parsed = RelayMessages.parse(message);
        if (parsed.isEmpty()) return;

        JsonObject msg = parsed.get();
        String type = RelayMessages.string(msg, "type");
        if (type == null || type.isBlank()) return;

        if (type.startsWith("link.")) {
            handleControlMessage(type, msg);
            return;
        }

        switch (type) {
            case "wtz.connect" -> sendConnected();
            case "wtz.shopping_list" -> {
                String code = RelayMessages.string(msg, "code");
                if (code != null && !code.isBlank())
                    ShoppingListManager.getInstance().importList(code, null, false);
            }
            default -> {
            }
        }
    }

    private void handleControlMessage(String type, JsonObject msg) {
        switch (type) {
            case "link.ready" -> {
                synchronized (lock) {
                    paired = RelayMessages.bool(msg, "paired", paired);
                }
            }
            case "link.paired" -> {
                String newKey = RelayMessages.string(msg, "pairingKey");
                if (newKey == null || newKey.isBlank()) return;

                boolean shouldPersist;
                synchronized (lock) {
                    shouldPersist = !newKey.equals(currentPairingKey);
                    currentPairingKey = newKey;
                    paired = true;
                }
                if (shouldPersist)
                    keyStore.savePairingKey(newKey);
            }
            case "link.peer_state" -> {
            }
            case "link.unlinked" -> clearPairingState();
            case "link.replaced" -> {
                synchronized (lock) {
                    reconnectSuppressed = true;
                }
                ChatHelper.sendWarning("Another mod instance replaced this relay connection.");
                RelayClient clientSnapshot;
                synchronized (lock) {
                    clientSnapshot = relayClient;
                }
                if (clientSnapshot != null) {
                    try {
                        clientSnapshot.close(1000, "replaced");
                    } catch (Exception ignored) {
                    }
                }
            }
            case "link.error" -> {
                boolean clearStoredPairingKey = RelayMessages.bool(msg, "clearStoredPairingKey", false);
                if (clearStoredPairingKey)
                    clearPairingState();
            }
            default -> {
            }
        }
    }

    private void sendConnected() {
        sendJson(RelayMessages.connected(modVersion()));
    }

    private void sendHello() {
        String token = keyStore.loadToken().orElse(null);
        String pairingKey;
        synchronized (lock) {
            pairingKey = currentPairingKey;
        }
        sendJson(RelayMessages.hello(token, pairingKey));
    }

    private void sendJson(JsonObject payload) {
        RelayClient clientSnapshot;
        synchronized (lock) {
            clientSnapshot = relayClient;
        }
        if (clientSnapshot == null || !clientSnapshot.isOpen()) return;
        try {
            clientSnapshot.send(RelayMessages.toJson(payload));
        } catch (Exception ignored) {
        }
    }

    private void clearPairingState() {
        synchronized (lock) {
            currentPairingKey = null;
            paired = false;
        }
        keyStore.clearPairingKey();
    }

    private void connectNow() {
        synchronized (lock) {
            if (shuttingDown) return;
            if (relayClient != null) {
                ReadyState state = relayClient.getReadyState();
                if (state == ReadyState.OPEN || state == ReadyState.NOT_YET_CONNECTED) return;
            }
            URI relayUri = resolveRelayUri();
            relayClient = new RelayClient(relayUri, this);
            relayClient.connect();
        }
    }

    public void refreshConnection() {
        RelayClient previousClient;
        synchronized (lock) {
            if (shuttingDown) return;
            previousClient = relayClient;
            relayClient = null;
            connected = false;
            reconnectScheduled = false;
            reconnectSuppressed = false;
        }

        if (previousClient != null) {
            try {
                previousClient.close(1000, "token_updated");
            } catch (Exception ignored) {
            }
        }

        connectNow();
    }

    private void scheduleReconnect() {
        synchronized (lock) {
            if (shuttingDown || reconnectSuppressed) return;
            if (reconnectScheduled) return;
            reconnectScheduled = true;
        }

        long baseDelayMs = (long) Math.min(30_000, 1_000 * Math.pow(2, Math.max(0, reconnectAttempt)));
        long jitterMs = ThreadLocalRandom.current().nextLong(0, 700);
        long delayMs = baseDelayMs + jitterMs;
        reconnectAttempt = Math.min(reconnectAttempt + 1, 10);
        reconnectExecutor.schedule(() -> {
            synchronized (lock) {
                reconnectScheduled = false;
                if (shuttingDown || reconnectSuppressed) return;
            }
            connectNow();
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private URI resolveRelayUri() {
        return URI.create(Endpoints.MOD_LINK_URL);
    }

    private static String modVersion() {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer(WTZClient.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static void runOnClientThread(Runnable task) {
        MinecraftClient client = WTZClient.client();
        if (client == null) {
            task.run();
            return;
        }
        client.execute(task);
    }

    static final class RelayClient extends WebSocketClient {

        private final RelayManager manager;

        RelayClient(URI serverUri, RelayManager manager) {
            super(serverUri);
            this.manager = manager;
            setConnectionLostTimeout(30);
        }

        @Override
        public void onOpen(ServerHandshake handshakeData) {
            manager.onRelayOpen(this);
        }

        @Override
        public void onMessage(String message) {
            manager.onRelayMessage(this, message);
        }

        @Override
        public void onMessage(ByteBuffer bytes) {
            manager.onRelayBinaryMessage(this);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            manager.onRelayClose(this, code, reason, remote);
        }

        @Override
        public void onError(Exception ex) {
            manager.onRelayError(this, ex);
        }
    }

    static final class RelayMessages {

        private static final Gson GSON = new Gson();

        private RelayMessages() {
        }

        static Optional<JsonObject> parse(String message) {
            try {
                JsonObject obj = GSON.fromJson(message, JsonObject.class);
                return obj == null ? Optional.empty() : Optional.of(obj);
            } catch (Exception ignored) {
                return Optional.empty();
            }
        }

        static String toJson(JsonObject payload) {
            return GSON.toJson(payload);
        }

        static String string(JsonObject obj, String key) {
            try {
                if (!obj.has(key)) return null;
                return obj.get(key).getAsString();
            } catch (Exception ignored) {
                return null;
            }
        }

        static boolean bool(JsonObject obj, String key, boolean fallback) {
            try {
                if (!obj.has(key)) return fallback;
                return obj.get(key).getAsBoolean();
            } catch (Exception ignored) {
                return fallback;
            }
        }

        static JsonObject appMessage(String type, JsonObject data) {
            JsonObject payload = new JsonObject();
            payload.addProperty("type", type);
            payload.add("data", data);
            return payload;
        }

        static JsonObject connected(String version) {
            JsonObject response = new JsonObject();
            response.addProperty("type", "wtz.connected");
            response.addProperty("version", version);
            return response;
        }

        static JsonObject hello(String token, String pairingKey) {
            JsonObject hello = new JsonObject();
            hello.addProperty("type", "link.hello");
            hello.addProperty("protocolVersion", 2);
            hello.addProperty("role", "mod");
            if (token != null && !token.isBlank()) hello.addProperty("token", token);
            if (pairingKey != null && !pairingKey.isBlank()) hello.addProperty("pairingKey", pairingKey);
            return hello;
        }
    }
}
