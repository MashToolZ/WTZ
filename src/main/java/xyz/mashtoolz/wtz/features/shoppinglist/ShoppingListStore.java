package xyz.mashtoolz.wtz.features.shoppinglist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.mashtoolz.wtz.util.WTZPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ShoppingListStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("WTZ-ShoppingListStore");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path saveFile = WTZPaths.configFile("shopping-lists.json");

    public State load() {
        if (!Files.exists(saveFile)) return State.empty();

        try (Reader reader = Files.newBufferedReader(saveFile, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return State.empty();

            String activeListId = root.has("activeListId") ? root.get("activeListId").getAsString() : null;
            int nextId = root.has("nextId") ? root.get("nextId").getAsInt() : 1;
            Map<String, ShoppingListData> lists = new LinkedHashMap<>();

            if (root.has("lists") && root.get("lists").isJsonObject()) {
                JsonObject listsObj = root.getAsJsonObject("lists");
                for (Map.Entry<String, JsonElement> entry : listsObj.entrySet()) {
                    ShoppingListData list = parseList(entry.getKey(), entry.getValue());
                    if (list != null) lists.put(entry.getKey(), list);
                }
            }

            return new State(lists, activeListId, nextId);
        } catch (Exception e) {
            LOGGER.warn("Failed to load shopping lists from {}", saveFile, e);
            return State.empty();
        }
    }

    public void save(Map<String, ShoppingListData> lists, String activeListId, int nextId) {
        JsonObject root = new JsonObject();
        root.addProperty("activeListId", activeListId);
        root.addProperty("nextId", nextId);

        JsonObject listsObj = new JsonObject();
        for (Map.Entry<String, ShoppingListData> entry : lists.entrySet()) {
            listsObj.add(entry.getKey(), writeList(entry.getValue()));
        }
        root.add("lists", listsObj);

        try {
            Files.createDirectories(saveFile.getParent());
        } catch (IOException e) {
            LOGGER.warn("Failed to create shopping list config directory", e);
            return;
        }

        Path tmpFile = saveFile.resolveSibling(saveFile.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmpFile, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            LOGGER.warn("Failed to write shopping list temp file {}", tmpFile, e);
            return;
        }

        try {
            Files.move(tmpFile, saveFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailed) {
            try {
                Files.move(tmpFile, saveFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.warn("Failed to save shopping lists to {}", saveFile, e);
            }
        }
    }

    private static ShoppingListData parseList(String id, JsonElement element) {
        if (!element.isJsonObject()) return null;

        JsonObject listObj = element.getAsJsonObject();
        if (!listObj.has("name")) return null;

        ShoppingListData list = new ShoppingListData(id, listObj.get("name").getAsString());
        if (listObj.has("items") && listObj.get("items").isJsonArray()) {
            for (JsonElement itemEl : listObj.getAsJsonArray("items")) {
                if (!itemEl.isJsonObject()) continue;
                JsonObject itemObj = itemEl.getAsJsonObject();
                if (!itemObj.has("name") || !itemObj.has("qty")) continue;
                list.addItem(itemObj.get("name").getAsString(), itemObj.get("qty").getAsInt());
            }
        }
        return list;
    }

    private static JsonObject writeList(ShoppingListData list) {
        JsonObject listObj = new JsonObject();
        listObj.addProperty("name", list.getName());

        JsonArray itemsArr = new JsonArray();
        for (ShoppingListData.ShoppingItem item : list.getItems()) {
            JsonObject itemObj = new JsonObject();
            itemObj.addProperty("name", item.getName());
            itemObj.addProperty("qty", item.getQuantity());
            itemsArr.add(itemObj);
        }
        listObj.add("items", itemsArr);
        return listObj;
    }

    public record State(Map<String, ShoppingListData> lists, String activeListId, int nextId) {
        static State empty() {
            return new State(new LinkedHashMap<>(), null, 1);
        }
    }
}
