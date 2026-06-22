package xyz.mashtoolz.wtz.features.mount.enclosure;

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

public final class BreedingResultSubmitter {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private BreedingResultSubmitter() {
    }

    public static CompletableFuture<Result> submit(List<JsonObject> reports, String token) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(Endpoints.MOUNT_SUBMIT_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(buildBody(reports).toString()))
                .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> new Result(
                        response.statusCode(),
                        response.body(),
                        parseAcceptedIds(response.body())
                ));
    }

    private static JsonObject buildBody(List<JsonObject> reports) {
        JsonArray events = new JsonArray();
        for (JsonObject report : reports) {
            events.add(report.deepCopy());
        }

        JsonObject body = new JsonObject();
        body.addProperty("type", "breed");
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
