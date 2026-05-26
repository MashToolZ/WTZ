package xyz.mashtoolz.wtz.features.bankfilter;

import net.minecraft.item.ItemStack;
import xyz.mashtoolz.wtz.client.WTZClient;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.WeakHashMap;

public final class WynntilsBankFilterSupport {
    private static final WeakHashMap<Object, String> ORIGINAL_QUERIES = new WeakHashMap<>();

    private WynntilsBankFilterSupport() {
    }

    public static Optional<Boolean> matchesBankFilters(Object searchQuery, ItemStack itemStack) {
        if (!WTZClient.CONFIG.bankFiltersEnabled) return Optional.empty();

        String query = queryString(searchQuery);
        if (query == null || query.isBlank()) return Optional.empty();

        BankFilterQuery filters = BankFilterQuery.parse(query);
        if (filters.isEmpty()) return Optional.empty();
        if (itemStack == null || itemStack.isEmpty()) return Optional.of(false);

        return Optional.of(filters.matches(new BankFilterContext(itemStack), WTZClient.CONFIG));
    }

    public static boolean hasBankFilters(String query) {
        return WTZClient.CONFIG.bankFiltersEnabled && BankFilterQuery.hasFilters(query, WTZClient.CONFIG);
    }

    public static String stripBankFilters(String query) {
        return BankFilterQuery.stripFilters(query, WTZClient.CONFIG);
    }

    public static void rememberOriginalQuery(Object searchQuery, String originalQuery) {
        if (searchQuery == null || originalQuery == null || !hasBankFilters(originalQuery)) return;

        synchronized (ORIGINAL_QUERIES) {
            ORIGINAL_QUERIES.put(searchQuery, originalQuery);
        }
    }

    public static boolean hasRememberedBankFilters(Object searchQuery) {
        synchronized (ORIGINAL_QUERIES) {
            return ORIGINAL_QUERIES.containsKey(searchQuery);
        }
    }

    private static String queryString(Object searchQuery) {
        synchronized (ORIGINAL_QUERIES) {
            String originalQuery = ORIGINAL_QUERIES.get(searchQuery);
            if (originalQuery != null) return originalQuery;
        }

        try {
            Method method = searchQuery.getClass().getMethod("queryString");
            Object value = method.invoke(searchQuery);
            return value instanceof String string ? string : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
