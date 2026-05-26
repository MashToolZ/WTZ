package xyz.mashtoolz.wtz.features.mount.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.mashtoolz.wtz.util.WTZPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class MountSkinQueue {

    public record Purchase(String id, String itemName, String primary, String secondary, long detectedAt) {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("WTZ-MountSkinQueue");

    private final Object lock = new Object();
    private final List<Purchase> purchases = new ArrayList<>();
    private final Path queueFile = WTZPaths.configFile("pending-skins.json");

    public void load() {
        if (!Files.exists(queueFile)) return;

        try {
            String json = Files.readString(queueFile);
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) {
                LOGGER.warn("Ignoring invalid WTZ pending mount skin queue at {}", queueFile);
                return;
            }

            synchronized (lock) {
                purchases.clear();
                for (JsonElement element : root.getAsJsonArray()) {
                    Purchase purchase = parsePurchase(element);
                    if (purchase != null) purchases.add(purchase);
                }
            }

            LOGGER.info("Loaded {} pending mount skin purchase(s).", size());
        } catch (Exception e) {
            LOGGER.error("Failed to load WTZ pending mount skin queue at {}", queueFile, e);
        }
    }

    public void add(String itemName, String primary, String secondary) {
        Purchase purchase = new Purchase(
                UUID.randomUUID().toString(),
                itemName,
                primary,
                secondary,
                System.currentTimeMillis()
        );

        synchronized (lock) {
            purchases.add(purchase);
            saveLocked();
        }
    }

    public boolean hasPurchases() {
        synchronized (lock) {
            return !purchases.isEmpty();
        }
    }

    public List<Purchase> firstBatch(int maxSize) {
        synchronized (lock) {
            int end = Math.min(maxSize, purchases.size());
            return new ArrayList<>(purchases.subList(0, end));
        }
    }

    public void removeAccepted(Set<String> acceptedIds) {
        if (acceptedIds.isEmpty()) return;

        synchronized (lock) {
            int before = purchases.size();
            purchases.removeIf(purchase -> acceptedIds.contains(purchase.id()));
            if (purchases.size() != before) saveLocked();
            LOGGER.info("Removed {} accepted mount skin purchase(s) from queue; {} still pending.",
                    before - purchases.size(), purchases.size());
        }
    }

    private int size() {
        synchronized (lock) {
            return purchases.size();
        }
    }

    private void saveLocked() {
        JsonArray entries = new JsonArray();
        for (Purchase purchase : purchases) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", purchase.id());
            entry.addProperty("itemName", purchase.itemName());
            entry.addProperty("primary", purchase.primary());
            entry.addProperty("secondary", purchase.secondary());
            entry.addProperty("detectedAt", purchase.detectedAt());
            entries.add(entry);
        }

        try {
            Files.createDirectories(queueFile.getParent());
            Files.writeString(queueFile, entries.toString());
        } catch (Exception e) {
            LOGGER.error("Failed to save WTZ pending mount skin queue", e);
        }
    }

    private static Purchase parsePurchase(JsonElement element) {
        if (!element.isJsonObject()) return null;

        JsonObject obj = element.getAsJsonObject();
        String id = stringValue(obj, "id");
        String itemName = stringValue(obj, "itemName");
        String primary = stringValue(obj, "primary");
        String secondary = stringValue(obj, "secondary");
        long detectedAt = obj.has("detectedAt") ? obj.get("detectedAt").getAsLong() : System.currentTimeMillis();
        if (id == null || itemName == null || primary == null || secondary == null) return null;
        return new Purchase(id, itemName, primary, secondary, detectedAt);
    }

    private static String stringValue(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) return null;
        String value = obj.get(key).getAsString().trim();
        return value.isEmpty() ? null : value;
    }
}
