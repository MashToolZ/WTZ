package xyz.mashtoolz.wtz.features.mount.enclosure;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.mashtoolz.wtz.util.WTZPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class BreedingResultQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger("WTZ-BreedingResultQueue");

    private final Object lock = new Object();
    private final List<JsonObject> reports = new ArrayList<>();
    private final Path queueFile = WTZPaths.configFile("pending-breeding-results.json");

    public void load() {
        if (!Files.exists(queueFile)) return;

        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(queueFile, StandardCharsets.UTF_8));
            if (!parsed.isJsonArray()) {
                LOGGER.warn("Ignoring invalid WTZ pending breeding result queue at {}", queueFile);
                return;
            }

            synchronized (lock) {
                reports.clear();
                for (JsonElement element : parsed.getAsJsonArray()) {
                    if (!element.isJsonObject()) continue;
                    JsonObject report = element.getAsJsonObject();
                    if (isValidStoredReport(report)) reports.add(report.deepCopy());
                }
            }

            LOGGER.info("Loaded {} pending breeding result report(s).", size());
        } catch (Exception e) {
            LOGGER.error("Failed to load WTZ pending breeding result queue at {}", queueFile, e);
        }
    }

    public String add(String location, int row, int mountSlot, JsonObject parentA, JsonObject parentB, JsonObject result, String source) {
        JsonObject report = new JsonObject();
        report.addProperty("id", UUID.randomUUID().toString());
        report.addProperty("location", location);
        report.addProperty("row", row);
        report.addProperty("mountSlot", mountSlot);
        report.addProperty("detectedAt", System.currentTimeMillis());
        report.addProperty("source", source);
        report.add("parentA", parentA.deepCopy());
        report.add("parentB", parentB.deepCopy());
        report.add("result", result.deepCopy());

        synchronized (lock) {
            reports.add(report);
            saveLocked();
        }
        return report.get("id").getAsString();
    }

    public boolean isEmpty() {
        synchronized (lock) {
            return reports.isEmpty();
        }
    }

    public List<JsonObject> firstBatch(int maxSize) {
        synchronized (lock) {
            int end = Math.min(maxSize, reports.size());
            List<JsonObject> batch = new ArrayList<>();
            for (JsonObject report : reports.subList(0, end)) {
                batch.add(report.deepCopy());
            }
            return batch;
        }
    }

    public void removeAccepted(Set<String> acceptedIds) {
        if (acceptedIds.isEmpty()) return;

        synchronized (lock) {
            int before = reports.size();
            reports.removeIf(report -> report.has("id") && acceptedIds.contains(report.get("id").getAsString()));
            if (reports.size() != before) saveLocked();
            LOGGER.info("Removed {} accepted breeding result report(s) from queue; {} still pending.",
                    before - reports.size(), reports.size());
        }
    }

    private int size() {
        synchronized (lock) {
            return reports.size();
        }
    }

    private void saveLocked() {
        JsonArray root = new JsonArray();
        for (JsonObject report : reports) {
            root.add(report);
        }

        try {
            Files.createDirectories(queueFile.getParent());
            Files.writeString(queueFile, root.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.error("Failed to save WTZ pending breeding result queue", e);
        }
    }

    private static boolean isValidStoredReport(JsonObject report) {
        return report.has("id")
                && report.has("parentA") && report.get("parentA").isJsonObject()
                && report.has("parentB") && report.get("parentB").isJsonObject()
                && report.has("result") && report.get("result").isJsonObject();
    }
}
