package xyz.mashtoolz.wtz.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.mashtoolz.wtz.features.mount.EnclosureScanner;
import xyz.mashtoolz.wtz.features.mount.MountStatsUpdater;

import java.util.List;

@Mixin(ScreenHandler.class)
public class ScreenHandlerMixin {

    @Inject(method = "updateSlotStacks", at = @At("TAIL"))
    private void WTZ_onUpdateSlotStacks(int revision, List<ItemStack> stacks, ItemStack cursorStack, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof HandledScreen<?> handled) {
            EnclosureScanner.onSlotsUpdated(handled);
            MountStatsUpdater.onSlotsUpdated(handled);
        }
    }

    @Inject(method = "setStackInSlot", at = @At("TAIL"))
    private void WTZ_onSetStackInSlot(int slot, int revision, ItemStack stack, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof HandledScreen<?> handled) {
            EnclosureScanner.onSlotChanged(handled);
        }
    }
}
