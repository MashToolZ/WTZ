package xyz.mashtoolz.wtz.features.mount;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
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
import xyz.mashtoolz.wtz.util.ChatHelper;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.net.Endpoints;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class MountManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("WTZ-MountManager");

    private static final int DETECTION_RADIUS = 200;

    private static volatile List<Powerup> powerups = List.of();
    private static volatile List<Powerup> filteredPowerups = List.of();
    private static volatile Map<Integer, PowerupDataEntry> powerupDataMap = Map.of();
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

        Map<String, int[]> mountStats = MountStatsOverlay.parse(MountStatsOverlay.getLastUsedItem());
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

            Map<Integer, PowerupDataEntry> parsed = new HashMap<>();
            for (PowerupEntry entry : entries) {
                int color;
                String colorStr = entry.color;
                if (colorStr.startsWith("#")) {
                    color = Integer.parseUnsignedInt(colorStr.substring(1), 16);
                } else {
                    color = Integer.parseUnsignedInt(colorStr, 10);
                }
                parsed.put(entry.id, new PowerupDataEntry(color & 0xFFFFFF, entry.name));
            }

            powerupDataMap = parsed;
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
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(new PowerupData(floatInt, pos));
        }

        boolean usesJumpHeight = MountStatsOverlay.parse(MountStatsOverlay.getLastUsedItem()).containsKey("Jump Height");

        List<Powerup> result = new ArrayList<>();
        for (List<PowerupData> group : groups.values()) {
            if (group.size() < 2) continue;

            PowerupData typeEntity = group.stream()
                    .min(Comparator.comparingInt(d -> d.floatValue))
                    .orElseThrow();

            PowerupDataEntry data = powerupDataMap.get(typeEntity.floatValue);
            if (data == null) continue;

            String name = data.name;
            if (usesJumpHeight && "Altitude".equals(name)) name = "Jump Height";
            else if (!usesJumpHeight && "Jump Height".equals(name)) name = "Altitude";

            result.add(new Powerup(typeEntity.pos, data.color, name));
        }

        powerups = result;
    }

    public static Map<String, Integer> getStatColors() {
        Map<String, Integer> colors = new HashMap<>();
        for (PowerupDataEntry entry : powerupDataMap.values()) {
            int argb = 0xFF000000 | entry.color;
            colors.putIfAbsent(entry.name, argb);
            if ("Altitude".equals(entry.name)) {
                colors.putIfAbsent("Jump Height", argb);
            } else if ("Jump Height".equals(entry.name)) {
                colors.putIfAbsent("Altitude", argb);
            }
        }
        return colors;
    }

    private static List<PowerupEntry> parseMountEntries(String payload) {
        Gson gson = new Gson();
        JsonElement root = gson.fromJson(payload, JsonElement.class);
        if (root == null || root.isJsonNull()) return List.of();

        if (root.isJsonArray()) {
            return gson.fromJson(root, new TypeToken<List<PowerupEntry>>() {
            }.getType());
        }

        if (!root.isJsonObject()) return List.of();
        JsonObject obj = root.getAsJsonObject();
        JsonArray powerups = obj.has("powerups") && obj.get("powerups").isJsonArray()
                ? obj.getAsJsonArray("powerups")
                : null;

        JsonElement entries = powerups == null ? root : powerups;
        return gson.fromJson(entries, new TypeToken<List<PowerupEntry>>() {
        }.getType());
    }

    private record PowerupData(int floatValue, Vec3d pos) {
    }

    private record PowerupDataEntry(int color, String name) {
    }

    private static class PowerupEntry {
        int id;
        String color;
        String name;
    }
}
