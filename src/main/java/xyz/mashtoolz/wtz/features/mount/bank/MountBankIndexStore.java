package xyz.mashtoolz.wtz.features.mount.bank;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.mashtoolz.wtz.features.mount.MountUtils;
import xyz.mashtoolz.wtz.util.WTZPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MountBankIndexStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("WTZ-MountBankIndexStore");
    private static final Gson GSON = new Gson();
    private static final Path INDEX_FILE = WTZPaths.configFile("mount-bank-index.json");

    private MountBankIndexStore() {
    }

    public static List<MountBankIndexEntry> load() {
        if (!Files.exists(INDEX_FILE)) return List.of();

        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(INDEX_FILE, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) return List.of();

            JsonObject root = parsed.getAsJsonObject();
            if (!root.has("entries") || !root.get("entries").isJsonArray()) return List.of();

            List<MountBankIndexEntry> entries = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("entries")) {
                if (!element.isJsonObject()) continue;
                MountBankIndexEntry entry = readEntry(element.getAsJsonObject());
                if (entry != null) entries.add(entry);
            }
            return List.copyOf(entries);
        } catch (Exception e) {
            LOGGER.warn("Failed to load mount bank index from {}", INDEX_FILE, e);
            return List.of();
        }
    }

    public static void save(List<MountBankIndexEntry> entries) {
        JsonObject root = toJson(entries);

        try {
            Files.createDirectories(INDEX_FILE.getParent());
            Files.writeString(INDEX_FILE, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.warn("Failed to save mount bank index to {}", INDEX_FILE, e);
        }
    }

    public static JsonObject toJson(List<MountBankIndexEntry> entries) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("indexedAt", System.currentTimeMillis());
        root.addProperty("count", entries.size());

        JsonArray array = new JsonArray();
        for (MountBankIndexEntry entry : entries) {
            array.add(writeEntry(entry));
        }
        root.add("entries", array);
        return root;
    }

    private static JsonObject writeEntry(MountBankIndexEntry entry) {
        JsonObject object = new JsonObject();
        object.addProperty("page", entry.page());
        object.addProperty("slot", entry.slot());
        object.addProperty("itemName", entry.itemName());
        object.addProperty("mountType", entry.mountType());
        object.addProperty("skin", entry.skin());
        object.addProperty("primarySkin", entry.primarySkin());
        object.addProperty("secondarySkin", entry.secondarySkin());
        object.addProperty("potentialText", entry.potentialText());
        object.addProperty("potential", entry.potential());
        object.addProperty("totalEnergy", entry.totalEnergy());
        object.addProperty("maxEnergy", entry.maxEnergy());

        JsonObject stats = new JsonObject();
        entry.stats().forEach((name, stat) -> {
            JsonObject statObject = new JsonObject();
            statObject.addProperty("level", stat.level());
            statObject.addProperty("limit", stat.limit());
            statObject.addProperty("max", stat.max());
            stats.add(name, statObject);
        });
        object.add("stats", stats);
        return object;
    }

    private static MountBankIndexEntry readEntry(JsonObject object) {
        if (!object.has("stats") || !object.get("stats").isJsonObject()) return null;

        Map<String, MountBankStat> stats = new LinkedHashMap<>();
        JsonObject statObject = object.getAsJsonObject("stats");
        for (String statName : MountUtils.STAT_ORDER) {
            if (!statObject.has(statName) || !statObject.get(statName).isJsonObject()) continue;
            JsonObject values = statObject.getAsJsonObject(statName);
            stats.put(statName, new MountBankStat(
                    intValue(values, "level", 0),
                    intValue(values, "limit", 0),
                    intValue(values, "max", -1)
            ));
        }

        return new MountBankIndexEntry(
                intValue(object, "page", 1),
                intValue(object, "slot", -1),
                stringValue(object, "itemName"),
                stringValue(object, "mountType"),
                stringValue(object, "skin"),
                stringValue(object, "primarySkin"),
                stringValue(object, "secondarySkin"),
                stringValue(object, "potentialText"),
                potentialValue(object),
                intValue(object, "totalEnergy", 0),
                intValue(object, "maxEnergy", 0),
                Collections.unmodifiableMap(new LinkedHashMap<>(stats))
        );
    }

    private static String stringValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) return null;
        try {
            return value.getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) return fallback;
        try {
            return value.getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double potentialValue(JsonObject object) {
        JsonElement value = object.get("potential");
        if (value == null || value.isJsonNull()) return 0.0;
        try {
            return value.getAsDouble();
        } catch (Exception ignored) {
            return 0.0;
        }
    }
}
