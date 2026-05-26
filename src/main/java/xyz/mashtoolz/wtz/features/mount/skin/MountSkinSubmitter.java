package xyz.mashtoolz.wtz.features.mount.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import xyz.mashtoolz.wtz.net.Endpoints;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class MountSkinSubmitter {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private MountSkinSubmitter() {
    }

    public static CompletableFuture<Result> submit(List<MountSkinQueue.Purchase> purchases, String token) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(Endpoints.MOUNT_SUBMIT_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(buildBody(purchases).toString()))
                .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> new Result(
                        response.statusCode(),
                        response.body(),
                        parseAcceptedIds(response.body())
                ));
    }

    private static JsonObject buildBody(List<MountSkinQueue.Purchase> purchases) {
        JsonArray events = new JsonArray();
        for (MountSkinQueue.Purchase purchase : purchases) {
            JsonObject event = new JsonObject();
            event.addProperty("id", purchase.id());
            event.addProperty("itemName", purchase.itemName());
            event.addProperty("primary", purchase.primary());
            event.addProperty("secondary", purchase.secondary());
            event.addProperty("detectedAt", purchase.detectedAt());
            events.add(event);
        }

        JsonObject body = new JsonObject();
        body.addProperty("type", "skin");
        body.add("events", events);
        return body;
    }

    private static Set<String> parseAcceptedIds(String body) {
        Set<String> acceptedIds = new HashSet<>();
        try {
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            if (!obj.has("acceptedIds") || !obj.get("acceptedIds").isJsonArray()) return acceptedIds;
            for (JsonElement element : obj.get("acceptedIds").getAsJsonArray()) {
                if (element.isJsonPrimitive()) acceptedIds.add(element.getAsString());
            }
        } catch (Exception ignored) {
        }
        return acceptedIds;
    }

    public record Result(int statusCode, String body, Set<String> acceptedIds) {
        public boolean accepted() {
            return statusCode >= 200 && statusCode < 300;
        }

        public boolean unauthorized() {
            return statusCode == 401;
        }
    }
}
