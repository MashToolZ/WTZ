package xyz.mashtoolz.wtz.util;

public final class TextRenderColorUtils {

    private TextRenderColorUtils() {
    }

    public static int avoidShaderMarkerColor(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        if (isMarkerLike(r, g, b)) {
            r = avoidExactMarkerChannel(r);
            g = avoidExactMarkerChannel(g);
            b = avoidExactMarkerChannel(b);
        }
        if (isWynnMovementMarker(g, b)) {
            g = moveAwayFromMarker(g);
        }
        if (isWynnEffectMarker(r, g, b)) {
            g = moveAwayFromMarker(g);
        }

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static boolean isMarkerLike(int r, int g, int b) {
        return r == 0 || r == 255 || g == 0 || g == 255 || b == 0 || b == 255;
    }

    private static int avoidExactMarkerChannel(int channel) {
        if (channel == 0) return 1;
        if (channel == 255) return 254;
        return channel;
    }

    private static int moveAwayFromMarker(int channel) {
        return Math.min(254, channel + 16);
    }

    private static boolean isWynnMovementMarker(int g, int b) {
        if (g == 235 && b >= 0 && b <= 72 && b % 4 == 0) return true;
        return g == (235 >> 2) && b >= 0 && b <= (72 >> 2);
    }

    private static boolean isWynnEffectMarker(int r, int g, int b) {
        return r >= 0 && r <= 3
                && g >= 240 && g <= 243
                && b >= 0 && b <= 39;
    }
}
