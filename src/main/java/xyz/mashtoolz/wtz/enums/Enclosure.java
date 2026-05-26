package xyz.mashtoolz.wtz.enums;

public enum Enclosure {
    TERNAVES("Ternaves", 825, -1606),
    BANTISU_AIR_TEMPLE("Bantisu Air Temple", 500, -4750),
    ALDWELL("Aldwell", -1276, -701);

    private final String displayName;
    private final int x;
    private final int z;

    Enclosure(String displayName, int x, int z) {
        this.displayName = displayName;
        this.x = x;
        this.z = z;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }
 
    public static Enclosure closest(double px, double pz) {
        Enclosure closest = null;
        double closestDist = Double.MAX_VALUE;
        for (Enclosure enc : values()) {
            double dx = px - enc.x;
            double dz = pz - enc.z;
            double dist = dx * dx + dz * dz;
            if (dist < closestDist) {
                closestDist = dist;
                closest = enc;
            }
        }
        return closest;
    }
}
