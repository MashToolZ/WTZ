package xyz.mashtoolz.wtz.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.mashtoolz.wtz.features.mount.MountItemOverlay;
import xyz.mashtoolz.wtz.features.mount.MountStatsUpdater;
import xyz.mashtoolz.wtz.features.qol.QualityOfLife;
import xyz.mashtoolz.wtz.features.shoppinglist.ShoppingListRenderer;

@Mixin(InGameHud.class)
public class InGameHudMixin {

    @SuppressWarnings("unused")
    @Inject(method = "renderHotbarItem", at = @At("TAIL"))
    private void WTZ_afterRenderHotbarItem(DrawContext context, int x, int y, RenderTickCounter tickCounter, PlayerEntity player, ItemStack stack, int seed, CallbackInfo ci) {
        MountItemOverlay.renderHotbarItemOverlay(context, x, y, stack);
    }

    @Inject(method = "setOverlayMessage", at = @At("HEAD"))
    private void WTZ_onSetOverlayMessage(Text message, boolean tinted, CallbackInfo ci) {
        MountStatsUpdater.onActionBar(message);
    }

    @Inject(method = "renderOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void WTZ_beforeRenderOverlayMessage(CallbackInfo ci) {
        Screen currentScreen = MinecraftClient.getInstance().currentScreen;
        if (currentScreen instanceof ChatScreen && QualityOfLife.shouldHideActionbarOnChat()) {
            ci.cancel();
            return;
        }

        if (currentScreen == null && QualityOfLife.shouldShowActionbarAboveChat() && !QualityOfLife.isRenderingActionbarAboveChat()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/InGameHud;renderChat(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void WTZ_afterRenderChat(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (MinecraftClient.getInstance().currentScreen != null) return;
        if (!QualityOfLife.shouldShowActionbarAboveChat()) return;

        QualityOfLife.renderActionbarAboveChat(
                () -> ((InGameHudOverlayInvoker) this).wtz$renderOverlayMessage(context, tickCounter)
        );
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void WTZ_afterRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (MinecraftClient.getInstance().currentScreen != null) return;
        ShoppingListRenderer.getInstance().render(context, -1, -1);
    }
}
