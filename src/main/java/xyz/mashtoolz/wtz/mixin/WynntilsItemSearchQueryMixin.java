package xyz.mashtoolz.wtz.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.mashtoolz.wtz.features.bankfilter.WynntilsBankFilterSupport;

@Mixin(targets = "com.wynntils.services.itemfilter.type.ItemSearchQuery", remap = false)
public abstract class WynntilsItemSearchQueryMixin {
    @Inject(method = "isEmpty", at = @At("RETURN"), cancellable = true, remap = false)
    private void wtz$bankFilterQueryIsNotEmpty(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && WynntilsBankFilterSupport.hasRememberedBankFilters(this)) {
            cir.setReturnValue(false);
        }
    }
}
