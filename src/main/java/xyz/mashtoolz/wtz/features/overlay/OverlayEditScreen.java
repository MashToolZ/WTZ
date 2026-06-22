package xyz.mashtoolz.wtz.features.overlay;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.config.WTZConfig;
import xyz.mashtoolz.wtz.features.mount.stats.MountEnergyOverlay;
import xyz.mashtoolz.wtz.features.mount.stats.MountJumpOverlay;
import xyz.mashtoolz.wtz.features.mount.stats.MountStatsOverlay;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OverlayEditScreen extends Screen {

    private static final int BACKDROP_COLOR = 0x66000000;
    private static final int BORDER_IDLE = 0x50FF4800;
    private static final int BORDER_HOVER = 0xA0FF4800;
    private static final int BORDER_SELECTED = 0xE000BFFF;
    private static final int BORDER_LOCKED = 0xC0808080;
    private static final int HANDLE_COLOR = 0xE000BFFF;
    private static final int MARQUEE_FILL = 0x3000BFFF;
    private static final int GUIDE_COLOR = 0xB0FFB000;
    private static final int RESIZE_HANDLE_SIZE = 8;
    private static final int SNAP_DISTANCE = 4;
    private static final int HISTORY_LIMIT = 50;

    private final List<EditableOverlayHandle> overlays = List.of(
            MountStatsOverlay.editorHandle(),
            MountEnergyOverlay.editorHandle(),
            MountJumpOverlay.editorHandle()
    );
    private final Set<EditableOverlayHandle> selected = new LinkedHashSet<>();
    private final Deque<OverlayState> undoStack = new ArrayDeque<>();
    private final Deque<OverlayState> redoStack = new ArrayDeque<>();

    private EditOperation operation = EditOperation.NONE;
    private OverlayState pendingEditState;
    private Map<EditableOverlayHandle, TransformStart> transformStarts = Map.of();
    private OverlayBounds startGroupBounds;
    private int startMouseX;
    private int startMouseY;
    private int lastMouseX;
    private int lastMouseY;
    private boolean marqueeAdditive;
    private Integer verticalGuide;
    private Integer horizontalGuide;

    public OverlayEditScreen() {
        super(Text.translatable("screen.wtz.overlay_edit"));
        MountStatsOverlay.setEditMode(true);
        MountEnergyOverlay.setEditMode(true);
        MountJumpOverlay.setEditMode(true);
    }

    public static void toggle() {
        if (WTZClient.client().currentScreen instanceof OverlayEditScreen) {
            WTZClient.client().setScreen(null);
        } else if (WTZClient.client().currentScreen == null) {
            WTZClient.client().setScreen(new OverlayEditScreen());
        }
    }

    @Override
    public void removed() {
        MountStatsOverlay.setEditMode(false);
        MountEnergyOverlay.setEditMode(false);
        MountJumpOverlay.setEditMode(false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        context.fill(0, 0, width, height, BACKDROP_COLOR);

        MountStatsOverlay.renderEditOverlay(context);
        MountEnergyOverlay.renderEditOverlay(context);
        MountJumpOverlay.renderEditOverlay(context);

        updateOperation(mouseX, mouseY);
        renderGuides(context);
        renderAffordances(context, mouseX, mouseY);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void applyBlur(DrawContext context) {
    }

    @Override
    public void blur() {
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        EditableOverlayHandle hovered = hoveredOverlay(click.x(), click.y());

        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            OverlayBounds selectedBounds = selectedBounds(unlockedSelection());
            if (isShiftDown()) {
                if (selectedBounds != null && isOverResizeHandle(click.x(), click.y(), selectedBounds)) {
                    resetSelectionScale();
                    return true;
                }
                if (hovered != null) {
                    resetSelectionPosition(hovered);
                    return true;
                }
            }
            if (hovered != null) {
                hovered.toggleLocked();
                WTZConfig.save();
                return true;
            }
            return super.mouseClicked(click, doubleClick);
        }
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(click, doubleClick);
        }

        boolean ctrl = isCtrlDown();
        OverlayBounds selectedBounds = selectedBounds(unlockedSelection());
        if (!ctrl && selectedBounds != null && isOverResizeHandle(click.x(), click.y(), selectedBounds)) {
            beginTransform(EditOperation.RESIZE, (int) click.x(), (int) click.y());
            return true;
        }
        if (ctrl && hovered != null) {
            if (!selected.remove(hovered)) selected.add(hovered);
            return true;
        }

        if (hovered == null) {
            if (!ctrl) selected.clear();
            beginMarquee((int) click.x(), (int) click.y(), ctrl);
            return true;
        }

        if (!selected.contains(hovered)) {
            selected.clear();
            selected.add(hovered);
        }

        beginTransform(EditOperation.MOVE, (int) click.x(), (int) click.y());
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (isCtrlDown()) {
            String keyName = GLFW.glfwGetKeyName(input.key(), input.scancode());
            if (isUndoKey(input, keyName)) {
                undo();
                return true;
            }
            if (isRedoKey(input, keyName)) {
                redo();
                return true;
            }
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_R) {
            rotateSelection();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_LEFT || input.key() == GLFW.GLFW_KEY_RIGHT
                || input.key() == GLFW.GLFW_KEY_UP || input.key() == GLFW.GLFW_KEY_DOWN) {
            nudgeSelection(input.key());
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void beginTransform(EditOperation operation, int mouseX, int mouseY) {
        List<EditableOverlayHandle> active = unlockedSelection();
        if (active.isEmpty()) return;

        Map<EditableOverlayHandle, TransformStart> starts = new LinkedHashMap<>();
        for (EditableOverlayHandle overlay : active) {
            OverlayBounds bounds = overlay.bounds();
            if (bounds != null) starts.put(overlay, new TransformStart(bounds, overlay.scale()));
        }
        if (starts.isEmpty()) return;

        this.operation = operation;
        this.pendingEditState = OverlayState.current();
        this.transformStarts = starts;
        this.startGroupBounds = unionBounds(starts.values().stream().map(TransformStart::bounds).toList());
        this.startMouseX = mouseX;
        this.startMouseY = mouseY;
        redoStack.clear();
    }

    private void beginMarquee(int mouseX, int mouseY, boolean additive) {
        operation = EditOperation.MARQUEE;
        startMouseX = mouseX;
        startMouseY = mouseY;
        marqueeAdditive = additive;
    }

    private void updateOperation(int mouseX, int mouseY) {
        if (operation == EditOperation.NONE) return;

        long handle = WTZClient.client().getWindow().getHandle();
        boolean mouseDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (!mouseDown) {
            finishOperation(mouseX, mouseY);
            return;
        }

        verticalGuide = null;
        horizontalGuide = null;
        if (operation == EditOperation.MOVE) updateMove(mouseX, mouseY);
        else if (operation == EditOperation.RESIZE) updateResize(mouseX, mouseY);
    }

    private void updateMove(int mouseX, int mouseY) {
        int dx = mouseX - startMouseX;
        int dy = mouseY - startMouseY;
        SnapResult snap = snapMove(startGroupBounds, dx, dy);
        verticalGuide = snap.verticalGuide();
        horizontalGuide = snap.horizontalGuide();

        for (Map.Entry<EditableOverlayHandle, TransformStart> entry : transformStarts.entrySet()) {
            OverlayBounds start = entry.getValue().bounds();
            entry.getKey().setPositionPixels(start.x() + snap.dx(), start.y() + snap.dy());
        }
    }

    private void updateResize(int mouseX, int mouseY) {
        float widthFactor = startGroupBounds.width() > 0
                ? (float) (mouseX - startGroupBounds.x()) / startGroupBounds.width()
                : 1.0f;
        float heightFactor = startGroupBounds.height() > 0
                ? (float) (mouseY - startGroupBounds.y()) / startGroupBounds.height()
                : 1.0f;
        float factor = Math.max(widthFactor, heightFactor);

        for (Map.Entry<EditableOverlayHandle, TransformStart> entry : transformStarts.entrySet()) {
            EditableOverlayHandle overlay = entry.getKey();
            float startScale = entry.getValue().scale();
            factor = Math.max(factor, overlay.minScale() / startScale);
            factor = Math.min(factor, overlay.maxScale() / startScale);
        }

        factor = clampResizeFactor(snapResizeFactor(factor));
        for (Map.Entry<EditableOverlayHandle, TransformStart> entry : transformStarts.entrySet()) {
            EditableOverlayHandle overlay = entry.getKey();
            TransformStart start = entry.getValue();
            int x = startGroupBounds.x() + Math.round((start.bounds().x() - startGroupBounds.x()) * factor);
            int y = startGroupBounds.y() + Math.round((start.bounds().y() - startGroupBounds.y()) * factor);
            overlay.setScale(start.scale() * factor);
            overlay.setPositionPixels(x, y);
        }
    }

    private void finishOperation(int mouseX, int mouseY) {
        if (operation == EditOperation.MARQUEE) {
            OverlayBounds marquee = OverlayBounds.between(startMouseX, startMouseY, mouseX, mouseY);
            if (!marqueeAdditive) selected.clear();
            for (EditableOverlayHandle overlay : overlays) {
                OverlayBounds bounds = overlay.bounds();
                if (bounds != null && marquee.intersects(bounds)) selected.add(overlay);
            }
        } else if (pendingEditState != null) {
            OverlayState current = OverlayState.current();
            if (!pendingEditState.equals(current)) {
                pushUndo(pendingEditState);
                WTZConfig.save();
            }
        }

        operation = EditOperation.NONE;
        pendingEditState = null;
        transformStarts = Map.of();
        startGroupBounds = null;
        verticalGuide = null;
        horizontalGuide = null;
    }

    private SnapResult snapMove(OverlayBounds group, int dx, int dy) {
        int snappedDx = dx;
        int snappedDy = dy;
        int bestX = SNAP_DISTANCE + 1;
        int bestY = SNAP_DISTANCE + 1;
        Integer xGuide = null;
        Integer yGuide = null;

        List<Integer> targetXs = new ArrayList<>();
        List<Integer> targetYs = new ArrayList<>();
        targetXs.add(0);
        targetXs.add(width / 2);
        targetXs.add(width);
        targetYs.add(0);
        targetYs.add(height / 2);
        targetYs.add(height);
        for (EditableOverlayHandle overlay : overlays) {
            if (transformStarts.containsKey(overlay)) continue;
            OverlayBounds bounds = overlay.bounds();
            if (bounds == null) continue;
            targetXs.add(bounds.x());
            targetXs.add(bounds.centerX());
            targetXs.add(bounds.right());
            targetYs.add(bounds.y());
            targetYs.add(bounds.centerY());
            targetYs.add(bounds.bottom());
        }

        int[] movingXs = {group.x() + dx, group.centerX() + dx, group.right() + dx};
        int[] movingYs = {group.y() + dy, group.centerY() + dy, group.bottom() + dy};
        for (int target : targetXs) {
            for (int moving : movingXs) {
                int distance = target - moving;
                if (Math.abs(distance) < bestX) {
                    bestX = Math.abs(distance);
                    snappedDx = dx + distance;
                    xGuide = target;
                }
            }
        }
        for (int target : targetYs) {
            for (int moving : movingYs) {
                int distance = target - moving;
                if (Math.abs(distance) < bestY) {
                    bestY = Math.abs(distance);
                    snappedDy = dy + distance;
                    yGuide = target;
                }
            }
        }
        if (bestX > SNAP_DISTANCE) xGuide = null;
        if (bestY > SNAP_DISTANCE) yGuide = null;
        return new SnapResult(bestX <= SNAP_DISTANCE ? snappedDx : dx, bestY <= SNAP_DISTANCE ? snappedDy : dy, xGuide, yGuide);
    }

    private float snapResizeFactor(float factor) {
        int desiredRight = startGroupBounds.x() + Math.round(startGroupBounds.width() * factor);
        int desiredBottom = startGroupBounds.y() + Math.round(startGroupBounds.height() * factor);
        int bestDistance = SNAP_DISTANCE + 1;
        Float snappedFactor = null;

        for (EditableOverlayHandle overlay : overlays) {
            if (transformStarts.containsKey(overlay)) continue;
            OverlayBounds bounds = overlay.bounds();
            if (bounds == null) continue;
            int[] xs = {bounds.x(), bounds.centerX(), bounds.right()};
            int[] ys = {bounds.y(), bounds.centerY(), bounds.bottom()};
            for (int x : xs) {
                int distance = Math.abs(x - desiredRight);
                if (distance < bestDistance && startGroupBounds.width() > 0) {
                    bestDistance = distance;
                    snappedFactor = (float) (x - startGroupBounds.x()) / startGroupBounds.width();
                    verticalGuide = x;
                    horizontalGuide = null;
                }
            }
            for (int y : ys) {
                int distance = Math.abs(y - desiredBottom);
                if (distance < bestDistance && startGroupBounds.height() > 0) {
                    bestDistance = distance;
                    snappedFactor = (float) (y - startGroupBounds.y()) / startGroupBounds.height();
                    horizontalGuide = y;
                    verticalGuide = null;
                }
            }
        }
        if (snappedFactor == null) return factor;
        return snappedFactor;
    }

    private float clampResizeFactor(float factor) {
        for (Map.Entry<EditableOverlayHandle, TransformStart> entry : transformStarts.entrySet()) {
            EditableOverlayHandle overlay = entry.getKey();
            float startScale = entry.getValue().scale();
            factor = Math.max(factor, overlay.minScale() / startScale);
            factor = Math.min(factor, overlay.maxScale() / startScale);
        }
        return factor;
    }

    private void renderGuides(DrawContext context) {
        if (verticalGuide != null) context.fill(verticalGuide, 0, verticalGuide + 1, height, GUIDE_COLOR);
        if (horizontalGuide != null) context.fill(0, horizontalGuide, width, horizontalGuide + 1, GUIDE_COLOR);
    }

    private void renderAffordances(DrawContext context, int mouseX, int mouseY) {
        EditableOverlayHandle hovered = hoveredOverlay(mouseX, mouseY);
        for (EditableOverlayHandle overlay : overlays) {
            OverlayBounds bounds = overlay.bounds();
            if (bounds == null) continue;
            int color = overlay.locked()
                    ? BORDER_LOCKED
                    : selected.contains(overlay) ? BORDER_SELECTED
                    : overlay == hovered ? BORDER_HOVER : BORDER_IDLE;
            drawBorder(context, bounds, color);
        }

        OverlayBounds groupBounds = selectedBounds(unlockedSelection());
        if (groupBounds != null) drawResizeHandle(context, groupBounds);

        if (operation == EditOperation.MARQUEE) {
            OverlayBounds marquee = OverlayBounds.between(startMouseX, startMouseY, mouseX, mouseY);
            context.fill(marquee.x(), marquee.y(), marquee.right(), marquee.bottom(), MARQUEE_FILL);
            drawBorder(context, marquee, BORDER_SELECTED);
        }
    }

    private void drawBorder(DrawContext context, OverlayBounds bounds, int color) {
        context.fill(bounds.x(), bounds.y(), bounds.right(), bounds.y() + 1, color);
        context.fill(bounds.x(), bounds.bottom() - 1, bounds.right(), bounds.bottom(), color);
        context.fill(bounds.x(), bounds.y(), bounds.x() + 1, bounds.bottom(), color);
        context.fill(bounds.right() - 1, bounds.y(), bounds.right(), bounds.bottom(), color);
    }

    private void drawResizeHandle(DrawContext context, OverlayBounds bounds) {
        for (int i = 0; i < RESIZE_HANDLE_SIZE; i++) {
            context.fill(bounds.right() - RESIZE_HANDLE_SIZE + i, bounds.bottom() - 1 - i,
                    bounds.right(), bounds.bottom() - i, HANDLE_COLOR);
        }
    }

    private boolean isOverResizeHandle(double mouseX, double mouseY, OverlayBounds bounds) {
        return mouseX >= bounds.right() - RESIZE_HANDLE_SIZE && mouseX < bounds.right()
                && mouseY >= bounds.bottom() - RESIZE_HANDLE_SIZE && mouseY < bounds.bottom();
    }

    private EditableOverlayHandle hoveredOverlay(double mouseX, double mouseY) {
        for (int i = overlays.size() - 1; i >= 0; i--) {
            EditableOverlayHandle overlay = overlays.get(i);
            if (overlay.contains(mouseX, mouseY)) return overlay;
        }
        return null;
    }

    private List<EditableOverlayHandle> unlockedSelection() {
        return selected.stream().filter(overlay -> !overlay.locked() && overlay.bounds() != null).toList();
    }

    private OverlayBounds selectedBounds(List<EditableOverlayHandle> selection) {
        return unionBounds(selection.stream().map(EditableOverlayHandle::bounds).toList());
    }

    private OverlayBounds unionBounds(List<OverlayBounds> boundsList) {
        if (boundsList.isEmpty()) return null;
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (OverlayBounds bounds : boundsList) {
            if (bounds == null) continue;
            left = Math.min(left, bounds.x());
            top = Math.min(top, bounds.y());
            right = Math.max(right, bounds.right());
            bottom = Math.max(bottom, bounds.bottom());
        }
        return left == Integer.MAX_VALUE ? null : new OverlayBounds(left, top, right - left, bottom - top);
    }

    private void rotateSelection() {
        List<EditableOverlayHandle> targets = actionTargets().stream()
                .filter(overlay -> !overlay.locked() && overlay.canRotate() && overlay.bounds() != null)
                .toList();
        OverlayState before = OverlayState.current();

        OverlayBounds groupBounds = selectedBounds(targets);
        Map<EditableOverlayHandle, OverlayBounds> originalBounds = new LinkedHashMap<>();
        for (EditableOverlayHandle overlay : targets) {
            originalBounds.put(overlay, overlay.bounds());
            overlay.rotateClockwise();
        }

        if (targets.size() > 1 && groupBounds != null) {
            double groupCenterX = groupBounds.x() + groupBounds.width() / 2.0;
            double groupCenterY = groupBounds.y() + groupBounds.height() / 2.0;
            for (EditableOverlayHandle overlay : targets) {
                OverlayBounds original = originalBounds.get(overlay);
                OverlayBounds rotated = overlay.bounds();
                if (original == null || rotated == null) continue;

                double relativeCenterX = original.x() + original.width() / 2.0 - groupCenterX;
                double relativeCenterY = original.y() + original.height() / 2.0 - groupCenterY;
                double rotatedCenterX = groupCenterX - relativeCenterY;
                double rotatedCenterY = groupCenterY + relativeCenterX;
                overlay.setPositionPixels(
                        (int) Math.round(rotatedCenterX - rotated.width() / 2.0),
                        (int) Math.round(rotatedCenterY - rotated.height() / 2.0)
                );
            }
        }

        finishImmediateEdit(before, !targets.isEmpty());
    }

    private void resetSelectionScale() {
        List<EditableOverlayHandle> targets = unlockedSelection();
        OverlayState before = OverlayState.current();
        for (EditableOverlayHandle overlay : targets) overlay.setScale(1.0f);
        finishImmediateEdit(before, !targets.isEmpty());
    }

    private void resetSelectionPosition(EditableOverlayHandle hovered) {
        List<EditableOverlayHandle> targets;
        if (selected.contains(hovered)) {
            targets = unlockedSelection();
        } else {
            selected.clear();
            selected.add(hovered);
            targets = hovered.locked() ? List.of() : List.of(hovered);
        }

        OverlayState before = OverlayState.current();
        for (EditableOverlayHandle overlay : targets) overlay.resetPosition();
        finishImmediateEdit(before, !targets.isEmpty());
    }

    private void nudgeSelection(int key) {
        int step = isShiftDown() ? 10 : 1;
        int dx = key == GLFW.GLFW_KEY_LEFT ? -step : key == GLFW.GLFW_KEY_RIGHT ? step : 0;
        int dy = key == GLFW.GLFW_KEY_UP ? -step : key == GLFW.GLFW_KEY_DOWN ? step : 0;
        List<EditableOverlayHandle> targets = actionTargets().stream().filter(overlay -> !overlay.locked()).toList();
        OverlayState before = OverlayState.current();
        for (EditableOverlayHandle overlay : targets) {
            OverlayBounds bounds = overlay.bounds();
            if (bounds != null) overlay.setPositionPixels(bounds.x() + dx, bounds.y() + dy);
        }
        finishImmediateEdit(before, !targets.isEmpty());
    }

    private List<EditableOverlayHandle> actionTargets() {
        EditableOverlayHandle hovered = hoveredOverlay(lastMouseX, lastMouseY);
        if (hovered != null && selected.contains(hovered)) return new ArrayList<>(selected);
        if (hovered != null) {
            selected.clear();
            selected.add(hovered);
            return List.of(hovered);
        }
        return new ArrayList<>(selected);
    }

    private void finishImmediateEdit(OverlayState before, boolean changed) {
        if (!changed) return;
        pushUndo(before);
        redoStack.clear();
        WTZConfig.save();
    }

    private boolean isCtrlDown() {
        long handle = WTZClient.client().getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private boolean isShiftDown() {
        long handle = WTZClient.client().getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private boolean isUndoKey(KeyInput input, String keyName) {
        return "z".equalsIgnoreCase(keyName) || (keyName == null && input.key() == GLFW.GLFW_KEY_Z);
    }

    private boolean isRedoKey(KeyInput input, String keyName) {
        return "y".equalsIgnoreCase(keyName) || (keyName == null && input.key() == GLFW.GLFW_KEY_Y);
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        OverlayState previous = undoStack.removeLast();
        redoStack.addLast(OverlayState.current());
        previous.apply();
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        OverlayState next = redoStack.removeLast();
        undoStack.addLast(OverlayState.current());
        trimHistory(undoStack);
        next.apply();
    }

    private void pushUndo(OverlayState state) {
        undoStack.addLast(state);
        trimHistory(undoStack);
    }

    private void trimHistory(Deque<OverlayState> history) {
        while (history.size() > HISTORY_LIMIT) history.removeFirst();
    }

    private enum EditOperation {
        NONE,
        MOVE,
        RESIZE,
        MARQUEE
    }

    private record TransformStart(OverlayBounds bounds, float scale) {
    }

    private record SnapResult(int dx, int dy, Integer verticalGuide, Integer horizontalGuide) {
    }

    private record OverlayState(
            double statsX,
            double statsY,
            float statsScale,
            double energyX,
            double energyY,
            float energyScale,
            int energyRotation,
            double jumpX,
            double jumpY,
            float jumpScale,
            int jumpRotation
    ) {
        private static OverlayState current() {
            WTZConfig config = WTZClient.CONFIG;
            return new OverlayState(
                    config.mountStatsDragPctX,
                    config.mountStatsDragPctY,
                    config.mountStatsDragScale,
                    config.mountEnergyDragPctX,
                    config.mountEnergyDragPctY,
                    config.mountEnergyDragScale,
                    config.mountEnergyRotation,
                    config.mountJumpDragPctX,
                    config.mountJumpDragPctY,
                    config.mountJumpDragScale,
                    config.mountJumpRotation
            );
        }

        private void apply() {
            WTZConfig config = WTZClient.CONFIG;
            config.mountStatsDragPctX = statsX;
            config.mountStatsDragPctY = statsY;
            config.mountStatsDragScale = statsScale;
            config.mountEnergyDragPctX = energyX;
            config.mountEnergyDragPctY = energyY;
            config.mountEnergyDragScale = energyScale;
            config.mountEnergyRotation = energyRotation;
            config.mountJumpDragPctX = jumpX;
            config.mountJumpDragPctY = jumpY;
            config.mountJumpDragScale = jumpScale;
            config.mountJumpRotation = jumpRotation;
            WTZConfig.save();
        }
    }
}
