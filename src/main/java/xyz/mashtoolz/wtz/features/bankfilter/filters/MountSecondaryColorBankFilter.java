package xyz.mashtoolz.wtz.features.bankfilter.filters;

import xyz.mashtoolz.wtz.config.WTZConfig;
import xyz.mashtoolz.wtz.features.bankfilter.BankFilter;
import xyz.mashtoolz.wtz.features.bankfilter.BankFilterContext;

public final class MountSecondaryColorBankFilter implements BankFilter {
    @Override
    public String key() {
        return "mc2";
    }

    @Override
    public boolean isEnabled(WTZConfig config) {
        return config.bankFilterMountFiltersEnabled;
    }

    @Override
    public String value(BankFilterContext context) {
        return MountBankFilterData.secondaryColor(context);
    }
}
