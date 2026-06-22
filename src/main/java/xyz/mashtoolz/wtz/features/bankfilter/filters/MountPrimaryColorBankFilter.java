package xyz.mashtoolz.wtz.features.bankfilter.filters;

import xyz.mashtoolz.wtz.config.WTZConfig;
import xyz.mashtoolz.wtz.features.bankfilter.BankFilter;
import xyz.mashtoolz.wtz.features.bankfilter.BankFilterContext;

public final class MountPrimaryColorBankFilter implements BankFilter {
    @Override
    public String key() {
        return "mc1";
    }

    @Override
    public boolean isEnabled(WTZConfig config) {
        return config.bankFilterMountFiltersEnabled;
    }

    @Override
    public String value(BankFilterContext context) {
        return MountBankFilterData.primaryColor(context);
    }
}
