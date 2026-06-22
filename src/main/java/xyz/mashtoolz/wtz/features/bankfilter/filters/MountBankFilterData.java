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
        return MountUtils.extractMountType(context.itemName());
    }

    private static MountSkin skin(BankFilterContext context) {
        return context.cached(MOUNT_SKIN_CACHE_KEY, () -> readSkin(context));
    }

    private static MountSkin readSkin(BankFilterContext context) {
        if (!MountUtils.isMountSkinItemName(context.itemName())) return null;

        LoreComponent lore = context.lore();
        if (lore == null) return null;

        String skin = MountUtils.extractSkin(lore.lines());
        if (skin == null) return null;

        MountUtils.MountSkinParts parts = MountUtils.parseSkinParts(skin);
        if (parts == null) return null;
        return new MountSkin(parts.primary(), parts.secondary());
    }

    private record MountSkin(String primary, String secondary) {
    }
}
