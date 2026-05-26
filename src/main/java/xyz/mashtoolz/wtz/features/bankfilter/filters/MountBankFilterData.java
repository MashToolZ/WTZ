package xyz.mashtoolz.wtz.features.bankfilter.filters;

import net.minecraft.component.type.LoreComponent;
import xyz.mashtoolz.wtz.features.bankfilter.BankFilterContext;
import xyz.mashtoolz.wtz.features.mount.MountUtils;

final class MountBankFilterData {
    private static final String MOUNT_TYPE_CACHE_KEY = "mount.type";
    private static final String MOUNT_SKIN_CACHE_KEY = "mount.skin";

    private MountBankFilterData() {
    }

    static String type(BankFilterContext context) {
        return context.cached(MOUNT_TYPE_CACHE_KEY, () -> readType(context));
    }

    static String primaryColor(BankFilterContext context) {
        MountSkin skin = skin(context);
        return skin == null ? null : skin.primary();
    }

    static String secondaryColor(BankFilterContext context) {
        MountSkin skin = skin(context);
        return skin == null ? null : skin.secondary();
    }

    private static String readType(BankFilterContext context) {
        String name = context.itemName();
        if (name.contains("Harness")) return "adasaur";
        if (name.contains("Saddle")) return "horse";
        if (name.contains("Reins")) return "wyvern";
        return null;
    }

    private static MountSkin skin(BankFilterContext context) {
        return context.cached(MOUNT_SKIN_CACHE_KEY, () -> readSkin(context));
    }

    private static MountSkin readSkin(BankFilterContext context) {
        LoreComponent lore = context.lore();
        if (lore == null) return null;

        String skin = MountUtils.extractSkin(lore.lines());
        if (skin == null) return null;

        String[] parts = skin.split("-", 2);
        if (parts.length != 2) return null;

        String primary = parts[0].trim();
        String secondary = parts[1].trim();
        if (primary.isEmpty() || secondary.isEmpty()) return null;

        return new MountSkin(primary, secondary);
    }

    private record MountSkin(String primary, String secondary) {
    }
}
