package xyz.mashtoolz.wtz.features.bankfilter;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class BankFilterContext {
    private final ItemStack stack;
    private final Map<String, Object> cache = new HashMap<>();

    public BankFilterContext(ItemStack stack) {
        this.stack = stack;
    }

    public ItemStack stack() {
        return stack;
    }

    public String itemName() {
        return stack.getName().getString();
    }

    public LoreComponent lore() {
        return stack.get(DataComponentTypes.LORE);
    }

    @SuppressWarnings("unchecked")
    public <T> T cached(String key, Supplier<T> supplier) {
        if (!cache.containsKey(key)) {
            cache.put(key, supplier.get());
        }
        return (T) cache.get(key);
    }
}
