package xyz.mashtoolz.wtz.features.overlay;

import xyz.mashtoolz.wtz.client.WTZClient;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class EditableOverlayHandle {

    private final String id;
    private final BooleanSupplier enabled;
    private final Supplier<OverlayBounds> bounds;
    private final DoubleConsumer setXPct;
    private final DoubleConsumer setYPct;
    private final double defaultXPct;
    private final double defaultYPct;
    private final FloatSupplier scale;
    private final FloatConsumer setScale;
    private final float minScale;
    private final float maxScale;
    private final BooleanSupplier locked;
    private final BooleanConsumer setLocked;
    private final IntSupplier rotation;
    private final IntConsumer setRotation;

    public EditableOverlayHandle(
            String id,
            BooleanSupplier enabled,
            Supplier<OverlayBounds> bounds,
            DoubleConsumer setXPct,
            DoubleConsumer setYPct,
            double defaultXPct,
            double defaultYPct,
            FloatSupplier scale,
            FloatConsumer setScale,
            float minScale,
            float maxScale,
            BooleanSupplier locked,
            BooleanConsumer setLocked,
            IntSupplier rotation,
            IntConsumer setRotation
    ) {
        this.id = id;
        this.enabled = enabled;
        this.bounds = bounds;
        this.setXPct = setXPct;
        this.setYPct = setYPct;
        this.defaultXPct = defaultXPct;
        this.defaultYPct = defaultYPct;
        this.scale = scale;
        this.setScale = setScale;
        this.minScale = minScale;
        this.maxScale = maxScale;
        this.locked = locked;
        this.setLocked = setLocked;
        this.rotation = rotation;
        this.setRotation = setRotation;
    }

    public String id() {
        return id;
    }

    public boolean enabled() {
        return enabled.getAsBoolean();
    }

    public OverlayBounds bounds() {
        return enabled() ? bounds.get() : null;
    }

    public boolean contains(double x, double y) {
        OverlayBounds bounds = bounds();
        return bounds != null && bounds.contains(x, y);
    }

    public float scale() {
        return scale.getAsFloat();
    }

    public void setScale(float value) {
        setScale.accept(Math.clamp(value, minScale, maxScale));
    }

    public float minScale() {
        return minScale;
    }

    public float maxScale() {
        return maxScale;
    }

    public boolean locked() {
        return locked.getAsBoolean();
    }

    public void toggleLocked() {
        setLocked.accept(!locked());
    }

    public boolean canRotate() {
        return rotation != null && setRotation != null;
    }

    public void rotateClockwise() {
        if (canRotate()) setRotation.accept((rotation.getAsInt() + 1) % 4);
    }

    public void setPositionPixels(int x, int y) {
        OverlayBounds bounds = bounds();
        if (bounds == null) return;

        int screenWidth = WTZClient.client().getWindow().getScaledWidth();
        int screenHeight = WTZClient.client().getWindow().getScaledHeight();
        int clampedX = Math.clamp(x, 0, Math.max(0, screenWidth - bounds.width()));
        int clampedY = Math.clamp(y, 0, Math.max(0, screenHeight - bounds.height()));
        setXPct.accept(screenWidth > 0 ? (double) clampedX / screenWidth * 100.0 : -1.0);
        setYPct.accept(screenHeight > 0 ? (double) clampedY / screenHeight * 100.0 : -1.0);
    }

    public void resetPosition() {
        setXPct.accept(defaultXPct);
        setYPct.accept(defaultYPct);
    }

    public interface FloatSupplier {
        float getAsFloat();
    }

    public interface FloatConsumer {
        void accept(float value);
    }

    public interface BooleanConsumer {
        void accept(boolean value);
    }
}
