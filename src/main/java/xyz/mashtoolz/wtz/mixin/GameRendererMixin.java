package xyz.mashtoolz.wtz.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.features.mount.MountCamera;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void WTZ_onGetFov(Camera camera, float tickProgress, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        int mountCameraFov = WTZClient.CONFIG.mountCameraFov;
        if (MountCamera.getInstance().isActive() && mountCameraFov >= 30) {
            cir.setReturnValue((float) WTZClient.CONFIG.mountCameraFov);
        }
    }
}
