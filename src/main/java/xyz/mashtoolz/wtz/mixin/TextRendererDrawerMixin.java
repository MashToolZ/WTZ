package xyz.mashtoolz.wtz.mixin;

import net.minecraft.client.font.BakedGlyph;
import net.minecraft.text.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.mashtoolz.wtz.features.mount.stats.MountStatsUpdater;

@Mixin(targets = "net.minecraft.client.font.TextRenderer$Drawer")
public abstract class TextRendererDrawerMixin {

    @Shadow
    float x;

    @Shadow
    float y;

    @Shadow
    private void updateBackgroundBounds(float x, float y, float advance) {
    }

    @Inject(method = "accept(ILnet/minecraft/text/Style;I)Z", at = @At("HEAD"), cancellable = true)
    private void WTZ_skipMountEnergyGlyph(int index, Style style, int codepoint, CallbackInfoReturnable<Boolean> cir) {
        if (!MountStatsUpdater.shouldSkipMountEnergyGlyph(style, codepoint)) return;

        BakedGlyph glyph = ((TextRendererAccessor) ((TextRendererDrawerAccessor) this).wtz$getTextRenderer()).wtz$getGlyph(codepoint, style);
        float advance = glyph.getMetrics().getAdvance(style.isBold());
        updateBackgroundBounds(x, y, advance);
        x += advance;
        cir.setReturnValue(true);
    }
}
