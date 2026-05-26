package xyz.mashtoolz.wtz.features.bankfilter;

import xyz.mashtoolz.wtz.config.WTZConfig;

public interface BankFilter {
    String key();

    boolean isEnabled(WTZConfig config);

    String value(BankFilterContext context);

    default boolean supportsNegative() {
        return true;
    }
}
