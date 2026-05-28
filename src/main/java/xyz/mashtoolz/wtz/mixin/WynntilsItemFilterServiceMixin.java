package xyz.mashtoolz.wtz.mixin;

import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.mashtoolz.wtz.features.bankfilter.WynntilsBankFilterSupport;

import java.util.Optional;

@Mixin(targets = "com.wynntils.services.itemfilter.ItemFilterService", remap = false)
public abstract class WynntilsItemFilterServiceMixin {
    private static final ThreadLocal<String> WTZ_ORIGINAL_QUERY = new ThreadLocal<>();

    @ModifyVariable(method = "createSearchQuery", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private String wtz$stripBankFilters(String queryString) {
        if (!WynntilsBankFilterSupport.hasBankFilters(queryString)) return queryString;

        WTZ_ORIGINAL_QUERY.set(queryString);
        return WynntilsBankFilterSupport.stripBankFilters(queryString);
    }

    @Inject(method = "createSearchQuery", at = @At("RETURN"), remap = false)
    private void wtz$rememberBankFilters(CallbackInfoReturnable<Object> cir) {
        String originalQuery = WTZ_ORIGINAL_QUERY.get();
        WTZ_ORIGINAL_QUERY.remove();
        WynntilsBankFilterSupport.rememberOriginalQuery(cir.getReturnValue(), originalQuery);
    }

    @Inject(method = "matches", at = @At("RETURN"), cancellable = true, remap = false)
    private void wtz$matchBankFilters(
            @Coerce Object searchQuery, ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;

        Optional<Boolean> bankFilterResult = WynntilsBankFilterSupport.matchesBankFilters(searchQuery, itemStack);
        bankFilterResult.ifPresent(cir::setReturnValue);
    }
}
