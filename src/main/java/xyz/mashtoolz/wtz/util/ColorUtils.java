package xyz.mashtoolz.wtz.util;

public class ColorUtils {

    public static int energyGradient(float ratio) {
        int r, g;
        if (ratio < 0.5f) {
            r = 255;
            g = (int) (255 * ratio / 0.5f);
        } else {
            r = (int) (255 * (1 - (ratio - 0.5f) / 0.5f));
            g = 255;
        }
        return (r << 16) | (g << 8);
    }
}
