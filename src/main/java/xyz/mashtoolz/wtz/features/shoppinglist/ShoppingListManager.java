package xyz.mashtoolz.wtz.features.shoppinglist;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.util.ChatHelper;

import java.util.*;

public class ShoppingListManager {

    private static final ShoppingListManager INSTANCE = new ShoppingListManager();

    private final Map<String, ShoppingListData> lists = new LinkedHashMap<>();
    private final ShoppingListStore store = new ShoppingListStore();
    private String activeListId;
    private int nextId = 1;

    private ShoppingListManager() {}

    public static ShoppingListManager getInstance() {
        return INSTANCE;
    }

    public void init() {
        load();
    }

    public void save() {
        store.save(lists, activeListId, nextId);
    }

    public void load() {
        lists.clear();
        ShoppingListStore.State state = store.load();
        lists.putAll(state.lists());
        activeListId = state.activeListId();
        nextId = state.nextId();

        if (lists.isEmpty()) {
            String id = String.valueOf(nextId++);
            lists.put(id, new ShoppingListData(id, "Default"));
            activeListId = id;
            save();
        }

        if (activeListId == null || !lists.containsKey(activeListId)) {
            activeListId = lists.keySet().iterator().next();
        }
    }

    public ShoppingListData createList(String name) {
        String id = String.valueOf(nextId++);
        ShoppingListData list = new ShoppingListData(id, name);
        lists.put(id, list);
        save();
        return list;
    }

    public boolean deleteList(String id) {
        if (lists.size() <= 1) return false;
        if (!lists.containsKey(id)) return false;

        lists.remove(id);
        if (id.equals(activeListId)) {
            activeListId = lists.keySet().iterator().next();
        }
        save();
        return true;
    }

    public void renameList(String id, String name) {
        ShoppingListData list = lists.get(id);
        if (list == null) return;
        list.setName(name);
        save();
    }

    public ShoppingListData getActiveList() {
        return lists.get(activeListId);
    }

    public void setActiveList(String id) {
        if (!lists.containsKey(id)) return;
        activeListId = id;
        save();
    }

    public ShoppingListData getList(String id) {
        return lists.get(id);
    }

    public Collection<ShoppingListData> getAllLists() {
        return lists.values();
    }

    public void addItem(String name) {
        addItem(activeListId, name);
    }

    public void addItem(String listId, String name) {
        ShoppingListData list = lists.get(listId);
        if (list == null) return;
        name = ShoppingListData.cleanName(name);
        if (name.isEmpty()) return;
        list.addItem(name, 1);
        save();
    }

    public void removeItem(String listId, String name) {
        ShoppingListData list = lists.get(listId);
        if (list == null) return;
        list.removeItem(name);
        save();
    }

    public void renameItem(String listId, String oldName, String newName) {
        ShoppingListData list = lists.get(listId);
        if (list == null) return;
        if (list.renameItem(oldName, newName)) {
            save();
        }
    }

    public void clearList(String listId) {
        ShoppingListData list = lists.get(listId);
        if (list == null) return;
        list.clear();
        save();
    }

    public void setQuantity(String listId, String itemName, int qty) {
        ShoppingListData list = lists.get(listId);
        if (list == null) return;
        ShoppingListData.ShoppingItem item = list.getItem(itemName);
        if (item == null) return;
        item.setQuantity(qty);
        save();
    }

    public int getHaveCount(String itemName) {
        ClientPlayerEntity player = WTZClient.player();
        if (player == null) return 0;

        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (ShoppingListData.cleanName(stack.getName().getString()).equals(itemName)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public String exportList(String id) {
        ShoppingListData list = lists.get(id);
        if (list == null) return null;

        return ShoppingListCodec.exportList(list);
    }

    public boolean importList(String encoded, String targetListId, boolean mergeIntoTarget) {
        ShoppingListCodec.ParsedShoppingList parsed = ShoppingListCodec.parse(encoded);
        if (parsed == null || parsed.items().isEmpty()) return false;

        if (!mergeIntoTarget) {
            importAsNewList(parsed);
            return true;
        }

        ShoppingListData target = lists.get(targetListId);
        if (target == null) return false;

        for (Map.Entry<String, Integer> entry : parsed.items().entrySet()) {
            String itemName = entry.getKey();
            int qtyToAdd = entry.getValue();
            ShoppingListData.ShoppingItem existing = target.getItem(itemName);
            if (existing == null) {
                target.addItem(itemName, qtyToAdd);
            } else {
                existing.setQuantity(existing.getQuantity() + qtyToAdd);
            }
        }

        save();
        return true;
    }

    private void importAsNewList(ShoppingListCodec.ParsedShoppingList parsed) {
        ShoppingListData list = createList(parsed.name());
        addParsedItems(list, parsed.items());
        setActiveList(list.getId());
        ShoppingListRenderer.getInstance().switchToList(list.getId());
        save();
    }

    private void addParsedItems(ShoppingListData list, Map<String, Integer> items) {
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            list.addItem(entry.getKey(), entry.getValue());
        }
    }

    public void removeCompletedItems(String listId) {
        ShoppingListData list = lists.get(listId);
        if (list == null) return;

        List<String> completed = new ArrayList<>();
        for (ShoppingListData.ShoppingItem item : list.getItems()) {
            if (getHaveCount(item.getName()) >= item.getQuantity()) {
                completed.add(item.getName());
            }
        }
        for (String name : completed) {
            removeItem(listId, name);
        }
    }

    public void exportListToClipboard(String listId) {
        String result = exportList(listId);
        if (result == null) {
            ChatHelper.sendError("Failed to export list");
            return;
        }

        WTZClient.client().keyboard.setClipboard(result);
        ChatHelper.sendSuccess("Shopping list copied to clipboard!");
    }

    public void importListFromClipboard(String targetListId, boolean mergeIntoCurrent) {
        String clipboard = WTZClient.client().keyboard.getClipboard();
        if (!importList(clipboard, targetListId, mergeIntoCurrent)) {
            ChatHelper.sendError("Invalid shopping list data in clipboard");
            return;
        }

        if (mergeIntoCurrent) {
            ChatHelper.sendSuccess("Shopping list merged into current list!");
        } else {
            ChatHelper.sendSuccess("Shopping list imported as new list!");
        }
    }

}
