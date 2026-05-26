package xyz.mashtoolz.wtz.features.bankfilter.filters;

import xyz.mashtoolz.wtz.config.WTZConfig;
import xyz.mashtoolz.wtz.features.bankfilter.BankFilter;
import xyz.mashtoolz.wtz.features.bankfilter.BankFilterContext;

public final class MountTypeBankFilter implements BankFilter {
    @Override
    public String key() {
        return "mt";
    }

    @Override
    public boolean isEnabled(WTZConfig config) {
        return config.bankFilterMountTypeEnabled;
    }

    @Override
    public String value(BankFilterContext context) {
        return MountBankFilterData.type(context);
    }

    @Override
    public boolean supportsNegative() {
        return false;
    }
}
