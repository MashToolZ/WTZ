package xyz.mashtoolz.wtz.features.overlay;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import xyz.mashtoolz.wtz.client.WTZClient;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

public final class EditableFrameBarOverlay {

    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 192;
    private static final int FRAME_WIDTH = 16;
    private static final int FRAME_HEIGHT = 64;
    private static final int FRAME_COLUMNS = 16;
    private static final int FRAME_ROWS = 3;
    private static final int FRAME_COUNT = FRAME_COLUMNS * FRAME_ROWS;

    private final Identifier texture;
    private final BooleanSupplier enabled;
    private final BooleanSupplier liveVisible;
    private final DoubleSupplier liveStrength;
    private final IntSupplier rotation;
    private final DoubleSupplier xPct;
    private final DoubleSupplier yPct;
    private final FloatSupplier scale;
    private final int defaultOffsetX;

    private boolean editMode;
    private int lastX;
    private int lastY;
    private int lastWidth;
    private int lastHeight;
    private boolean interactiveBoundsVisible;

    public EditableFrameBarOverlay(
            Identifier texture,
            BooleanSupplier enabled,
            BooleanSupplier liveVisible,
            DoubleSupplier liveStrength,
            IntSupplier rotation,
            DoubleSupplier xPct,
            DoubleSupplier yPct,
            FloatSupplier scale,
            int defaultOffsetX
    ) {
        this.texture = texture;
        this.enabled = enabled;
        this.liveVisible = liveVisible;
        this.liveStrength = liveStrength;
        this.rotation = rotation;
        this.xPct = xPct;
        this.yPct = yPct;
        this.scale = scale;
        this.defaultOffsetX = defaultOffsetX;
    }

    public void setEditMode(boolean enabled) {
        editMode = enabled;
        if (!enabled) {
            interactiveBoundsVisible = false;
        }
    }

    public void renderHud(DrawContext context) {
        if (!enabled.getAsBoolean() || editMode) return;
        interactiveBoundsVisible = false;
        if (!liveVisible.getAsBoolean()) return;
        render(context, (float) liveStrength.getAsDouble(), false);
    }

    public void renderEditOverlay(DrawContext context) {
        if (!enabled.getAsBoolean()) {
            interactiveBoundsVisible = false;
            return;
        }
        render(context, 1.0f, true);
    }

    public OverlayBounds editBounds() {
        if (!enabled.getAsBoolean() || !interactiveBoundsVisible) return null;
        return new OverlayBounds(lastX, lastY, lastWidth, lastHeight);
    }

    private void render(DrawContext context, float strength, boolean keepInteractiveBounds) {
        float scale = getScale();
        int rotation = Math.floorMod(this.rotation.getAsInt(), 4);
        boolean horizontal = rotation % 2 != 0;
        int textureWidth = Math.max(1, Math.round(FRAME_WIDTH * scale));
        int textureHeight = Math.max(1, Math.round(FRAME_HEIGHT * scale));
        int width = horizontal ? textureHeight : textureWidth;
        int height = horizontal ? textureWidth : textureHeight;
        int[] position = computePosition(width, height);
        int frame = Math.clamp(Math.round((1.0f - strength) * (FRAME_COUNT - 1)), 0, FRAME_COUNT - 1);
        int u = (frame % FRAME_COLUMNS) * FRAME_WIDTH;
        int v = (frame / FRAME_COLUMNS) * FRAME_HEIGHT;

        lastX = position[0];
        lastY = position[1];
        lastWidth = width;
        lastHeight = height;
        interactiveBoundsVisible = keepInteractiveBounds;

        drawRotatedFrame(context, rotation, u, v, textureWidth, textureHeight);
    }

    private void drawRotatedFrame(DrawContext context, int rotation, int u, int v, int width, int height) {
        if (rotation == 0) {
            drawFrame(context, lastX, lastY, u, v, width, height);
            return;
        }

        context.getMatrices().pushMatrix();
        if (rotation == 1) {
            context.getMatrices().translate(lastX + height, lastY);
            context.getMatrices().rotate((float) Math.PI / 2.0f);
        } else if (rotation == 2) {
            context.getMatrices().translate(lastX + width, lastY + height);
            context.getMatrices().rotate((float) Math.PI);
        } else {
            context.getMatrices().translate(lastX, lastY + width);
            context.getMatrices().rotate((float) -Math.PI / 2.0f);
        }
        drawFrame(context, 0, 0, u, v, width, height);
        context.getMatrices().popMatrix();
    }

    private void drawFrame(DrawContext context, int x, int y, int u, int v, int width, int height) {
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                texture,
                x,
                y,
                u,
                v,
                width,
                height,
                FRAME_WIDTH,
                FRAME_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT
        );
    }

    private int[] computePosition(int width, int height) {
        int screenWidth = WTZClient.client().getWindow().getScaledWidth();
        int screenHeight = WTZClient.client().getWindow().getScaledHeight();
        if (xPct.getAsDouble() >= 0 && yPct.getAsDouble() >= 0) {
            return new int[]{
                    Math.clamp((int) Math.round(screenWidth * xPct.getAsDouble() / 100.0), 0, Math.max(0, screenWidth - width)),
                    Math.clamp((int) Math.round(screenHeight * yPct.getAsDouble() / 100.0), 0, Math.max(0, screenHeight - height))
            };
        }
        return new int[]{(screenWidth - width) / 2 + defaultOffsetX, Math.max(0, screenHeight - height - 32)};
    }

    private float getScale() {
        return scale.getAsFloat();
    }

    public interface FloatSupplier {
        float getAsFloat();
    }
}
