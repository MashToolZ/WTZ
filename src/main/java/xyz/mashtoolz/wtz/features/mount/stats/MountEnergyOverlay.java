package xyz.mashtoolz.wtz.features.mount.stats;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.config.WTZConfig;
import xyz.mashtoolz.wtz.features.mount.MountUtils;
import xyz.mashtoolz.wtz.features.overlay.EditableFrameBarOverlay;
import xyz.mashtoolz.wtz.features.overlay.EditableOverlayHandle;

public final class MountEnergyOverlay {

    private static final EditableFrameBarOverlay OVERLAY = new EditableFrameBarOverlay(
            Identifier.of("wtz", "textures/img/overlay/mount_energy.png"),
            () -> WTZClient.CONFIG.mountEnergyOverlayEnabled,
            () -> MountUtils.isMounted() && MountStatsUpdater.hasMountEnergyBar(),
            MountStatsUpdater::getMountEnergyStrength,
            () -> WTZClient.CONFIG.mountEnergyRotation,
            () -> WTZClient.CONFIG.mountEnergyDragPctX,
            () -> WTZClient.CONFIG.mountEnergyDragPctY,
            () -> WTZClient.CONFIG.mountEnergyDragScale,
            20
    );
    private static final EditableOverlayHandle EDITOR_HANDLE = new EditableOverlayHandle(
            "mount_energy",
            () -> WTZClient.CONFIG.mountEnergyOverlayEnabled,
            OVERLAY::editBounds,
            value -> WTZClient.CONFIG.mountEnergyDragPctX = value,
            value -> WTZClient.CONFIG.mountEnergyDragPctY = value,
            WTZConfig.DEFAULT_MOUNT_ENERGY_DRAG_PCT_X,
            WTZConfig.DEFAULT_MOUNT_ENERGY_DRAG_PCT_Y,
            () -> WTZClient.CONFIG.mountEnergyDragScale,
            value -> WTZClient.CONFIG.mountEnergyDragScale = value,
            0.3f,
            3.0f,
            () -> WTZClient.CONFIG.mountEnergyEditLocked,
            value -> WTZClient.CONFIG.mountEnergyEditLocked = value,
            () -> WTZClient.CONFIG.mountEnergyRotation,
            value -> WTZClient.CONFIG.mountEnergyRotation = value
    );

    private MountEnergyOverlay() {
    }

    public static void register() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.SCOREBOARD,
                Identifier.of("wtz", "mount_energy"),
                (context, tickCounter) -> OVERLAY.renderHud(context)
        );
    }

    public static void setEditMode(boolean enabled) {
        OVERLAY.setEditMode(enabled);
    }

    public static void renderEditOverlay(DrawContext context) {
        OVERLAY.renderEditOverlay(context);
    }

    public static EditableOverlayHandle editorHandle() {
        return EDITOR_HANDLE;
    }
}
