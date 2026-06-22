package xyz.mashtoolz.wtz.features.mount.enclosure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.mashtoolz.wtz.util.WTZPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BreedingQueueCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("WTZ-BreedingQueueCache");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final BreedingQueueCache INSTANCE = new BreedingQueueCache();

    private final Path cacheFile = WTZPaths.configFile("pending-breeding-queues.json");
    private final Map<String, JsonObject> entries = new LinkedHashMap<>();

    private BreedingQueueCache() {
        load();
    }

    public static BreedingQueueCache getInstance() {
        return INSTANCE;
    }

    public synchronized void capture(EnclosureState state, boolean closing) {
        if (state.location() == null) return;

        boolean changed = false;
        for (EnclosureState.Row row : state.rows()) {
            String key = state.location() + ":" + row.index();
            JsonObject previous = entries.get(key);
            if (previous != null && previous.has("resultMount")) continue;

            if (!row.isBreedingQueue()) {
                
                
                captureResultMount(key, row, closing);
                continue;
            }

            JsonObject next = createEntry(state.location(), row);
            if (next.equals(previous)) continue;

            entries.put(key, next);
            changed = true;
            LOGGER.info("Cached breeding queue {}: {}", key, GSON.toJson(next));
        }

        if (changed) save();
    }

    private void captureResultMount(String key, EnclosureState.Row row, boolean closing) {
        JsonObject entry = entries.get(key);
        if (entry == null || row.mainMount() == null) return;
        if (entry.has("resultMount")) return;
        if (!row.mainMount().breedingAlert() && !isCloseFallbackResult(entry, row, closing)) return;

        JsonObject next = row.mainMount().toJson();
        String source = row.mainMount().breedingAlert() ? "alert" : "close_fallback";
        entry.add("resultMount", next);
        entry.remove("resultCandidate");
        enqueueReport(entry, row, next, source);
        LOGGER.info("Captured breeding result mount {}: {}", key, GSON.toJson(next));
        save();
    }

    private void enqueueReport(JsonObject entry, EnclosureState.Row row, JsonObject resultMount, String source) {
        if (!entry.has("location") || !entry.has("mainMount") || !entry.has("queue")) return;
        if (!entry.get("mainMount").isJsonObject() || !entry.get("queue").isJsonArray()) return;

        JsonObject parentA = entry.getAsJsonObject("mainMount");
        JsonObject parentB = firstQueuedMount(entry);
        if (parentB == null) return;
        if (isMissingRequiredSkin(parentA) || isMissingRequiredSkin(parentB) || isMissingRequiredSkin(resultMount)) {
            LOGGER.warn("Skipping breeding result report for row {} because a parent/result mount is missing skin data.", row.index());
            return;
        }

        BreedingResultReporter.enqueue(
                entry.get("location").getAsString(),
                row.index(),
                row.mountSlot(),
                parentA,
                parentB,
                resultMount,
                source
        );
    }

    private static JsonObject firstQueuedMount(JsonObject entry) {
        for (JsonElement element : entry.getAsJsonArray("queue")) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            if (!item.has("kind") || !"mount".equals(item.get("kind").getAsString())) continue;
            JsonObject mount = item.deepCopy();
            mount.remove("kind");
            return mount;
        }
        return null;
    }

    private static boolean isMissingRequiredSkin(JsonObject mount) {
        return isBlankText(mount, "skin") || isBlankText(mount, "primarySkin") || isBlankText(mount, "secondarySkin");
    }

    private static boolean isBlankText(JsonObject object, String key) {
        return !object.has(key) || !object.get(key).isJsonPrimitive() || object.get(key).getAsString().trim().isEmpty();
    }

    private static boolean isCloseFallbackResult(JsonObject entry, EnclosureState.Row row, boolean closing) {
        if (!closing || !row.queue().isEmpty()) return false;
        if (!entry.has("mainMount") || !entry.get("mainMount").isJsonObject()) return false;

        JsonObject cachedMain = comparableMount(entry.getAsJsonObject("mainMount"));
        JsonObject currentMain = comparableMount(row.mainMount().toJson());
        return !currentMain.equals(cachedMain);
    }

    private static JsonObject comparableMount(JsonObject mount) {
        JsonObject comparable = mount.deepCopy();
        comparable.remove("status");
        comparable.remove("timer");
        return comparable;
    }

    private JsonObject createEntry(String location, EnclosureState.Row row) {
        JsonObject entry = new JsonObject();
        entry.addProperty("location", location);
        entry.addProperty("row", row.index());
        entry.addProperty("mountSlot", row.mountSlot());
        entry.add("mainMount", row.mainMount().toJson());

        com.google.gson.JsonArray queue = new com.google.gson.JsonArray();
        for (EnclosureState.QueueItem item : row.queue()) queue.add(item.toJson());
        entry.add("queue", queue);
        return entry;
    }

    private void load() {
        if (!Files.exists(cacheFile)) return;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(cacheFile, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) return;
            for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject object = entry.getValue().getAsJsonObject();
                promoteCachedResultCandidate(object);
                entries.put(entry.getKey(), object);
            }
            LOGGER.info("Loaded {} pending breeding queue(s).", entries.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load pending breeding queues from {}", cacheFile, e);
        }
    }

    private void save() {
        JsonObject root = new JsonObject();
        entries.forEach(root::add);
        try {
            Files.createDirectories(cacheFile.getParent());
            Files.writeString(cacheFile, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.error("Failed to save pending breeding queues to {}", cacheFile, e);
        }
    }

    private static void promoteCachedResultCandidate(JsonObject entry) {
        if (entry.has("resultMount") || !entry.has("resultCandidate") || !entry.get("resultCandidate").isJsonObject()) return;

        JsonObject candidate = entry.getAsJsonObject("resultCandidate");
        if (!candidate.has("componentDump")) return;

        String componentDump = candidate.get("componentDump").getAsString();
        if (!componentDump.contains("strings=[alert]")) return;

        entry.add("resultMount", candidate);
        entry.remove("resultCandidate");
    }
}
