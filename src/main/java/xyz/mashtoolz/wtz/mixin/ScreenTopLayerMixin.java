package xyz.mashtoolz.wtz.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.mashtoolz.wtz.features.mount.bank.MountBankIndexPanel;
import xyz.mashtoolz.wtz.features.shoppinglist.ShoppingListRenderer;

@Mixin(Screen.class)
public class ScreenTopLayerMixin {

    @Inject(method = "renderWithTooltip", at = @At("TAIL"))
    private void WTZ_afterRenderWithTooltip(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (!(screen instanceof HandledScreen<?>) && !(screen instanceof ChatScreen)) return;

        context.createNewRootLayer();
        if (screen instanceof HandledScreen<?> handledScreen) {
            ShoppingListRenderer.getInstance().autoOpenForScreen(handledScreen);
        }
        ShoppingListRenderer.getInstance().render(context, mouseX, mouseY);
        if (screen instanceof HandledScreen<?> handledScreen) {
            MountBankIndexPanel.render(context, handledScreen, mouseX, mouseY);
        }
    }
}
