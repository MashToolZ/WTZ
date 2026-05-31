package xyz.mashtoolz.wtz.mixin;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.mashtoolz.wtz.client.WTZKeybinds;
import xyz.mashtoolz.wtz.features.mount.EnclosureScanner;
import xyz.mashtoolz.wtz.features.mount.MountItemOverlay;
import xyz.mashtoolz.wtz.features.mount.MountUtils;
import xyz.mashtoolz.wtz.features.qol.QualityOfLife;
import xyz.mashtoolz.wtz.features.shoppinglist.ShoppingListRenderer;

@Mixin(HandledScreen.class)
@SuppressWarnings({"DataFlowIssue"})
public abstract class HandledScreenMixin extends Screen {

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Shadow
    public int x;

    @Shadow
    public int y;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void WTZ_onKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (QualityOfLife.isScreenFrozen()) {
            cir.setReturnValue(true);
            return;
        }
        if (WTZKeybinds.COPY_MOUNT_STATS.matchesKey(input)) {
            MountUtils.copyHoveredStats();
            cir.setReturnValue(true);
        }
        HandledScreen<?> handled = (HandledScreen<?>) (Object) this;
        if (WTZKeybinds.ADD_TO_SHOPPING_LIST.matchesKey(input)) {
            Slot slot = handled.focusedSlot;
            if (slot != null && slot.hasStack()) {
                String name = slot.getStack().getName().getString().replaceAll("§.", "").replaceAll("[^\\x20-\\x7E]", "").trim();
                ShoppingListRenderer.getInstance().addItemToActiveList(name);
                cir.setReturnValue(true);
            }
        }
        if (WTZKeybinds.TOGGLE_SHOPPING_LIST.matchesKey(input)) {
            ShoppingListRenderer.getInstance().toggleVisibility();
            cir.setReturnValue(true);
        }
        if (ShoppingListRenderer.getInstance().onKeyPressed(input.key())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void WTZ_onClose(CallbackInfo ci) {
        QualityOfLife.onHandledScreenCloseStarted();
        ShoppingListRenderer.getInstance().onScreenClosed((HandledScreen<?>) (Object) this);
        EnclosureScanner.onScreenClose((HandledScreen<?>) (Object) this);
    }

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void WTZ_afterDrawSlot(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        MountItemOverlay.renderSlotOverlay(context, slot);
        MountItemOverlay.renderSkinOverlay(context, slot);
    }

    @SuppressWarnings("unused")
    @Inject(method = "isPointOverSlot", at = @At("HEAD"), cancellable = true)
    private void WTZ_blockSlotHoverUnderShoppingList(Slot slot, double pointX, double pointY, CallbackInfoReturnable<Boolean> cir) {
        if (ShoppingListRenderer.getInstance().isMouseOverPanel(pointX, pointY)) {
            cir.setReturnValue(false);
        }
    }

    @SuppressWarnings("unused")
    @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"), cancellable = true)
    private void WTZ_blockTooltipUnderShoppingList(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        if (ShoppingListRenderer.getInstance().isMouseOverPanel(mouseX, mouseY)) {
            ci.cancel();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void WTZ_onMouseClicked(Click click, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (QualityOfLife.isScreenFrozen()) {
            cir.setReturnValue(true);
            return;
        }
        HandledScreen<?> handled = (HandledScreen<?>) (Object) this;
        if (ShoppingListRenderer.getInstance().onMouseClicked(click.x(), click.y(), click.button())) {
            cir.setReturnValue(true);
            return;
        }
        Slot slot = WTZ_getHoveredSlot(handled, click.x(), click.y());
        if (slot != null
                && !WTZKeybinds.ADD_TO_SHOPPING_LIST.isUnbound()
                && WTZKeybinds.ADD_TO_SHOPPING_LIST.matchesMouse(click)
                && slot.hasStack()) {
            String name = slot.getStack().getName().getString().replaceAll("§.", "").replaceAll("[^\\x20-\\x7E]", "").trim();
            ShoppingListRenderer.getInstance().addItemToActiveList(name);
            cir.setReturnValue(true);
            return;
        }
        if (QualityOfLife.onHandledScreenClick(handled, click, slot)) {
            cir.setReturnValue(true);
        }
    }

    @SuppressWarnings("unused")
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void WTZ_onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        if (QualityOfLife.isScreenFrozen()) {
            cir.setReturnValue(true);
            return;
        }
        if (ShoppingListRenderer.getInstance().onMouseScrolled(mouseX, mouseY, verticalAmount)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private Slot WTZ_getHoveredSlot(HandledScreen<?> handled, double mouseX, double mouseY) {
        for (Slot slot : handled.getScreenHandler().slots) {
            int slotX = this.x + slot.x;
            int slotY = this.y + slot.y;
            if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                return slot;
            }
        }
        return null;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (QualityOfLife.isScreenFrozen()) return true;
        if (ShoppingListRenderer.getInstance().onCharTyped(input.codepoint())) {
            return true;
        }
        return super.charTyped(input);
    }
}
