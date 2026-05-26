package xyz.mashtoolz.wtz.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class WTZMixinPlugin implements IMixinConfigPlugin {
    private static final String WYNNTILS_ITEM_FILTER_MIXIN =
            "xyz.mashtoolz.wtz.mixin.WynntilsItemFilterServiceMixin";
    private static final String WYNNTILS_ITEM_SEARCH_QUERY_MIXIN =
            "xyz.mashtoolz.wtz.mixin.WynntilsItemSearchQueryMixin";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.equals(WYNNTILS_ITEM_FILTER_MIXIN)
                || mixinClassName.equals(WYNNTILS_ITEM_SEARCH_QUERY_MIXIN)) {
            return FabricLoader.getInstance().isModLoaded("wynntils");
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
