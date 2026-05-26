package xyz.mashtoolz.wtz.features.mount;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.item.ItemStack;
import xyz.mashtoolz.wtz.client.WTZClient;

public class MountCamera {

    private static final MountCamera INSTANCE = new MountCamera();
    private static final float RETURN_SPEED = 0.15f;
    private static final float SNAP_THRESHOLD = 0.5f;
    private static final float MIN_ZOOM = 0.0f;
    private static final float MAX_ZOOM = 10.0f;
    private static final float ZOOM_STEP = 0.5f;
    private static final float DEFAULT_ZOOM = 0.0f;

    private float yawOffset = 0;
    private float pitchOffset = 0;
    private boolean freeLooking = false;
    private boolean returning = false;
    private float zoomDistance = DEFAULT_ZOOM;

    private enum State {
        IDLE,
        AWAITING_MOUNT,
        MOUNTED,
        RESTORING
    }

    private State currentState = State.IDLE;
    private int timer = 0;
    private Perspective previousPerspective = null;
    private Perspective restoreTarget = null;

    public static MountCamera getInstance() {
        return INSTANCE;
    }

    public boolean isActive() {
        return currentState == State.MOUNTED && WTZClient.CONFIG.mountCameraEnabled;
    }

    public boolean isInThirdPerson() {
        MinecraftClient client = WTZClient.client();
        if (client == null || client.options == null) return false;
        return !client.options.getPerspective().isFirstPerson();
    }

    public boolean isThirdPersonActive() {
        return isActive() && isInThirdPerson();
    }

    public boolean isFreeLooking() {
        return (freeLooking || returning) && isThirdPersonActive();
    }

    public void onItemUsed(ItemStack stack) {
        if (!WTZClient.CONFIG.mountCameraEnabled) return;
        if (currentState != State.IDLE) return;
        ClientPlayerEntity player = WTZClient.player();
        if (player != null && player.hasVehicle()) return;
        if (stack != null && !stack.isEmpty() && !MountStatsOverlay.parse(stack).isEmpty()) {
            previousPerspective = WTZClient.client().options.getPerspective();
            currentState = State.AWAITING_MOUNT;
            timer = 100;
        }
    }

    public void tickPerspective() {
        ClientPlayerEntity player = WTZClient.player();
        if (player == null) return;

        if (!WTZClient.CONFIG.mountCameraEnabled) {
            if (currentState != State.IDLE) {
                currentState = State.IDLE;
                previousPerspective = null;
                restoreTarget = null;
                reset();
            }
            return;
        }

        boolean isPhysicallyMounted = MountUtils.isMounted();

        switch (currentState) {
            case IDLE:
                break;

            case AWAITING_MOUNT:
                if (isPhysicallyMounted) {
                    if (WTZClient.CONFIG.mountCameraAutoPerspective) {
                        WTZClient.client().options.setPerspective(Perspective.THIRD_PERSON_BACK);
                    }
                    currentState = State.MOUNTED;
                } else {
                    timer--;
                    if (timer <= 0) currentState = State.IDLE;
                }
                break;

            case MOUNTED:
                if (!isPhysicallyMounted) {
                    if (WTZClient.CONFIG.mountCameraAutoPerspective) {
                        restoreTarget = previousPerspective != null ? previousPerspective : Perspective.FIRST_PERSON;
                        currentState = State.RESTORING;
                        timer = 100;
                    } else {
                        currentState = State.IDLE;
                        previousPerspective = null;
                    }
                    reset();
                }
                break;

            case RESTORING:
                Perspective currentView = WTZClient.client().options.getPerspective();
                if (currentView == restoreTarget) {
                    currentState = State.IDLE;
                    previousPerspective = null;
                    restoreTarget = null;
                } else {
                    WTZClient.client().options.setPerspective(restoreTarget);
                    timer--;
                    if (timer <= 0) currentState = State.IDLE;
                }
                break;
        }
    }

    public void tick() {
        if (!returning) return;
        yawOffset *= (1.0f - RETURN_SPEED);
        pitchOffset *= (1.0f - RETURN_SPEED);
        if (Math.abs(yawOffset) < SNAP_THRESHOLD && Math.abs(pitchOffset) < SNAP_THRESHOLD) {
            yawOffset = 0;
            pitchOffset = 0;
            returning = false;
        }
    }

    public void addFreeLookDelta(double deltaYaw, double deltaPitch) {
        if (isActive()) {
            yawOffset += (float) deltaYaw;
            pitchOffset += (float) deltaPitch;
        }
    }

    public void setFreeLooking(boolean freeLooking) {
        if (!freeLooking && this.freeLooking) returning = true;
        this.freeLooking = freeLooking;
    }

    public void reset() {
        yawOffset = 0;
        pitchOffset = 0;
        freeLooking = false;
        returning = false;
    }

    public void onScroll(double amount) {
        if (!isActive()) return;
        zoomDistance = Math.clamp(zoomDistance - (float) amount * ZOOM_STEP, MIN_ZOOM, MAX_ZOOM);
    }

    public void resetZoom() {
        if (isActive()) zoomDistance = DEFAULT_ZOOM;
    }

    public float getZoomDistance() {
        return zoomDistance;
    }

    public float getYawOffset() {
        return yawOffset;
    }

    public float getPitchOffset() {
        return pitchOffset;
    }
}
