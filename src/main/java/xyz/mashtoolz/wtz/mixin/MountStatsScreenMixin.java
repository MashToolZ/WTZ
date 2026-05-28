package xyz.mashtoolz.wtz.mixin;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.mashtoolz.wtz.features.mount.MountStatsOverlay;

@Mixin(HandledScreen.class)
public abstract class MountStatsScreenMixin extends Screen {

    protected MountStatsScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void WTZ_mountStatsMouseClicked(Click click, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (MountStatsOverlay.onScreenMouseClicked(click.x(), click.y(), click.button())) {
            cir.setReturnValue(true);
        }
    }

    @SuppressWarnings("unused")
    @Inject(method = "isPointOverSlot", at = @At("HEAD"), cancellable = true)
    private void WTZ_blockSlotHoverUnderMountStats(Slot slot, double pointX, double pointY, CallbackInfoReturnable<Boolean> cir) {
        if (MountStatsOverlay.isMouseOverOverlay(pointX, pointY)) {
            cir.setReturnValue(false);
        }
    }

    @SuppressWarnings("unused")
    @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"), cancellable = true)
    private void WTZ_blockTooltipUnderMountStats(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        if (MountStatsOverlay.isMouseOverOverlay(mouseX, mouseY)) {
            ci.cancel();
        }
    }
}
