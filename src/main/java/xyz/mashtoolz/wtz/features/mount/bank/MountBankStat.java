package xyz.mashtoolz.wtz.features.mount.bank;

public record MountBankStat(int level, int limit, int max) {
    public static MountBankStat from(int[] values) {
        return new MountBankStat(values[0], values[1], values[2]);
    }
}
