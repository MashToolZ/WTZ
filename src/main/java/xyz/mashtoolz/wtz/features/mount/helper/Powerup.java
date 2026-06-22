package xyz.mashtoolz.wtz.features.mount.helper;

import net.minecraft.util.math.Vec3d;

public class Powerup {

    private final Vec3d pos;
    private final int color;
    private final String name;
    private boolean maxed;

    public Powerup(Vec3d pos, int color, String name) {
        this.pos = pos;
        this.color = color;
        this.name = name;
        this.maxed = false;
    }

    public Vec3d pos() {
        return pos;
    }

    public int color() {
        return color;
    }

    public String name() {
        return name;
    }

    public boolean isMaxed() {
        return maxed;
    }

    public void setMaxed(boolean maxed) {
        this.maxed = maxed;
    }
}
