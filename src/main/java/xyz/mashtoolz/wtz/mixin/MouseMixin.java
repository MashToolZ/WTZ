package xyz.mashtoolz.wtz.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.features.mount.MountCamera;

@Mixin(Mouse.class)
public class MouseMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    private double cursorDeltaX;

    @Shadow
    private double cursorDeltaY;

    @Inject(method = "updateMouse", at = @At("HEAD"))
    private void WTZ_onUpdateMouse(CallbackInfo ci) {
        MountCamera cam = MountCamera.getInstance();
        if (!cam.isThirdPersonActive() || !WTZClient.CONFIG.mountCameraFreeLook) {
            cam.setFreeLooking(false);
            if (!cam.isActive()) cam.reset();
            return;
        }

        boolean leftHeld = this.client.options.attackKey.isPressed();
        cam.setFreeLooking(leftHeld);

        if (leftHeld && (cursorDeltaX != 0 || cursorDeltaY != 0)) {
            double sensitivity = this.client.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
            double scale = sensitivity * sensitivity * sensitivity * 8.0;

            cam.addFreeLookDelta(cursorDeltaX * scale * 0.15, cursorDeltaY * scale * 0.15);

            cursorDeltaX = 0;
            cursorDeltaY = 0;
        }
    }

    @SuppressWarnings("unused")
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void WTZ_onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (client.currentScreen != null) return;

        MountCamera cam = MountCamera.getInstance();
        if (cam.isThirdPersonActive() && WTZClient.CONFIG.mountCameraScrollZoom) {
            cam.onScroll(vertical);
            ci.cancel();
        }
    }

    @SuppressWarnings("unused")
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void WTZ_onMouseButton(long window, MouseInput input, int action, CallbackInfo ci) {
        if (client.currentScreen != null) return;

        MountCamera cam = MountCamera.getInstance();
        if (cam.isThirdPersonActive() && WTZClient.CONFIG.mountCameraScrollZoom && input.button() == 2 && action == 1) {
            cam.resetZoom();
        }
    }
}
