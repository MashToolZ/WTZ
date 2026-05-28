package xyz.mashtoolz.wtz.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.mashtoolz.wtz.features.qol.QualityOfLife;
import xyz.mashtoolz.wtz.features.mount.MountCamera;
import xyz.mashtoolz.wtz.features.mount.MountStatsUpdater;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void WTZ_doAttack(CallbackInfoReturnable<Boolean> cir) {
        MountCamera cam = MountCamera.getInstance();
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            cam.onItemUsed(player.getMainHandStack());
        }
        if (cam.isThirdPersonActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void WTZ_handleBlockBreaking(CallbackInfo ci) {
        if (MountCamera.getInstance().isThirdPersonActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void WTZ_blockCloseWhileFrozen(@Nullable Screen screen, CallbackInfo ci) {
        if (screen == null && QualityOfLife.isScreenFrozen()) {
            ci.cancel();
        }
    }

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void WTZ_afterSetScreen(@Nullable Screen screen, CallbackInfo ci) {
        if (screen != null) {
            MountStatsUpdater.onScreenOpened(screen);
        }
    }
}
