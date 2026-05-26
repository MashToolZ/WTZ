package xyz.mashtoolz.wtz.features.shoppinglist;

import java.util.ArrayList;
import java.util.List;

public class ShoppingListData {

    public static class ShoppingItem {

        private final String name;
        private int quantity;

        public ShoppingItem(String name, int quantity) {
            this.name = name;
            this.quantity = Math.max(1, quantity);
        }

        public String getName() {
            return name;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = Math.max(1, quantity);
        }
    }

    public static String cleanName(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c <= 255) sb.append(c);
        }
        return sb.toString().trim();
    }

    private final String id;
    private String name;
    private final List<ShoppingItem> items = new ArrayList<>();

    public ShoppingListData(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<ShoppingItem> getItems() {
        return items;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean addItem(String name, int qty) {
        if (hasItem(name)) return false;
        items.add(new ShoppingItem(name, qty));
        return true;
    }

    public void removeItem(String name) {
        items.removeIf(item -> item.getName().equals(name));
    }

    public ShoppingItem getItem(String name) {
        for (ShoppingItem item : items) {
            if (item.getName().equals(name)) return item;
        }
        return null;
    }

    public boolean hasItem(String name) {
        return getItem(name) != null;
    }

    public void clear() {
        items.clear();
    }
}
