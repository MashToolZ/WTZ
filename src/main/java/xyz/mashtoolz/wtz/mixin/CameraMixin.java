package xyz.mashtoolz.wtz.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.features.mount.MountCamera;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void moveBy(float surge, float heave, float sway);

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    private float clipToSpace(float distance) {
        throw new AssertionError();
    }

    @Shadow
    public abstract float getYaw();

    @Shadow
    public abstract float getPitch();

    @Shadow
    private float cameraY;

    @Shadow
    private float lastCameraY;

    @Inject(method = "update", at = @At("RETURN"))
    private void WTZ_afterUpdate(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
        MountCamera cam = MountCamera.getInstance();
        cam.tick();

        if (!cam.isThirdPersonActive()) return;

        Vec3d entityPos = focusedEntity.getLerpedPos(tickProgress);
        double eyeY = entityPos.y + MathHelper.lerp(tickProgress, lastCameraY, cameraY);
        setPos(entityPos.x, eyeY, entityPos.z);

        float yaw = getYaw() + (cam.isFreeLooking() ? cam.getYawOffset() : 0);
        float pitch = getPitch() + (cam.isFreeLooking() ? cam.getPitchOffset() : 0);
        setRotation(yaw, pitch);

        float offsetZ = (float) WTZClient.CONFIG.mountCameraOffsetZ;
        moveBy(-clipToSpace(cam.getZoomDistance() + offsetZ), 0.0f, 0.0f);
    }
}
