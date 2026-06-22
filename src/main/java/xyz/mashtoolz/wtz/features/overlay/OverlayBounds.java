package xyz.mashtoolz.wtz.features.overlay;

public record OverlayBounds(int x, int y, int width, int height) {
    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public int centerX() {
        return x + width / 2;
    }

    public int centerY() {
        return y + height / 2;
    }

    public boolean contains(double px, double py) {
        return px >= x && px < right() && py >= y && py < bottom();
    }

    public boolean intersects(OverlayBounds other) {
        return x < other.right() && right() > other.x
                && y < other.bottom() && bottom() > other.y;
    }

    public static OverlayBounds between(int startX, int startY, int endX, int endY) {
        int x = Math.min(startX, endX);
        int y = Math.min(startY, endY);
        return new OverlayBounds(x, y, Math.abs(endX - startX), Math.abs(endY - startY));
    }
}
