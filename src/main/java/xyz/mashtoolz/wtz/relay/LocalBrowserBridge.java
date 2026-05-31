package xyz.mashtoolz.wtz.relay;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.Executors;

public final class LocalBrowserBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("WTZ-LocalBridge");
    private static final Gson GSON = new Gson();
    private static final LocalBrowserBridge INSTANCE = new LocalBrowserBridge();
    private static final int PORT = 38421;
    private static final Set<String> ALLOWED_ORIGINS = Set.of(
            "https://wynn.mashtoolz.xyz",
            "http://localhost:3000",
            "http://127.0.0.1:3000"
    );

    private HttpServer server;
    private long lastPairRequestAt = 0;

    private LocalBrowserBridge() {
    }

    public static LocalBrowserBridge getInstance() {
        return INSTANCE;
    }

    public synchronized void start() {
        if (server != null) return;

        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            server.createContext("/pair", this::handlePair);
            server.setExecutor(Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "WTZ-LocalBridge");
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
            LOGGER.info("WTZ local bridge listening on 127.0.0.1:{}", PORT);
        } catch (IOException e) {
            LOGGER.warn("Failed to start WTZ local bridge", e);
            server = null;
        }
    }

    public synchronized void stop() {
        if (server == null) return;
        server.stop(0);
        server = null;
    }

    private void handlePair(HttpExchange exchange) throws IOException {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (!isAllowedOrigin(origin)) {
            sendText(exchange, 403, "forbidden", null);
            return;
        }

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 204, "", origin);
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "method_not_allowed", origin);
            return;
        }

        String bridgeHeader = exchange.getRequestHeaders().getFirst("X-WTZ-Local-Bridge");
        if (!"1".equals(bridgeHeader)) {
            sendText(exchange, 400, "missing_bridge_header", origin);
            return;
        }

        long now = System.currentTimeMillis();
        synchronized (this) {
            if (now - lastPairRequestAt < 2_000) {
                sendText(exchange, 429, "too_many_requests", origin);
                return;
            }
            lastPairRequestAt = now;
        }

        String pairingKey = RelayManager.getInstance().getOrCreateLocalPairingKey();
        JsonObject body = new JsonObject();
        body.addProperty("ok", true);
        body.addProperty("pairingKey", pairingKey);
        body.addProperty("relayPath", "/api/ws/mod-link");
        sendJson(exchange, body, origin);
    }

    private boolean isAllowedOrigin(String origin) {
        return origin != null && ALLOWED_ORIGINS.contains(origin);
    }

    private void addCorsHeaders(HttpExchange exchange, String origin) {
        if (origin == null) return;
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", origin);
        headers.set("Vary", "Origin");
        headers.set("Access-Control-Allow-Methods", "POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, X-WTZ-Local-Bridge");
        headers.set("Access-Control-Allow-Private-Network", "true");
        headers.set("Access-Control-Max-Age", "600");
    }

    private void sendJson(HttpExchange exchange, JsonObject body, String origin) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        addCorsHeaders(exchange, origin);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private void sendText(HttpExchange exchange, int status, String body, String origin) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        addCorsHeaders(exchange, origin);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        if (status != 204)
            exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
