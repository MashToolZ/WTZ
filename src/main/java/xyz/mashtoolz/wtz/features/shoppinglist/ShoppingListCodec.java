package xyz.mashtoolz.wtz.features.shoppinglist;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ShoppingListCodec {

    private static final Logger LOGGER = LoggerFactory.getLogger("WTZ-ShoppingListCodec");
    private static final Gson GSON = new Gson();
    private static final String PREFIX = "WTZ1:";

    private ShoppingListCodec() {
    }

    public static String exportList(ShoppingListData list) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", list.getName());

        JsonArray itemsArr = new JsonArray();
        for (ShoppingListData.ShoppingItem item : list.getItems()) {
            JsonObject itemObj = new JsonObject();
            itemObj.addProperty("n", item.getName());
            itemObj.addProperty("q", item.getQuantity());
            itemsArr.add(itemObj);
        }
        obj.add("items", itemsArr);

        String encoded = Base64.getEncoder().encodeToString(obj.toString().getBytes(StandardCharsets.UTF_8));
        return PREFIX + encoded;
    }

    public static ParsedShoppingList parse(String encoded) {
        if (encoded == null) return null;

        String trimmed = encoded.trim();
        if (!trimmed.startsWith(PREFIX)) return null;

        try {
            byte[] decoded = Base64.getDecoder().decode(trimmed.substring(PREFIX.length()));
            JsonObject obj = GSON.fromJson(new String(decoded, StandardCharsets.UTF_8), JsonObject.class);
            if (obj == null) return null;

            String name = obj.has("name") ? ShoppingListData.cleanName(obj.get("name").getAsString()) : "Imported";
            if (name.isBlank()) name = "Imported";

            Map<String, Integer> items = new LinkedHashMap<>();
            if (obj.has("items") && obj.get("items").isJsonArray()) {
                for (JsonElement el : obj.getAsJsonArray("items")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject itemObj = el.getAsJsonObject();
                    if (!itemObj.has("n") || !itemObj.has("q")) continue;

                    String itemName = ShoppingListData.cleanName(itemObj.get("n").getAsString());
                    if (itemName.isEmpty()) continue;

                    int qty = Math.max(1, itemObj.get("q").getAsInt());
                    items.merge(itemName, qty, Integer::sum);
                }
            }

            return items.isEmpty() ? null : new ParsedShoppingList(name, items);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse shopping list import data", e);
            return null;
        }
    }

    public record ParsedShoppingList(String name, Map<String, Integer> items) {
    }
}
