package xyz.mashtoolz.wtz.features.mount.helper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.mashtoolz.wtz.client.WTZClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class MountPowerupTextureResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger("WTZ-MountPowerupTextureResolver");
    private static final String EMPTY_TEXTURE_HASH = "5f70bf18a086007016e948b04aed3b82103a36bea41755b6cddfaf10ace3c6ef";
    private static final String MARKER_ALPHA_HASH = "bbe08f623cc996511573a3016b9208a71f02af2553bc3c690a1563298c263e9f";

    private static final Map<Identifier, Optional<JsonObject>> jsonCache = new HashMap<>();
    private static final Map<Identifier, Optional<FlatModel>> modelCache = new HashMap<>();
    private static final Map<Identifier, Optional<TextureAnalysis>> textureCache = new HashMap<>();
    private static final Map<String, ResolvedItem> itemCache = new HashMap<>();

    private MountPowerupTextureResolver() {
    }

    static Optional<String> classify(List<ItemStack> stacks, Set<String> knownHashes) {
        for (ItemStack stack : stacks) {
            ResolvedItem resolved = resolveItem(stack);
            for (Identifier texture : resolved.textures) {
                Optional<TextureAnalysis> analysis = analyzeTexture(texture);
                if (analysis.isEmpty()) continue;

                TextureAnalysis a = analysis.get();
                if (a.rgbaHash.equals(EMPTY_TEXTURE_HASH)) continue;
                if (a.alphaHash.equals(MARKER_ALPHA_HASH)) continue;
                if (knownHashes.contains(a.rgbaHash)) return Optional.of(a.rgbaHash);
            }
        }
        return Optional.empty();
    }

    static void clearCaches() {
        jsonCache.clear();
        modelCache.clear();
        textureCache.clear();
        itemCache.clear();
    }

    private static ResolvedItem resolveItem(ItemStack stack) {
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        List<Float> cmdFloats = customModelDataFloats(stack);
        String cacheKey = itemId + "|" + cmdFloats;
        ResolvedItem cached = itemCache.get(cacheKey);
        if (cached != null) return cached;

        Set<Identifier> textures = new LinkedHashSet<>();
        Identifier itemDefId = itemDefId(parseIdentifier(itemId));
        Optional<JsonObject> itemDef = readJson(itemDefId);
        if (itemDef.isPresent()) {
            JsonElement model = itemDef.get().get("model");
            for (String modelId : resolveItemModelDef(model, cmdFloats)) {
                Optional<FlatModel> flat = loadFlatModel(parseIdentifier(modelId), new LinkedHashSet<>());
                flat.ifPresent(flatModel -> textures.addAll(flatModel.resolvedTextures()));
            }
        }

        ResolvedItem resolved = new ResolvedItem(List.copyOf(textures));
        itemCache.put(cacheKey, resolved);
        return resolved;
    }

    private static List<String> resolveItemModelDef(JsonElement element, List<Float> cmdFloats) {
        if (element == null || !element.isJsonObject()) return List.of();

        JsonObject def = element.getAsJsonObject();
        String type = bare(string(def, "type"));
        if (type.isEmpty()) type = def.has("model") && def.get("model").isJsonPrimitive() ? "model" : "";

        return switch (type) {
            case "model" -> {
                String model = string(def, "model");
                yield model == null ? List.of() : List.of(model);
            }
            case "empty" -> List.of();
            case "composite" -> {
                List<String> result = new ArrayList<>();
                JsonArray models = models(def);
                if (models != null) {
                    for (JsonElement child : models) result.addAll(resolveItemModelDef(child, cmdFloats));
                }
                yield result;
            }
            case "condition" -> {
                JsonElement child = def.get("on_false");
                if (child == null) child = def.get("fallback");
                yield resolveItemModelDef(child, cmdFloats);
            }
            case "select" -> {
                String property = bare(string(def, "property"));
                String value = null;
                if ("custom_model_data".equals(property)) {
                    int index = customModelDataIndex(def);
                    if (index >= 0 && index < cmdFloats.size()) value = trimFloat(cmdFloats.get(index));
                }

                JsonArray cases = cases(def);
                if (value != null && cases != null) {
                    for (JsonElement caseElement : cases) {
                        if (!caseElement.isJsonObject()) continue;
                        JsonObject caseObj = caseElement.getAsJsonObject();
                        if (matchesWhen(caseObj.get("when"), value)) {
                            yield resolveItemModelDef(caseObj.get("model"), cmdFloats);
                        }
                    }
                }
                yield resolveItemModelDef(def.get("fallback"), cmdFloats);
            }
            case "range_dispatch" -> {
                String property = bare(string(def, "property"));
                float value = 0.0f;
                if ("custom_model_data".equals(property)) {
                    int index = customModelDataIndex(def);
                    if (index >= 0 && index < cmdFloats.size()) value = cmdFloats.get(index);
                }
                value *= floatValue(def, "scale", 1.0f);

                JsonObject selected = null;
                JsonArray entries = entries(def);
                if (entries != null) {
                    List<JsonObject> sorted = new ArrayList<>();
                    for (JsonElement entryElement : entries) {
                        if (entryElement.isJsonObject()) sorted.add(entryElement.getAsJsonObject());
                    }
                    sorted.sort(Comparator.comparingDouble(entry -> floatValue(entry, "threshold", 0.0f)));
                    for (JsonObject entry : sorted) {
                        if (value >= floatValue(entry, "threshold", 0.0f)) selected = entry;
                        else break;
                    }
                }
                yield selected == null
                        ? resolveItemModelDef(def.get("fallback"), cmdFloats)
                        : resolveItemModelDef(selected.get("model"), cmdFloats);
            }
            default -> List.of();
        };
    }

    private static boolean matchesWhen(JsonElement whenElement, String value) {
        if (whenElement == null || whenElement.isJsonNull()) return false;
        if (whenElement.isJsonArray()) {
            for (JsonElement child : whenElement.getAsJsonArray()) {
                if (matchesWhen(child, value)) return true;
            }
            return false;
        }
        if (!whenElement.isJsonPrimitive()) return false;
        return bare(whenElement.getAsString()).equals(bare(value));
    }

    private static Optional<FlatModel> loadFlatModel(Identifier modelId, Set<Identifier> seen) {
        Optional<FlatModel> cached = modelCache.get(modelId);
        if (cached != null) return cached;
        if (!seen.add(modelId)) return Optional.empty();

        Optional<JsonObject> json = readJson(modelFileId(modelId));
        if (json.isEmpty()) {
            modelCache.put(modelId, Optional.empty());
            return Optional.empty();
        }

        Map<String, String> textures = new LinkedHashMap<>();
        String parentId = string(json.get(), "parent");
        if (parentId != null && !parentId.startsWith("builtin/")) {
            Optional<FlatModel> parent = loadFlatModel(parseIdentifier(parentId), seen);
            parent.ifPresent(flatModel -> textures.putAll(flatModel.textures));
        }

        JsonObject modelTextures = textures(json.get());
        if (modelTextures != null) {
            for (Map.Entry<String, JsonElement> entry : modelTextures.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) textures.put(entry.getKey(), entry.getValue().getAsString());
            }
        }

        Optional<FlatModel> result = Optional.of(new FlatModel(textures));
        modelCache.put(modelId, result);
        return result;
    }

    private static Optional<TextureAnalysis> analyzeTexture(Identifier textureId) {
        Optional<TextureAnalysis> cached = textureCache.get(textureId);
        if (cached != null) return cached;

        ResourceManager manager = WTZClient.client().getResourceManager();
        Identifier resourceId = textureFileId(textureId);
        Optional<Resource> resource = manager.getResource(resourceId);
        if (resource.isEmpty()) {
            textureCache.put(textureId, Optional.empty());
            return Optional.empty();
        }

        try (InputStream in = resource.get().getInputStream(); NativeImage image = NativeImage.read(in)) {
            MessageDigest rgba = sha256();
            MessageDigest alpha = sha256();

            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getColorArgb(x, y);
                    rgba.update((byte) ((argb >>> 24) & 0xFF));
                    rgba.update((byte) ((argb >>> 16) & 0xFF));
                    rgba.update((byte) ((argb >>> 8) & 0xFF));
                    rgba.update((byte) (argb & 0xFF));
                    alpha.update((byte) ((argb >>> 24) & 0xFF));
                }
            }

            Optional<TextureAnalysis> result = Optional.of(new TextureAnalysis(hex(rgba.digest()), hex(alpha.digest())));
            textureCache.put(textureId, result);
            return result;
        } catch (Exception e) {
            LOGGER.warn("Failed to analyze texture {}", resourceId, e);
            textureCache.put(textureId, Optional.empty());
            return Optional.empty();
        }
    }

    private static Optional<JsonObject> readJson(Identifier resourceId) {
        Optional<JsonObject> cached = jsonCache.get(resourceId);
        if (cached != null) return cached;

        Optional<Resource> resource = WTZClient.client().getResourceManager().getResource(resourceId);
        if (resource.isEmpty()) {
            jsonCache.put(resourceId, Optional.empty());
            return Optional.empty();
        }

        try (InputStream in = resource.get().getInputStream();
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            Optional<JsonObject> result = parsed != null && parsed.isJsonObject()
                    ? Optional.of(parsed.getAsJsonObject())
                    : Optional.empty();
            jsonCache.put(resourceId, result);
            return result;
        } catch (IOException e) {
            LOGGER.warn("Failed to read JSON resource {}", resourceId, e);
            jsonCache.put(resourceId, Optional.empty());
            return Optional.empty();
        }
    }

    private static List<Float> customModelDataFloats(ItemStack stack) {
        CustomModelDataComponent modelData = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (modelData == null || modelData.floats() == null) return List.of();
        return modelData.floats();
    }

    private static Identifier itemDefId(Identifier itemId) {
        return Identifier.of(itemId.getNamespace(), "items/" + itemId.getPath() + ".json");
    }

    private static Identifier modelFileId(Identifier modelId) {
        return Identifier.of(modelId.getNamespace(), "models/" + modelId.getPath() + ".json");
    }

    private static Identifier textureFileId(Identifier textureId) {
        return Identifier.of(textureId.getNamespace(), "textures/" + textureId.getPath() + ".png");
    }

    private static Identifier parseIdentifier(String value) {
        if (value == null || value.isBlank()) return Identifier.of("minecraft", "");
        return value.contains(":") ? Identifier.of(value) : Identifier.of("minecraft", value);
    }

    private static String resolveTextureRef(Map<String, String> textures, String ref) {
        String value = ref;
        Set<String> seen = new LinkedHashSet<>();
        while (value != null && value.startsWith("#")) {
            String key = value.substring(1);
            if (!seen.add(key)) return null;
            value = textures.get(key);
        }
        return value;
    }

    private static String bare(String value) {
        if (value == null) return "";
        int colon = value.indexOf(':');
        return colon >= 0 ? value.substring(colon + 1) : value;
    }

    private static String string(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static JsonObject textures(JsonObject obj) {
        JsonElement element = obj.get("textures");
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray models(JsonObject obj) {
        JsonElement element = obj.get("models");
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static JsonArray cases(JsonObject obj) {
        JsonElement element = obj.get("cases");
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static JsonArray entries(JsonObject obj) {
        JsonElement element = obj.get("entries");
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static int customModelDataIndex(JsonObject obj) {
        JsonElement element = obj.get("index");
        return element != null && element.isJsonPrimitive() ? element.getAsInt() : 0;
    }

    private static float floatValue(JsonObject obj, String key, float fallback) {
        JsonElement element = obj.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsFloat() : fallback;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 0xFF));
        return out.toString();
    }

    private static String trimFloat(float value) {
        if (value == (int) value) return Integer.toString((int) value);
        return Float.toString(value);
    }

    private record ResolvedItem(List<Identifier> textures) {
    }

    private record FlatModel(Map<String, String> textures) {
        private List<Identifier> resolvedTextures() {
            List<Identifier> result = new ArrayList<>();
            Set<Identifier> seen = new LinkedHashSet<>();
            for (String ref : textures.values()) {
                String texture = resolveTextureRef(textures, ref);
                if (texture == null || texture.startsWith("#")) continue;
                Identifier id = parseIdentifier(texture);
                if (seen.add(id)) result.add(id);
            }
            return result;
        }
    }

    private record TextureAnalysis(String rgbaHash, String alphaHash) {
    }
}
