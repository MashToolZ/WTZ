package xyz.mashtoolz.wtz.mixin;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.mashtoolz.wtz.features.mount.MountStatsOverlay;

@Mixin(ChatScreen.class)
public abstract class MountStatsChatScreenMixin extends Screen {

    protected MountStatsChatScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void WTZ_mountStatsMouseClickedOnChat(Click click, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (MountStatsOverlay.onScreenMouseClicked(click.x(), click.y(), click.button())) {
            cir.setReturnValue(true);
        }
    }
}
