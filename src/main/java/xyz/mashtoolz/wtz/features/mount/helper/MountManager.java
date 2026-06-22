package xyz.mashtoolz.wtz.features.mount.helper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.mashtoolz.wtz.features.mount.MountSkinColors;
import xyz.mashtoolz.wtz.features.mount.stats.MountStatsOverlay;
import xyz.mashtoolz.wtz.util.ChatHelper;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.net.Endpoints;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MountManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("WTZ-MountManager");

    private static final int DETECTION_RADIUS = 200;

    private static volatile List<Powerup> powerups = List.of();
    private static volatile List<Powerup> filteredPowerups = List.of();
    private static volatile Map<String, PowerupDataEntry> powerupDataByHash = Map.of();
    private static volatile Map<String, Integer> statColorMap = Map.of();
    private static final Map<Integer, PowerupDataEntry> dynamicPowerupCache = new HashMap<>();
    private static final Set<Integer> pendingDynamicPowerups = new HashSet<>();
    private static volatile long lastFetchTime = 0;
    private static long lastNonMaxedSeenTime = 0;

    public static List<Powerup> getFilteredPowerups() {
        return filteredPowerups;
    }

    public static void filter() {
        List<Powerup> all = powerups;

        if (!WTZClient.CONFIG.mountHelperHideMaxed || all.isEmpty()) {
            for (Powerup p : all) p.setMaxed(false);
            filteredPowerups = all;
            return;
        }

        Map<String, int[]> mountStats = MountStatsOverlay.parse(MountStatsOverlay.getActiveMountItem());
        if (mountStats.isEmpty()) {
            for (Powerup p : all) p.setMaxed(false);
            filteredPowerups = all;
            return;
        }

        List<Powerup> nonMaxed = new ArrayList<>();
        List<Powerup> maxedPowerups = new ArrayList<>();
        List<Powerup> alwaysShow = new ArrayList<>();
        for (Powerup powerup : all) {
            powerup.setMaxed(false);
            if ("Speed Boost".equals(powerup.name()) || "Energy Boost".equals(powerup.name())) {
                alwaysShow.add(powerup);
                continue;
            }
            int[] stat = mountStats.get(powerup.name());
            if (stat == null || stat[0] < stat[1]) {
                nonMaxed.add(powerup);
            } else {
                maxedPowerups.add(powerup);
            }
        }

        long timeoutMs = WTZClient.CONFIG.mountHelperMaxedTimeout * 1000L;
        if (timeoutMs <= 0) {
            List<Powerup> result = new ArrayList<>(nonMaxed);
            result.addAll(alwaysShow);
            for (Powerup m : maxedPowerups) {
                m.setMaxed(true);
                result.add(m);
            }
            filteredPowerups = result;
            return;
        }

        if (!nonMaxed.isEmpty()) {
            lastNonMaxedSeenTime = System.currentTimeMillis();
            nonMaxed.addAll(alwaysShow);
            filteredPowerups = nonMaxed;
        } else {
            if (lastNonMaxedSeenTime == 0) lastNonMaxedSeenTime = System.currentTimeMillis();
            long elapsed = System.currentTimeMillis() - lastNonMaxedSeenTime;
            if (elapsed >= timeoutMs) {
                List<Powerup> result = new ArrayList<>(alwaysShow);
                for (Powerup m : maxedPowerups) {
                    m.setMaxed(true);
                    result.add(m);
                }
                filteredPowerups = result;
            } else {
                filteredPowerups = alwaysShow;
            }
        }
    }

    public static void fetchMountData() {
        long now = System.currentTimeMillis();
        if (now - lastFetchTime < 2000) return;
        lastFetchTime = now;

        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(Endpoints.MOUNT_DATA_URL))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            MountSkinColors.updateFromPayload(response.body());

            List<PowerupEntry> entries = parseMountEntries(response.body());
            List<PowerupEntry> colorEntries = parseMountColorEntries(response.body());

            Map<Integer, PowerupDataEntry> parsed = new HashMap<>();
            Map<String, PowerupDataEntry> parsedByHash = new HashMap<>();
            for (PowerupEntry entry : entries) {
                Integer color = parseColor(entry.color());
                if (color == null || entry.name() == null || entry.name().isBlank()) continue;
                PowerupDataEntry data = new PowerupDataEntry(color & 0xFFFFFF, entry.name());
                parsed.put(entry.id(), data);

                for (String hash : entry.allHashes()) {
                    parsedByHash.put(hash, data);
                }
            }

            Map<String, Integer> parsedStatColors = new HashMap<>();
            for (PowerupEntry entry : colorEntries) {
                Integer color = parseColor(entry.color());
                if (color == null || entry.name() == null || entry.name().isBlank()) continue;
                putStatColor(parsedStatColors, entry.name(), color & 0xFFFFFF);
            }
            if (parsedStatColors.isEmpty()) {
                for (PowerupDataEntry entry : parsed.values()) {
                    putStatColor(parsedStatColors, entry.name, entry.color);
                }
            }

            powerupDataByHash = parsedByHash;
            statColorMap = Map.copyOf(parsedStatColors);
            synchronized (dynamicPowerupCache) {
                dynamicPowerupCache.clear();
                pendingDynamicPowerups.clear();
            }
            MountPowerupTextureResolver.clearCaches();
        } catch (Exception e) {
            LOGGER.warn("Failed to fetch mount data", e);
            WTZClient.client().execute(() ->
                    ChatHelper.sendError("Failed to fetch mount data")
            );
        }
    }

    public static void refresh() {
        ClientPlayerEntity player = WTZClient.player();
        if (player == null) return;

        Box searchBox = player.getBoundingBox().expand(DETECTION_RADIUS);
        Map<String, List<PowerupData>> groups = new HashMap<>();

        for (DisplayEntity.ItemDisplayEntity entity : player.getEntityWorld().getEntitiesByClass(
                DisplayEntity.ItemDisplayEntity.class, searchBox, e -> true)) {

            var stackReference = entity.getStackReference(0);
            if (stackReference == null) continue;

            ItemStack stack = stackReference.get();
            if (stack.isEmpty() || !stack.isOf(Items.OAK_BOAT)) continue;

            CustomModelDataComponent modelData = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
            if (modelData == null) continue;

            Float floatValue = modelData.getFloat(0);
            if (floatValue == null) continue;

            int floatInt = (int) floatValue.floatValue();
            Vec3d pos = entity.getEntityPos();
            String key = String.format("%.2f,%.2f,%.2f", pos.x, pos.y, pos.z);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(new PowerupData(floatInt, pos, stack.copy()));
        }

        boolean usesJumpHeight = MountStatsOverlay.parse(MountStatsOverlay.getActiveMountItem()).containsKey("Jump Height");

        List<Powerup> result = new ArrayList<>();
        for (List<PowerupData> group : groups.values()) {
            if (group.size() < 2) continue;

            PowerupData typeEntity = group.stream()
                    .min(Comparator.comparingInt(d -> d.floatValue))
                    .orElseThrow();

            PowerupDataEntry data = getDynamicPowerupData(typeEntity.floatValue);
            if (data == null) {
                scheduleDynamicClassification(typeEntity.floatValue, group);
            }
            if (data == null) continue;

            String name = data.name;
            if (usesJumpHeight && "Altitude".equals(name)) name = "Jump Height";
            else if (!usesJumpHeight && "Jump Height".equals(name)) name = "Altitude";

            result.add(new Powerup(typeEntity.pos, data.color, name));
        }

        powerups = result;
    }

    private static PowerupDataEntry getDynamicPowerupData(int currentId) {
        synchronized (dynamicPowerupCache) {
            return dynamicPowerupCache.get(currentId);
        }
    }

    private static void scheduleDynamicClassification(int currentId, List<PowerupData> group) {
        synchronized (dynamicPowerupCache) {
            if (dynamicPowerupCache.containsKey(currentId) || !pendingDynamicPowerups.add(currentId)) return;
        }

        List<ItemStack> stacks = group.stream().map(PowerupData::stack).toList();
        Set<String> knownHashes = knownPowerupHashes();
        CompletableFuture.runAsync(() -> {
            PowerupDataEntry data = MountPowerupTextureResolver.classify(stacks, knownHashes)
                    .map(MountManager::powerupDataForHash)
                    .orElse(null);

            synchronized (dynamicPowerupCache) {
                if (data != null) dynamicPowerupCache.put(currentId, data);
                pendingDynamicPowerups.remove(currentId);
            }
        });
    }

    private static Set<String> knownPowerupHashes() {
        return new HashSet<>(powerupDataByHash.keySet());
    }

    private static PowerupDataEntry powerupDataForHash(String hash) {
        return powerupDataByHash.get(hash);
    }

    public static Map<String, Integer> getStatColors() {
        return statColorMap;
    }

    private static List<PowerupEntry> parseMountEntries(String payload) {
        Gson gson = new Gson();
        JsonElement root = gson.fromJson(payload, JsonElement.class);
        if (root == null || root.isJsonNull()) return List.of();

        if (root.isJsonArray()) {
            return parsePowerupEntries(root.getAsJsonArray());
        }

        if (!root.isJsonObject()) return List.of();
        JsonObject obj = root.getAsJsonObject();
        JsonArray powerups = obj.has("powerups") && obj.get("powerups").isJsonArray()
                ? obj.getAsJsonArray("powerups")
                : null;

        JsonElement entries = powerups == null ? root : powerups;
        return entries.isJsonArray() ? parsePowerupEntries(entries.getAsJsonArray()) : List.of();
    }

    private static List<PowerupEntry> parseMountColorEntries(String payload) {
        Gson gson = new Gson();
        JsonElement root = gson.fromJson(payload, JsonElement.class);
        if (root == null || root.isJsonNull()) return List.of();

        if (root.isJsonArray()) {
            return parsePowerupEntries(root.getAsJsonArray());
        }

        if (!root.isJsonObject()) return List.of();
        JsonObject obj = root.getAsJsonObject();
        JsonArray colors = obj.has("colors") && obj.get("colors").isJsonArray()
                ? obj.getAsJsonArray("colors")
                : null;
        JsonArray powerups = obj.has("powerups") && obj.get("powerups").isJsonArray()
                ? obj.getAsJsonArray("powerups")
                : null;

        JsonElement entries = colors != null ? colors : powerups;
        if (entries == null) return List.of();
        return entries.isJsonArray() ? parsePowerupEntries(entries.getAsJsonArray()) : List.of();
    }

    private static List<PowerupEntry> parsePowerupEntries(JsonArray entries) {
        List<PowerupEntry> result = new ArrayList<>();
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            result.add(new PowerupEntry(
                    idValue(object),
                    stringValue(object, "color"),
                    stringValue(object, "name"),
                    stringValue(object, "hash"),
                    stringValue(object, "rgbaHash"),
                    stringValue(object, "textureHash"),
                    hashesValue(object)
            ));
        }
        return result;
    }

    private static void putStatColor(Map<String, Integer> colors, String name, int rgb) {
        int argb = 0xFF000000 | (rgb & 0xFFFFFF);
        colors.putIfAbsent(name, argb);
        if ("Altitude".equals(name)) {
            colors.putIfAbsent("Jump Height", argb);
        } else if ("Jump Height".equals(name)) {
            colors.putIfAbsent("Altitude", argb);
        }
    }

    private static Integer parseColor(String color) {
        if (color == null || color.isBlank()) return null;
        String normalized = color.trim();
        try {
            if (normalized.startsWith("#")) {
                return Integer.parseUnsignedInt(normalized.substring(1), 16);
            }
            return Integer.parseUnsignedInt(normalized, 10);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record PowerupData(int floatValue, Vec3d pos, ItemStack stack) {
    }

    private record PowerupDataEntry(int color, String name) {
    }

    private record PowerupEntry(
            int id,
            String color,
            String name,
            String hash,
            String rgbaHash,
            String textureHash,
            List<String> hashes
    ) {
        List<String> allHashes() {
            List<String> out = new ArrayList<>();
            addHash(out, hash);
            addHash(out, rgbaHash);
            addHash(out, textureHash);
            if (hashes != null) {
                for (String value : hashes) addHash(out, value);
            }
            return out;
        }

        private static void addHash(List<String> out, String value) {
            if (value == null || value.isBlank()) return;
            out.add(value.trim());
        }
    }

    private static String stringValue(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static int idValue(JsonObject object) {
        JsonElement element = object.get("id");
        return element != null && element.isJsonPrimitive() ? element.getAsInt() : 0;
    }

    private static List<String> hashesValue(JsonObject object) {
        JsonElement element = object.get("hashes");
        if (element == null || !element.isJsonArray()) return List.of();

        List<String> result = new ArrayList<>();
        for (JsonElement child : element.getAsJsonArray()) {
            if (child.isJsonPrimitive()) result.add(child.getAsString());
        }
        return result;
    }
}
