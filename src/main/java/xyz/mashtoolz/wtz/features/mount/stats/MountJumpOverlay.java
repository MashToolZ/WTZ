package xyz.mashtoolz.wtz.features.mount.stats;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.config.WTZConfig;
import xyz.mashtoolz.wtz.features.overlay.EditableFrameBarOverlay;
import xyz.mashtoolz.wtz.features.overlay.EditableOverlayHandle;

import java.util.Map;

public final class MountJumpOverlay {

    private static final EditableFrameBarOverlay OVERLAY = new EditableFrameBarOverlay(
            Identifier.of("wtz", "textures/img/overlay/mount_jump.png"),
            () -> WTZClient.CONFIG.mountJumpOverlayEnabled,
            MountJumpOverlay::isVisibleInHud,
            MountJumpOverlay::getStrength,
            () -> WTZClient.CONFIG.mountJumpRotation,
            () -> WTZClient.CONFIG.mountJumpDragPctX,
            () -> WTZClient.CONFIG.mountJumpDragPctY,
            () -> WTZClient.CONFIG.mountJumpDragScale,
            0
    );
    private static final EditableOverlayHandle EDITOR_HANDLE = new EditableOverlayHandle(
            "mount_jump",
            () -> WTZClient.CONFIG.mountJumpOverlayEnabled,
            OVERLAY::editBounds,
            value -> WTZClient.CONFIG.mountJumpDragPctX = value,
            value -> WTZClient.CONFIG.mountJumpDragPctY = value,
            WTZConfig.DEFAULT_MOUNT_JUMP_DRAG_PCT_X,
            WTZConfig.DEFAULT_MOUNT_JUMP_DRAG_PCT_Y,
            () -> WTZClient.CONFIG.mountJumpDragScale,
            value -> WTZClient.CONFIG.mountJumpDragScale = value,
            0.3f,
            3.0f,
            () -> WTZClient.CONFIG.mountJumpEditLocked,
            value -> WTZClient.CONFIG.mountJumpEditLocked = value,
            () -> WTZClient.CONFIG.mountJumpRotation,
            value -> WTZClient.CONFIG.mountJumpRotation = value
    );

    private MountJumpOverlay() {
    }

    public static void register() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.SCOREBOARD,
                Identifier.of("wtz", "mount_jump"),
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

    private static boolean isVisibleInHud() {
        boolean mounted = WTZClient.player() != null && WTZClient.player().getJumpingMount() != null;
        if (!mounted || !isSupportedJumpMount()) return false;

        double strength = getStrength();
        return strength > 0.0f || WTZClient.CONFIG.mountJumpAlwaysShow;
    }

    private static double getStrength() {
        return WTZClient.player() != null ? WTZClient.player().getMountJumpStrength() : 0.0f;
    }

    private static boolean isSupportedJumpMount() {
        ItemStack mountedItem = MountStatsOverlay.getMountedItem();
        if (mountedItem == null || mountedItem.isEmpty()) return false;

        Map<String, int[]> stats = MountStatsOverlay.parse(mountedItem);
        return stats.containsKey("Jump Height") && !stats.containsKey("Altitude");
    }
}
