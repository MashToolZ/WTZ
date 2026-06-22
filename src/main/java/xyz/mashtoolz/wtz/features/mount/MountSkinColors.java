package xyz.mashtoolz.wtz.features.mount;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class MountSkinColors {

    private static final Logger LOGGER = LoggerFactory.getLogger("WTZ-MountSkinColors");
    private static final Gson GSON = new Gson();

    private static volatile Map<String, Integer> colorsByKey = Map.of();
    private static volatile long lastFetchTime = 0;

    private MountSkinColors() {
    }

    public static void updateFromPayload(String payload) {
        long now = System.currentTimeMillis();
        if (now - lastFetchTime < 2000) return;
        lastFetchTime = now;

        Map<String, Integer> parsed = parseColors(payload);
        if (parsed.isEmpty()) return;

        colorsByKey = Map.copyOf(parsed);
    }

    public static int colorFor(String mount, String role, String name, int fallback) {
        Integer color = colorsByKey.get(key(mount, role, name));
        return color != null ? color : fallback;
    }

    private static Map<String, Integer> parseColors(String payload) {
        try {
            JsonElement root = GSON.fromJson(payload, JsonElement.class);
            if (root == null || root.isJsonNull()) return Map.of();

            Map<String, Integer> parsed = new HashMap<>();
            readColorEntries(root, parsed);
            return parsed;
        } catch (Exception e) {
            LOGGER.debug("Failed to parse mount skin colors", e);
            return Map.of();
        }
    }

    private static void readColorEntries(JsonElement element, Map<String, Integer> out) {
        if (element == null || element.isJsonNull()) return;

        if (element.isJsonArray()) {
            readColorArray(element.getAsJsonArray(), out);
            return;
        }

        if (!element.isJsonObject()) return;

        JsonObject obj = element.getAsJsonObject();
        if (isColorEntry(obj)) {
            readColorEntry(obj, out);
            return;
        }

        JsonElement skins = obj.get("skins");
        if (skins != null) readColorEntries(skins, out);
    }

    private static void readColorArray(JsonArray array, Map<String, Integer> out) {
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject obj = element.getAsJsonObject();
            if (isColorEntry(obj)) readColorEntry(obj, out);
        }
    }

    private static boolean isColorEntry(JsonObject obj) {
        return getString(obj, "name", "colorName", "label") != null
                && getString(obj, "color", "hex", "value") != null;
    }

    private static void readColorEntry(JsonObject obj, Map<String, Integer> out) {
        String mount = getString(obj, "mount", "type");
        String role = getString(obj, "role", "part");
        String name = getString(obj, "name", "colorName", "label");
        String color = getString(obj, "color", "hex", "value");
        if (mount == null || role == null || name == null || color == null) return;

        Integer parsed = parseColor(color);
        if (parsed != null) out.put(key(mount, role, name), parsed);
    }

    private static String getString(JsonObject obj, String... keys) {
        for (String key : keys) {
            JsonElement element = obj.get(key);
            if (element != null && element.isJsonPrimitive()) {
                String value = element.getAsString();
                if (value != null && !value.isBlank()) return value.trim();
            }
        }
        return null;
    }

    private static Integer parseColor(String value) {
        String normalized = value.trim();
        if (normalized.startsWith("#")) normalized = normalized.substring(1);
        if (normalized.length() == 8) normalized = normalized.substring(2);
        if (normalized.length() != 6) return null;

        try {
            return 0xFF000000 | Integer.parseUnsignedInt(normalized, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String key(String mount, String role, String name) {
        return normalize(mount) + "\u0000" + normalize(role) + "\u0000" + normalize(name);
    }
}
