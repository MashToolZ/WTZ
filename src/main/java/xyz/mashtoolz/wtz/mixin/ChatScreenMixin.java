package xyz.mashtoolz.wtz.mixin;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.mashtoolz.wtz.client.WTZKeybinds;
import xyz.mashtoolz.wtz.features.shoppinglist.ShoppingListRenderer;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {

    protected ChatScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void WTZ_afterRender(DrawContext context, int mouseX, int mouseY, float deltaTick, CallbackInfo ci) {
        ShoppingListRenderer.getInstance().render(context, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void WTZ_onMouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (ShoppingListRenderer.getInstance().onMouseClicked(click.x(), click.y(), click.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void WTZ_onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        if (ShoppingListRenderer.getInstance().onMouseScrolled(mouseX, mouseY, verticalAmount)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void WTZ_onKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (WTZKeybinds.TOGGLE_SHOPPING_LIST.matchesKey(input)) {
            ShoppingListRenderer.getInstance().toggleVisibility();
            cir.setReturnValue(true);
        }
        if (ShoppingListRenderer.getInstance().onKeyPressed(input.key())) {
            cir.setReturnValue(true);
        }
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (ShoppingListRenderer.getInstance().onCharTyped(input.codepoint())) {
            return true;
        }
        return super.charTyped(input);
    }
}
