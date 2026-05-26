package xyz.mashtoolz.wtz.features.bankfilter;

import xyz.mashtoolz.wtz.features.bankfilter.filters.MountPrimaryColorBankFilter;
import xyz.mashtoolz.wtz.features.bankfilter.filters.MountSecondaryColorBankFilter;
import xyz.mashtoolz.wtz.features.bankfilter.filters.MountTypeBankFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BankFilterRegistry {
    private static final List<BankFilter> FILTERS = new ArrayList<>();
    private static boolean defaultsRegistered;

    private BankFilterRegistry() {
    }

    public static synchronized void registerDefaults() {
        if (defaultsRegistered) return;
        register(new MountTypeBankFilter());
        register(new MountPrimaryColorBankFilter());
        register(new MountSecondaryColorBankFilter());
        defaultsRegistered = true;
    }

    public static synchronized void register(BankFilter filter) {
        FILTERS.removeIf(existing -> existing.key().equalsIgnoreCase(filter.key()));
        FILTERS.add(filter);
    }

    public static synchronized BankFilter byKey(String key) {
        registerDefaults();

        String normalized = key.toLowerCase(Locale.ROOT);
        boolean negative = normalized.startsWith("!");
        String positiveKey = negative ? normalized.substring(1) : normalized;

        for (BankFilter filter : FILTERS) {
            if (!filter.key().equalsIgnoreCase(positiveKey)) continue;
            if (negative && !filter.supportsNegative()) return null;
            return filter;
        }
        return null;
    }
}
