package xyz.mashtoolz.wtz.features.shoppinglist;

import com.google.gson.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.mashtoolz.wtz.net.Endpoints;
import xyz.mashtoolz.wtz.util.WTZPaths;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class ShoppingListCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("WTZ-ShoppingListCache");
    private static final ShoppingListCache INSTANCE = new ShoppingListCache();
    private static final Gson GSON = new Gson();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private volatile List<CachedItem> items = List.of();
    private volatile Map<String, CachedItem> itemsByName = Map.of();
    private volatile boolean fetchStarted = false;

    private ShoppingListCache() {
    }

    public static ShoppingListCache getInstance() {
        return INSTANCE;
    }

    public void init() {
        loadFromDisk();
        refreshAsync();
    }

    public CachedItem getByName(String name) {
        return itemsByName.get(normalize(name));
    }

    public ItemStack getIconStack(String name) {
        CachedItem item = getByName(name);
        if (item == null) return ItemStack.EMPTY;
        return item.iconStack();
    }

    public List<CachedItem> search(String query) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) return List.of();

        return items.stream()
                .map(item -> new ScoredItem(item, score(item.normalizedName(), normalizedQuery)))
                .filter(entry -> entry.score() < Integer.MAX_VALUE)
                .sorted(Comparator
                        .comparingInt(ScoredItem::score)
                        .thenComparing(entry -> entry.item().name()))
                .map(ScoredItem::item)
                .toList();
    }

    private void refreshAsync() {
        if (fetchStarted) return;
        fetchStarted = true;
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject manifest = fetchJson(Endpoints.SHOPPING_LIST_CACHE_MANIFEST_URL);
                String fileName = stringValue(manifest, "fileName");
                if (fileName == null || !fileName.matches("shopping-list-cache-[a-f0-9]+\\.json")) return;

                String base = Endpoints.SHOPPING_LIST_CACHE_MANIFEST_URL.replace("shopping-list-cache-manifest.json", "");
                JsonObject payload = fetchJson(base + fileName);
                applyPayload(payload);
                savePayload(payload);
            } catch (Exception e) {
                LOGGER.warn("Failed to refresh shopping list cache", e);
            }
        });
    }

    private JsonObject fetchJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        JsonElement parsed = JsonParser.parseString(response.body());
        if (!parsed.isJsonObject()) throw new IOException("Invalid JSON object from " + url);
        return parsed.getAsJsonObject();
    }

    private void loadFromDisk() {
        Path path = cachePath();
        if (!Files.exists(path)) return;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            if (parsed.isJsonObject()) {
                applyPayload(parsed.getAsJsonObject());
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load cached shopping list data", e);
        }
    }

    private void savePayload(JsonObject payload) {
        try {
            Files.createDirectories(WTZPaths.configDir());
            Files.writeString(cachePath(), GSON.toJson(payload), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Failed to save shopping list cache", e);
        }
    }

    private Path cachePath() {
        return WTZPaths.configFile("shopping-list-cache.json");
    }

    private void applyPayload(JsonObject payload) {
        if (!payload.has("items") || !payload.get("items").isJsonArray()) return;

        JsonArray array = payload.getAsJsonArray("items");
        List<CachedItem> nextItems = new ArrayList<>();
        Map<String, CachedItem> nextByName = new HashMap<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            String name = stringValue(object, "n");
            if (name == null || name.isBlank()) continue;
            String iconId = stringValue(object, "i");
            Float customModelData = customModelDataValue(object);
            CachedItem item = new CachedItem(ShoppingListData.cleanName(name), iconId, customModelData);
            String key = item.normalizedName();
            if (key.isEmpty() || nextByName.containsKey(key)) continue;
            nextItems.add(item);
            nextByName.put(key, item);
        }
        nextItems.sort(Comparator.comparing(CachedItem::name));
        items = List.copyOf(nextItems);
        itemsByName = Map.copyOf(nextByName);
    }

    private static String stringValue(JsonObject object, String key) {
        if (!object.has(key)) return null;
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) return null;
        try {
            return value.getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Float customModelDataValue(JsonObject object) {
        if (!object.has("c")) return null;
        JsonElement value = object.get("c");
        if (value == null || value.isJsonNull()) return null;
        try {
            return value.getAsFloat();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int score(String name, String query) {
        if (name.equals(query)) return 0;
        if (name.startsWith(query)) return 100 + name.length() - query.length();
        int wordStart = name.indexOf(" " + query);
        if (wordStart >= 0) return 200 + wordStart;
        int contains = name.indexOf(query);
        if (contains >= 0) return 300 + contains;

        int subsequence = subsequenceScore(name, query);
        if (subsequence >= 0) return 500 + subsequence;
        return Integer.MAX_VALUE;
    }

    private static int subsequenceScore(String name, String query) {
        int nameIndex = 0;
        int skipped = 0;
        for (int queryIndex = 0; queryIndex < query.length(); queryIndex++) {
            char c = query.charAt(queryIndex);
            int found = name.indexOf(c, nameIndex);
            if (found < 0) return -1;
            skipped += found - nameIndex;
            nameIndex = found + 1;
        }
        return skipped + name.length() - query.length();
    }

    private static String normalize(String value) {
        return ShoppingListData.cleanName(value).toLowerCase(Locale.ROOT).trim();
    }

    public record CachedItem(String name, String iconId, Float customModelData) {
        public String normalizedName() {
            return normalize(name);
        }

        public ItemStack iconStack() {
            if (iconId == null || iconId.isBlank()) return ItemStack.EMPTY;
            try {
                Identifier id = Identifier.of(iconId);
                Item item = Registries.ITEM.getOptionalValue(id).orElse(Items.AIR);
                if (item == Items.AIR) return ItemStack.EMPTY;
                ItemStack stack = new ItemStack(item);
                if (customModelData != null) {
                    stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(
                            List.of(customModelData),
                            List.of(),
                            List.of(),
                            List.of()
                    ));
                }
                return stack;
            } catch (Exception ignored) {
                return ItemStack.EMPTY;
            }
        }
    }

    private record ScoredItem(CachedItem item, int score) {
    }
}
